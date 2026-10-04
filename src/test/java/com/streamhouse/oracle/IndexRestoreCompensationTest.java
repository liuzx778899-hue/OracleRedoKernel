package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexRestoreCompensationTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector restore() {
        return new RedoRecords.Vector(10,5,1,12,50331947,2207267,1,3,List.of(
                hex("020db771000000007a2d4002f2002a00"),hex("020101000d00")));
    }
    RedoRecords.Vector marker() {
        return new RedoRecords.Vector(5,6,36,11,37760378,2207267,1,3,List.of(
                hex("641d0100641d010005000000000000000a16112b1000bf04020db771"),hex("f40f000002000000")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(10170,68,1000,1,2207267,1,677737149,List.of(v)); }
    IndexUndoPair original() throws Exception {
        var row=new RedoRecords.Vector(10,4,1,12,50331947,2207267,1,3,List.of(
                hex("020dd175000000007a2d4002f2002c00"),hex("020001000d00")));
        var undo=new RedoRecords.Vector(5,1,36,11,37760378,2207267,1,3,List.of(
                hex("5400e00f220000000a001100bf040000f2002c0c"),hex("641d0100641d010005000000000000000a16112b0000bf04"),
                hex("020db771000000007a2d4002f2002a00"),hex("050201302a0100032b0100030000000000000000"),hex("02c103"),hex("030001270001")));
        return IndexUndoPair.bind(record(row,undo),row);
    }
    /** Synthetic activity binding for the real frozen pair; no native begin acceptance asserted here. */
    TransactionLifecycle active() throws Exception {
        var state=new TransactionLifecycle();state.archive(new TransactionLifecycleTest().archive(18,2203939,2207287,42));
        byte[] b=new byte[32],u=new byte[20],m=new byte[76];
        ArchivedRedoTest.put(b,0,17,2);ArchivedRedoTest.put(b,4,1215,4);ArchivedRedoTest.put(b,8,37760378,4);
        ArchivedRedoTest.put(b,12,242,2);b[14]=39;ArchivedRedoTest.put(b,16,0x52,2);
        ArchivedRedoTest.put(u,8,10,2);ArchivedRedoTest.put(u,10,17,2);ArchivedRedoTest.put(u,12,1215,4);
        ArchivedRedoTest.put(u,16,242,2);u[18]=39;ArchivedRedoTest.put(m,20,0x0c08,2);
        var start=new RedoRecords.Vector(5,2,35,11,37749008,2207267,1,3,List.of(b,hex("bd726528")));
        var undo=new RedoRecords.Vector(5,1,36,11,37760378,2207267,1,3,List.of(u,m));
        state.begin(record(start,undo),start);return state;
    }
    @Test void bindsRestoreToDeleteWithOriginalUbaBeyondHistoricalCReference() throws Exception {
        var c=marker();var row=restore();var original=original();
        assertSame(original,IndexCompensation.match(record(row,c),c,row,List.of(original),active()));
        assertEquals(44,original.undo().identity().uba().record());
        assertEquals(IndexUndoPair.Kind.RESTORE,IndexCompensation.decodeRollback(row).kind());
        assertArrayEquals(hex("030001270001"),original.undo().keydata());
    }
    @Test void rejectsPurgeAsRestoreAndLatches() throws Exception {
        var row=restore();var c=marker();var original=original();var state=active();
        var purge=new RedoRecords.Vector(10,3,1,12,row.dba(),row.scn(),1,3,row.fields());
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,purge),c,purge,List.of(original),state));
        assertEquals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED",assertThrows(RedoException.class,state::finish).getMessage());
    }
    @Test void rejectsWrongRestoreSlotSizeModeAndKtb() throws Exception {
        for (int mode=0;mode<4;mode++) {
            var row=restore();var c=marker();var original=original();var state=active();
            if (mode==0)row.fields().get(1)[2]^=1;
            if (mode==1)row.fields().get(1)[4]^=1;
            if (mode==2)row.fields().get(1)[1]=0;
            if (mode==3)row.fields().get(0)[0]=1;
            assertThrows(RedoException.class,()->IndexCompensation.match(record(c,row),c,row,List.of(original),state));
            assertThrows(RedoException.class,state::finish);
        }
    }
    @Test void rejectsDuplicateRestoreOriginalAndPhysicalVector() throws Exception {
        var row=restore();var c=marker();var original=original();var state=active();
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,row),c,row,List.of(original,original),state));
        var second=active();
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,row,row),c,row,List.of(original),second));
        assertThrows(RedoException.class,second::finish);
    }
    @Test void refusesRestorationWithoutActiveFullIdentity() throws Exception {
        var row=restore();var c=marker();var original=original();var state=new TransactionLifecycle();
        state.archive(new TransactionLifecycleTest().archive(18,2203939,2207287,42));
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,row),c,row,List.of(original),state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void keepsLegacyPurgeDecoderStrictAndRejectsExtraFields() {
        var row=restore();assertThrows(RedoException.class,()->IndexCompensation.decodePurge(row));
        var extra=new RedoRecords.Vector(10,5,1,12,row.dba(),row.scn(),1,3,List.of(row.fields().get(0),row.fields().get(1),new byte[1]));
        assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(extra));
    }
}
