package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleTemporalProjectionTest {
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
