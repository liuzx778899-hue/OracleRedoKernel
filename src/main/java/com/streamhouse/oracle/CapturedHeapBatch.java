package com.streamhouse.oracle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

/** Complete fixed-schema captured heap batch. Not a historical dictionary or public CDC authorization. */
public final class CapturedHeapBatch {
    public record Schema(FrozenHeapBaseline.ObjectRole heap,FrozenHeapBaseline.ObjectRole index,
                         List<FrozenHeapBaseline.PhysicalColumn> columns,String sha256) {
        public Schema { columns=List.copyOf(columns); }
    }
    public record Event(String eventId,String payloadSha256,ArchivedUndoInventory.Position pairedUndoPosition,
                        TransactionLifecycle.ClosedIdentity transaction,ArchivedHeapRows.RowKey rowKey,
                        HeapRowPatch.Kind operation,List<ArchivedHeapRows.Cell> before,List<ArchivedHeapRows.Cell> after) {
        public Event { before=List.copyOf(before);after=List.copyOf(after); }
    }
    private final FrozenHeapBaseline.Snapshot snapshot;
    private final Schema schema;
    private final List<CaptureJournal.Source> captures;
    private final List<Event> events;
    private final String sha256;
    private final long leaf;
    private CapturedHeapBatch(FrozenHeapBaseline.Snapshot snapshot,Schema schema,List<CaptureJournal.Source> captures,
                              List<Event> events,String sha256,long leaf) {
        this.snapshot=snapshot;this.schema=schema;this.captures=List.copyOf(captures);
        this.events=List.copyOf(events);this.sha256=sha256;this.leaf=leaf;
    }
    public ArchivedHeapRows.Source source() { return snapshot.source(); }
    public String baselineSha256() { return snapshot.sha256(); }
    public long baselineScn() { return snapshot.scn(); }
    public Schema schema() { return schema; }
    public List<CaptureJournal.Source> captures() { return captures; }
    public List<Event> events() { return events; }
    public String sha256() { return sha256; }
    public long leaf() { return leaf; }

    public static CapturedHeapBatch read(List<CaptureJournal.Verified> captures,Path snapshotPath,
                                         String trustedAcquisitionSha256,long leaf) throws IOException {
        var inputs=List.copyOf(captures);
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var physical=ArchivedPhysicalTransactions.readVerifiedBaseline(RedoBatch.captures(inputs),snapshotPath,trustedAcquisitionSha256,leaf);
        var rows=ArchivedHeapRows.readCaptured(inputs,snapshotPath,trustedAcquisitionSha256,leaf);
        if (!physical.sources().equals(rows.sources())) throw new RedoException("CAPTURED_BATCH_SOURCE_CHANGED");
        var owners=new HashMap<ArchivedUndoInventory.Position,TransactionLifecycle.ClosedIdentity>();
        for (var t:physical.transactions()) for (var live:t.graph().live())
            if (owners.putIfAbsent(live.position(),t.identity())!=null)
                throw new RedoException("CAPTURED_BATCH_POSITION_OWNER_AMBIGUOUS");
        var schema=new Schema(snapshot.heap(),snapshot.index(),snapshot.columns(),schemaDigest(snapshot));
        var events=new ArrayList<Event>();var ids=new java.util.HashSet<String>();
        for (var change:rows.changes()) {
            var owner=owners.get(change.position());
            if (owner==null || owner.rolledBack() || !owner.xid().equals(change.xid())
                    || owner.endRecordScn()<=snapshot.scn()) throw new RedoException("CAPTURED_BATCH_ROW_OWNER_MISMATCH");
            String id=eventId(snapshot.source(),schema.sha256(),change);
            if (!ids.add(id)) throw new RedoException("CAPTURED_BATCH_DUPLICATE_EVENT_ID");
            events.add(new Event(id,payloadDigest(change,owner),change.position(),owner,change.key(),
                    change.kind(),change.before(),change.after()));
        }
        var sources=inputs.stream().map(CaptureJournal.Verified::source).toList();
        String batch=hash(out->{
            text(out,"ORK_CAPTURED_HEAP_BATCH_V1");text(out,snapshot.sha256());text(out,schema.sha256());
            out.writeLong(leaf);out.writeInt(sources.size());
            for (var source:sources) text(out,source.journalSha256());
            out.writeInt(events.size());for (var event:events) {text(out,event.eventId());text(out,event.payloadSha256());}
        });
        return new CapturedHeapBatch(snapshot,schema,sources,events,batch,leaf);
    }
    static String schemaDigest(FrozenHeapBaseline.Snapshot snapshot) {
        return hash(out->{
            text(out,"ORK_FIXED_HEAP_SCHEMA_V1");text(out,"AL32UTF8");
            for (var role:List.of(snapshot.heap(),snapshot.index())) {
                text(out,role.owner());text(out,role.name());out.writeLong(role.logicalObject());out.writeLong(role.dataObject());
            }
            out.writeInt(snapshot.columns().size());for (var c:snapshot.columns()) {
                out.writeInt(c.segment());out.writeInt(c.internal());text(out,c.name());text(out,c.type());
                out.writeBoolean(c.nullable());out.writeBoolean(c.hidden());
            }
        });
    }
    static String eventId(ArchivedHeapRows.Source source,String schema,ArchivedHeapRows.Change change) {
        return hash(out->{
            text(out,"ORK_CAPTURED_HEAP_EVENT_V1");
            for (long value:new long[]{source.dbid(),source.activationId(),source.compatibility(),source.thread(),
                    source.resetlogsId(),source.resetlogsScn(),source.containerUid(),source.containerId()}) out.writeLong(value);
            text(out,schema);var p=change.position();out.writeLong(p.archiveSequence());out.writeLong(p.block());
            out.writeInt(p.offset());out.writeInt(p.vector());var x=change.xid();
            out.writeInt(x.usn());out.writeInt(x.slot());out.writeLong(x.sequence());
            var k=change.key();out.writeLong(k.dataObjectId());out.writeLong(k.dba());out.writeInt(k.slot());
        });
    }
    static String payloadDigest(ArchivedHeapRows.Change change,TransactionLifecycle.ClosedIdentity transaction) {
        return hash(out->{
            text(out,"ORK_CAPTURED_HEAP_PAYLOAD_V1");text(out,change.kind().name());
            out.writeLong(transaction.beginRecordScn());out.writeLong(transaction.endRecordScn());
            for (var cells:List.of(change.before(),change.after())) {
                out.writeInt(cells.size());for (var cell:cells) {text(out,cell.state().name());text(out,cell.hex());}
            }
        });
    }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static String hash(Writer writer) {
        try {
            var bytes=new ByteArrayOutputStream();try (var out=new DataOutputStream(bytes)) {writer.write(out);}
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {throw new AssertionError(e);}
    }
    private static void text(DataOutputStream out,String value) throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
    }
}
