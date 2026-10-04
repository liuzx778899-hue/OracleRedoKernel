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
