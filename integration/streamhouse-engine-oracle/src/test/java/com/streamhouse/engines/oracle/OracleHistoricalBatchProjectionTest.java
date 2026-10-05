package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleHistoricalBatchProjectionTest {
    CapturedHeapBatch.Schema schema(boolean extra) {
        var columns=new ArrayList<>(List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"TXT","VARCHAR2",true,false),
                new FrozenHeapBaseline.PhysicalColumn(3,3,"NOTE","VARCHAR2",true,true)));
        if(extra)columns.add(new FrozenHeapBaseline.PhysicalColumn(4,4,"EXTRA","VARCHAR2",true,false));
        return new CapturedHeapBatch.Schema(new FrozenHeapBaseline.ObjectRole("ORKLAB","T",1,1),
                new FrozenHeapBaseline.ObjectRole("ORKLAB","PK",2,2),columns,(extra?"a":"b").repeat(64));
    }
    CapturedHeapBatch.Event event(boolean extra) {
        var before=new ArrayList<>(List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"80"),
                new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"61"),new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"616e63686f72")));
        if(extra)before.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""));
        var after=new ArrayList<>(before);after.set(1,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"62"));
        return new CapturedHeapBatch.Event((extra?"c":"d").repeat(64),"e".repeat(64),new ArchivedUndoInventory.Position(1,2,16,4),
                new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(1,2,3),4,3,5,6,false),
                new ArchivedHeapRows.RowKey(1,2,0),HeapRowPatch.Kind.UPDATE,before,after);
    }
    @Test void schemaTransitionKeepsHiddenColumnSqlNullAndExactChangedColumns()throws Exception {
        var ordinals=new HashMap<HeapRowPatch.Xid,Long>();
        var a=OracleBatchProjection.projectEntry(schema(true),event(true),"f".repeat(64),ordinals).envelope();
        var b=OracleBatchProjection.projectEntry(schema(false),event(false),"f".repeat(64),ordinals).envelope();
        assertEquals(List.of("ID","TXT","NOTE","EXTRA"),new ArrayList<>(a.before().keySet()));
        assertEquals(BigDecimal.ZERO,a.before().get("ID"));assertEquals("anchor",a.after().get("NOTE"));
        assertTrue(a.after().containsKey("EXTRA"));assertNull(a.after().get("EXTRA"));
        assertFalse(b.after().containsKey("EXTRA"));assertEquals(List.of("TXT"),a.changedColumns());
        assertEquals("a".repeat(64),a.metadata().schemaVersion());assertEquals("b".repeat(64),b.metadata().schemaVersion());
        assertEquals(1,a.transaction().sequence());assertEquals(2,b.transaction().sequence());
        assertNull(a.transaction().commitLsn());assertThrows(UnsupportedOperationException.class,()->a.after().put("TXT","bad"));
    }
    @Test void sequenceCommitmentDetectsSwappedSchemasOrderOrEventAssociation()throws Exception {
        var a=new OracleHistoricalBatchProjection.Entry(schema(true),OracleBatchProjection.projectEntry(schema(true),event(true),"f".repeat(64),new HashMap<>()));
        var b=new OracleHistoricalBatchProjection.Entry(schema(false),OracleBatchProjection.projectEntry(schema(false),event(false),"f".repeat(64),new HashMap<>()));
        String digest=OracleHistoricalBatchProjection.schemaSequenceDigest(List.of(a,b));
        assertNotEquals(digest,OracleHistoricalBatchProjection.schemaSequenceDigest(List.of(b,a)));
        assertNotEquals(digest,OracleHistoricalBatchProjection.schemaSequenceDigest(List.of(new OracleHistoricalBatchProjection.Entry(b.schema(),a.event()),b)));
        assertNotEquals(digest,OracleHistoricalBatchProjection.schemaSequenceDigest(List.of(a,a)));
    }
}
