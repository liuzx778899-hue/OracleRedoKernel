package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Exact same-record forward / inverse association, not complete row images or CDC events. */
public record HeapUndoPair(long containerUid,long containerId,Patch forward,UndoRowPatch undo) {
    public static HeapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,int columns) throws RedoException {
        return bind(record,row,columns,false);
    }
    static HeapUndoPair bindDictionaryObject(RedoRecords.Record record,RedoRecords.Vector row,int columns) throws RedoException {
        return bind(record,row,columns,true);
    }
    private static HeapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,int columns,boolean dictionaryObject) throws RedoException {
        return bind(record,row,columns,dictionaryObject,false);
    }
    public static HeapUndoPair bindLob(RedoRecords.Record record,RedoRecords.Vector row,int columns)throws RedoException {
        if(columns<1 || columns>255)throw new RedoException("UNSUPPORTED_LOB_HEAP_COLUMN_PROFILE");
        return bind(record,row,columns,false,true);
    }
    private static HeapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,int columns,boolean dictionaryObject,boolean lobHeap) throws RedoException {
        if (!record.vectors().contains(row)) throw new RedoException("ROW_VECTOR_NOT_IN_RECORD");
        var decoder=new HeapRowPatch();var p=lobHeap?decoder.decodeLob(row,columns):dictionaryObject?decoder.decodeDictionaryObject(row,columns):decoder.decode(row,columns);
        if (p.xtype()!=1 || p.uba()==null) throw new RedoException("NOT_FORWARD_ROW_UNDO_REFERENCE");
        UndoRowPatch match=null;
        int rowMatches=0;
        for (var v:record.vectors()) {
            if (v.containerId()!=row.containerId()) continue;
            if (v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==4 || v.opcodeMinor()==5)
                    && v.dba()==row.dba()) {
                var other=lobHeap?decoder.decodeLob(v,columns):dictionaryObject?decoder.decodeDictionaryObject(v,columns):decoder.decode(v,columns);
                if (other.xtype()==1 && p.uba().equals(other.uba())) rowMatches++;
            }
            if (v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=p.uba().dba()) continue;
            if (v.fields().isEmpty() || v.fields().get(0).length!=20)
                throw new RedoException("UNREADABLE_ROW_UNDO_CANDIDATE");
            byte[] b=v.fields().get(0);
            if (u16(b,16)!=p.uba().sequence() || (b[18]&255)!=p.uba().record()) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_ROW_UNDO_UBA");
            match=lobHeap?UndoRowPatch.decodeLob(v,columns):UndoRowPatch.decode(v,columns,dictionaryObject);
        }
        if (rowMatches!=1) throw new RedoException("AMBIGUOUS_FORWARD_ROW_UBA");
        if (match==null) throw new RedoException("MISSING_ROW_UNDO_UBA");
        var before=match.inverse();
        Kind inverse=p.kind()==Kind.INSERT ? Kind.DELETE : p.kind()==Kind.DELETE ? Kind.INSERT : p.kind();
        if (before.kind()!=inverse || before.dba()!=p.dba() || before.slot()!=p.slot()
                || (p.xid()!=null && !p.xid().equals(match.identity().xid())))
            throw new RedoException("ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH");
        if (p.kind()==Kind.LOCK && (!Integer.valueOf(lobHeap?row.fields().get(1)[12]&255:1).equals(p.lockTo()) || !Integer.valueOf(0).equals(before.lockTo())))
            throw new RedoException("UNSUPPORTED_HEAP_LOCK_TRANSITION");
        if (p.kind()==Kind.UPDATE) {
            var extent=p.extent();var prior=before.extent();boolean tail=extent.removedColumn()>=0;
            // Raw117/native URP changes existing LOB locators and materializes one trailing NULL column.
            boolean lobAppend=lobHeap && !tail && prior!=null && prior.removedColumn()==-1
                    && extent.storedColumns()==prior.storedColumns()+1 && extent.changedColumns()==prior.changedColumns()+1
                    && extent.sizeDelta()!=null && prior.sizeDelta()!=null && extent.sizeDelta()==-prior.sizeDelta();
            if(lobAppend) {
                int added=prior.storedColumns();
                if(added>=columns || p.columns().get(added).state()!=State.VALUE || before.columns().get(added).state()!=State.ABSENT)
                    throw new RedoException("LOB_ROW_APPEND_COLUMN_MISMATCH");
                int delta=0;
                for(int i=0;i<columns;i++) {
                    var f=p.columns().get(i);var b=before.columns().get(i);
                    if(i!=added && (f.state()==State.ABSENT)!=(b.state()==State.ABSENT))throw new RedoException("LOB_ROW_APPEND_CHANGED_COLUMNS");
                    if(f.state()!=State.ABSENT)delta+=f.bytes().length+1;
                    if(b.state()!=State.ABSENT)delta-=b.bytes().length+1;
                }
                if(delta!=extent.sizeDelta())throw new RedoException("LOB_ROW_APPEND_SIZE_DELTA");
                var values=new java.util.ArrayList<>(before.columns());values.set(added,new Column(State.NULL,new byte[0]));
                before=new Patch(before.kind(),before.dba(),before.slot(),before.xtype(),before.xid(),before.uba(),java.util.List.copyOf(values),prior,before.lockTo());
                match=new UndoRowPatch(match.identity(),before,match.previousDba(),match.predecessorRecord(),match.wrap());
            }
            if (tail) {
                int removed=extent.removedColumn();var value=before.columns().get(removed);
                boolean suffixTwo=columns==3 && extent.storedColumns()==1 && removed==1 && prior!=null
                        && prior.storedColumns()==3 && prior.changedColumns()==2 && prior.removedColumn()==-1
                        && p.columns().get(1).state()==State.NULL && p.columns().get(2).state()==State.NULL
                        && before.columns().get(1).state()==State.VALUE && before.columns().get(2).state()==State.VALUE
                        && before.columns().get(1).bytes().length<=250 && before.columns().get(2).bytes().length<=250
                        && extent.sizeDelta()!=null && extent.sizeDelta()==-(before.columns().get(1).bytes().length+before.columns().get(2).bytes().length+2)
                        && prior.sizeDelta()!=null && prior.sizeDelta()==-extent.sizeDelta();
                if (!suffixTwo && (prior==null || prior.removedColumn()!=-1 || prior.storedColumns()!=extent.storedColumns()+1
                        || prior.changedColumns()!=1 || value.state()!=State.VALUE || value.bytes().length>250
                        || extent.sizeDelta()!=-(value.bytes().length+1)))
                    throw new RedoException("ROW_UNDO_TAIL_EXTENT_MISMATCH");
            } else if (prior!=null && prior.removedColumn()>=0) {
                int added=prior.removedColumn();var value=p.columns().get(added);
                if (extent.storedColumns()!=prior.storedColumns()+1 || extent.changedColumns()!=1
                        || value.state()!=State.VALUE || value.bytes().length>250
                        || extent.sizeDelta()==null || extent.sizeDelta()!=value.bytes().length+1
                        || prior.sizeDelta()!=-extent.sizeDelta())
                    throw new RedoException("ROW_UNDO_TAIL_EXTENT_MISMATCH");
            } else if (!lobAppend && (prior==null || extent.storedColumns()!=prior.storedColumns()))
                throw new RedoException("UNSUPPORTED_ROW_UNDO_EXTENT_TRANSITION");
            for (int i=0;i<columns;i++) {
                if (tail && i==extent.removedColumn()) continue;
                if (!tail && prior.removedColumn()==i) continue;
                if ((p.columns().get(i).state()==State.ABSENT)!=(before.columns().get(i).state()==State.ABSENT))
                    throw new RedoException("ROW_UNDO_CHANGED_COLUMN_SET_MISMATCH");
            }
        }
        return new HeapUndoPair(record.containerUid(),row.containerId(),p,match);
    }
}
