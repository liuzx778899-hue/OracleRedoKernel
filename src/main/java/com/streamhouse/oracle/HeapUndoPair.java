package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Exact same-record forward / inverse association, not complete row images or CDC events. */
public record HeapUndoPair(long containerUid,long containerId,Patch forward,UndoRowPatch undo) {
    public static HeapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,int columns) throws RedoException {
        if (!record.vectors().contains(row)) throw new RedoException("ROW_VECTOR_NOT_IN_RECORD");
        var p=new HeapRowPatch().decode(row,columns);
        if (p.xtype()!=1 || p.uba()==null) throw new RedoException("NOT_FORWARD_ROW_UNDO_REFERENCE");
        UndoRowPatch match=null;
        int rowMatches=0;
        for (var v:record.vectors()) {
            if (v.containerId()!=row.containerId()) continue;
            if (v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==5)
                    && v.dba()==row.dba()) {
                var other=new HeapRowPatch().decode(v,columns);
                if (other.xtype()==1 && p.uba().equals(other.uba())) rowMatches++;
            }
            if (v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=p.uba().dba()) continue;
            if (v.fields().isEmpty() || v.fields().get(0).length!=20)
                throw new RedoException("UNREADABLE_ROW_UNDO_CANDIDATE");
            byte[] b=v.fields().get(0);
            if (u16(b,16)!=p.uba().sequence() || (b[18]&255)!=p.uba().record()) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_ROW_UNDO_UBA");
            match=UndoRowPatch.decode(v,columns);
        }
        if (rowMatches!=1) throw new RedoException("AMBIGUOUS_FORWARD_ROW_UBA");
        if (match==null) throw new RedoException("MISSING_ROW_UNDO_UBA");
        var before=match.inverse();
        Kind inverse=p.kind()==Kind.INSERT ? Kind.DELETE : p.kind()==Kind.DELETE ? Kind.INSERT : Kind.UPDATE;
        if (before.kind()!=inverse || before.dba()!=p.dba() || before.slot()!=p.slot()
                || (p.xid()!=null && !p.xid().equals(match.identity().xid())))
            throw new RedoException("ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH");
        if (p.kind()==Kind.UPDATE) {
            var extent=p.extent();var prior=before.extent();boolean tail=extent.removedColumn()>=0;
            if (tail) {
                int removed=extent.removedColumn();var value=before.columns().get(removed);
                if (prior==null || prior.removedColumn()!=-1 || prior.storedColumns()!=extent.storedColumns()+1
                        || prior.changedColumns()!=1 || value.state()!=State.VALUE || value.bytes().length>250
                        || extent.sizeDelta()!=-(value.bytes().length+1))
                    throw new RedoException("ROW_UNDO_TAIL_EXTENT_MISMATCH");
            } else if (prior==null || extent.storedColumns()!=prior.storedColumns())
                throw new RedoException("UNSUPPORTED_ROW_UNDO_EXTENT_TRANSITION");
            for (int i=0;i<columns;i++) {
                if (tail && i==extent.removedColumn()) continue;
                if ((p.columns().get(i).state()==State.ABSENT)!=(before.columns().get(i).state()==State.ABSENT))
                    throw new RedoException("ROW_UNDO_CHANGED_COLUMN_SET_MISMATCH");
            }
        }
        return new HeapUndoPair(record.containerUid(),row.containerId(),p,match);
    }
}
