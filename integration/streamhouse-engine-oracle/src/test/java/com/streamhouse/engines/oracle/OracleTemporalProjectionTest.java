package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleTemporalProjectionTest {
    @Test void frozenBcRowsKeepOracleEraThroughTypedProjection()throws Exception {
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"D","DATE",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"TS","TIMESTAMP",false,false));
        var image=OracleBatchProjection.image(columns,List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"6438030f0d2339"),
                new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"6463010101010100000001")));
        assertEquals(new OracleTemporalValue("DATE","-0043-03-15T12:34:56"),image.get("D"));
        assertEquals(new OracleTemporalValue("TIMESTAMP","0000-01-01T00:00:00.000000001"),image.get("TS"));
        assertEquals(LocalDateTime.of(0,1,1,0,0,0,1),((OracleTemporalValue)image.get("TS")).localDateTime());
        assertEquals("0044-03-15T12:34:56 BC",((OracleTemporalValue)image.get("D")).bcText());
        assertEquals("0001-01-01T00:00:00.000000001 BC",((OracleTemporalValue)image.get("TS")).bcText());
        assertThrows(IllegalArgumentException.class,()->OracleTemporalValue.of("DATE",LocalDateTime.of(-4712,1,1,0,0)));
    }
    @Test void keepsTypeWallClockNanosecondsAndNull()throws Exception {
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"D","DATE",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"TS","TIMESTAMP",true,false));
        var image=OracleBatchProjection.image(columns,List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c021d183c3b"),
                new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c021d183c3b075bcd15")));
        assertEquals(new OracleTemporalValue("DATE","2024-02-29T23:59:58"),image.get("D"));
        assertEquals(new OracleTemporalValue("TIMESTAMP","2024-02-29T23:59:58.123456789"),image.get("TS"));
        assertNull(OracleBatchProjection.image(columns,List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c021d183c3b"),
                new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""))).get("TS"));
        assertThrows(IllegalArgumentException.class,()->OracleTemporalValue.of("DATE",LocalDateTime.of(2024,2,29,23,59,58,1)));
        assertThrows(java.time.format.DateTimeParseException.class,()->new OracleTemporalValue("TIMESTAMP","2024-02-29T23:59:58.123"));
        assertThrows(java.time.format.DateTimeParseException.class,()->new OracleTemporalValue("DATE","2023-02-29T23:59:58"));
        assertThrows(IllegalArgumentException.class,()->new OracleTemporalValue("TIMESTAMP WITH TIME ZONE","2024-02-29T23:59:58.123456789"));
    }
}
