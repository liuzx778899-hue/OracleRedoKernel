package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Observed raw120 CON$ exchange: both constraint numbers retain complete same-owner row/index evidence. */
final class DictionaryConstraintExchange {
    static Set<UndoHeader> require(List<DictionaryRedefinitionAuxiliary.Row> all,List<DictionaryRedefinitionAuxiliary.Index> indexes,
            Set<UndoHeader> used)throws RedoException {
        var rows=all.stream().filter(r->r.logical()==28).map(DictionaryRedefinitionAuxiliary.Row::physical).toList();
        var deletes=rows.stream().filter(r->r.forward().kind()==Kind.DELETE).toList();
        if(deletes.isEmpty())return Set.of();
        var covered=new HashSet<UndoHeader>();
        for(var deleted:deletes) {
            var before=deleted.undo().inverse();
            var matches=rows.stream().filter(r->r.forward().kind()==Kind.INSERT && equal(r.forward(),before,0) && equal(r.forward(),before,1)).toList();
            if(matches.isEmpty())continue;
            if(matches.size()!=1)fail("INSERT_COVERAGE");var inserted=matches.get(0);var after=inserted.forward();
            if(before.extent().storedColumns()!=4 || after.extent().storedColumns()!=4 || equal(before,after,2))fail("NUMBER_EXCHANGE");
            for(int i=0;i<9;i++)if(i!=2 && !equal(before,after,i))fail("ROW_IDENTITY_CHANGED");
            var swaps=rows.stream().filter(r->r.forward().kind()==Kind.UPDATE && equal(r.undo().inverse(),after,2) && equal(r.forward(),before,2)).toList();
            var noops=rows.stream().filter(r->r.forward().kind()==Kind.UPDATE && r.forward().dba()==after.dba() && r.forward().slot()==after.slot()
                    && equal(r.forward(),after,2) && equal(r.undo().inverse(),after,2)).toList();
            if(swaps.size()!=1 || noops.size()!=1)fail("UPDATE_COVERAGE");var swap=swaps.get(0);var noop=noops.get(0);
            for(var update:List.of(swap,noop))for(int i=0;i<9;i++) {
                var a=update.forward().columns().get(i);var b=update.undo().inverse().columns().get(i);
                if(i==2?a.state()!=State.VALUE || b.state()!=State.VALUE:a.state()!=State.ABSENT || b.state()!=State.ABSENT)fail("UPDATE_COLUMNS");
            }
            for(var row:List.of(deleted,inserted,swap,noop)) {
                if(row.containerUid()!=deleted.containerUid() || row.containerId()!=deleted.containerId()
                        || !row.undo().identity().xid().equals(deleted.undo().identity().xid()) || !covered.add(row.undo().identity()))fail("ROW_OWNER_OR_REUSE");
            }
            reference(indexes,used,51,IndexUndoPair.Kind.DELETE,before,new int[]{0,1},before,null,deleted);
            reference(indexes,used,52,IndexUndoPair.Kind.DELETE,before,new int[]{2},before,null,deleted);
            reference(indexes,used,52,IndexUndoPair.Kind.DELETE,swap.undo().inverse(),new int[]{2},swap.undo().inverse(),null,deleted);
            reference(indexes,used,52,IndexUndoPair.Kind.RESTORE,swap.forward(),new int[]{2},swap.forward(),before,deleted);
            reference(indexes,used,51,IndexUndoPair.Kind.RESTORE,after,new int[]{0,1},after,before,deleted);
            reference(indexes,used,52,IndexUndoPair.Kind.RESTORE,after,new int[]{2},after,swap.undo().inverse(),deleted);
        }
        return Set.copyOf(covered);
    }
    private static void reference(List<DictionaryRedefinitionAuxiliary.Index> indexes,Set<UndoHeader> used,long logical,
            IndexUndoPair.Kind kind,Patch values,int[] columns,Patch destination,Patch origin,HeapUndoPair owner)throws RedoException {
        var matches=indexes.stream().filter(i->i.logical()==logical && i.physical().forward().kind()==kind
                && i.rowDba()==destination.dba() && i.rowSlot()==destination.slot()).toList();
        if(matches.size()!=1)fail("INDEX_COVERAGE");var index=matches.get(0);var pair=index.physical();
        if(pair.containerUid()!=owner.containerUid() || pair.containerId()!=owner.containerId()
                || !pair.undo().identity().xid().equals(owner.undo().identity().xid()) || !used.add(pair.undo().identity()))fail("INDEX_OWNER_OR_REUSE");
        for(int i=0;i<columns.length;i++)if(values.columns().get(columns[i]).state()!=State.VALUE
                || !Arrays.equals(values.columns().get(columns[i]).bytes(),index.key().get(i)))fail("INDEX_KEY");
        if(origin!=null) {
            byte[] data=pair.undo().keydata();if(data==null || data.length!=6)fail("RELOCATION_ROWID");
            long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
            if(dba!=origin.dba() || (((data[4]&255)<<8)|(data[5]&255))!=origin.slot())fail("RELOCATION_ORIGIN");
        }
    }
    private static boolean equal(Patch a,Patch b,int column) {
        return a.columns().get(column).state()==b.columns().get(column).state()
                && Arrays.equals(a.columns().get(column).bytes(),b.columns().get(column).bytes());
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_CONSTRAINT_EXCHANGE_"+detail);}
}
