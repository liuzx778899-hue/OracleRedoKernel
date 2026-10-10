package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class HeapRowPatchTest {
    // Self-generated ORKLAB/19.3 no-supplemental fixture, archive8; not reference implementation code.
    static final String F="010d1a00720200000a000c0051040000be2c4002f2000700";
    static final String IRP="8300000382000003fa120201010000002c0104000000000060000000000000000000000002000000190000000000000028";
    static final String URP="8300000382000003fa120501020000002c02120000000401ffff000000";
    static final String C="020d10000c000000be2c4002f2001c00";
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    @Test void observedSixColumnPackedTemporalSuffixKeepsAllFiveValues()throws Exception {
        // Preserve seq110 suffix encoding; relocate its block to this test fixture address.
        String kdo="8300000382000003fa1205810200ffff2c0212000000060500000300";
        String values="076438030f0d2339076438030f0d233a0b6438030f0d23393ae3af000b6438030f0d23393ade67700b6463010101010100000001";
        var p=new HeapRowPatch().decode(vector(5,F,kdo,"0100",values),6);
        assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(0).state());
        assertEquals(java.time.LocalDateTime.of(-43,3,15,12,34,56),OracleScalars.date(p.columns().get(1).bytes()));
        assertEquals(57,OracleScalars.timestamp(p.columns().get(2).bytes()).getSecond());
        assertEquals(988000000,OracleScalars.timestamp(p.columns().get(3).bytes()).getNano());
        assertEquals(987654000,OracleScalars.timestamp(p.columns().get(4).bytes()).getNano());
        assertEquals(java.time.LocalDateTime.of(0,1,1,0,0,0,1),OracleScalars.timestamp(p.columns().get(5).bytes()));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(5,F,kdo,"0100",values+"00"),6));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(5,F,kdo,"0200",values),6));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(5,F,kdo,"0100",values.substring(0,values.length()-2)),6));
    }
    RedoRecords.Vector vector(int op,String... fields) {
        return new RedoRecords.Vector(11,op,1,12,0x03000083,2148761,1,3,
                Arrays.stream(fields).map(this::hex).toList());
    }
    @Test void observedMixedNineColumnLobInsertRetainsEachPhysicalValue()throws Exception {
        var fields=new java.util.ArrayList<byte[]>();
        fields.add(hex("010d00000000000005000f00ad020000a70b400234011200"));
        fields.add(hex("0b9a00030a9a0003fa120201010000002c0109000000000060000000000000000000000002000000270100000000000028c3"));
        for(int i=0;i<9;i++)fields.add(new byte[]{(byte)(i+1)});
        var v=new RedoRecords.Vector(11,2,1,12,50371083,3169164,1,3,fields);
        var p=new HeapRowPatch().decodeLob(v,9);
        assertEquals(HeapRowPatch.Kind.INSERT,p.kind());assertEquals(9,p.extent().storedColumns());
        for(int i=0;i<9;i++)assertArrayEquals(new byte[]{(byte)(i+1)},p.columns().get(i).bytes());
        assertThrows(RedoException.class,()->new HeapRowPatch().decodeLob(v,8));
        var scalar=new HeapRowPatch().decode(v,9);
        for(int i=0;i<9;i++)assertArrayEquals(p.columns().get(i).bytes(),scalar.columns().get(i).bytes());
        fields.get(1)[18]=10;
        assertThrows(RedoException.class,()->new HeapRowPatch().decodeLob(v,9));
    }
    @Test void decodesInsertBytesAndTransactionIdentity() throws Exception {
        var p=new HeapRowPatch().decode(vector(2,F,IRP,"c102","616c706861","c202182e","787e0a04090a0b"),4);
        assertEquals(0,p.slot()); assertEquals(new HeapRowPatch.Xid(10,12,1105),p.xid());
        assertEquals(new HeapRowPatch.Uba(0x02402cbe,242,7),p.uba());
        assertEquals("123.45",OracleScalars.number(p.columns().get(2).bytes()).toPlainString());
        assertEquals("alpha",OracleScalars.varchar2(p.columns().get(1).bytes(),"AL32UTF8"));
    }
    @Test void updateHasAbsentKeyAndChangedValueOnly() throws Exception {
        var p=new HeapRowPatch().decode(vector(5,F,URP,"0100","62657461"),4);
        assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(0).state());
        assertEquals(HeapRowPatch.State.VALUE,p.columns().get(1).state());
        assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(2).state());
    }
    @Test void doesNotInventXidFromKtbC() throws Exception {
        var p=new HeapRowPatch().decode(vector(5,C,URP,"0100","62657461"),4);
        assertNull(p.xid()); assertEquals(28,p.uba().record());
    }
    @Test void distinguishesExplicitNullAndOmittedColumn() throws Exception {
        var p=new HeapRowPatch().decode(vector(2,F,IRP,"c10e","","80","787e0a04010101"),4);
        assertEquals(HeapRowPatch.State.NULL,p.columns().get(1).state());
        byte[] kdo=hex(IRP); kdo[18]=2;
        p=new HeapRowPatch().decode(vector(2,F,HexFormat.of().formatHex(kdo),"c10d","e4b8ade69687"),4);
        assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(2).state());
        assertEquals("中文",OracleScalars.varchar2(p.columns().get(1).bytes(),"AL32UTF8"));
    }
    @Test void actualSeq89CleanoutUpdateKeepsOnlyCurrentPrefixUba()throws Exception {
        var v=new RedoRecords.Vector(11,5,1,12,0x03006b4d,2921895,1,3,Arrays.asList(
                hex("120d000000000000f4004002180103000000000000000000000000000000000000000000000000000000000002010d00a9952c000080000001060080a7952c00000000000000"),
                hex("4d6b00034a6b0003fa120501020044242c021200000003010300000000"),hex("0200"),hex("6f7665726c61702d62")));
        var p=new HeapRowPatch().decode(v,3);assertNull(p.xid());
        assertEquals(new HeapRowPatch.Uba(0x024000f4,280,3),p.uba());
        assertEquals(HeapRowPatch.Kind.UPDATE,p.kind());assertEquals(0,p.slot());
        assertEquals("overlap-b",OracleScalars.varchar2(p.columns().get(2).bytes(),"AL32UTF8"));
        v.fields().get(0)[0]=0x13;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(v,3));
    }
    @Test void deleteDoesNotGuessBeforeImage() throws Exception {
        var p=new HeapRowPatch().decode(vector(3,F,"8300000382000003fa1203010100000000000000"),4);
        assertEquals(HeapRowPatch.Kind.DELETE,p.kind());
        assertTrue(p.columns().stream().allMatch(c -> c.state() == HeapRowPatch.State.ABSENT));
    }
    @Test void rollbackItlIsNotCurrentTransactionIdentity() throws Exception {
        var p=new HeapRowPatch().decode(vector(3,"040d4002f20017000a0003006e040000be2c4002f200180000800080b6c92000",
                "8300000382000003fa120302010000000300002e"),4);
        assertEquals(2,p.xtype()); assertEquals(3,p.slot()); assertNull(p.xid()); assertNull(p.uba());
    }
    @Test void refusesColumnMapPoisonMissingFieldAndSplitRow() {
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(5,F,URP,"0400","61"),4));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(2,F,IRP,"c102"),4));
        byte[] kdo=hex(IRP); kdo[16]=0x20;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(vector(2,F,HexFormat.of().formatHex(kdo),"c102","61","80","787e0a04010101"),4));
    }
}
