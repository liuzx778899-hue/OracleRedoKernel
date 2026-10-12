package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete raw LOB origins and an independently acquired AS OF anchor. Never publishes the context prefix. */
final class CapturedLobTrustedBatch {
    private record Generation(List<CapturedHistoricalHeapBatch.Event> events,java.util.Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows) {}
    record Prepared(List<CapturedHistoricalHeapBatch.Event> events,java.util.Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows) {
        Prepared {events=List.copyOf(events);rows=java.util.Map.copyOf(rows);}
    }
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
        return prepare(RedoBatch.captures(captures),snapshot,leaf,history).events();
    }
    static Prepared prepare(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,long leaf,ArchivedDictionaryHistory.Timeline history)throws IOException {
        if(inputs.isEmpty() || snapshot.lobs().isEmpty() || snapshot.dictionaryBaseline()==null || leaf<=0)
            throw new RedoException("LOB_TRUSTED_INPUT_REQUIRED");
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
                for(var lob:snapshot.lobs())for(var role:List.of(lob.lob(),lob.index()))
                    if(u32(vector.fields().get(3),4)==role.logicalObject() || HistoricalSchemaBarrier.affectsObject(vector,role.logicalObject()))
                        if(history.commits().stream().noneMatch(c->c.identity().beginRecordScn()<=record.scn() && c.identity().endRecordScn()>=record.scn()
                                && (ArchivedDictionaryHistory.isMigration(c.generations(),snapshot) || c.generations().stream().anyMatch(g->g.logicalObject()==lob.lob().logicalObject()))))
                            throw new RedoException("LOB_HISTORICAL_GENERATION_PROOF_MISSING");
            }
        var events=new ArrayList<CapturedHistoricalHeapBatch.Event>();
        var changes=history==null?List.<ArchivedDictionaryHistory.Commit>of():history.commits().stream().filter(c->!c.generations().isEmpty()).toList();
        var heap=snapshot.heap();var index=snapshot.index();var bindings=snapshot.lobs();var columns=snapshot.columns();ArchivedDictionaryHistory.Commit start=null;
        java.util.Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> priorRows=java.util.Map.of();
        for(int i=0;i<=changes.size();i++) {
            var next=i<changes.size()?changes.get(i):null;
            var generation=readGeneration(inputs,snapshot,leaf,history,records,heap,index,bindings,columns,start,next,priorRows);
            events.addAll(generation.events());priorRows=generation.rows();
            if(next!=null) {
                start=next;long scn=next.identity().endRecordScn();heap=history.heapAt(scn,next.commitPosition());index=history.indexAt(scn,next.commitPosition());
                bindings=history.lobsAt(scn,next.commitPosition());columns=history.schemaAt(scn,next.commitPosition());
            }
        }
        events.sort(java.util.Comparator.comparing(e->e.row().transaction().endRecordScn()));
        if(events.stream().map(e->e.row().eventId()).distinct().count()!=events.size())throw new RedoException("LOB_TRUSTED_DUPLICATE_EVENT");
        return new Prepared(events,priorRows);
    }
    private static Generation readGeneration(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,long leaf,
            ArchivedDictionaryHistory.Timeline history,List<RedoRecords.Record> records,FrozenHeapBaseline.ObjectRole heap,FrozenHeapBaseline.ObjectRole indexRole,
            List<FrozenHeapBaseline.LobColumn> bindings,List<FrozenHeapBaseline.PhysicalColumn> initialColumns,
            ArchivedDictionaryHistory.Commit start,ArchivedDictionaryHistory.Commit end,java.util.Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> priorRows)throws IOException {
        var source=snapshot.source();var storage=bindings.get(0).storage();var lobs=new ArrayList<CapturedLobTransactions.Lob>();
        var logical=new HashMap<Long,Long>();logical.put(heap.dataObject(),heap.logicalObject());logical.put(indexRole.dataObject(),indexRole.logicalObject());
        for(var l:bindings) {
            logical.put(l.lob().dataObject(),l.lob().logicalObject());logical.put(l.index().dataObject(),l.index().logicalObject());
            int column=l.column()-1;
            if(l.storage()!=storage || column<0 || column>=initialColumns.size()
                    || !List.of("BLOB","CLOB").contains(initialColumns.get(column).type()))throw new RedoException("LOB_TRUSTED_INPUT_REQUIRED");
            lobs.add(new CapturedLobTransactions.Lob(column,LobLocator.Kind.valueOf(initialColumns.get(column).type()),l.lob().dataObject(),
                    l.index().dataObject(),storage==LobLocator.Storage.BASICFILE?parent(records,source,l.lob().dataObject()):0));
        }
        boolean index=false;var leaves=new HashSet<Long>();
        for(var r:records)for(var v:r.vectors()) {
            if(v.containerId()!=source.containerId())continue;
            if(v.opcodeMajor()==10 && v.objectId()==indexRole.dataObject()) {
                if(heap.equals(snapshot.heap()) && v.dba()!=leaf)throw new RedoException("LOB_PRIMARY_INDEX_LEAF_CHANGED");leaves.add(v.dba());index=true;
            }
        }
        if(!index || leaves.size()!=1)throw new RedoException("LOB_PRIMARY_INDEX_PROOF_MISSING");
        var scope=new CapturedLobTransactions.Scope(source.containerUid(),source.containerId(),heap.dataObject(),indexRole.dataObject(),storage,
                initialColumns,lobs,logical);
        var raw=CapturedLobBatch.read(inputs,scope,history,snapshot);
        for(var t:raw.transactions())if(t.identity().beginRecordScn()<=snapshot.scn() && t.identity().endRecordScn()>snapshot.scn())
            throw new RedoException("LOB_BASELINE_CROSSES_TRANSACTION");
        var anchor=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        for(var e:raw.events())if(e.transaction().endRecordScn()<=snapshot.scn()) {
            if(!anchor.getOrDefault(e.rowKey(),List.of()).equals(e.before()))throw new RedoException("LOB_CONTEXT_ROW_ORDER_MISMATCH");
            if(e.after().isEmpty())anchor.remove(e.rowKey());else anchor.put(e.rowKey(),e.after());
        }
        var wanted=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        snapshot.rows().forEach((key,value)->wanted.put(key,value.stream().map(cell->new ArchivedHeapRows.Cell(cell.state(),cell.hex().toLowerCase(java.util.Locale.ROOT))).toList()));
        if(start==null && !anchor.equals(wanted))throw new RedoException("LOB_ACQUIRED_ANCHOR_AND_RAW_CONTEXT_MISMATCH");
        boolean migration=start!=null && ArchivedDictionaryHistory.isMigration(start.generations(),snapshot);
        var copied=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        var finalRows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        for(var event:raw.events()) {
            var transaction=raw.transactions().stream().filter(t->t.identity().equals(event.transaction())).findFirst().orElseThrow();
            if(end!=null && CapturedLobTransactions.compare(transaction.commit(),end.commitPosition())>=0)throw new RedoException("LOB_DML_OUTSIDE_PROVEN_GENERATION");
            if(event.after().isEmpty())finalRows.remove(event.rowKey());else finalRows.put(event.rowKey(),event.after());
            if(migration && CapturedLobTransactions.compare(transaction.commit(),start.commitPosition())<0) {
                if(event.after().isEmpty())copied.remove(event.rowKey());else copied.put(event.rowKey(),event.after());
            }
        }
        if(migration && !businessRows(copied).equals(businessRows(priorRows)))throw new RedoException("LOB_MIGRATION_COPY_VALUE_MISMATCH");
        var events=new ArrayList<CapturedHistoricalHeapBatch.Event>();var ids=new HashSet<String>();
        for(var e:raw.events())if(e.transaction().endRecordScn()>snapshot.scn()) {
            var transaction=raw.transactions().stream().filter(t->t.identity().equals(e.transaction())).findFirst().orElseThrow();
            if(migration && CapturedLobTransactions.compare(transaction.commit(),start.commitPosition())<0)continue;
            if(start!=null && CapturedLobTransactions.compare(transaction.commit(),start.commitPosition())<=0
                    || end!=null && CapturedLobTransactions.compare(transaction.commit(),end.commitPosition())>=0)
                throw new RedoException("LOB_DML_OUTSIDE_PROVEN_GENERATION");
            var columns=history==null?snapshot.columns():history.schemaAt(e.transaction().endRecordScn(),transaction.commit());
            if(history!=null && (!heap.equals(history.heapAt(e.transaction().endRecordScn(),transaction.commit()))
                    || !indexRole.equals(history.indexAt(e.transaction().endRecordScn(),transaction.commit()))))throw new RedoException("LOB_EVENT_GENERATION_CONFLICT");
            var schema=schema(snapshot,heap,indexRole,columns,bindings);String digest=schema.sha256();
            var change=new ArchivedHeapRows.Change(e.pairedUndoPosition(),e.transaction().xid(),e.rowKey(),e.operation(),e.before(),e.after());
            String id=CapturedHeapBatch.eventId(source,digest,change);if(!ids.add(id))throw new RedoException("LOB_TRUSTED_DUPLICATE_EVENT");
            events.add(new CapturedHistoricalHeapBatch.Event(schema,new CapturedHeapBatch.Event(id,CapturedHeapBatch.payloadDigest(change,e.transaction()),e.pairedUndoPosition(),e.transaction(),e.rowKey(),e.operation(),e.before(),e.after())));
        }
        if(end!=null && !ArchivedDictionaryHistory.isMigration(end.generations(),snapshot)) {
            // A proven TRUNCATE clears the complete parsed generation. Its SYS original is the cause, not a heap DELETE record.
            var rows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
            for(var event:raw.events()) {
                if(event.after().isEmpty())rows.remove(event.rowKey());else rows.put(event.rowKey(),event.after());
            }
            var causes=new ArrayList<ArchivedUndoInventory.Position>();
            for(var original:end.original())if(original.identity().objectId()==18)for(var record:records)
                if(record.scn()>=end.identity().beginRecordScn() && record.scn()<=end.identity().endRecordScn()
                        && record.block()==original.position().block() && record.offset()==original.position().offset())for(var vector:record.vectors())
                    if(vector.containerId()==source.containerId() && vector.opcodeMajor()==11 && vector.opcodeMinor()==5) {
                        var pair=DictionaryObjectUndoPair.bind(record,vector,new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37).physical();
                        if(pair.undo().identity().equals(original.identity()) && OracleScalars.number(pair.forward().columns().get(0).bytes()).longValueExact()==heap.logicalObject())
                            causes.add(original.position());
                    }
            if(causes.size()!=1)throw new RedoException("LOB_TRUNCATE_HEAP_CAUSE_COVERAGE");
            var at=causes.get(0);var columns=history.schemaAt(end.identity().endRecordScn(),end.commitPosition());
            var schema=schema(snapshot,heap,indexRole,columns,bindings);
            for(var key:rows.keySet().stream().sorted(java.util.Comparator.comparingLong(ArchivedHeapRows.RowKey::dba).thenComparingInt(ArchivedHeapRows.RowKey::slot)).toList()) {
                var before=rows.get(key);if(before.size()!=columns.size())throw new RedoException("LOB_TRUNCATE_ROW_SCHEMA_MISMATCH");
                for(int i=0;i<before.size();i++) {
                    var cell=before.get(i);var column=columns.get(i);
                    if(!List.of(HeapRowPatch.State.VALUE,HeapRowPatch.State.NULL).contains(cell.state()))throw new RedoException("LOB_TRUNCATE_INCOMPLETE_VALUE");
                    if(column.type().equals("CLOB") && cell.state()==HeapRowPatch.State.VALUE)OracleScalars.varchar2(java.util.HexFormat.of().parseHex(cell.hex()),"AL32UTF8");
                    if(!List.of("BLOB","CLOB").contains(column.type()))ArchivedHeapRows.requireCompleteValues(List.of(cell),List.of(column));
                }
                var change=new ArchivedHeapRows.Change(at,end.identity().xid(),key,HeapRowPatch.Kind.DELETE,before,List.of());
                String id=CapturedHeapBatch.eventId(source,schema.sha256(),change);if(!ids.add(id))throw new RedoException("LOB_TRUSTED_DUPLICATE_EVENT");
                events.add(new CapturedHistoricalHeapBatch.Event(schema,new CapturedHeapBatch.Event(id,CapturedHeapBatch.payloadDigest(change,end.identity()),
                        at,end.identity(),key,HeapRowPatch.Kind.DELETE,before,List.of())));
            }
        }
        return new Generation(List.copyOf(events),java.util.Map.copyOf(finalRows));
    }
    private static java.util.Map<String,List<ArchivedHeapRows.Cell>> businessRows(java.util.Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows)throws RedoException {
        var result=new HashMap<String,List<ArchivedHeapRows.Cell>>();
        for(var row:rows.values()) {
            if(row.isEmpty() || row.get(0).state()!=HeapRowPatch.State.VALUE)throw new RedoException("LOB_MIGRATION_PRIMARY_VALUE_MISSING");
            String key=OracleScalars.number(java.util.HexFormat.of().parseHex(row.get(0).hex())).toPlainString();
            if(result.putIfAbsent(key,row)!=null)throw new RedoException("LOB_MIGRATION_DUPLICATE_PRIMARY_VALUE");
        }
        return result;
    }
    private static CapturedHeapBatch.Schema schema(FrozenHeapBaseline.Snapshot snapshot,FrozenHeapBaseline.ObjectRole heap,FrozenHeapBaseline.ObjectRole index,
            List<FrozenHeapBaseline.PhysicalColumn> columns,List<FrozenHeapBaseline.LobColumn> bindings) {
        String fixed=CapturedHeapBatch.schemaDigest(heap,index,columns,snapshot.timeZoneContext());
        String digest=CapturedHeapBatch.hash(out->{CapturedHeapBatch.text(out,fixed);for(var lob:bindings)CapturedHeapBatch.text(out,lob.toString());});
        return new CapturedHeapBatch.Schema(heap,index,columns,digest,snapshot.timeZoneContext());
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
