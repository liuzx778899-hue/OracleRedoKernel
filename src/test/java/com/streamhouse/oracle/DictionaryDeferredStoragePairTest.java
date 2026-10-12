package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryDeferredStoragePairTest {
    private List<RedoRecords.Record> records()throws Exception {
        return new DictionaryObjectUndoPairTest().records("/dictionary-deferred-storage-raw120.txt");
    }
    private IndexUndoPair index(RedoRecords.Record record)throws Exception {
        var old=record.vectors().get(1);var v=new RedoRecords.Vector(old.opcodeMajor(),old.opcodeMinor(),1,old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),old.fields(),82);
        var owned=new RedoRecords.Record(record.block(),record.offset(),record.length(),record.validity(),record.scn(),record.subScn(),record.containerUid(),List.of(record.vectors().get(0),v));
        return DictionaryDeferredStoragePair.bindIndex(owned,v);
    }
    @Test void raw120StorageInsertAndDeleteMatchTheirNumericObjectIndex()throws Exception {
        var records=records();assertEquals(4,records.size());
        for(int i:new int[]{0,2}) {
            var record=records.get(i);var row=DictionaryDeferredStoragePair.bind(record,record.vectors().get(1));
            DictionaryDeferredStoragePair.requireRow(index(records.get(i+1)),row);
            var values=i==0?row.forward():row.undo().inverse();assertEquals(25,values.columns().size());
            assertEquals(HeapRowPatch.State.NULL,values.columns().get(3).state());
        }
    }
    @Test void corruptedBitmapOwnerSizeAndIndexCannotAuthorizeDeferredStorage()throws Exception {
        for(int at:new int[]{18,40,45,48}) {
            var record=records().get(0);record.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryDeferredStoragePair.bind(record,record.vectors().get(1)));
        }
        var record=records().get(0);record.vectors().get(1).fields().get(0)[12]^=1;
        assertThrows(RedoException.class,()->DictionaryDeferredStoragePair.bind(record,record.vectors().get(1)));
        var good=records().get(0);var row=DictionaryDeferredStoragePair.bind(good,good.vectors().get(1));
        var wrong=records().get(1);wrong.vectors().get(1).fields().get(3)[5]^=1;
        assertThrows(RedoException.class,()->DictionaryDeferredStoragePair.requireRow(index(wrong),row));
        assertThrows(RedoException.class,()->IndexUndoPair.bindDictionary(records().get(1),records().get(1).vectors().get(1)));
    }
    @Test void raw120CurrentOwnerRemainsSeparateFromBothCleanoutEntries()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var records=helper.records("/dictionary-deferred-storage-owner-raw120.txt");
        var record=records.get(0);assertEquals(80,record.vectors().get(1).fields().get(0).length);
        var row=DictionaryDeferredStoragePair.bind(record,record.vectors().get(1));
        assertEquals(new HeapRowPatch.Xid(10,18,5250),row.forward().xid());
        DictionaryDeferredStoragePair.requireRow(index(records.get(1)),row);
        record.vectors().get(1).fields().get(0)[44]^=1;
        assertThrows(RedoException.class,()->DictionaryDeferredStoragePair.bind(record,record.vectors().get(1)));
    }
    @Test void otherDictionaryRowsInOneRecordDoNotReplaceOrDuplicateTheOwnedPair()throws Exception {
        var deferred=records().get(0);var object=new DictionaryObjectUndoPairTest().records().get(0);
        var vectors=new ArrayList<>(deferred.vectors());vectors.addAll(object.vectors());
        var mixed=new RedoRecords.Record(deferred.block(),deferred.offset(),deferred.length(),deferred.validity(),deferred.scn(),deferred.subScn(),deferred.containerUid(),List.copyOf(vectors));
        var row=deferred.vectors().get(1);
        assertEquals(81,DictionaryDeferredStoragePair.bind(mixed,row).undo().identity().objectId());
        assertEquals(18,DictionaryObjectUndoPair.bind(mixed,object.vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37).physical().undo().identity().objectId());
        vectors.add(row);
        var duplicate=new RedoRecords.Record(mixed.block(),mixed.offset(),mixed.length(),mixed.validity(),mixed.scn(),mixed.subScn(),mixed.containerUid(),List.copyOf(vectors));
        assertThrows(RedoException.class,()->DictionaryDeferredStoragePair.bind(duplicate,row));
    }
}
