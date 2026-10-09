package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.ArchivedHeapRows;
import com.streamhouse.oracle.FrozenHeapBaseline;
import com.streamhouse.oracle.HeapRowPatch;
import com.streamhouse.oracle.RedoException;
import com.streamhouse.spi.CapDecoder;
import com.streamhouse.spi.EngineConnector;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleBatchProjectionTest {
    final FrozenHeapBaseline.PhysicalColumn id=new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false);
    final FrozenHeapBaseline.PhysicalColumn note=new FrozenHeapBaseline.PhysicalColumn(2,2,"NOTE","VARCHAR2",true,true);
    ArchivedHeapRows.Cell value(String hex) {return new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,hex);}
    @Test void absentImageIsNullWhileSqlNullRemainsInPresentImage() throws Exception {
        assertNull(OracleBatchProjection.image(List.of(id,note),List.of()));
        var present=OracleBatchProjection.image(List.of(note),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")));
        assertTrue(present.containsKey("NOTE"));assertNull(present.get("NOTE"));
    }
    @Test void retainsExactNumberSqlNullAndHiddenPhysicalColumn() throws Exception {
        var image=OracleBatchProjection.image(List.of(id,note),List.of(value("c10c"),new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")));
        assertEquals(new BigDecimal("11"),image.get("ID"));assertTrue(image.containsKey("NOTE"));assertNull(image.get("NOTE"));
        assertThrows(UnsupportedOperationException.class,()->image.put("ID",12));
    }
    @Test void nonAsciiAl32utf8ValueDecodesWithoutReplacement() throws Exception {
        assertEquals("中文",OracleBatchProjection.image(List.of(note),List.of(value("e4b8ade69687"))).get("NOTE"));
    }
    @Test void lateInvalidUtf8AndIncompleteImageRefuse() {
        assertEquals("INVALID_VARCHAR2_ENCODING",assertThrows(RedoException.class,
                ()->OracleBatchProjection.image(List.of(id,note),List.of(value("c10c"),value("80")))).getMessage());
        assertEquals("ORACLE_IMAGE_COLUMN_COVERAGE",assertThrows(RedoException.class,
                ()->OracleBatchProjection.image(List.of(id,note),List.of(value("c10c")))).getMessage());
    }
    @Test void notNullAndUnsupportedTypeRefuse() {
        assertEquals("ORACLE_NULL_IN_NOT_NULL_COLUMN",assertThrows(RedoException.class,
                ()->OracleBatchProjection.image(List.of(id),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")))).getMessage());
        var lob=new FrozenHeapBaseline.PhysicalColumn(1,1,"LOB","BFILE",true,false);
        assertEquals("ORACLE_UNSUPPORTED_PROJECTION_TYPE",assertThrows(RedoException.class,
                ()->OracleBatchProjection.image(List.of(lob),List.of(value("01")))).getMessage());
    }
    @Test void publicDecoderCannotBeEnabledByAnOptionOrDiscovery() throws Exception {
        var connector=ServiceLoader.load(EngineConnector.class).stream().map(ServiceLoader.Provider::get)
                .filter(c->c.descriptor().engineType().equals("ORACLE")).findFirst().orElseThrow();
        var result=connector.decoder().decode(Path.of("untrusted.oracap"),
                new CapDecoder.DecodeContext("source","",Map.of("authorize","true","mode","BOUNDED_EVIDENCE")));
        assertNotEquals(0,result.rc());assertFalse(result.events().hasNext());assertNull(result.eventsPath());
        assertThrows(java.io.IOException.class,()->connector.windowScanner().scanFrozenWindows(Path.of(".")));
    }
}
