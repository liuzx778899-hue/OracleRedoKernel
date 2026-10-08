package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtentMapPairTest {
    RedoRecords.Vector vector(int major,int minor,long dba,String... fields) {
        return new RedoRecords.Vector(major,minor,4,12,dba,2923459,1,3,
                java.util.Arrays.stream(fields).map(HexFormat.of()::parseHex).toList());
    }
    RedoRecords.Vector undo() {
        return vector(5,1,0x02400233,"6000e61c220000000a001f00250e00006d010900",
                "01000000e631010005000000000000000e051f088000250e",
                "ba6c000304000000ba6c000302000000c86c000308000000",
                "02698a83010000000000000000000000","05518a8301000000080000000000000000000000");
    }
    RedoRecords.Vector forward() {
        return vector(14,4,0x03006cba,"01000000c86c00030800000001000000",
                "0c7f000001000000b86c0003c86c0003","0500000002000000100000000000000000000000");
    }
    RedoRecords.Record record(RedoRecords.Vector u,RedoRecords.Vector f) {
        return new RedoRecords.Record(1328,464,264,1,2923459,11,677737149,List.of(u,f));
    }
    @Test void bindsRealExtentAddToExactUndo() throws Exception {
        var u=undo();var f=forward();var pair=ExtentMapPair.bind(record(u,f),u,f);
        assertEquals(new HeapRowPatch.Xid(10,31,3621),pair.xid());
        assertEquals(new HeapRowPatch.Uba(0x02400233,365,9),pair.uba());
        assertEquals(78310,pair.dataObject());assertEquals(0x03006cc8,pair.extent());
        assertEquals(8,pair.blocks());assertEquals(1,pair.previousExtents());assertEquals(8,pair.previousBlocks());
    }
    @Test void rejectsAnotherExtentAndDoesNotBroadenRowUndoFlags() {
        var u=undo();var f=forward();f.fields().get(0)[4]^=1;
        assertThrows(RedoException.class,()->ExtentMapPair.bind(record(u,f),u,f));
        assertThrows(RedoException.class,()->UndoHeader.decode(u));
    }
}
