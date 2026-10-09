package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated real seq96: contiguous two-value KDOM2 and two-column NULL tail. */
class HeapTemporalUpdateTest {
    java.util.List<byte[]> fields(String... hex) {return Arrays.stream(hex).map(HexFormat.of()::parseHex).toList();}
    static final String PACKED="ef810003ea810003fa120581020000002c0012000000030200000300";
    static final String VALUES="07787e0a090d0e0f0b787e0a090d0e0f3ade68b1";
    @Test void packedXaAndXrKeepTwoDistinctValuesAndAbsentKey()throws Exception {
        for(String kdo:new String[]{PACKED,PACKED.replace("0581","0582")}) {
            var patch=HeapRowPatch.decodeKdo(fields("",kdo,"0100",VALUES),5,0x030081efL,3);
            assertEquals(HeapRowPatch.State.ABSENT,patch.columns().get(0).state());
            assertEquals("2026-10-09T12:13:14",OracleScalars.date(patch.columns().get(1).bytes()).toString());
            assertEquals(987654321,OracleScalars.timestamp(patch.columns().get(2).bytes()).getNano());
            assertEquals(2,patch.extent().changedColumns());
        }
        for(String value:new String[]{VALUES.substring(2),VALUES+"00",VALUES.substring(0,VALUES.length()-2),"ff"+VALUES.substring(2),VALUES.replace("0b787e","0a787e")})
            assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(fields("",PACKED,"0100",value),5,0x030081efL,3));
        assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(fields("",PACKED,"0200",VALUES),5,0x030081efL,3));
    }
    @Test void realTwoColumnNullTailRequiresExactUndoExtentAndValues()throws Exception {
        var row=new RedoRecords.Vector(11,5,0,12,0x030081eeL,3030955,1,3,fields(
                "110d0000000000000a00070067100000420440028e011d00000000000000000000000000000000000000000002010d00aa3f2e000080000001060080a53f2e00000000000000",
                "ee810003ea810003fa120501020000002c02120000000100f0ff0300","01000200","",""));
        var undo=new RedoRecords.Vector(5,1,0,12,0x02400442L,3030955,1,3,fields(
                "9800ee12120040020a000700671000008e011d00",
                "3c3501003c35010005000000000000000b010700080c010000000000420440028e011b002f3f2e0000000000313f2e000000000000000000a83f2e00000000003f0440020000000000000000",
                "030db78200000000","ee810003ea810003fa120501020000002c001200000003021000000000","01000200","7764021c010101","77c70c1f183c3c"));
        var record=new RedoRecords.Record(25552,16,152,1,3030955,0,677737149,java.util.List.of(undo,row));
        var pair=HeapUndoPair.bind(record,row,3);
        assertEquals(HeapRowPatch.State.NULL,pair.forward().columns().get(1).state());
        assertEquals(HeapRowPatch.State.NULL,pair.forward().columns().get(2).state());
        assertEquals(-16,pair.forward().extent().sizeDelta());
        row.fields().get(1)[24]=(byte)0xf1;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record,row,3));
    }
}
