package com.streamhouse.oracle;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionMarkersTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector vector(int minor,String... fields) {
        return new RedoRecords.Vector(5,minor,35,9,0x02400110,2148790,1,3,
                java.util.Arrays.stream(fields).map(this::hex).toList());
    }
    @Test void commitRetainsSegmentAddressWithoutGuessingUsn() throws Exception {
        var h=TransactionEnd.decode(vector(4,"0c00000051040000000000000900000002000000",
                "be2c4002f20008004f00b21c00000000","ea44c16a"));
        assertEquals(12,h.slot()); assertEquals(1105,h.sequence());
        assertEquals(0x02400110,h.segmentDba()); assertFalse(h.rolledBack());
    }
    @Test void fullRollbackIsNotACommit() throws Exception {
        var h=TransactionEnd.decode(vector(4,"1b0000006d040000000000000900000004000000","ea44c16a"));
        assertTrue(h.rolledBack()); assertEquals(1133,h.sequence());
    }
    @Test void readsSavepointUndoHeaderWithoutInventingUndoneUba() throws Exception {
        var h=UndoCompensation.decode(vector(6,"401d0100401d010005000000000000000b01052310006d04020d0000",
                "9010200002000000"));
        assertEquals(73024,h.dataObjectId()); assertEquals(5,h.slot());
        assertEquals(35,h.predecessorRecord()); assertEquals(1133,h.wrap());
        assertFalse(h.beginsTransaction()); assertEquals(11,h.opcodeMajor());
    }
    @Test void beginCompensationDoesNotExposeWrapAsTransactionSequence() throws Exception {
        var h=UndoCompensation.decode(vector(11,"401d0100401d010005000000000000000b010400180c010000000000",
                "900a200002000000"));
        assertTrue(h.beginsTransaction()); assertEquals(1,h.wrap());
        assertEquals(4,h.slot()); assertEquals(0,h.predecessorRecord());
    }
    @Test void refusesUnknownEndStateAndTruncatedFields() {
        assertThrows(RedoException.class,()->TransactionEnd.decode(vector(4,
                "0c00000051040000000000000900000008000000","ea44c16a")));
        assertThrows(RedoException.class,()->TransactionEnd.decode(vector(4,"00","ea44c16a")));
        assertThrows(RedoException.class,()->TransactionEnd.decode(vector(6,
                "0c00000051040000000000000900000002000000","ea44c16a")));
    }
    @Test void refusesMissingUserUndoDoneAndWrongBeginWrap() {
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(6,
                "401d0100401d010005000000000000000b01052300006d04020d0000","9010200002000000")));
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(11,
                "401d0100401d010005000000000000000b010400180c9d0200000000","900a200002000000")));
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(11,"00","00")));
    }
}
