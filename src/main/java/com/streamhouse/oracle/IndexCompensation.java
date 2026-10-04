package com.streamhouse.oracle;

import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed regular index purge/restore compensation. Caller still owns complete ordered history coverage. */
public final class IndexCompensation {
    public record Purge(long block,int itl,int slot,int rowSize) {}
    public record LeafRollback(IndexUndoPair.Kind kind,long block,int itl,int slot,int rowSize) {}
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
    /** Position correction is derived from an owned source inventory, never a caller-supplied slot. */
    public static IndexUndoPair match(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                      List<IndexUndoPair> history,TransactionLifecycle state,IndexLeafPositions positions,
                                      ArchivedUndoInventory.Position at) throws RedoException {
        try {
            if (positions!=null && at==null) throw new RedoException("INDEX_POSITION_QUERY_REQUIRED");
            if (!record.vectors().contains(marker) || !record.vectors().contains(row)
                    || marker.containerId()!=row.containerId())
                throw new RedoException("INDEX_COMPENSATION_RECORD_OR_CONTAINER_MISMATCH");
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
