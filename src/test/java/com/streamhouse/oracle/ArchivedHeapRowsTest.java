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
}
