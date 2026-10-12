package com.streamhouse.oracle;

import java.util.*;

/** Every selected object/storage index original must refer to one parsed row of the same SYS owner. */
final class DictionaryObjectDependencies {
    static void require(List<ArchivedDictionaryHistory.RowOperation> rows,List<DictionaryObjectIndexPair.Pair> indexes,
            List<DictionaryDataObjectIndexPair> dataIndexes,List<HeapUndoPair> deferredRows,List<IndexUndoPair> deferredIndexes)throws RedoException {
        var used=new HashSet<UndoHeader>();var usedData=new HashSet<UndoHeader>();var usedDeferred=new HashSet<UndoHeader>();
        var objects=rows.stream().filter(r->r.role().name().equals("OBJ$")).toList();
        for(var object:objects) {
            var forward=object.forward();var inverse=object.inverse();
            if(forward.kind()==HeapRowPatch.Kind.INSERT || forward.kind()==HeapRowPatch.Kind.DELETE) {
                var image=forward.kind()==HeapRowPatch.Kind.INSERT?forward:inverse;
                var refs=indexes.stream().filter(i->i.rowDba()==image.dba() && i.rowSlot()==image.slot()
                        && (i.physical().forward().kind()==IndexUndoPair.Kind.DELETE)==(forward.kind()==HeapRowPatch.Kind.DELETE)).toList();
                var dataRefs=dataIndexes.stream().filter(i->i.rowDba()==image.dba() && i.rowSlot()==image.slot()
                        && (i.physical().forward().kind()==IndexUndoPair.Kind.DELETE)==(forward.kind()==HeapRowPatch.Kind.DELETE)).toList();
                if(refs.size()!=3 || dataRefs.size()!=1 || !new HashSet<>(refs.stream().map(DictionaryObjectIndexPair.Pair::logical).toList()).equals(Set.of(36L,37L,40L)))
                    fail("OBJECT_INDEX_COVERAGE");
                for(var ref:refs) {
                    DictionaryObjectIndexPair.requireObject(ref,image,object.identity());
                    if(!used.add(ref.physical().undo().identity()))fail("OBJECT_INDEX_REUSE");
                    if(ref.physical().forward().kind()==IndexUndoPair.Kind.RESTORE) {
                        byte[] oldRow=ref.physical().undo().keydata();long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(oldRow[i]&255);
                        long oldDba=dba;int slot=((oldRow[4]&255)<<8)|(oldRow[5]&255);
                        var prior=objects.stream().filter(r->r.forward().kind()==HeapRowPatch.Kind.DELETE && r.inverse().dba()==oldDba && r.inverse().slot()==slot).toList();
                        if(prior.size()!=1)fail("OBJECT_RELOCATION_BEFORE_MISSING");
                        DictionaryObjectIndexPair.requireRelocation(ref,prior.get(0).inverse(),image,object.identity());
                        DictionaryObjectRebind.requireRows(prior.get(0).inverse(),image);
                    }
                }
                dataRefs.get(0).requireObject(image,object.identity());
                if(!usedData.add(dataRefs.get(0).physical().undo().identity()))fail("DATA_INDEX_REUSE");
            } else if(forward.kind()==HeapRowPatch.Kind.UPDATE) {
                var old=inverse.columns().get(1);var next=forward.columns().get(1);
                boolean changed=old.state()!=next.state() || !Arrays.equals(old.bytes(),next.bytes());
                if(!changed)continue;
                var refs=dataIndexes.stream().filter(i->i.rowDba()==forward.dba() && i.rowSlot()==forward.slot()
                        && !usedData.contains(i.physical().undo().identity())).toList();
                if(refs.size()!=2)fail("DATA_UPDATE_INDEX_COVERAGE:"+object.identity()+":"+forward.dba()+"/"+forward.slot()+":"+refs.size());
                var kinds=new HashSet<IndexUndoPair.Kind>();
                for(var ref:refs) {
                    var kind=ref.physical().forward().kind();kinds.add(kind);
                    ref.requireObject(kind==IndexUndoPair.Kind.DELETE?inverse:forward,object.identity());
                    if(!usedData.add(ref.physical().undo().identity()))fail("DATA_INDEX_REUSE");
                }
                if(changed && !kinds.equals(Set.of(IndexUndoPair.Kind.DELETE,IndexUndoPair.Kind.INSERT)))fail("DATA_UPDATE_OPERATIONS");
            }
        }
        for(var row:deferredRows) {
            var image=row.forward().kind()==HeapRowPatch.Kind.INSERT?row.forward():row.undo().inverse();
            int matches=0;
            for(var index:deferredIndexes) {
                byte[] data=index.forward().kind()==IndexUndoPair.Kind.INSERT?index.forward().keydata():index.undo().keydata();
                long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
                if(dba!=image.dba() || (((data[4]&255)<<8)|(data[5]&255))!=image.slot())continue;
                DictionaryDeferredStoragePair.requireRow(index,row);matches++;
                if(!usedDeferred.add(index.undo().identity()))fail("DEFERRED_INDEX_REUSE");
            }
            if(matches!=1)fail("DEFERRED_INDEX_COVERAGE");
        }
        if(used.size()!=indexes.size() || usedData.size()!=dataIndexes.size() || usedDeferred.size()!=deferredIndexes.size())fail("UNASSOCIATED_INDEX");
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_OBJECT_DEPENDENCY_"+detail);}
}
