package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryObjectRegistrationTest {
    private final FrozenHeapBaseline.ObjectRole role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
    @Test void raw120RegistrationAndVersionChangesKeepBothImages()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-object-registration-raw120.txt");
        assertEquals(4,records.size());
        for(var record:records) {
            var pair=DictionaryObjectUndoPair.bind(record,record.vectors().get(1),role,37).physical();
            assertEquals(HeapRowPatch.Kind.UPDATE,pair.forward().kind());
            assertEquals(HeapRowPatch.Kind.UPDATE,pair.undo().inverse().kind());
            assertEquals(26,pair.forward().extent().storedColumns());
            assertEquals(HeapRowPatch.State.NULL,pair.forward().columns().get(14).state());
        }
        var first=DictionaryObjectUndoPair.bind(records.get(0),records.get(0).vectors().get(1),role,37).physical();
        assertEquals(16,first.forward().columns().get(21).bytes().length);
        assertEquals(HeapRowPatch.State.NULL,first.undo().inverse().columns().get(21).state());
        var absentData=DictionaryObjectUndoPair.bind(records.get(1),records.get(1).vectors().get(1),role,37).physical();
        assertEquals(HeapRowPatch.State.NULL,absentData.forward().columns().get(1).state());
    }
    @Test void nullBitmapColumnMapAndPhysicalOwnerCannotBeSubstituted()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();
        for(int at:new int[]{18,26,28}) {
            var record=helper.records("/dictionary-object-registration-raw120.txt").get(0);
            record.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(record,record.vectors().get(1),role,37));
        }
        var record=helper.records("/dictionary-object-registration-raw120.txt").get(0);
        record.vectors().get(1).fields().get(2)[10]=20;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(record,record.vectors().get(1),role,37));
        var owner=helper.records("/dictionary-object-registration-raw120.txt").get(0);
        owner.vectors().get(1).fields().get(0)[12]^=1;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(owner,owner.vectors().get(1),role,37));
    }
}
