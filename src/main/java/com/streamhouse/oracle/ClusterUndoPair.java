package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Exact same-record clustered forward/inverse association. Parent object and SCN history remain separate gates. */
public record ClusterUndoPair(long containerUid,long containerId,FrozenHeapBaseline.ObjectRole role,
                              ClusterRowPatch.Patch forward,UndoHeader identity,ClusterRowPatch.Patch inverse) {
    private record Current(Xid xid,Uba uba) {}
    private static Current current(RedoRecords.Vector v) throws RedoException {
        if(v.fields().isEmpty())throw new RedoException("MISSING_CLUSTER_FORWARD_KTB");
        byte[] b=v.fields().get(0);int offset;Xid xid=null;
        if(b.length<2 || b[1]!=0x0d)throw new RedoException("UNSUPPORTED_CLUSTER_FORWARD_KTB");
        if((b[0]==1 && b.length==24) || (b[0]==17 && (b.length==70 || b.length==80 && b[44]==2 && b[45]==2))) {
            xid=new Xid(u16(b,8),u16(b,10),u32(b,12));offset=16;
        } else if(b[0]==2 && b.length==16)offset=8;
        else throw new RedoException("UNSUPPORTED_CLUSTER_FORWARD_KTB");
        var uba=new Uba(u32(b,offset),u16(b,offset+4),b[offset+6]&255);
        if(uba.dba()==0 || uba.record()==0)throw new RedoException("INVALID_CLUSTER_FORWARD_UBA");
        return new Current(xid,uba);
    }
    public static ClusterUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,
                                        FrozenHeapBaseline.ObjectRole role,int tableNumber,int columns) throws RedoException {
        if(!record.vectors().contains(row))throw new RedoException("CLUSTER_ROW_NOT_IN_RECORD");
        if(row.opcodeMajor()!=11 || row.opcodeMinor()<2 || row.opcodeMinor()>5)
            throw new RedoException("UNSUPPORTED_CLUSTER_FORWARD_OPCODE");
        if(!role.owner().equals("SYS") || !(role.name().equals("TAB$") && tableNumber==1
                || role.name().equals("COL$") && tableNumber==5))
            throw new RedoException("CLUSTER_DICTIONARY_ROLE_OR_TABLE_MISMATCH");
        var ref=current(row);var forward=ClusterRowPatch.decode(row.fields(),row.opcodeMinor(),row.dba(),tableNumber,columns);
        RedoRecords.Vector original=null;int forwards=0;
        for(var v:record.vectors()) {
            if(v.containerId()!=row.containerId())continue;
            if(v.opcodeMajor()==11 && v.dba()==row.dba() && v.opcodeMinor()>=2 && v.opcodeMinor()<=5
                    && current(v).uba().equals(ref.uba()))forwards++;
            if(v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=ref.uba().dba())continue;
            if(v.fields().isEmpty() || v.fields().get(0).length!=20)throw new RedoException("UNREADABLE_CLUSTER_UNDO_CANDIDATE");
            byte[] b=v.fields().get(0);
            if(u16(b,16)!=ref.uba().sequence() || (b[18]&255)!=ref.uba().record())continue;
            if(original!=null)throw new RedoException("AMBIGUOUS_CLUSTER_UNDO_UBA");
            original=v;
        }
        if(forwards!=1)throw new RedoException("AMBIGUOUS_CLUSTER_FORWARD_UBA");
        if(original==null)throw new RedoException("MISSING_CLUSTER_UNDO_UBA");
        var h=UndoHeader.decode(original);var fields=original.fields();
        if(h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0
                || fields.size()<4 || fields.get(2).length<2)
            throw new RedoException("CLUSTER_UNDO_LOGICAL_ROLE_MISMATCH");
        byte[] m=fields.get(1),b=fields.get(2);
        if(m[16]!=11 || m[17]!=1 || (m[18]&255)!=h.xid().slot()
                || (!h.beginsTransaction() && u16(m,22)!=(h.xid().sequence()&0xffff)))
            throw new RedoException("CLUSTER_UNDO_IDENTITY_MISMATCH");
        if(b[1]!=0x0d || !((b[0]==3 && b.length==8) || (b[0]==2 && b.length==16) || (b[0]==4 && b.length==32)))
            throw new RedoException("UNSUPPORTED_CLUSTER_INVERSE_KTB");
        if(fields.get(3).length<11)throw new RedoException("TRUNCATED_CLUSTER_INVERSE_KDO");
        var inverse=ClusterRowPatch.decode(fields.subList(2,fields.size()),fields.get(3)[10]&255,row.dba(),tableNumber,columns);
        var p=forward.row();var prior=inverse.row();Kind expected=p.kind()==Kind.INSERT ? Kind.DELETE : p.kind()==Kind.DELETE ? Kind.INSERT : p.kind();
        if(prior.kind()!=expected || prior.slot()!=p.slot() || (ref.xid()!=null && !ref.xid().equals(h.xid())))
            throw new RedoException("CLUSTER_UNDO_OPERATION_SLOT_OR_XID_MISMATCH");
        if(p.kind()==Kind.LOCK && (!Integer.valueOf(2).equals(p.lockTo()) || !Integer.valueOf(0).equals(prior.lockTo())))
            throw new RedoException("UNSUPPORTED_CLUSTER_LOCK_TRANSITION");
        if(p.kind()==Kind.UPDATE) {
            var a=p.extent();var z=prior.extent();
            if(a.storedColumns()!=z.storedColumns() || a.changedColumns()!=z.changedColumns()
                    || a.sizeDelta()+z.sizeDelta()!=0)
                throw new RedoException("CLUSTER_UNDO_UPDATE_EXTENT_MISMATCH");
            for(int i=0;i<columns;i++)if((p.columns().get(i).state()==State.ABSENT)!=(prior.columns().get(i).state()==State.ABSENT))
                throw new RedoException("CLUSTER_UNDO_CHANGED_COLUMN_SET_MISMATCH");
        }
        var bound=new Patch(p.kind(),p.dba(),p.slot(),p.xtype(),ref.xid(),ref.uba(),p.columns(),p.extent(),p.lockTo());
        return new ClusterUndoPair(record.containerUid(),row.containerId(),role,
                new ClusterRowPatch.Patch(forward.tableNumber(),forward.clusterKeyIndex(),bound),h,inverse);
    }
}
