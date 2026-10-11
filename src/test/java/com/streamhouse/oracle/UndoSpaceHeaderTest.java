package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoSpaceHeaderTest {
    @Test void raw119EightSpaceHeadersKeepObjectDataObjectAndFullXid()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-space-headers-raw119.txt");
        assertEquals(8,records.size());
        for(var r:records) {
            var h=UndoHeader.decode(r.vectors().get(0));
            assertEquals(new HeapRowPatch.Xid(10,31,5224),h.xid());assertEquals(1,h.objectId());assertEquals(5,h.tablespace());
            assertTrue(h.dataObjectId()>0);assertFalse(h.beginsTransaction());
        }
    }
    @Test void flag128DoesNotAdmitOtherOperationsOrTablespaces()throws Exception {
        for(int at:new int[]{0,8,16,17,20,21}) {
            var row=new DictionaryObjectUndoPairTest().records("/dictionary-space-headers-raw119.txt").get(0).vectors().get(0);
            row.fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->UndoHeader.decode(row));
        }
    }
}
