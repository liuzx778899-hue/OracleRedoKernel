package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Static LOB schema, complete raw origins and an independently acquired AS OF anchor. Never publishes the context prefix. */
final class CapturedLobTrustedBatch {
    static CapturedHeapBatch read(List<CaptureJournal.Verified> captures,FrozenHeapBaseline.Snapshot snapshot,long leaf)throws IOException {
        if(captures.isEmpty() || snapshot.lobs().size()!=2 || snapshot.dictionaryBaseline()==null || leaf<=0)
            throw new RedoException("LOB_TRUSTED_INPUT_REQUIRED");
        var inputs=RedoBatch.captures(captures);HistoricalSchemaBarrier.requireUnchanged(inputs,snapshot);
        var records=new ArrayList<RedoRecords.Record>();boolean covered=false;var continuity=new TransactionLifecycle();
        for(var input:inputs) {
            var h=input.header();snapshot.requireSource(h);continuity.archive(h);
            if(h.firstScn()<=snapshot.scn() && snapshot.scn()<h.nextScn())covered=true;
            input.visit(h,r->{if(r.containerUid()==snapshot.source().containerUid())records.add(r);});
        }
        continuity.finish();if(!covered)throw new RedoException("LOB_BASELINE_OUTSIDE_INPUT_COVERAGE");
        var b=snapshot.lobs().get(0);var c=snapshot.lobs().get(1);var source=snapshot.source();
        long bitmapB=b.storage()==LobLocator.Storage.BASICFILE?parent(records,source,b.lob().dataObject()):0;
        long bitmapC=c.storage()==LobLocator.Storage.BASICFILE?parent(records,source,c.lob().dataObject()):0;
        boolean index=false;
        var dictionaries=snapshot.dictionaryAnchors().stream().map(a->a.key().dataObjectId()).collect(java.util.stream.Collectors.toSet());
        for(var r:records)for(var v:r.vectors()) {
            if(v.containerId()!=source.containerId())continue;
            if(v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject()) {
                if(v.dba()!=leaf)throw new RedoException("LOB_PRIMARY_INDEX_LEAF_CHANGED");index=true;
            }
            if(r.scn()>snapshot.scn() && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=24
                    && dictionaries.contains(u32(v.fields().get(1),4)))throw new RedoException("LOB_STATIC_DICTIONARY_WRITE_REQUIRES_HISTORY");
        }
        if(!index)throw new RedoException("LOB_PRIMARY_INDEX_PROOF_MISSING");
        var scope=new CapturedLobTransactions.Scope(source.containerUid(),source.containerId(),snapshot.heap().dataObject(),snapshot.index().dataObject(),b.storage(),
                b.lob().dataObject(),b.index().dataObject(),bitmapB,c.lob().dataObject(),c.index().dataObject(),bitmapC);
        var raw=CapturedLobBatch.read(captures,scope);
        for(var t:raw.transactions())if(t.identity().beginRecordScn()<=snapshot.scn() && t.identity().endRecordScn()>snapshot.scn())
            throw new RedoException("LOB_BASELINE_CROSSES_TRANSACTION");
        var anchor=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        for(var e:raw.events())if(e.transaction().endRecordScn()<=snapshot.scn()) {
            if(!anchor.getOrDefault(e.rowKey(),List.of()).equals(e.before()))throw new RedoException("LOB_CONTEXT_ROW_ORDER_MISMATCH");
            if(e.after().isEmpty())anchor.remove(e.rowKey());else anchor.put(e.rowKey(),e.after());
        }
        var wanted=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        snapshot.rows().forEach((key,value)->wanted.put(key,value.stream().map(cell->new ArchivedHeapRows.Cell(cell.state(),cell.hex().toLowerCase(java.util.Locale.ROOT))).toList()));
        if(!anchor.equals(wanted))throw new RedoException("LOB_ACQUIRED_ANCHOR_AND_RAW_CONTEXT_MISMATCH");
        String schema=CapturedHeapBatch.schemaDigest(snapshot);var events=new ArrayList<CapturedHeapBatch.Event>();var ids=new HashSet<String>();
        for(var e:raw.events())if(e.transaction().endRecordScn()>snapshot.scn()) {
            var change=new ArchivedHeapRows.Change(e.pairedUndoPosition(),e.transaction().xid(),e.rowKey(),e.operation(),e.before(),e.after());
            String id=CapturedHeapBatch.eventId(source,schema,change);if(!ids.add(id))throw new RedoException("LOB_TRUSTED_DUPLICATE_EVENT");
            events.add(new CapturedHeapBatch.Event(id,CapturedHeapBatch.payloadDigest(change,e.transaction()),e.pairedUndoPosition(),e.transaction(),e.rowKey(),e.operation(),e.before(),e.after()));
        }
        return CapturedHeapBatch.lob(snapshot,captures.stream().map(CaptureJournal.Verified::source).toList(),events,leaf);
    }
    private static long parent(List<RedoRecords.Record> records,ArchivedHeapRows.Source source,long object)throws RedoException {
        var parents=new HashSet<Long>();
        // Page headers point to L1 maps; the owned 13.24 parent metadata names the L2 scope used by bitmap pairs.
        for(var r:records)for(var v:r.vectors())if(v.containerId()==source.containerId() && v.objectId()==object && v.opcodeMajor()==13 && v.opcodeMinor()==24)
            parents.add(v.dba());
        if(parents.size()!=1)throw new RedoException("LOB_OWNED_BITMAP_PARENT_NOT_UNIQUE");return parents.iterator().next();
    }
}
