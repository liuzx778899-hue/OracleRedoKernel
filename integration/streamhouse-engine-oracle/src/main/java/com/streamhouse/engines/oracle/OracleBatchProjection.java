package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.ArchivedHeapRows;
import com.streamhouse.oracle.ArchivedUndoInventory;
import com.streamhouse.oracle.CapturedHeapBatch;
import com.streamhouse.oracle.CapturedHistoricalHeapBatch;
import com.streamhouse.oracle.FrozenHeapBaseline;
import com.streamhouse.oracle.HeapRowPatch;
import com.streamhouse.oracle.OracleScalars;
import com.streamhouse.oracle.RedoException;
import com.streamhouse.oracle.TransactionLifecycle;
import com.streamhouse.spi.EventEnvelope;
import com.streamhouse.spi.OracleBatchEvidence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed bounded projection only. No public decoder success, target write or ACK. */
public final class OracleBatchProjection {
    private OracleBatchProjection() {}
    public record Entry(EventEnvelope envelope,String payloadSha256,
                        ArchivedUndoInventory.Position pairedUndoPosition,
                        TransactionLifecycle.ClosedIdentity transaction) {}
    public static final class Batch {
        private final String inputBatchSha256;
        private final ArchivedHeapRows.Source source;
        private final CapturedHeapBatch.Schema schema;
        private final List<Entry> entries;
        private final OracleBatchEvidence evidence;
        private Batch(String inputBatchSha256,ArchivedHeapRows.Source source,CapturedHeapBatch.Schema schema,List<Entry> entries,OracleBatchEvidence evidence) {
            this.inputBatchSha256=inputBatchSha256;this.source=source;this.schema=schema;this.entries=List.copyOf(entries);
            this.evidence=evidence;
        }
        public String inputBatchSha256() {return inputBatchSha256;}
        public ArchivedHeapRows.Source source() {return source;}
        public CapturedHeapBatch.Schema schema() {return schema;}
        public List<Entry> entries() {return entries;}
        public OracleBatchEvidence evidence() {return evidence;}
    }
    public static Batch project(CapturedHistoricalHeapBatch input) throws RedoException {
        var historical=OracleHistoricalBatchProjection.project(input);
        return new Batch(input.sha256(),input.source(),input.baselineSchema(),
                historical.entries().stream().map(OracleHistoricalBatchProjection.Entry::event).toList(),historical.evidence());
    }
    public static Batch project(CapturedHeapBatch input) throws RedoException {
        var entries=new ArrayList<Entry>();var ordinals=new HashMap<HeapRowPatch.Xid,Long>();
        for (var event:input.events()) {
            entries.add(projectEntry(input.schema(),event,input.sha256(),ordinals));
        }
        return new Batch(input.sha256(),input.source(),input.schema(),entries,evidence(input));
    }
    static Entry projectEntry(CapturedHeapBatch.Schema schema,CapturedHeapBatch.Event event,String batchSha256,
                              Map<HeapRowPatch.Xid,Long> ordinals) throws RedoException {
            var columns=schema.columns();
            var before=image(columns,event.before());var after=image(columns,event.after());
            var changed=new ArrayList<String>();
            for (int i=0;i<columns.size();i++) if (event.before().isEmpty() || event.after().isEmpty()
                    || !event.before().get(i).equals(event.after().get(i))) changed.add(columns.get(i).name());
            var x=event.transaction().xid();long sequence=ordinals.merge(x,1L,Long::sum);
            EventEnvelope.Operation operation=switch(event.operation()) {
                case INSERT->EventEnvelope.Operation.INSERT;
                case UPDATE->EventEnvelope.Operation.UPDATE;
                case DELETE->EventEnvelope.Operation.DELETE;
                default->throw new RedoException("ORACLE_NON_DML_PROJECTION");
            };
            var envelope=new EventEnvelope("oracle1:"+event.eventId(),
                    new EventEnvelope.Source(null,null,null,null),
                    new EventEnvelope.TableRef(schema.heap().owner(),schema.heap().name()),operation,
                    // These legacy fields require SQL Server LSNs. Oracle positions remain typed below.
                    new EventEnvelope.TransactionRef("oracle-xid:"+x.usn()+"."+x.slot()+"."+x.sequence(),null,null,null,sequence),
                    before,after,List.copyOf(changed),
                    new EventEnvelope.Metadata("oracle-batch-sha256:"+batchSha256,schema.sha256(),true,true));
            return new Entry(envelope,event.payloadSha256(),event.pairedUndoPosition(),event.transaction());
    }
    private static OracleBatchEvidence evidence(CapturedHeapBatch input) {
        var s=input.source();var schema=input.schema();
        var source=new OracleBatchEvidence.Source(s.dbid(),s.activationId(),s.compatibility(),s.thread(),
                s.resetlogsId(),s.resetlogsScn(),s.containerUid(),s.containerId());
        var captures=input.captures().stream().map(c->new OracleBatchEvidence.Capture(c.journalSha256(),
                c.journalBytes(),c.capacityBlocks(),c.blocks(),c.sequence(),c.firstScn(),c.nextScn())).toList();
        var heap=schema.heap();var index=schema.index();
        var columns=schema.columns().stream().map(c->new OracleBatchEvidence.Column(c.segment(),c.internal(),
                c.name(),c.type(),c.nullable(),c.hidden())).toList();
        var events=input.events().stream().map(e->{
            var t=e.transaction();var x=t.xid();var p=e.pairedUndoPosition();var k=e.rowKey();
            return new OracleBatchEvidence.Event("oracle1:"+e.eventId(),e.payloadSha256(),
                    new OracleBatchEvidence.Xid(x.usn(),x.slot(),x.sequence()),t.beginRecordScn(),t.endRecordScn(),
                    t.rolledBack(),new OracleBatchEvidence.Position(p.archiveSequence(),p.block(),p.offset(),p.vector()),
                    new OracleBatchEvidence.RowKey(k.dataObjectId(),k.dba(),k.slot()));
        }).toList();
        return new OracleBatchEvidence(source,captures,input.baselineSha256(),input.baselineScn(),schema.sha256(),
                new OracleBatchEvidence.ObjectRef(heap.owner(),heap.name(),heap.logicalObject(),heap.dataObject()),
                new OracleBatchEvidence.ObjectRef(index.owner(),index.name(),index.logicalObject(),index.dataObject()),
                columns,input.leaf(),input.sha256(),events);
    }
    static Map<String,Object> image(List<FrozenHeapBaseline.PhysicalColumn> columns,List<ArchivedHeapRows.Cell> cells) throws RedoException {
        if (cells.isEmpty()) return null;
        if (cells.size()!=columns.size()) throw new RedoException("ORACLE_IMAGE_COLUMN_COVERAGE");
        var image=new LinkedHashMap<String,Object>();
        for (int i=0;i<columns.size();i++) {
            var column=columns.get(i);var cell=cells.get(i);Object value;
            if (cell.state()==HeapRowPatch.State.NULL) {
                if (!column.nullable()) throw new RedoException("ORACLE_NULL_IN_NOT_NULL_COLUMN");
                value=null;
            } else {
                byte[] bytes=HexFormat.of().parseHex(cell.hex());
                value=switch(column.type()) {
                    case "NUMBER"->OracleScalars.number(bytes);
                    case "VARCHAR2"->OracleScalars.varchar2(bytes,"AL32UTF8");
                    case "RAW"->new OracleRawValue(HexFormat.of().formatHex(bytes));
                    case "DATE"->OracleTemporalValue.of("DATE",OracleScalars.date(bytes));
                    case "TIMESTAMP"->OracleTemporalValue.of("TIMESTAMP",OracleScalars.timestamp(bytes));
                    case "BLOB","CLOB"->new OracleLobValue(column.type(),cell.hex());
                    default->throw new RedoException("ORACLE_UNSUPPORTED_PROJECTION_TYPE");
                };
            }
            image.put(column.name(),value);
        }
        // Map.copyOf rejects SQL NULL; preserve it together with physical column order.
        return Collections.unmodifiableMap(image);
    }
}
