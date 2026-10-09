package com.streamhouse.oracle;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RawColumnTest {
    @Test void rawIsBinaryAndNullIsDistinctFromMissingOrEmpty()throws Exception {
        var schema=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"BIN","RAW",false,false));
        ArchivedHeapRows.requireCompleteValues(List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"00ffc328")),schema);
        assertThrows(RedoException.class,()->ArchivedHeapRows.requireCompleteValues(List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"")),schema));
        assertThrows(RedoException.class,()->ArchivedHeapRows.requireCompleteValues(List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")),schema));
        assertEquals(23,OracleColumnTypes.oracleId("RAW"));assertEquals("RAW",OracleColumnTypes.name(23));
        assertThrows(RedoException.class,()->OracleColumnTypes.name(0x100000017L));
        assertThrows(RedoException.class,()->OracleColumnTypes.oracleId("BLOB"));
    }
}
