package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KtbZInsertTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector row() {
        return new RedoRecords.Vector(11,2,1,12,50331955,2255346,1,3,List.of(
                hex("030dc000681ce80e"),hex("3301000332010003fa120202020000002c0002f6e3760000d06618f6e376000000000000000000000c0001000000000010"),
                hex("c104"),hex("7468726565")));
    }
    @Test void observedXrInsertRestoresColumnsWithoutInventingIdentity() throws Exception {
        var p=new HeapRowPatch().decode(row(),2);
        assertEquals(HeapRowPatch.Kind.INSERT,p.kind());assertEquals(2,p.xtype());assertEquals(1,p.slot());
        assertNull(p.xid());assertNull(p.uba());assertEquals("3",OracleScalars.number(p.columns().get(0).bytes()).toPlainString());
        assertEquals("three",OracleScalars.varchar2(p.columns().get(1).bytes(),"AL32UTF8"));
    }
    @Test void xaPackedModeAndOtherOpcodeRemainRefused() {
        for (int mode:new int[]{1,0x82}) {
            var r=row();r.fields().get(1)[11]=(byte)mode;
            assertEquals("UNSUPPORTED_KTB_Z_ROW_PROFILE",assertThrows(RedoException.class,()->new HeapRowPatch().decode(r,2)).getMessage());
        }
        var r=row();var other=new RedoRecords.Vector(11,5,1,12,r.dba(),r.scn(),1,3,r.fields());
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(other,2));
    }
    @Test void wrongKdoOpcodeSplitPieceAndMissingColumnCannotPass() {
        var r=row();r.fields().get(1)[10]=3;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(r,2));
        var split=row();split.fields().get(1)[16]=0x20;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(split,2));
        var missing=row();var shortRow=new RedoRecords.Vector(11,2,1,12,missing.dba(),missing.scn(),1,3,missing.fields().subList(0,3));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(shortRow,2));
    }
}
