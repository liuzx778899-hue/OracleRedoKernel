package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleZonedTemporalProjectionTest {
    private final OracleTimeZoneContext context=new OracleTimeZoneContext("+00:00",32,Map.of(382,"Europe/Paris"));
    @Test void projectionKeepsNativeBytesAndTrustedOracleTimeBasis()throws Exception {
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"TZ","TIMESTAMP WITH TIME ZONE",true,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"LTZ","TIMESTAMP WITH LOCAL TIME ZONE",true,false));
        var cells=List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c031f011f2e075bca0085f8"),
                new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c031f011f2e075bca00"));
        var values=OracleBatchProjection.image(columns,cells,context);
        var tz=(OracleZonedTemporalValue)values.get("TZ");assertEquals(cells.get(0).hex(),tz.hex());
        assertEquals("Europe/Paris",tz.zone());assertEquals(32,tz.timeZoneVersion());
        var ltz=(OracleZonedTemporalValue)values.get("LTZ");assertEquals(cells.get(1).hex(),ltz.hex());assertEquals("+00:00",ltz.zone());
        assertThrows(RedoException.class,()->OracleBatchProjection.image(columns,cells));
        assertThrows(RedoException.class,()->OracleBatchProjection.image(columns,cells,new OracleTimeZoneContext("+00:00",32,Map.of())));
    }
    @Test void nativeOffsetAndBcLocalBytesDoNotLoseFractions()throws Exception {
        var offset=OracleZonedTemporalValue.of("TIMESTAMP WITH TIME ZONE",java.util.HexFormat.of().parseHex("787c031e17012e075bcd15195a"),context);
        assertEquals("+05:30",offset.zone());assertEquals(13,offset.bytes().length);
        var bc=OracleZonedTemporalValue.of("TIMESTAMP WITH LOCAL TIME ZONE",java.util.HexFormat.of().parseHex("6438030f0d23393ade68b1"),context);
        assertEquals("6438030f0d23393ade68b1",bc.hex());
        assertThrows(IllegalArgumentException.class,()->new OracleZonedTemporalValue(bc.type(),bc.hex(),"+00:00",32,"Europe/Paris"));
    }
}
