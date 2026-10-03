package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.List;

/** Matches observed heap compensation. Does not cancel an event or prove the complete undo chain. */
public final class HeapCompensation {
    public static HeapUndoPair match(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                    List<HeapUndoPair> history,int columns) throws RedoException {
        return match(record,marker,row,history,columns,null);
    }

    public static HeapUndoPair matchBegin(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                         List<HeapUndoPair> history,int columns,TransactionLifecycle state) throws RedoException {
        try {
            var xid=state.beginCompensationIdentity(record,marker);
            var pair=match(record,marker,row,history,columns,xid);
            state.requireActive(record,pair);
            return pair;
        } catch (RedoException e) { state.compensationRefused(); throw e; }
    }

    private static HeapUndoPair match(RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                                     List<HeapUndoPair> history,int columns,HeapRowPatch.Xid beginXid) throws RedoException {
        if (!record.vectors().contains(marker) || !record.vectors().contains(row)
                || marker.containerId()!=row.containerId())
            throw new RedoException("COMPENSATION_RECORD_OR_CONTAINER_MISMATCH");
        var c=UndoCompensation.decode(marker);
        if (c.beginsTransaction()!=(beginXid!=null) || c.opcodeMajor()!=11 || c.opcodeMinor()!=1)
            throw new RedoException("UNSUPPORTED_HEAP_COMPENSATION_ASSOCIATION");
        var rollback=new HeapRowPatch().decode(row,columns);
        if (rollback.xtype()!=2) throw new RedoException("NOT_HEAP_COMPENSATION_ROW");
        HeapUndoPair match=null;
        for (var pair:history) {
            var u=pair.undo(); var h=u.identity();
            if (pair.containerUid()!=record.containerUid() || pair.containerId()!=marker.containerId()
                    || h.beginsTransaction()!=c.beginsTransaction() || h.xid().slot()!=c.slot() || u.wrap()!=c.wrap()
                    || h.objectId()!=c.objectId() || h.dataObjectId()!=c.dataObjectId() || h.tablespace()!=c.tablespace()
                    || u.previousDba()!=c.previousDba() || u.predecessorRecord()!=c.predecessorRecord()
                    || !sameInverse(u.inverse(),rollback)) continue;
            if (beginXid!=null ? !h.xid().equals(beginXid)
                    : h.uba().dba()!=c.dba() || (h.xid().sequence()&0xffff)!=c.wrap()) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_HEAP_COMPENSATION_ORIGINAL");
            match=pair;
        }
        if (match==null) throw new RedoException("MISSING_HEAP_COMPENSATION_ORIGINAL");
        int rows=0;
        for (var v:record.vectors()) {
            if (v.containerId()!=row.containerId() || v.dba()!=row.dba() || v.opcodeMajor()!=11
                    || (v.opcodeMinor()!=2 && v.opcodeMinor()!=3 && v.opcodeMinor()!=5)) continue;
            var other=new HeapRowPatch().decode(v,columns);
            if (other.xtype()==2 && sameInverse(other,rollback)) rows++;
        }
        if (rows!=1) throw new RedoException("AMBIGUOUS_HEAP_COMPENSATION_ROW");
        return match;
    }

    private static boolean sameInverse(HeapRowPatch.Patch a,HeapRowPatch.Patch b) {
        if (a.kind()!=b.kind() || a.dba()!=b.dba() || a.slot()!=b.slot() || a.columns().size()!=b.columns().size()) return false;
        for (int i=0;i<a.columns().size();i++) {
            var x=a.columns().get(i); var y=b.columns().get(i);
            if (x.state()!=y.state() || !Arrays.equals(x.bytes(),y.bytes())) return false;
        }
        return true;
    }
}
