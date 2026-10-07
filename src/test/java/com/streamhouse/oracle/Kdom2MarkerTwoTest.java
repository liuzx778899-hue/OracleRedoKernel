package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Kdom2MarkerTwoTest {
    // Independent owned 19.3 seq75/76 raw/dump: XA URP column1, two exact 24-byte values.
    static final String KDO="842a0003822a0003fa120581010000002c0112000000030100000200";
    byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    HeapRowPatch.Patch decode(String kdo,String map,String value,int columns) throws Exception {
        return HeapRowPatch.decodeKdo(List.of(new byte[0],hex(kdo),hex(map),hex(value)),5,50342532,columns);
    }
    @Test void decodesActualForwardAndUndoValuesWithoutInventingOtherColumns() throws Exception {
        var before=decode(KDO,"0100","18686973746f72792d70726f647563742d726573746f726564",3);
        var after=decode(KDO,"0100","1870726f647563742d6163746976652d73746167652d6f6e65",3);
        assertEquals("history-product-restored",OracleScalars.varchar2(before.columns().get(1).bytes(),"AL32UTF8"));
        assertEquals("product-active-stage-one",OracleScalars.varchar2(after.columns().get(1).bytes(),"AL32UTF8"));
        assertEquals(HeapRowPatch.State.ABSENT,after.columns().get(0).state());
        assertEquals(HeapRowPatch.State.ABSENT,after.columns().get(2).state());
        assertEquals(new HeapRowPatch.RowExtent(3,1,-1,null),after.extent());
    }
    @Test void rejectsLengthMapMarkerAndDictionaryPoison() {
        String value="1870726f647563742d6163746976652d73746167652d6f6e65";
        assertThrows(RedoException.class,()->decode(KDO,"0100","17"+value.substring(2),3));
        assertThrows(RedoException.class,()->decode(KDO,"0200",value,3));
        assertThrows(RedoException.class,()->decode(KDO.substring(0,52)+"0400","0100",value,3));
        assertThrows(RedoException.class,()->decode(KDO,"0100",value,4));
    }
}
