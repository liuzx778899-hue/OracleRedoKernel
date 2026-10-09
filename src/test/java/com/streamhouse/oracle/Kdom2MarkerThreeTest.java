package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual dedicated 19c seq41: short marker3 at physical column1, ncol3, XA, value16. */
class Kdom2MarkerThreeTest {
    static final String KDO="9301000392010003fa120581010040002c0112000000030100000300";
    static final String KTB="110d02002900000002001600540200006c264002bf000b000000000000000000280000004f524b313900000002010d00131d240000800000020600800a1d2400000000000000";
    static final String VALUE="10636170747572652d666f6c6c6f772d62";
    RedoRecords.Vector forward(String kdo,String map,String value) {
        return new RedoRecords.Vector(11,5,1,12,0x03000193,2366730,2,3,
                Arrays.stream(new String[]{KTB,kdo,map,value}).map(HexFormat.of()::parseHex).toList());
    }
    @Test void realRawRollbackInverseRestoresFourBinaryBytesInTwoColumns()throws Exception {
        var vector=new RedoRecords.Vector(5,1,0,12,0x02400b99L,3021679,1,3,Arrays.stream(new String[]{
                "a000141e1200000003001a00ff020000e5010400",
                "8d3401008d34010005000000000000000b011a00080c010000000000990b4002e501030061022e000000000062022e0000000000080c01006d1b2e0000000000fa0140020000000000000000",
                "040d00000000000007000100ad0200006b2840020801230000800080dc132e00",
                "a77d0003a27d0003fa1205810100ffff2c0012000000020100000300","0100","04010080ff"
        }).map(HexFormat.of()::parseHex).toList());
        var patch=UndoRowPatch.decode(vector,2).inverse();
        assertEquals(HeapRowPatch.State.ABSENT,patch.columns().get(0).state());
        assertArrayEquals(HexFormat.of().parseHex("010080ff"),patch.columns().get(1).bytes());
        var row=new RedoRecords.Vector(11,5,0,12,0x03007da7L,3021679,1,3,Arrays.stream(new String[]{
                "040d00000000000007000100ad0200006b2840020801230000800080dc132e00",
                "a77d0003a27d0003fa1205820100ffff2c0012000000020100000300","0100","04010080ff"
        }).map(HexFormat.of()::parseHex).toList());
        var restored=new HeapRowPatch().decode(row,2);
        assertEquals(2,restored.xtype());assertArrayEquals(patch.columns().get(1).bytes(),restored.columns().get(1).bytes());
        vector.fields().get(5)[0]=3;
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector,2));
    }
    @Test void realForwardAndInverseKeepAbsentColumns() throws Exception {
        var f=new HeapRowPatch().decode(forward(KDO,"0100",VALUE),3);
        assertEquals("capture-follow-b",OracleScalars.varchar2(f.columns().get(1).bytes(),"AL32UTF8"));
        assertEquals(HeapRowPatch.State.ABSENT,f.columns().get(0).state());assertEquals(HeapRowPatch.State.ABSENT,f.columns().get(2).state());
        var inverse=new RedoRecords.Vector(5,1,20,11,0x0240266c,2366381,2,3,Arrays.stream(new String[]{
                "ac00361a120000000200160054020000bf000b00",
                "711d0100741d010005000000000000000b011600080c0100000000006c264002bf000900f9eb230000000000efec230000000000080c010000000000000000006a2640020000000000000000",
                "040d0000000000000a0016008b060000142b400208010400008000805e0c2400",
                "9301000392010003fa120581010040002c0012000000030100000300","0100","10636170747572652d666f6c6c6f772d61"
        }).map(HexFormat.of()::parseHex).toList());
        var u=UndoRowPatch.decode(inverse,3);
        assertEquals("capture-follow-a",OracleScalars.varchar2(u.inverse().columns().get(1).bytes(),"AL32UTF8"));
        assertEquals(f.uba(),u.identity().uba());assertEquals(f.xid(),u.identity().xid());
    }
    @Test void doesNotGeneralizeUnknownMarkerThreeShortProfiles() {
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(forward(KDO,"0200",VALUE),3));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(forward(KDO,"0100","0f"+VALUE.substring(2)),3));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(forward(KDO,"0100",VALUE.substring(0,VALUE.length()-2)),3));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(forward(KDO,"0100",VALUE),4));
        byte[] raw=HexFormat.of().parseHex(KDO);raw[11]=(byte)0x82;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(forward(HexFormat.of().formatHex(raw),"0100",VALUE),3));
    }
}
