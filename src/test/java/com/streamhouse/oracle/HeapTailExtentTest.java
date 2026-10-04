package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.put;

class HeapTailExtentTest {
    // Self-generated 19.3 seq25, record11164. Native ncol2/nnew0 retains a tail map plus empty field.
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector row() {
        return new RedoRecords.Vector(11,5,1,12,0x03000193L,2282705,1,3,List.of(
                hex("020d000000000000a3294002f5002700"),hex("9301000392010003fa120501020000002c02120000000200f9ff0100"),hex("0200"),new byte[0]));
    }
    RedoRecords.Vector undo() {
        byte[] id=new byte[20],m=new byte[24];put(id,8,10,2);put(id,10,24,2);put(id,12,1246,4);put(id,16,245,2);put(id,18,39,1);
        put(m,0,73073,4);put(m,4,73073,4);put(m,8,5,4);put(m,16,11,1);put(m,17,1,1);put(m,18,24,1);put(m,19,38,1);put(m,22,1246,2);
        return new RedoRecords.Vector(5,1,36,11,0x024029a3L,2282705,1,3,List.of(id,m,
                hex("020db77100000000a3294002f5002600"),hex("9301000392010003fa120501020000002c021200000003010700000000"),hex("0200"),hex("736563726574")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) {return new RedoRecords.Record(11164,16,1024,1,2282705,1,677737149,List.of(v));}
    @Test void rawTailMarkerDoesNotTurnAbsentIntoNull() throws Exception {
        var p=new HeapRowPatch().decode(row(),3);assertEquals(new HeapRowPatch.RowExtent(2,0,2,-7),p.extent());
        assertTrue(p.columns().stream().allMatch(c->c.state()==HeapRowPatch.State.ABSENT));
    }
    @Test void bindsTailOnlyToMatchingExactUbaAndSingleInverseColumn() throws Exception {
        var row=row();var pair=HeapUndoPair.bind(record(row,undo()),row,3);
        assertEquals(2,pair.forward().extent().removedColumn());assertEquals(3,pair.undo().inverse().extent().storedColumns());
        assertEquals("secret",OracleScalars.varchar2(pair.undo().inverse().columns().get(2).bytes(),"AL32UTF8"));
        assertEquals(HeapRowPatch.State.ABSENT,pair.forward().columns().get(2).state());
    }
    @Test void smallerOrdinaryExtentRetainsUnchangedTrailingAbsence() throws Exception {
        var original=row();var kdo=hex("9301000392010003fa120501020000002c021200000002010100000000");
        var ordinary=new RedoRecords.Vector(11,5,1,12,original.dba(),original.scn(),1,3,List.of(original.fields().get(0),kdo,hex("0100"),hex("67616d6d61")));
        var p=new HeapRowPatch().decode(ordinary,3);assertEquals(2,p.extent().storedColumns());
        assertEquals(HeapRowPatch.State.VALUE,p.columns().get(1).state());assertEquals(HeapRowPatch.State.ABSENT,p.columns().get(2).state());
        var bad=kdo.clone();bad[22]=4;
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(new RedoRecords.Vector(11,5,1,12,ordinary.dba(),ordinary.scn(),1,3,List.of(original.fields().get(0),bad,hex("0100"),hex("61"))),3));
    }
    @Test void wrongTailMapNonemptyValueAndPositiveSizeRefuse() {
        for (int mode=0;mode<3;mode++) {
            var row=row();var fields=new java.util.ArrayList<>(row.fields());
            if (mode==0)fields.get(2)[0]=1;
            if (mode==1)fields.set(3,hex("61"));
            if (mode==2)put(fields.get(1),24,7,2);
            var wrong=new RedoRecords.Vector(11,5,1,12,row.dba(),row.scn(),1,3,fields);
            assertThrows(RedoException.class,()->new HeapRowPatch().decode(wrong,3));
        }
    }
    @Test void wrongUbaSlotInverseMapAndRemovedSizeRefuseBinding() {
        for (int mode=0;mode<4;mode++) {
            var row=row();var undo=undo();
            if (mode==0)undo.fields().get(0)[18]^=1;
            if (mode==1)undo.fields().get(3)[20]^=1;
            if (mode==2)undo.fields().get(4)[0]=1;
            if (mode==3)put(row.fields().get(1),24,-6,2);
            assertThrows(RedoException.class,()->HeapUndoPair.bind(record(undo,row),row,3));
        }
    }
}
