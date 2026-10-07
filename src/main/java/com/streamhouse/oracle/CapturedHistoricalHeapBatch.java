package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.CapturedHeapBatch.*;

/** Complete captured events with archive-owned committed dictionary schemas at each event SCN. */
public final class CapturedHistoricalHeapBatch {
    public record Event(Schema schema,CapturedHeapBatch.Event row) {}
    private final FrozenHeapBaseline.Snapshot snapshot;
    private final List<CaptureJournal.Source> captures;
    private final List<Event> events;
    private final String sha256;
    private final long leaf;
    private CapturedHistoricalHeapBatch(FrozenHeapBaseline.Snapshot snapshot,List<CaptureJournal.Source> captures,List<Event> events,String sha256,long leaf) {
        this.snapshot=snapshot;this.captures=List.copyOf(captures);this.events=List.copyOf(events);this.sha256=sha256;this.leaf=leaf;
    }
    public ArchivedHeapRows.Source source(){return snapshot.source();}
    public String baselineSha256(){return snapshot.sha256();}
    public long baselineScn(){return snapshot.scn();}
    public CapturedHeapBatch.Schema baselineSchema(){return new CapturedHeapBatch.Schema(snapshot.heap(),snapshot.index(),snapshot.columns(),CapturedHeapBatch.schemaDigest(snapshot));}
    public List<CaptureJournal.Source> captures(){return captures;}
    public List<Event> events(){return events;}
    public String sha256(){return sha256;}
    public long leaf(){return leaf;}
    public static CapturedHistoricalHeapBatch read(List<CaptureJournal.Verified> captures,Path snapshotPath,String trustedAcquisitionSha256,long leaf)throws IOException {
        var inputs=List.copyOf(captures);var paths=RedoBatch.captures(inputs);
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var history=ArchivedDictionaryHistory.read(paths,snapshot);
        return read(inputs,paths,snapshot,history,leaf);
    }
    public static CapturedHistoricalHeapBatch readRestored(List<CaptureJournal.Verified> captures,Path snapshotPath,String trustedAcquisitionSha256,
                                                         long leaf,byte[] dictionaryCheckpoint,String trustedCheckpointSha256)throws IOException {
        var inputs=List.copyOf(captures);var paths=RedoBatch.captures(inputs);
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var history=ArchivedDictionaryHistory.restore(paths,snapshot,dictionaryCheckpoint,trustedCheckpointSha256);
        return read(inputs,paths,snapshot,history,leaf);
    }
    private static CapturedHistoricalHeapBatch read(List<CaptureJournal.Verified> inputs,RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,
                                                   ArchivedDictionaryHistory.Timeline history,long leaf)throws IOException {
        var physical=ArchivedPhysicalTransactions.readHistory(paths,snapshot,history,leaf);
        var rows=ArchivedHeapRows.readHistory(paths,snapshot,leaf,history,physical);
        if(!physical.sources().equals(rows.sources()) || !rows.sources().equals(history.sources()))throw new RedoException("HISTORICAL_BATCH_SOURCE_CHANGED");
        var owners=new HashMap<ArchivedUndoInventory.Position,TransactionLifecycle.ClosedIdentity>();
        for(var t:physical.transactions())for(var live:t.graph().live())if(owners.putIfAbsent(live.position(),t.identity())!=null)
            throw new RedoException("HISTORICAL_BATCH_POSITION_OWNER_AMBIGUOUS");
        var events=new ArrayList<Event>();var ids=new HashSet<String>();
        for(var change:rows.changes()) {
            var owner=owners.get(change.position());
            if(owner==null || owner.rolledBack() || !owner.xid().equals(change.xid()) || owner.endRecordScn()<=snapshot.scn())
                throw new RedoException("HISTORICAL_BATCH_ROW_OWNER_MISMATCH");
            var columns=history.schemaAt(owner.endRecordScn());
            var schema=new Schema(snapshot.heap(),snapshot.index(),columns,schemaDigest(snapshot.heap(),snapshot.index(),columns));
            String id=eventId(snapshot.source(),schema.sha256(),change);if(!ids.add(id))throw new RedoException("HISTORICAL_BATCH_DUPLICATE_EVENT_ID");
            events.add(new Event(schema,new CapturedHeapBatch.Event(id,payloadDigest(change,owner),change.position(),owner,change.key(),change.kind(),change.before(),change.after())));
        }
        var sources=inputs.stream().map(CaptureJournal.Verified::source).toList();
        String digest=hash(out->{
            text(out,"ORK_CAPTURED_HISTORICAL_HEAP_BATCH_V1");text(out,snapshot.sha256());out.writeLong(leaf);out.writeInt(sources.size());
            for(var s:sources)text(out,s.journalSha256());
            out.writeInt(events.size());for(var e:events){text(out,e.schema().sha256());text(out,e.row().eventId());text(out,e.row().payloadSha256());}
        });
        return new CapturedHistoricalHeapBatch(snapshot,sources,events,digest,leaf);
    }
}
