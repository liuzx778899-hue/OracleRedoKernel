package com.streamhouse.oracle;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

class ArchivedHeapRowsTest {
    ArchivedHeapRows.Cell value(String hex) {return new ArchivedHeapRows.Cell(State.VALUE,hex);}
    List<ArchivedHeapRows.Cell> baseline() {return List.of(value("c10b"),value("62657461"),value("736563726574"));}
    HeapUndoPair tail() throws Exception {
        var fixture=new HeapTailExtentTest();var forward=fixture.row();
        return HeapUndoPair.bind(fixture.record(fixture.undo(),forward),forward,3);
    }
    @Test void matchedTailRestoresNullAndCarriesOnlyBaselineUnchangedValues() throws Exception {
        var result=ArchivedHeapRows.apply(tail(),baseline(),3);
        assertEquals(List.of(value("c10b"),value("62657461"),new ArchivedHeapRows.Cell(State.NULL,"")),result);
        assertEquals(State.VALUE,baseline().get(2).state());
        assertThrows(UnsupportedOperationException.class,()->result.clear());
    }
    @Test void missingBaselineWrongBeforeAndWrongPhysicalExtentRefuse() throws Exception {
        var pair=tail();
        assertEquals("ROW_REPLAY_MISSING_BASELINE_ROW",assertThrows(RedoException.class,()->ArchivedHeapRows.apply(pair,null,3)).getMessage());
        assertEquals("ROW_REPLAY_BEFORE_VALUE_MISMATCH",assertThrows(RedoException.class,()->ArchivedHeapRows.apply(pair,List.of(value("c10b"),value("62657461"),value("6e6577")),3)).getMessage());
        assertEquals("ROW_REPLAY_BEFORE_EXTENT_MISMATCH",assertThrows(RedoException.class,()->ArchivedHeapRows.apply(pair,List.of(value("c10b"),value("62657461"),new ArchivedHeapRows.Cell(State.NULL,"")),3)).getMessage());
    }
    @Test void completeBaselineCannotContainAbsentAndOwnsItsRows() {
        assertThrows(IllegalArgumentException.class,()->new ArchivedHeapRows.Cell(State.ABSENT,""));
        assertThrows(IllegalArgumentException.class,()->new ArchivedHeapRows.Cell(State.NULL,"61"));
        var key=new ArchivedHeapRows.RowKey(73073,0x03000193L,0);var row=new java.util.ArrayList<>(baseline());var rows=new java.util.HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();rows.put(key,row);
        var b=new ArchivedHeapRows.Baseline(null,2282688,73073,3,rows);row.clear();rows.clear();assertEquals(3,b.rows().get(key).size());
        assertThrows(UnsupportedOperationException.class,()->b.rows().clear());
    }
    @Test void missingBaselineRefusesBeforeReadingInput() {
        assertEquals("MISSING_COMPLETE_BASELINE",assertThrows(RedoException.class,()->ArchivedHeapRows.read(List.of(),null,73074,0x0300019bL)).getMessage());
    }
    List<FrozenHeapBaseline.PhysicalColumn> nullableTail(boolean lastNullable) {
        return List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"TXT","VARCHAR2",true,false),
                new FrozenHeapBaseline.PhysicalColumn(3,3,"NOTE","VARCHAR2",lastNullable,true));
    }
    HeapUndoPair omittedTail(boolean delete,int stored) throws Exception {
        var original=tail();var absent=new Column(State.ABSENT,new byte[0]);
        var insert=new Patch(Kind.INSERT,original.forward().dba(),original.forward().slot(),1,
                original.forward().xid(),original.forward().uba(),List.of(new Column(State.VALUE,new byte[]{(byte)0xc1,12}),absent,absent),
                new RowExtent(stored,stored,-1,6));
        var drop=new Patch(Kind.DELETE,insert.dba(),insert.slot(),1,insert.xid(),insert.uba(),List.of(absent,absent,absent));
        var undo=original.undo();
        return new HeapUndoPair(original.containerUid(),original.containerId(),delete ? drop : insert,
                new UndoRowPatch(undo.identity(),delete ? insert : drop,undo.previousDba(),undo.predecessorRecord(),undo.wrap()));
    }
    @Test void onlyTypedCompleteNullableTailCreatesNullImagesAndValidatesDeleteBefore() throws Exception {
        var pair=omittedTail(false,1);var image=List.of(value("c10c"),new ArchivedHeapRows.Cell(State.NULL,""),new ArchivedHeapRows.Cell(State.NULL,""));
        assertEquals(image,ArchivedHeapRows.apply(pair,null,3,nullableTail(true)));
        assertEquals(State.ABSENT,pair.forward().columns().get(1).state());
        assertEquals(List.of(),ArchivedHeapRows.apply(omittedTail(true,1),image,3,nullableTail(true)));
        assertThrows(RedoException.class,()->ArchivedHeapRows.apply(pair,null,3));
        assertThrows(RedoException.class,()->ArchivedHeapRows.apply(omittedTail(true,1),image,3));
        assertEquals("ROW_REPLAY_BEFORE_EXTENT_MISMATCH",assertThrows(RedoException.class,
                ()->ArchivedHeapRows.apply(omittedTail(true,1),baseline(),3,nullableTail(true))).getMessage());
    }
    @Test void nonnullableTailAndAbsentInsideStoredPrefixCannotBeFilled() throws Exception {
        assertEquals("ROW_REPLAY_INCOMPLETE_INSERT",assertThrows(RedoException.class,
                ()->ArchivedHeapRows.apply(omittedTail(false,1),null,3,nullableTail(false))).getMessage());
        assertEquals("ROW_REPLAY_INCOMPLETE_INSERT",assertThrows(RedoException.class,
                ()->ArchivedHeapRows.apply(omittedTail(false,2),null,3,nullableTail(true))).getMessage());
    }
}
