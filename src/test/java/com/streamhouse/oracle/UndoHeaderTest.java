package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoHeaderTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector vector(String first,String second) {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148758,1,3,List.of(hex(first),hex(second)));
    }
    @Test void readsRealBeginIdentityAndMatchesExactRowUba() throws Exception {
        var h=UndoHeader.decode(vector("7000821d120000000a000c0051040000f2000700",
                "401d0100401d010005000000000000000b010c00080c010000000000be2c4002f200060075c320000000000077c320000000000000000000ffffffffffffffffb92c40020000000000000000"));
        assertEquals(new HeapRowPatch.Xid(10,12,1105),h.xid());
        assertEquals(new HeapRowPatch.Uba(0x02402cbe,242,7),h.uba());
        assertEquals(73024,h.dataObjectId()); assertTrue(h.beginsTransaction());
    }
    @Test void readsContinuationWithoutInventingBegin() throws Exception {
        var h=UndoHeader.decode(vector("5c008013220000000a0014006e040000f2001c00",
                "401d0100401d010005000000000000000b01141b00006e04"));
        assertFalse(h.beginsTransaction()); assertEquals(28,h.uba().record());
        assertEquals(new HeapRowPatch.Xid(10,20,1134),h.xid());
    }
    @Test void rejectsUnknownLayoutAndFlags() {
        assertThrows(RedoException.class,()->UndoHeader.decode(vector("00","00")));
        assertThrows(RedoException.class,()->UndoHeader.decode(vector("5c008013220000000a0014006e040000f2001c00",
                "401d0100401d010005000000000000000b01141b10006e04")));
    }
}
