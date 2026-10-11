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
    private final boolean incremental;
    private CapturedHistoricalHeapBatch(FrozenHeapBaseline.Snapshot snapshot,List<CaptureJournal.Source> captures,List<Event> events,String sha256,long leaf) {
        this(snapshot,captures,events,sha256,leaf,false);
    }
    private CapturedHistoricalHeapBatch(FrozenHeapBaseline.Snapshot snapshot,List<CaptureJournal.Source> captures,List<Event> events,String sha256,long leaf,boolean incremental) {
        this.snapshot=snapshot;this.captures=List.copyOf(captures);this.events=List.copyOf(events);this.sha256=sha256;this.leaf=leaf;
        this.incremental=incremental;
    }
    public ArchivedHeapRows.Source source(){return snapshot.source();}
    public String baselineSha256(){return snapshot.sha256();}
    public long baselineScn(){return snapshot.scn();}
    public CapturedHeapBatch.Schema baselineSchema(){return new CapturedHeapBatch.Schema(snapshot.heap(),snapshot.index(),snapshot.columns(),CapturedHeapBatch.schemaDigest(snapshot),snapshot.timeZoneContext());}
    public List<CaptureJournal.Source> captures(){return captures;}
    public List<Event> events(){return events;}
    public String sha256(){return sha256;}
    public long leaf(){return leaf;}
    public record PublicationBoundary(int events,String sha256) {
        public PublicationBoundary {
            if(events<0 || sha256==null || !sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("HISTORICAL_PUBLICATION_BOUNDARY_INVALID");
        }
    }
    /** Computes a complete-transaction prefix receipt; computing it does not prove target commitment. */
    public PublicationBoundary publicationBoundary(int count)throws RedoException {
        if(incremental || count<0 || count>events.size())throw new RedoException("HISTORICAL_PUBLICATION_PREFIX_RANGE");
        var owners=new HashSet<TransactionLifecycle.ClosedIdentity>();
        for(int i=0;i<count;i++)owners.add(events.get(i).row().transaction());
        for(int i=count;i<events.size();i++)if(owners.contains(events.get(i).row().transaction()))
            throw new RedoException("HISTORICAL_PUBLICATION_TRANSACTION_SPLIT");
        String digest=hash(out->{
            text(out,"ORK_HISTORICAL_PUBLICATION_PREFIX_V1");text(out,snapshot.sha256());out.writeLong(leaf);out.writeInt(count);
            for(int i=0;i<count;i++){var event=events.get(i);text(out,event.schema().sha256());text(out,event.row().eventId());text(out,event.row().payloadSha256());}
        });
        return new PublicationBoundary(count,digest);
    }
    /** Boundary must come from the authenticated atomic target/control commit, never a client offset. */
    public CapturedHistoricalHeapBatch afterPublished(PublicationBoundary boundary)throws RedoException {
        if(boundary==null || !publicationBoundary(boundary.events()).equals(boundary))
            throw new RedoException("HISTORICAL_PUBLICATION_PREFIX_CHANGED");
        String digest=hash(out->{text(out,"ORK_INCREMENTAL_HISTORICAL_BATCH_V1");text(out,sha256);text(out,boundary.sha256());out.writeInt(boundary.events());});
        return new CapturedHistoricalHeapBatch(snapshot,captures,events.subList(boundary.events(),events.size()),digest,leaf,true);
    }
    public static CapturedHistoricalHeapBatch read(List<CaptureJournal.Verified> captures,Path snapshotPath,String trustedAcquisitionSha256,long leaf)throws IOException {
        var inputs=List.copyOf(captures);var paths=RedoBatch.captures(inputs);
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var history=ArchivedDictionaryHistory.read(paths,snapshot);
        if(!snapshot.lobs().isEmpty())return lob(snapshot,inputs.stream().map(CaptureJournal.Verified::source).toList(),
                CapturedLobTrustedBatch.readHistorical(inputs,snapshot,leaf,history),leaf);
        return read(inputs,paths,snapshot,history,leaf);
    }
    private static CapturedHistoricalHeapBatch lob(FrozenHeapBaseline.Snapshot snapshot,List<CaptureJournal.Source> captures,List<Event> events,long leaf) {
        String digest=hash(out->{text(out,"ORK_HISTORICAL_LOB_BATCH_V1");text(out,snapshot.sha256());out.writeLong(leaf);
            for(var capture:captures)text(out,capture.journalSha256());
            for(var event:events){text(out,event.schema().sha256());text(out,event.row().eventId());text(out,event.row().payloadSha256());}});
        return new CapturedHistoricalHeapBatch(snapshot,captures,events,digest,leaf);
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
        if(!snapshot.lobs().isEmpty())return lob(snapshot,inputs.stream().map(CaptureJournal.Verified::source).toList(),
                CapturedLobTrustedBatch.readHistorical(inputs,snapshot,leaf,history),leaf);
        var physical=ArchivedPhysicalTransactions.readHistory(paths,snapshot,history,leaf);
        return readWithPhysical(inputs,paths,snapshot,history,leaf,physical);
    }
    static CapturedHistoricalHeapBatch readWithPhysical(List<CaptureJournal.Verified> inputs,RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,
                                                       ArchivedDictionaryHistory.Timeline history,long leaf,ArchivedPhysicalTransactions.Result physical)throws IOException {
        var rows=ArchivedHeapRows.readHistory(paths,snapshot,leaf,history,physical);
        return readWithRows(inputs,snapshot,history,leaf,physical,rows);
    }
    static CapturedHistoricalHeapBatch readWithRows(List<CaptureJournal.Verified> inputs,FrozenHeapBaseline.Snapshot snapshot,
                                                   ArchivedDictionaryHistory.Timeline history,long leaf,ArchivedPhysicalTransactions.Result physical,
                                                   ArchivedHeapRows.Result rows)throws IOException {
        return readWithSourceRows(inputs.stream().map(CaptureJournal.Verified::source).toList(),snapshot,history,leaf,physical,rows);
    }
    static CapturedHistoricalHeapBatch readWithSourceRows(List<CaptureJournal.Source> sources,FrozenHeapBaseline.Snapshot snapshot,
                                                   ArchivedDictionaryHistory.Timeline history,long leaf,ArchivedPhysicalTransactions.Result physical,
                                                   ArchivedHeapRows.Result rows)throws IOException {
        if(!physical.sources().equals(rows.sources()) || !rows.sources().equals(history.sources()))throw new RedoException("HISTORICAL_BATCH_SOURCE_CHANGED");
        var owners=new HashMap<ArchivedUndoInventory.Position,ArchivedPhysicalTransactions.Transaction>();
        for(var t:physical.transactions())for(var live:t.graph().live())if(owners.putIfAbsent(live.position(),t)!=null)
            throw new RedoException("HISTORICAL_BATCH_POSITION_OWNER_AMBIGUOUS");
        var events=new ArrayList<Event>();var ids=new HashSet<String>();
        for(var change:rows.changes()) {
            var transaction=owners.get(change.position());var owner=transaction==null?null:transaction.identity();
            if(owner==null || owner.rolledBack() || !owner.xid().equals(change.xid()) || owner.endRecordScn()<=snapshot.scn())
                throw new RedoException("HISTORICAL_BATCH_ROW_OWNER_MISMATCH");
            var columns=history.schemaAt(owner.endRecordScn(),transaction.commitPosition());
            var schema=new Schema(snapshot.heap(),snapshot.index(),columns,schemaDigest(snapshot.heap(),snapshot.index(),columns,snapshot.timeZoneContext()),snapshot.timeZoneContext());
            String id=eventId(snapshot.source(),schema.sha256(),change);if(!ids.add(id))throw new RedoException("HISTORICAL_BATCH_DUPLICATE_EVENT_ID");
            events.add(new Event(schema,new CapturedHeapBatch.Event(id,payloadDigest(change,owner),change.position(),owner,change.key(),change.kind(),change.before(),change.after())));
        }
        String digest=hash(out->{
            text(out,"ORK_CAPTURED_HISTORICAL_HEAP_BATCH_V1");text(out,snapshot.sha256());out.writeLong(leaf);out.writeInt(sources.size());
            for(var s:sources)text(out,s.journalSha256());
            out.writeInt(events.size());for(var e:events){text(out,e.schema().sha256());text(out,e.row().eventId());text(out,e.row().payloadSha256());}
        });
        return new CapturedHistoricalHeapBatch(snapshot,sources,events,digest,leaf);
    }
}
