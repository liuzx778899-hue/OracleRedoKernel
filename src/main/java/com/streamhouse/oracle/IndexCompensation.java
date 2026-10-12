package com.streamhouse.oracle;

import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed regular index purge/restore compensation. Caller still owns complete ordered history coverage. */
public final class IndexCompensation {
    public record Purge(long block,int itl,int slot,int rowSize) {}
    public record LeafRollback(IndexUndoPair.Kind kind,long block,int itl,int slot,int rowSize) {}
    public record ArrayPurge(long block,int itl,int sno,int rowSize,List<Integer> slots) {}
    /** Raw108 multi-key PURGE: repeated slots are successive deletions, not duplicate owners. */
    public static ArrayPurge decodeArrayPurge(RedoRecords.Vector vector)throws RedoException {
        if(vector.opcodeMajor()!=10 || vector.opcodeMinor()!=3 || vector.fields().size()!=2)
            throw new RedoException("UNSUPPORTED_INDEX_ARRAY_PURGE_LAYOUT");
        byte[] ktb=vector.fields().get(0),leaf=vector.fields().get(1);
        boolean shortZ=ktb.length==8 && ktb[0]==3 && ktb[1]==13 && ktb[2]==0
                && (ktb[3]==0 || ktb[3]==(byte)0x80);
        if(ktb.length<2 || ktb[1]!=13 || !(ktb[0]==4 && ktb.length==32 || ktb[0]==2 && ktb.length==16 || shortZ)
                || leaf.length<12 || leaf[0]==0 || leaf[1]!=0x25 || u16(leaf,6)!=0 || u16(leaf,10)!=0)
            throw new RedoException("UNSUPPORTED_INDEX_ARRAY_PURGE_PROFILE");
        int count=u16(leaf,8);
        boolean paddedFifteen=count==15 && leaf.length==12+count*2+2 && u16(leaf,leaf.length-2)==0x9d72;
        if(count==0 || leaf.length!=12+count*2 && !paddedFifteen || u16(leaf,4)<count*4)
            throw new RedoException("INDEX_ARRAY_PURGE_COUNT_OR_SIZE");
        var slots=new java.util.ArrayList<Integer>();
        for(int i=0;i<count;i++)slots.add(u16(leaf,12+i*2));
        if(u16(leaf,2)!=slots.get(0))throw new RedoException("INDEX_ARRAY_PURGE_SNO");
        return new ArrayPurge(vector.dba(),leaf[0]&255,u16(leaf,2),u16(leaf,4),List.copyOf(slots));
    }
    static List<IndexUndoPair> matchArray(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                         List<IndexUndoPair> history,UndoHeader original,TransactionLifecycle state,
                                         IndexLeafPositions positions,ArchivedUndoInventory.Position at)throws RedoException {
        try {
            if(positions==null || at==null || !record.vectors().contains(marker) || !record.vectors().contains(row)
                    || marker.containerId()!=row.containerId())throw new RedoException("INDEX_ARRAY_COMPENSATION_SOURCE");
            var c=UndoCompensation.decode(marker);var purge=decodeArrayPurge(row);
            var owned=history.stream().filter(p->p.undo().identity().equals(original)).toList();
            if(marker.fields().size()!=3 || owned.size()!=UndoCompensation.arrayCancelledKeys(marker) || owned.size()!=purge.slots().size()
                    || c.beginsTransaction() || c.crossesUndoBlock() || c.opcodeMajor()!=10 || c.opcodeMinor()!=22)
                throw new RedoException("INDEX_ARRAY_COMPENSATION_COVERAGE_OR_BITMAP");
            int size=0;
            for(int i=0;i<owned.size();i++) {
                var p=owned.get(i);var u=p.undo();var f=p.forward();
                if(p.containerUid()!=record.containerUid() || p.containerId()!=row.containerId()
                        || original.xid().slot()!=c.slot() || (original.xid().sequence()&0xffff)!=c.wrap()
                        || original.uba().dba()!=c.dba() || original.objectId()!=c.objectId()
                        || original.dataObjectId()!=c.dataObjectId() || original.tablespace()!=c.tablespace()
                        || u.wrap()!=c.wrap() || u.previousDba()!=c.previousDba() || u.predecessorRecord()!=c.predecessorRecord()
                        || u.kind()!=IndexUndoPair.Kind.PURGE || f.kind()!=IndexUndoPair.Kind.INSERT
                        || f.block()!=purge.block() || f.itl()!=purge.itl()
                        || positions.slotBefore(p,record,marker,row,at,state,i)!=purge.slots().get(i))
                    throw new RedoException("INDEX_ARRAY_COMPENSATION_ORIGINAL_MISMATCH");
                size+=f.rowSize();state.requireActive(record,p);
            }
            if(size!=purge.rowSize())throw new RedoException("INDEX_ARRAY_COMPENSATION_TOTAL_SIZE");
            positions.matched(at);return owned;
        } catch(RedoException e){state.compensationRefused();if(positions!=null)positions.refused();throw e;}
    }
    public static Purge decodePurge(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=10 || vector.opcodeMinor()!=3)
            throw new RedoException("UNSUPPORTED_INDEX_PURGE_LAYOUT");
        var leaf=decodeRollback(vector);
        return new Purge(leaf.block(),leaf.itl(),leaf.slot(),leaf.rowSize());
    }
    public static LeafRollback decodeRollback(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=10 || (vector.opcodeMinor()!=3 && vector.opcodeMinor()!=5) || vector.fields().size()!=2
                || vector.fields().get(1).length!=6)
            throw new RedoException("UNSUPPORTED_INDEX_ROLLBACK_LAYOUT");
        byte[] ktb=vector.fields().get(0),leaf=vector.fields().get(1);
        boolean shortZPurge=vector.opcodeMinor()==3 && java.util.Arrays.equals(ktb,new byte[]{3,13,0,0,0,0,0,0});
        // Observed RESTORE short-Z bytes remain opaque; unobserved variants stay refused.
        boolean shortZRestore=vector.opcodeMinor()==5 && java.util.Arrays.equals(ktb,new byte[]{3,13,(byte)0xb8,0x71,0,0,0,0});
        if (ktb.length<2 || (ktb[1]&255)!=0x0d
                || !((ktb[0]==4 && ktb.length==32) || (ktb[0]==2 && ktb.length==16) || shortZPurge || shortZRestore)
                || leaf[0]==0 || leaf[1]!=1 || u16(leaf,4)<4)
            throw new RedoException("UNSUPPORTED_INDEX_ROLLBACK_MODE_OR_KTB");
        return new LeafRollback(vector.opcodeMinor()==3 ? IndexUndoPair.Kind.PURGE : IndexUndoPair.Kind.RESTORE,
                vector.dba(),leaf[0]&255,u16(leaf,2),u16(leaf,4));
    }

    public static IndexUndoPair match(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                      List<IndexUndoPair> history,TransactionLifecycle state) throws RedoException {
        return match(record,marker,row,history,state,null,null);
    }
    /** Source-routed LOB index purge; leaf position remains the exact observed forward slot. */
    public static IndexUndoPair matchLob(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                       List<IndexUndoPair> history,UndoHeader original)throws RedoException {
        if(marker.fields().size()!=2 || !record.vectors().contains(marker) || !record.vectors().contains(row) || marker.containerId()!=row.containerId())
            throw new RedoException("LOB_INDEX_COMPENSATION_RECORD_OR_CONTAINER");
        var c=UndoCompensation.decode(marker);var rollback=decodePurge(row);
        if(marker.opcodeMinor()!=6 || c.beginsTransaction() || c.crossesUndoBlock() || c.opcodeMajor()!=10 || c.opcodeMinor()!=22 || row.blockClass()!=1 || row.objectId()!=original.dataObjectId())
            throw new RedoException("LOB_INDEX_COMPENSATION_SCOPE_OR_OPCODE");
        IndexUndoPair match=null;
        for(var pair:history) {
            var u=pair.undo();var f=pair.forward();
            if(pair.containerUid()!=record.containerUid() || pair.containerId()!=row.containerId() || !u.identity().equals(original)
                    || c.dba()!=original.uba().dba() || c.slot()!=original.xid().slot() || c.objectId()!=original.objectId()
                    || c.dataObjectId()!=original.dataObjectId() || c.tablespace()!=original.tablespace() || c.wrap()!=u.wrap()
                    || c.previousDba()!=u.previousDba() || c.predecessorRecord()!=u.predecessorRecord() || u.kind()!=IndexUndoPair.Kind.PURGE
                    || f.kind()!=IndexUndoPair.Kind.INSERT || f.block()!=rollback.block() || f.itl()!=rollback.itl()
                    || f.slot()!=rollback.slot() || f.rowSize()!=rollback.rowSize())continue;
            if(match!=null)throw new RedoException("LOB_INDEX_COMPENSATION_AMBIGUOUS_ORIGINAL");match=pair;
        }
        if(match==null)throw new RedoException("LOB_INDEX_COMPENSATION_MISSING_ORIGINAL");
        IndexUndoPair.validateLobKeyData(match.forward().key(),match.forward().keydata());
        IndexUndoPair.validateLobKeyData(match.undo().key(),null);
        int rows=0;
        for(var candidate:record.vectors())if(candidate.containerId()==row.containerId() && candidate.opcodeMajor()==10
                && candidate.opcodeMinor()==3 && decodePurge(candidate).equals(rollback))rows++;
        if(rows!=1)throw new RedoException("LOB_INDEX_COMPENSATION_AMBIGUOUS_PURGE");
        return match;
    }
    /** Position correction is derived from an owned source inventory, never a caller-supplied slot. */
    public static IndexUndoPair match(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                      List<IndexUndoPair> history,TransactionLifecycle state,IndexLeafPositions positions,
                                      ArchivedUndoInventory.Position at) throws RedoException {
        try {
            if (positions!=null && at==null) throw new RedoException("INDEX_POSITION_QUERY_REQUIRED");
            if (!record.vectors().contains(marker) || !record.vectors().contains(row)
                    || marker.containerId()!=row.containerId())
                throw new RedoException("INDEX_COMPENSATION_RECORD_OR_CONTAINER_MISMATCH");
            if(marker.fields().size()!=2)throw new RedoException("UNSUPPORTED_SINGLE_INDEX_COMPENSATION_FIELDS");
            var c=UndoCompensation.decode(marker);
            if (marker.opcodeMinor()!=6 || c.beginsTransaction() || c.crossesUndoBlock()
                    || c.opcodeMajor()!=10 || c.opcodeMinor()!=22)
                throw new RedoException("UNSUPPORTED_INDEX_COMPENSATION_ASSOCIATION");
            var rollback=decodeRollback(row);
            IndexUndoPair match=null;
            for (var pair:history) {
                var u=pair.undo();var h=u.identity();var f=pair.forward();
                if (pair.containerUid()!=record.containerUid() || pair.containerId()!=marker.containerId()
                        || h.beginsTransaction() || h.xid().slot()!=c.slot() || (h.xid().sequence()&0xffff)!=c.wrap()
                        || u.wrap()!=c.wrap() || h.uba().dba()!=c.dba() || h.objectId()!=c.objectId()
                        || h.dataObjectId()!=c.dataObjectId() || h.tablespace()!=c.tablespace()
                        || u.previousDba()!=c.previousDba() || u.predecessorRecord()!=c.predecessorRecord()
                        || u.kind()!=rollback.kind() || f.kind()!=(rollback.kind()==IndexUndoPair.Kind.PURGE ? IndexUndoPair.Kind.INSERT : IndexUndoPair.Kind.DELETE)
                        || u.block()!=rollback.block() || f.itl()!=rollback.itl()
                        || f.rowSize()!=rollback.rowSize()) continue;
                int slot=positions==null ? f.slot() : positions.slotBefore(pair,record,marker,row,at,state);
                if (slot!=rollback.slot()) continue;
                if (match!=null) throw new RedoException("AMBIGUOUS_INDEX_COMPENSATION_ORIGINAL");
                match=pair;
            }
            if (match==null) throw new RedoException("MISSING_INDEX_COMPENSATION_ORIGINAL");
            int rows=0;
            for (var v:record.vectors()) if (v.containerId()==row.containerId() && v.dba()==row.dba()
                    && v.opcodeMajor()==10 && (v.opcodeMinor()==3 || v.opcodeMinor()==5) && decodeRollback(v).equals(rollback)) rows++;
            if (rows!=1) throw new RedoException("AMBIGUOUS_INDEX_COMPENSATION_LEAF");
            state.requireActive(record,match);
            if (positions!=null) positions.matched(at);
            return match;
        } catch (RedoException e) { state.compensationRefused();if (positions!=null) positions.refused();throw e; }
    }
}
