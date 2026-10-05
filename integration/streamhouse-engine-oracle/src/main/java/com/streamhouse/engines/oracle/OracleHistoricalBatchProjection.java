package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import com.streamhouse.spi.OracleBatchEvidence;
import com.streamhouse.spi.OracleSchemaSequence;
import java.util.*;

/** Projection of factory-owned history; each event keeps its complete schema. No publication permit. */
public final class OracleHistoricalBatchProjection {
    private OracleHistoricalBatchProjection() {}
    public record Entry(CapturedHeapBatch.Schema schema,OracleBatchProjection.Entry event) {}
    public static final class Batch {
        private final CapturedHistoricalHeapBatch input;
        private final List<Entry> entries;
        private final String schemaSequenceSha256;
        private final OracleBatchEvidence evidence;
        private Batch(CapturedHistoricalHeapBatch input,List<Entry> entries,String schemaSequenceSha256) {
            this.input=input;this.entries=List.copyOf(entries);this.schemaSequenceSha256=schemaSequenceSha256;
            this.evidence=OracleHistoricalBatchProjection.evidence(input,this.entries,schemaSequenceSha256);
        }
        public String inputBatchSha256(){return input.sha256();}
        public ArchivedHeapRows.Source source(){return input.source();}
        public String baselineSha256(){return input.baselineSha256();}
        public long baselineScn(){return input.baselineScn();}
        public long leaf(){return input.leaf();}
        public List<CaptureJournal.Source> captures(){return input.captures();}
        public List<Entry> entries(){return entries;}
        public String schemaSequenceSha256(){return schemaSequenceSha256;}
        public OracleBatchEvidence evidence(){return evidence;}
    }
    public static Batch project(CapturedHistoricalHeapBatch input)throws RedoException {
        var entries=new ArrayList<Entry>();var ordinals=new HashMap<HeapRowPatch.Xid,Long>();
        for(var e:input.events())entries.add(new Entry(e.schema(),OracleBatchProjection.projectEntry(e.schema(),e.row(),input.sha256(),ordinals)));
        return new Batch(input,entries,schemaSequenceDigest(entries));
    }
    static String schemaSequenceDigest(List<Entry> entries) {
        return OracleSchemaSequence.digest(eventEvidence(entries),schemaEvidence(entries));
    }
    private static List<OracleBatchEvidence.EventSchema> schemaEvidence(List<Entry> entries) {
        return entries.stream().map(e->new OracleBatchEvidence.EventSchema(e.event().envelope().eventId(),e.schema().sha256(),columns(e.schema()))).toList();
    }
    private static List<OracleBatchEvidence.Column> columns(CapturedHeapBatch.Schema schema) {
        return schema.columns().stream().map(c->new OracleBatchEvidence.Column(c.segment(),c.internal(),c.name(),c.type(),c.nullable(),c.hidden())).toList();
    }
    private static List<OracleBatchEvidence.Event> eventEvidence(List<Entry> entries) {
        return entries.stream().map(e->{
            var event=e.event();var t=event.transaction();var x=t.xid();var p=event.pairedUndoPosition();
            // Row keys are supplied by the factory below, never inferred from envelope values.
            return new OracleBatchEvidence.Event(event.envelope().eventId(),event.payloadSha256(),new OracleBatchEvidence.Xid(x.usn(),x.slot(),x.sequence()),
                    t.beginRecordScn(),t.endRecordScn(),t.rolledBack(),new OracleBatchEvidence.Position(p.archiveSequence(),p.block(),p.offset(),p.vector()),null);
        }).toList();
    }
    private static OracleBatchEvidence evidence(CapturedHistoricalHeapBatch input,List<Entry> entries,String digest) {
        var s=input.source();var schema=input.baselineSchema();var h=schema.heap();var i=schema.index();
        var events=new ArrayList<OracleBatchEvidence.Event>();var identities=eventEvidence(entries);
        for(int at=0;at<identities.size();at++) {
            var e=identities.get(at);var k=input.events().get(at).row().rowKey();
            events.add(new OracleBatchEvidence.Event(e.eventId(),e.payloadSha256(),e.xid(),e.beginRecordScn(),e.endRecordScn(),e.rolledBack(),e.pairedUndoPosition(),new OracleBatchEvidence.RowKey(k.dataObjectId(),k.dba(),k.slot())));
        }
        return new OracleBatchEvidence(new OracleBatchEvidence.Source(s.dbid(),s.activationId(),s.compatibility(),s.thread(),s.resetlogsId(),s.resetlogsScn(),s.containerUid(),s.containerId()),
                input.captures().stream().map(c->new OracleBatchEvidence.Capture(c.journalSha256(),c.journalBytes(),c.capacityBlocks(),c.blocks(),c.sequence(),c.firstScn(),c.nextScn())).toList(),
                input.baselineSha256(),input.baselineScn(),schema.sha256(),new OracleBatchEvidence.ObjectRef(h.owner(),h.name(),h.logicalObject(),h.dataObject()),
                new OracleBatchEvidence.ObjectRef(i.owner(),i.name(),i.logicalObject(),i.dataObject()),columns(schema),input.leaf(),input.sha256(),events,schemaEvidence(entries),digest);
    }
}
