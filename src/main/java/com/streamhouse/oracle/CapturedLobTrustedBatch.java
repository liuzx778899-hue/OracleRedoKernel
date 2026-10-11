package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete raw LOB origins and an independently acquired AS OF anchor. Never publishes the context prefix. */
final class CapturedLobTrustedBatch {
    static CapturedHeapBatch read(List<CaptureJournal.Verified> captures,FrozenHeapBaseline.Snapshot snapshot,long leaf)throws IOException {
        return CapturedHeapBatch.lob(snapshot,captures.stream().map(CaptureJournal.Verified::source).toList(),
                readEvents(captures,snapshot,leaf,null).stream().map(CapturedHistoricalHeapBatch.Event::row).toList(),leaf);
    }
    static List<CapturedHistoricalHeapBatch.Event> readHistorical(List<CaptureJournal.Verified> captures,FrozenHeapBaseline.Snapshot snapshot,long leaf,
                                                               ArchivedDictionaryHistory.Timeline history)throws IOException {
        return readEvents(captures,snapshot,leaf,history);
    }
    private static List<CapturedHistoricalHeapBatch.Event> readEvents(List<CaptureJournal.Verified> captures,FrozenHeapBaseline.Snapshot snapshot,long leaf,
                                                                    ArchivedDictionaryHistory.Timeline history)throws IOException {
        if(captures.isEmpty() || snapshot.lobs().isEmpty() || snapshot.dictionaryBaseline()==null || leaf<=0)
            throw new RedoException("LOB_TRUSTED_INPUT_REQUIRED");
        var inputs=RedoBatch.captures(captures);
        if(history==null)HistoricalSchemaBarrier.requireUnchanged(inputs,snapshot);else history.requireBoundTo(inputs,snapshot);
        var records=new ArrayList<RedoRecords.Record>();boolean covered=false;var continuity=new TransactionLifecycle();
        for(var input:inputs) {
            var h=input.header();snapshot.requireSource(h);continuity.archive(h);
            if(h.firstScn()<=snapshot.scn() && snapshot.scn()<h.nextScn())covered=true;
            input.visit(h,r->{if(r.containerUid()==snapshot.source().containerUid())records.add(r);});
        }
        continuity.finish();if(!covered)throw new RedoException("LOB_BASELINE_OUTSIDE_INPUT_COVERAGE");
        if(history!=null)for(var record:records)if(record.scn()>snapshot.scn())for(var vector:record.vectors())
            if(vector.containerId()==snapshot.source().containerId() && vector.opcodeMajor()==24 && vector.opcodeMinor()==1) {
                HistoricalSchemaBarrier.requireMarkerProfile(record,vector);
                for(var l:snapshot.lobs())for(var role:List.of(l.lob(),l.index()))
                    if(u32(vector.fields().get(3),4)==role.logicalObject() || HistoricalSchemaBarrier.affectsObject(vector,role.logicalObject()))
                        throw new RedoException("LOB_HISTORICAL_GENERATION_CHANGE_NOT_IMPLEMENTED");
            }
        var source=snapshot.source();var storage=snapshot.lobs().get(0).storage();var lobs=new ArrayList<CapturedLobTransactions.Lob>();
        for(var l:snapshot.lobs()) {
            int column=l.column()-1;
            if(l.storage()!=storage || column<0 || column>=snapshot.columns().size()
                    || !List.of("BLOB","CLOB").contains(snapshot.columns().get(column).type()))throw new RedoException("LOB_TRUSTED_INPUT_REQUIRED");
            lobs.add(new CapturedLobTransactions.Lob(column,LobLocator.Kind.valueOf(snapshot.columns().get(column).type()),l.lob().dataObject(),
                    l.index().dataObject(),storage==LobLocator.Storage.BASICFILE?parent(records,source,l.lob().dataObject()):0));
        }
        boolean index=false;
        for(var r:records)for(var v:r.vectors()) {
            if(v.containerId()!=source.containerId())continue;
            if(v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject()) {
                if(v.dba()!=leaf)throw new RedoException("LOB_PRIMARY_INDEX_LEAF_CHANGED");index=true;
            }
        }
        if(!index)throw new RedoException("LOB_PRIMARY_INDEX_PROOF_MISSING");
        var scope=new CapturedLobTransactions.Scope(source.containerUid(),source.containerId(),snapshot.heap().dataObject(),snapshot.index().dataObject(),storage,
                snapshot.columns(),lobs);
        var raw=CapturedLobBatch.read(captures,scope,history,snapshot);
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
        var events=new ArrayList<CapturedHistoricalHeapBatch.Event>();var ids=new HashSet<String>();
        for(var e:raw.events())if(e.transaction().endRecordScn()>snapshot.scn()) {
            var transaction=raw.transactions().stream().filter(t->t.identity().equals(e.transaction())).findFirst().orElseThrow();
            var columns=history==null?snapshot.columns():history.schemaAt(e.transaction().endRecordScn(),transaction.commit());
            String digest=CapturedHeapBatch.schemaDigest(snapshot,columns);
            var schema=new CapturedHeapBatch.Schema(snapshot.heap(),snapshot.index(),columns,digest,snapshot.timeZoneContext());
            var change=new ArchivedHeapRows.Change(e.pairedUndoPosition(),e.transaction().xid(),e.rowKey(),e.operation(),e.before(),e.after());
            String id=CapturedHeapBatch.eventId(source,digest,change);if(!ids.add(id))throw new RedoException("LOB_TRUSTED_DUPLICATE_EVENT");
            events.add(new CapturedHistoricalHeapBatch.Event(schema,new CapturedHeapBatch.Event(id,CapturedHeapBatch.payloadDigest(change,e.transaction()),e.pairedUndoPosition(),e.transaction(),e.rowKey(),e.operation(),e.before(),e.after())));
        }
        return List.copyOf(events);
    }
    private static long parent(List<RedoRecords.Record> records,ArchivedHeapRows.Source source,long object)throws RedoException {
        var parents=new HashSet<Long>();
        // Page headers point to L1 maps; the owned 13.24 parent metadata names the L2 scope used by bitmap pairs.
        for(var r:records)for(var v:r.vectors())if(v.containerId()==source.containerId() && v.objectId()==object && v.opcodeMajor()==13 && v.opcodeMinor()==24)
            parents.add(v.dba());
        if(parents.size()>1)throw new RedoException("LOB_OWNED_BITMAP_PARENT_NOT_UNIQUE");
        return parents.isEmpty()?0:parents.iterator().next();
    }
}
