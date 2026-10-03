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
    RedoRecords.Vector vector(int op,String... fields) {
        return new RedoRecords.Vector(11,op,1,12,0x03000083,2148761,1,3,
                Arrays.stream(fields).map(this::hex).toList());
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
