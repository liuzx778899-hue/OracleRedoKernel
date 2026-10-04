package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapCleanoutCTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector row() {
        return new RedoRecords.Vector(11,3,1,12,50331943,2207273,1,3,List.of(
                hex("120d0000000000007a2d4002f20032000000000000000000000000000000000000000000000000000000000002010d0029ae2100008000000206008024ae2100000000000000"),
                hex("2701000322010003fa1203010100323000000030")));
    }
    RedoRecords.Vector undo() {
        return new RedoRecords.Vector(5,1,36,11,37760378,2207273,1,3,List.of(
                hex("7000a00d220000000a000e00be040000f2003200"),hex("631d0100631d010005000000000000000b010e310000be04"),
                hex("020d0001690132007a2d4002f2002f00"),
                hex("2701000322010003fa120201010032302c0002d3fe7f00007820897a0000000000000000000000000b00000000000000c8"),
                hex("c102"),hex("6b656570")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(10176,16,1408,13,2207273,1,677737149,List.of(v)); }
    @Test void decodesCurrentCReferenceAndBindsRestoredBeforeImage() throws Exception {
        var row=row();var pair=HeapUndoPair.bind(record(row,undo()),row,2);
        assertNull(pair.forward().xid());
        assertEquals(new HeapRowPatch.Uba(37760378,242,50),pair.forward().uba());
        assertEquals(new HeapRowPatch.Xid(10,14,1214),pair.undo().identity().xid());
        assertEquals("keep",OracleScalars.varchar2(pair.undo().inverse().columns().get(1).bytes(),"AL32UTF8"));
    }
    @Test void refusesUnobservedLengthVersionOrOperation() {
        var r=row();
        var shortRow=new RedoRecords.Vector(11,3,1,12,r.dba(),r.scn(),1,3,List.of(Arrays.copyOf(r.fields().get(0),69),r.fields().get(1)));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(shortRow,2));
        var version=row();version.fields().get(0)[1]=0;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(version,2));
        var update=new RedoRecords.Vector(11,5,1,12,r.dba(),r.scn(),1,3,r.fields());
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(update,2));
    }
    @Test void rejectsWrongCurrentUbaAndRollbackMode() {
        var r=row();r.fields().get(0)[14]=51;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(r,undo()),r,2));
        var xr=row();xr.fields().get(1)[11]=2;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(xr,2));
    }
}
