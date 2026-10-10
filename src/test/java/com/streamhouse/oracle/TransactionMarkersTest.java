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
    @Test void extendedFullRollbackHasNoFabricatedTail() throws Exception {
        var v=vector(4,"0d00000045020000000000000900000014000000",
                "000000000100000085d2c16a010000001000000010000000","85d2c16a");
        var end=TransactionEnd.decode(v);assertTrue(end.rolledBack());assertEquals(581,end.sequence());
        assertEquals("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE",assertThrows(RedoException.class,()->TransactionEnd.undoTail(v)).getMessage());
        for (int offset:new int[]{0,4,8,12,16,20}) {
            var bad=vector(4,"0d00000045020000000000000900000014000000",
                    "000000000100000085d2c16a010000001000000010000000","85d2c16a");
            bad.fields().get(1)[offset]^=1;
            assertEquals("UNSUPPORTED_TRANSACTION_END_EXTENSION",assertThrows(RedoException.class,()->TransactionEnd.decode(bad)).getMessage());
        }
    }
    @Test void rollbackExtensionCannotBeUsedUnderCommitOrPlainRollbackFlags() {
        for (int flags:new int[]{0,2,4,0x12}) {
            var bad=vector(4,"0d00000045020000000000000900000014000000",
                    "000000000100000085d2c16a010000001000000010000000","85d2c16a");
            bad.fields().get(0)[16]=(byte)flags;
            assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
        }
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
    RedoRecords.Vector emptyExtension(boolean ext) {
        return ext?vector(4,"1d00000063020000000000000900000012000000","de004002c8002f000100b40e00000000",
                "0100000001000000b9c7c36a000000000000000000000000","b9c7c36a"):
                vector(4,"020000007e020000000000000900000012000000","87004002ff00350000006c0700000000",
                "000000000000000075c7c36a000000000000000000000000","75c7c36a");
    }
    @Test void native104MaintenanceExtensionsPreserveTailAndRejectUnknownVariants()throws Exception {
        for(boolean offset:new boolean[]{false,true}) {
            var v=offset?vector(4,"0f000000e5030000000000000900000012000000","830640023c0101000c00040b007f0000",
                    "0b0000000c0000002e0eca6a000000000000000000000000","2e0eca6a"):
                    vector(4,"1d000000c4020000000000000900000012000000","8b0b40021b0101000200040b007f0000",
                    "00000000020000002f0eca6a010000000500000005000000","2f0eca6a");
            assertFalse(TransactionEnd.decode(v).rolledBack());
            assertEquals(new HeapRowPatch.Uba(offset?0x02400683L:0x02400b8bL,offset?316:283,1),TransactionEnd.undoTail(v));
            for(int at:new int[]{4,8,12,16,20}) {
                v.fields().get(2)[at]^=1;
                assertThrows(RedoException.class,()->TransactionEnd.decode(v));v.fields().get(2)[at]^=1;
            }
            v.fields().get(1)[8]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(v));
        }
    }
    @Test void native104MaintenanceEndWithoutTailCannotSupplyBusinessUndoTail()throws Exception {
        for(var v:java.util.List.of(
                vector(4,"1c000000e4030000000000000900000010000000","0b0000000c0000002e0eca6a000000000000000000000000","2e0eca6a"),
                vector(4,"20000000c4020000000000000900000010000000","00000000020000002f0eca6a010000000500000005000000","2f0eca6a"))) {
            assertFalse(TransactionEnd.decode(v).rolledBack());
            assertEquals("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE",assertThrows(RedoException.class,()->TransactionEnd.undoTail(v)).getMessage());
            v.fields().get(0)[16]=0x14;
            assertThrows(RedoException.class,()->TransactionEnd.decode(v));
        }
    }
    @Test void observedEmptyCommitExtensionsKeepTheirActualTailAndIdentity() throws Exception {
        for(boolean ext:new boolean[]{false,true}) {
            var v=emptyExtension(ext);var end=TransactionEnd.decode(v);assertFalse(end.rolledBack());
            assertEquals(ext?29:2,end.slot());assertEquals(ext?611:638,end.sequence());
            assertEquals(new HeapRowPatch.Uba(ext?0x024000deL:0x02400087L,ext?200:255,ext?47:53),TransactionEnd.undoTail(v));
        }
    }
    @Test void emptyCommitExtensionCannotMixTailExtTimestampOrReservedFields() {
        for(boolean ext:new boolean[]{false,true}) {
            for(int offset:new int[]{0,4,8,12,16,20}) {
                var v=emptyExtension(ext);v.fields().get(2)[offset]^=1;
                assertThrows(RedoException.class,()->TransactionEnd.decode(v));
            }
            var v=emptyExtension(ext);v.fields().get(1)[8]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(v));
            var r=emptyExtension(ext);r.fields().get(0)[16]=0x14;
            assertThrows(RedoException.class,()->TransactionEnd.decode(r));
        }
    }
    RedoRecords.Vector native93Commit() {
        return vector(4,"0000000095020000000000000900000012000000",
                "a7014002ea0009000200541b00000000",
                "020000000200000037cec76a000000000000000000000000","37cec76a");
    }
    @Test void native93EmptyExtTwoKeepsIdentityAndTail() throws Exception {
        var v=native93Commit();var end=TransactionEnd.decode(v);
        assertEquals(0,end.slot());assertEquals(661,end.sequence());assertFalse(end.rolledBack());
        assertEquals(new HeapRowPatch.Uba(0x024001a7L,234,9),TransactionEnd.undoTail(v));
        for(int offset:new int[]{0,4,8,12,16,20}) {
            var bad=native93Commit();bad.fields().get(2)[offset]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
        }
        var mixed=native93Commit();mixed.fields().get(1)[8]=1;
        assertThrows(RedoException.class,()->TransactionEnd.decode(mixed));
        var unknown=native93Commit();unknown.fields().get(1)[8]=3;
        unknown.fields().get(2)[0]=3;unknown.fields().get(2)[4]=3;
        assertThrows(RedoException.class,()->TransactionEnd.decode(unknown));
    }
    RedoRecords.Vector native92Rollback() {
        return vector(4,"1b000000540e0000000000000900000014000000",
                "03000000030000004dccc76a000000000000000000000000","4dccc76a");
    }
    @Test void native92EmptyRollbackHasFullIdentityAndNoTail() throws Exception {
        var v=native92Rollback();var end=TransactionEnd.decode(v);
        assertTrue(end.rolledBack());assertEquals(27,end.slot());assertEquals(3668,end.sequence());
        assertEquals("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE",assertThrows(RedoException.class,()->TransactionEnd.undoTail(v)).getMessage());
        for(int offset:new int[]{0,4,8,12,16,20}) {
            var bad=native92Rollback();bad.fields().get(1)[offset]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
        }
        var bad=native92Rollback();bad.fields().get(0)[16]=0x12;
        assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
    }
    RedoRecords.Vector historicalCommit() {
        return vector(4,"1b0000005a020000000000000900000012000000",
                "c7004002ff0007000000f01b000b0000",
                "0000000000000000f306c46a000000000000000000000000","f306c46a");
    }
    @Test void observedHistoricalCommitKeepsTailAndRejectsMixedExtension() throws Exception {
        var v=historicalCommit();var end=TransactionEnd.decode(v);
        assertEquals(27,end.slot());assertEquals(602,end.sequence());assertFalse(end.rolledBack());
        assertEquals(new HeapRowPatch.Uba(0x024000c7L,255,7),TransactionEnd.undoTail(v));
        for(int offset:new int[]{7,8}) {
            var bad=historicalCommit();bad.fields().get(1)[offset]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
        }
        for(int offset:new int[]{0,4,8,12,16,20}) {
            var bad=historicalCommit();bad.fields().get(2)[offset]^=1;
            assertThrows(RedoException.class,()->TransactionEnd.decode(bad));
        }
        var mixed=historicalCommit();mixed.fields().get(1)[8]=1;
        assertThrows(RedoException.class,()->TransactionEnd.decode(mixed));
    }
    @Test void nativeFbiAndPaddingCannotChangeCommitIdentityOrUndoTail()throws Exception {
        var v=vector(4,"03000000e30d0000000000000900000012000000","1601400266012a000000621001750000",
                "0000000000000000a76cc76a000000000000000000000000","a76cc76a");
        var end=TransactionEnd.decode(v);var tail=TransactionEnd.undoTail(v);
        assertEquals(3,end.slot());assertEquals(3555,end.sequence());assertEquals(new HeapRowPatch.Uba(37749014,358,42),tail);
        for(int i=12;i<16;i++)v.fields().get(1)[i]^=1;
        assertEquals(end,TransactionEnd.decode(v));assertEquals(tail,TransactionEnd.undoTail(v));
    }
    @Test void refusesMissingUserUndoDoneAndWrongBeginWrap() {
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(6,
                "401d0100401d010005000000000000000b01052300006d04020d0000","9010200002000000")));
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(11,
                "401d0100401d010005000000000000000b010400180c9d0200000000","900a200002000000")));
        assertThrows(RedoException.class,()->UndoCompensation.decode(vector(11,"00","00")));
    }
}
