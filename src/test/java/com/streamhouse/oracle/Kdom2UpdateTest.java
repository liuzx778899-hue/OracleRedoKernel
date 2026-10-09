package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Kdom2UpdateTest {
    // Self-generated 19.3 no-supplemental long transaction, seq10. Only extended single-column form.
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    byte[] value() { byte[] b=new byte[903]; Arrays.fill(b,(byte)'K'); b[0]=(byte)0xfe; b[1]=(byte)0x84; b[2]=3; return b; }
    RedoRecords.Vector vector(byte[] kdo,byte[] map,byte[] bytes) {
        return new RedoRecords.Vector(11,5,1,11,0x03000095,2158789,1,3,
                List.of(hex("020d726f6772616d71214002e5001f00"),kdo,map,bytes));
    }
    byte[] kdo() { return hex("9500000392000003fa120581020004002c0212000000020100000300"); }
    @Test void unwraps900ByteValueWithoutPublishingOrInventingKey() throws Exception {
        var p=new HeapRowPatch().decode(vector(kdo(),hex("0100"),value()),2);
        assertEquals(1,p.xtype()); assertNull(p.xid());
        assertNull(p.extent().sizeDelta());
        assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(0).state());
        assertEquals(900,p.columns().get(1).bytes().length);
        assertTrue(Arrays.equals(Arrays.copyOfRange(value(),3,903),p.columns().get(1).bytes()));
    }
    @Test void rollbackFormRemainsXr() throws Exception {
        byte[] b=kdo(); b[11]=(byte)0x82;
        assertEquals(2,new HeapRowPatch().decode(vector(b,hex("0100"),value()),2).xtype());
    }
    @Test void shortKtbZRestoresBytesWithoutInventingIdentity() throws Exception {
        byte[] b=kdo();b[11]=(byte)0x82;
        for (String ktb:List.of("030d200075230000","030d0001c8001500")) {
            var v=vector(b,hex("0100"),value());var fields=new java.util.ArrayList<>(v.fields());fields.set(0,hex(ktb));
            var patch=new HeapRowPatch().decode(new RedoRecords.Vector(11,5,1,11,v.dba(),v.scn(),1,3,fields),2);
            assertEquals(2,patch.xtype());assertNull(patch.xid());assertNull(patch.uba());
            assertEquals(900,patch.columns().get(1).bytes().length);
        }
    }
    @Test void shortZCannotClaimXaOrUnprovenHeaderProfile() {
        for (String ktb:List.of("030d200075230000","030c200075230000","030d20007523")) {
            var v=vector(kdo(),hex("0100"),value());var fields=new java.util.ArrayList<>(v.fields());fields.set(0,hex(ktb));
            assertThrows(RedoException.class,()->new HeapRowPatch().decode(new RedoRecords.Vector(11,5,1,11,v.dba(),v.scn(),1,3,fields),2));
        }
    }
    @Test void refusesLengthPoisonAndTrailingPayload() {
        byte[] b=value(); b[1]=(byte)0x85;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(kdo(),hex("0100"),b),2));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(kdo(),hex("0100"),Arrays.copyOf(value(),904)),2));
    }
    @Test void refusesUnprovenNullAndMultipleColumnLayouts() throws Exception {
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(kdo(),hex("0100"),hex("ff")),2));
        assertArrayEquals(hex("61"),new HeapRowPatch().decode(vector(kdo(),hex("0100"),hex("0161")),2).columns().get(1).bytes());
        byte[] b=kdo(); b[23]=2;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(b,hex("00000100"),value()),2));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(kdo(),hex("0200"),value()),2));
    }
    @Test void observedXaShortPrefixRestoresNumberAndKeepsOtherColumnsAbsent() throws Exception {
        byte[] k=kdo();k[22]=3;k[26]=0;
        var patch=new HeapRowPatch().decode(vector(k,hex("0000"),hex("02c10c")),3);
        assertArrayEquals(hex("c10c"),patch.columns().get(0).bytes());assertEquals(HeapRowPatch.State.ABSENT,patch.columns().get(1).state());
        assertEquals(3,patch.extent().storedColumns());assertNull(patch.extent().sizeDelta());
    }
    @Test void shortPrefixLengthNullTrailingDataAndXrRefuse() {
        byte[] k=kdo();k[22]=3;k[26]=0;
        for(var bytes:List.of(hex("03c10c"),hex("02c10c00"),hex("ff"),hex("00"),hex("fec10c")))
            assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(k,hex("0000"),bytes),3));
        k[11]=(byte)0x82;assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(k,hex("0000"),hex("02c10c")),3));
    }
    @Test void observedColumnTwoShortStringProfileKeepsUntouchedColumnsAbsent() throws Exception {
        byte[] k=kdo();k[22]=3;k[26]=0x10;
        var patch=new HeapRowPatch().decode(vector(k,hex("0200"),hex("0c7061727469616c2d6e6f7465")),3);
        assertArrayEquals(hex("7061727469616c2d6e6f7465"),patch.columns().get(2).bytes());
        assertEquals(HeapRowPatch.State.ABSENT,patch.columns().get(0).state());
        assertEquals(HeapRowPatch.State.ABSENT,patch.columns().get(1).state());assertNull(patch.extent().sizeDelta());
    }
    @Test void columnTwoControlDoesNotAuthorizeOtherMapsFlagsOrXr() {
        byte[] k=kdo();k[22]=3;k[26]=0x10;byte[] bytes=hex("0c7061727469616c2d6e6f7465");
        for (var map:List.of(hex("0000"),hex("0100"))) assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(k,map,bytes),3));
        k[26]=0x20;assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(k,hex("0200"),bytes),3));
        k[26]=0x10;k[11]=(byte)0x82;assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(k,hex("0200"),bytes),3));
    }
}
