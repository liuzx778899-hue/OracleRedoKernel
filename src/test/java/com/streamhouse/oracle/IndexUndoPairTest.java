package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexUndoPairTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector insert() {
        return new RedoRecords.Vector(10,2,1,12,0x0300008b,2148761,1,3,List.of(
                hex("010d5000000000000a000c0051040000be2c4002f2000800"),hex("020000000d00"),hex("02c102"),hex("030000830000")));
    }
    RedoRecords.Vector purgeUndo() {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148761,1,3,List.of(
                hex("5c00101d220000000a000c0051040000f2000829"),hex("411d0100411d010005000000000000000a160c0700005104"),
                hex("040d0c0004000400ffff00000000000000000000000000000080008091c92000"),
                hex("030201008a0000038b0000030000000000000000"),hex("02c102")));
    }
    RedoRecords.Vector delete() {
        return new RedoRecords.Vector(10,4,1,12,0x0300008b,2148789,1,3,List.of(
                hex("010d0000000000000a0003006e040000be2c4002f2001900"),hex("020000000d00")));
    }
    RedoRecords.Vector restoreUndo() {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148789,1,3,List.of(
                hex("6400ce142200d3c00a0003006e040000f200192e"),hex("411d0100411d010005000000000000000a16031800006e04"),
                hex("040d18004d0400000a000c0051040000be2c4002f2000800008000809ac92000"),
                hex("050201008a0000038b0000030000000000006627"),hex("02c102"),hex("030000830000")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) {
        return new RedoRecords.Record(30680,280,1000,1,2148761,1,677737149,List.of(v));
    }
    @Test void insertMatchesExactUbaAndIgnoresHistoricalItlXid() throws Exception {
        var row=insert(); var u=purgeUndo(); var p=IndexUndoPair.bind(record(u,row),row);
        assertEquals(new HeapRowPatch.Xid(10,12,1105),p.undo().identity().xid());
        assertEquals(IndexUndoPair.Kind.PURGE,p.undo().kind());
        assertArrayEquals(hex("02c102"),p.undo().key());
        assertNull(p.undo().keydata());
        assertEquals(p.undo().identity().uba(),p.forward().uba());
    }
    @Test void deleteRetainsInverseKeydataWithoutInventingForwardKey() throws Exception {
        var row=delete(); var p=IndexUndoPair.bind(record(row,restoreUndo()),row);
        assertNull(p.forward().key()); assertNull(p.forward().keydata());
        assertEquals(IndexUndoPair.Kind.RESTORE,p.undo().kind());
        assertArrayEquals(hex("030000830000"),p.undo().keydata());
        assertEquals(new HeapRowPatch.Xid(10,3,1134),p.undo().identity().xid());
    }
    @Test void cReferenceNeedsNoForwardXid() throws Exception {
        var r=insert();
        var c=new RedoRecords.Vector(10,2,1,12,r.dba(),r.scn(),1,3,List.of(
                hex("020d000000000000be2c4002f2000800"),r.fields().get(1),r.fields().get(2),r.fields().get(3)));
        var pair=IndexUndoPair.bind(record(c,purgeUndo()),c);
        assertNull(pair.forward().xid()); assertEquals(1105,pair.undo().identity().xid().sequence());
    }
    @Test void refusesMissingDuplicateAndForeignRecord() {
        var r=insert(); var u=purgeUndo();
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r),r));
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,u,u),r));
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,r,u),r));
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(u),r));
    }
    @Test void refusesContainerMixAndWrongFullUba() {
        var r=insert(); var u=purgeUndo();
        var other=new RedoRecords.Vector(5,1,36,11,u.dba(),u.scn(),1,4,u.fields());
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,other),r));
        u.fields().get(0)[16]++;
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,u),r));
    }
    @Test void refusesWrongCurrentXidBlockItlAndKey() {
        for (int field:new int[]{0,3,4}) {
            var r=insert(); var u=purgeUndo();
            int offset=field==0 ? 12 : field==4 ? 2 : 8;
            u.fields().get(field)[offset]^=1;
            assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,u),r));
        }
        var r=insert(); var u=purgeUndo(); u.fields().get(3)[1]++;
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,u),r));
    }
    @Test void refusesMalformedModesLengthsAndMissingRestoreData() {
        var r=insert(); r.fields().get(1)[1]=1;
        assertThrows(RedoException.class,()->IndexUndoPair.decodeForward(r));
        var badKey=insert(); badKey.fields().get(2)[0]=1;
        assertThrows(RedoException.class,()->IndexUndoPair.decodeForward(badKey));
        var u=restoreUndo(); var f=new ArrayList<>(u.fields()); f.remove(5);
        var truncated=new RedoRecords.Vector(5,1,36,11,u.dba(),u.scn(),1,3,f);
        assertThrows(RedoException.class,()->IndexUndoPair.decodeUndo(truncated));
    }
    @Test void refusesUnknownKtbAndUnsupportedIndexCompanion() {
        var r=insert(); r.fields().get(0)[0]=4;
        assertThrows(RedoException.class,()->IndexUndoPair.decodeForward(r));
        var row=insert(); var unknown=new RedoRecords.Vector(10,31,1,12,row.dba(),row.scn(),1,3,List.of());
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(row,purgeUndo(),unknown),row));
    }
    @Test void refusesRestoreSizeAndUndoMetadataMismatch() {
        var r=delete(); r.fields().get(1)[4]=14;
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(r,restoreUndo()),r));
        var u=purgeUndo(); u.fields().get(1)[17]=21;
        assertThrows(RedoException.class,()->IndexUndoPair.decodeUndo(u));
    }
}
