package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ArchivedRedoTest {
    @TempDir Path dir;
    // Synthetic structural fixture. Real Oracle acceptance is tracked separately in lab evidence.
    private byte[] fixture() {
        byte[] b=new byte[1536];
        put(b,0,0x2200,2); put(b,20,512,4); put(b,24,2,4); put(b,28,0x7a7b7c7dL,4);
        put(b,512,0x2201,2); put(b,516,1,4); put(b,520,7,4);
        put(b,532,0x13000000,4); put(b,536,42,4); put(b,668,3,4);
        put(b,688,1,2); put(b,692,100,4); put(b,704,200,4);
        put(b,1024,0x2201,2); put(b,1028,2,4); put(b,1032,7,4); put(b,1036,0x8010,2);
        put(b,1040,64,4); put(b,1044,1,1); put(b,1048,101,4); put(b,1056,1,4);
        put(b,1064,5,1); put(b,1065,4,1); put(b,1088,1,4);
        put(b,1096,4,2); put(b,1098,1,2); put(b,1100,0x10,1);
        for (int p=0;p<b.length;p+=512) seal(b,p);
        return b;
    }
    static void put(byte[] b,int p,long value,int n) { for (int i=0;i<n;i++) b[p+i]=(byte)(value >>> (8*i)); }
    static void seal(byte[] b,int p) {
        put(b,p+14,0,2);
        int checksum=0;
        for (int i=p;i<p+512;i+=2) checksum ^= RedoBytes.u16(b,i);
        put(b,p+14,checksum,2);
    }
    Path write(byte[] b) throws Exception { Path p=dir.resolve("archive.bin"); Files.write(p,b); return p; }
    @Test void readsHeaderAndVector() throws Exception {
        Path p=write(fixture()); var h=new ArchivedRedo().scan(p);
        assertEquals(42,h.dbid()); assertEquals(7,h.sequence()); assertEquals(100,h.firstScn());
        var records=new ArrayList<RedoRecords.Record>();
        assertEquals(1,new RedoRecords().visit(p,h,records::add));
        assertEquals(101,records.get(0).scn()); assertEquals(4,records.get(0).vectors().get(0).opcodeMinor());
        assertArrayEquals(new byte[]{16},records.get(0).vectors().get(0).fields().get(0));
    }
    @Test void rejectsUnalignedTruncation() throws Exception {
        var b=fixture(); Path p=write(Arrays.copyOf(b,b.length-1));
        assertTrue(assertThrows(RedoException.class,()->new ArchivedRedo().scan(p)).getMessage().contains("TRUNCATED"));
    }
    @Test void rejectsWholeMissingBlock() throws Exception {
        var b=Arrays.copyOf(fixture(),1024); Path p=write(b);
        assertThrows(RedoException.class,()->new ArchivedRedo().scan(p));
    }
    @Test void rejectsChecksumTamper() throws Exception {
        var b=fixture(); b[1100]^=1; Path p=write(b);
        assertTrue(assertThrows(RedoException.class,()->new ArchivedRedo().scan(p)).getMessage().contains("CHECKSUM"));
    }
    @Test void rejectsResealedSequencePoison() throws Exception {
        var b=fixture(); put(b,1032,8,4); seal(b,1024); Path p=write(b);
        assertTrue(assertThrows(RedoException.class,()->new ArchivedRedo().scan(p)).getMessage().contains("IDENTITY"));
    }
    @Test void rejectsOversizedFieldEvenWithValidChecksum() throws Exception {
        var b=fixture(); put(b,1098,1000,2); seal(b,1024); Path p=write(b); var h=new ArchivedRedo().scan(p);
        assertThrows(RedoException.class,()->new RedoRecords().visit(p,h,r -> fail("must not yield malformed record")));
    }
    @Test void refusesEventsAfterSuccessfulScan() throws Exception {
        assertEquals(1,RedoCli.run(new String[]{"events",write(fixture()).toString()}));
    }
    @Test void readsObservedCdbValidityAndRejectsUnknown() throws Exception {
        var b=fixture(); b[1044]=9; seal(b,1024); Path p=write(b); var h=new ArchivedRedo().scan(p);
        assertEquals(1,new RedoRecords().visit(p,h,r -> assertEquals(9,r.validity())));
        b[1044]=0x41; seal(b,1024); write(b); h=new ArchivedRedo().scan(p);
        final var bad=h;
        assertThrows(RedoException.class,()->new RedoRecords().visit(p,bad,r -> fail("unknown validity")));
    }
    @Test void markerValidityStillContainsVectors() throws Exception {
        var b=fixture(); b[1044]=2; seal(b,1024); Path p=write(b); var h=new ArchivedRedo().scan(p);
        assertEquals(1,new RedoRecords().visit(p,h,r -> assertEquals(1,r.vectors().size())));
    }
}
