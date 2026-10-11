package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Complete commit-ordered LOB row events from raw INSERT origins; declared scope is not trusted acquisition/publication. */
public final class CapturedLobBatch {
    public record Maintenance(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Position> originals,long object,long extent,long blocks) {
        public Maintenance {originals=List.copyOf(originals);}
    }
    public record Result(List<CapturedHeapBatch.Event> events,List<CapturedLobTransactions.Transaction> transactions,List<Maintenance> maintenance,String sha256) {
        public Result {events=List.copyOf(events);transactions=List.copyOf(transactions);maintenance=List.copyOf(maintenance);}
        public int maintenanceTransactions() {return maintenance.size();}
        public int maintenanceOriginals() {return maintenance.stream().mapToInt(r->r.originals().size()).sum();}
    }
    private record Locator(ArchivedUndoInventory.Position position,UndoHeader owner,int columns) {}
    private record Pending(ArchivedUndoInventory.Position original,List<ArchivedHeapRows.Cell> before) {}
    public static Result read(List<CaptureJournal.Verified> captures,CapturedLobTransactions.Scope scope)throws IOException {
        return read(captures,scope,null,null);
    }
    static Result read(List<CaptureJournal.Verified> captures,CapturedLobTransactions.Scope scope,ArchivedDictionaryHistory.Timeline history,
                       FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        var graph=CapturedLobTransactions.read(captures,scope,history,snapshot);
        var maintenance=CapturedLobMaintenance.read(graph,history);
        var columns=scope.columns();
        var source=ArchivedHeapRows.Source.of(graph.records().get(0).source(),scope.uid(),scope.container());
        String schema=CapturedHeapBatch.hash(out->{CapturedHeapBatch.text(out,"ORK_DECLARED_LOB_SCHEMA_V1");CapturedHeapBatch.text(out,scope.toString());});
        var basic=new HashMap<Integer,CapturedBasicFileLobValues>();
        if(scope.storage()==LobLocator.Storage.BASICFILE)for(var l:scope.lobs())basic.put(l.column(),CapturedBasicFileLobValues.read(captures,
                new CapturedBasicFileLobValues.Scope(scope.uid(),scope.container(),scope.heap(),l.object(),l.index(),l.bitmap(),l.column(),columns.size(),l.kind())));
        var rawRows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        var logicalRows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        var locators=new HashMap<ArchivedHeapRows.RowKey,Locator[]>();var events=new ArrayList<CapturedHeapBatch.Event>();var ids=new java.util.HashSet<String>();
        long previousCommit=0;
        for(var transaction:graph.transactions()) {
            if(transaction.identity().rolledBack())continue;
            if(history!=null) {
                var next=transaction.identity().endRecordScn()<=snapshot.scn()?scope.columns()
                        :history.schemaAt(transaction.identity().endRecordScn(),transaction.commit());
                if(!next.equals(columns)) {
                    for(var l:scope.lobs())if(l.column()>=next.size() || !columns.get(l.column()).equals(next.get(l.column())))
                        throw new RedoException("LOB_HISTORICAL_STORAGE_COLUMN_CHANGED");
                    if(next.size()<columns.size())requireDroppedRows(rawRows,columns,next,graph,history,snapshot,previousCommit,transaction);
                    remap(rawRows,columns,next,history,transaction);remap(logicalRows,columns,next,history,transaction);
                    for(var key:locators.keySet())locators.put(key,java.util.Arrays.copyOf(locators.get(key),next.size()));
                    columns=next;
                }
            }
            previousCommit=transaction.identity().endRecordScn();
            var pending=new LinkedHashMap<ArchivedHeapRows.RowKey,Pending>();
            for(var row:transaction.rows()) {
                var pair=row.pair();var owner=pair.undo().identity();
                if(transaction.graph().live().stream().noneMatch(e->e.identity().equals(owner)))continue;
                if(pair.forward().columns().size()!=columns.size())throw new RedoException("LOB_HISTORICAL_ROW_RECORD_SCHEMA_TRANSITION");
                var p=pair.forward();var key=new ArchivedHeapRows.RowKey(scope.heap(),p.dba(),p.slot());
                var current=rawRows.get(key);
                if(p.kind()==Kind.LOCK) {
                    if(current==null || p.lockTo()==null || (p.lockTo()!=1 && p.lockTo()!=2) || !Integer.valueOf(0).equals(pair.undo().inverse().lockTo())
                            || p.columns().stream().anyMatch(c->c.state()!=State.ABSENT) || pair.undo().inverse().columns().stream().anyMatch(c->c.state()!=State.ABSENT))
                        throw new RedoException("LOB_BATCH_UNVERIFIED_LOCK");
                    continue;
                }
                var original=transaction.graph().live().stream().filter(e->e.identity().equals(owner)).findFirst().orElseThrow().position();
                pending.putIfAbsent(key,new Pending(original,logicalRows.getOrDefault(key,List.of())));
                var after=ArchivedHeapRows.apply(pair,current,columns.size(),columns);
                if(after.isEmpty()) {rawRows.remove(key);locators.remove(key);continue;}
                for(int c=0;c<columns.size();c++)if(!columns.get(c).type().equals("BLOB") && !columns.get(c).type().equals("CLOB"))
                    ArchivedHeapRows.requireCompleteValues(List.of(after.get(c)),List.of(columns.get(c)));
                rawRows.put(key,after);var locations=locators.get(key);
                if(locations==null){locations=new Locator[columns.size()];locators.put(key,locations);}
                for(var l:scope.lobs()) {int c=l.column();if(p.columns().get(c).state()!=State.ABSENT || (p.extent()!=null && p.extent().removedColumn()==c))
                    locations[c]=new Locator(row.position(),owner,columns.size());}
            }
            for(var entry:pending.entrySet()) {
                var key=entry.getKey();var p=entry.getValue();var raw=rawRows.get(key);var logical=new ArrayList<ArchivedHeapRows.Cell>();
                if(raw!=null) {
                    logical.addAll(raw);var locations=locators.get(key);
                    for(var l:scope.lobs()) {int c=l.column();
                        var cell=raw.get(c);if(cell.state()==State.NULL)continue;
                        var at=locations[c];if(at==null)throw new RedoException("LOB_BATCH_LOCATOR_ORIGIN_MISSING");
                        byte[] bytes;
                        if(scope.storage()==LobLocator.Storage.BASICFILE) {
                            var value=basic.get(c).valueAt(at.position(),at.columns());
                            for(var page:value.pages())if(graph.transaction(page.owner().xid()).graph().live().stream().noneMatch(e->e.identity().equals(page.owner())))
                                throw new RedoException("LOB_BATCH_PAGE_ALLOCATION_NOT_LIVE");
                            bytes=value.bytes();
                        } else bytes=CapturedSecureFileLobValues.value(graph,c,HexFormat.of().parseHex(cell.hex()),transaction.commit(),at.owner());
                        if(bytes==null)throw new RedoException("LOB_BATCH_VALUE_STATE_MISMATCH");
                        logical.set(c,new ArchivedHeapRows.Cell(State.VALUE,HexFormat.of().formatHex(bytes)));
                    }
                }
                var before=p.before();var after=List.copyOf(logical);
                if(before.isEmpty() && after.isEmpty())continue;
                Kind kind=before.isEmpty()?Kind.INSERT:after.isEmpty()?Kind.DELETE:Kind.UPDATE;
                var change=new ArchivedHeapRows.Change(p.original(),transaction.identity().xid(),key,kind,before,after);
                String id=CapturedHeapBatch.eventId(source,schema,change);if(!ids.add(id))throw new RedoException("LOB_BATCH_DUPLICATE_EVENT_ID");
                events.add(new CapturedHeapBatch.Event(id,CapturedHeapBatch.payloadDigest(change,transaction.identity()),
                        p.original(),transaction.identity(),key,kind,before,after));
                if(after.isEmpty())logicalRows.remove(key);else logicalRows.put(key,after);
            }
        }
        String sha=CapturedHeapBatch.hash(out->{CapturedHeapBatch.text(out,"ORK_CAPTURED_LOB_BATCH_V1");CapturedHeapBatch.text(out,schema);
            for(var capture:captures)CapturedHeapBatch.text(out,capture.source().journalSha256());
            out.writeInt(events.size());for(var event:events) {CapturedHeapBatch.text(out,event.eventId());CapturedHeapBatch.text(out,event.payloadSha256());}});
        return new Result(events,graph.transactions(),maintenance.stream().map(r->new Maintenance(r.identity(),r.originals(),r.object(),r.extent(),r.blocks())).toList(),sha);
    }
    private static void requireDroppedRows(HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,
                                          List<FrozenHeapBaseline.PhysicalColumn> previous,List<FrozenHeapBaseline.PhysicalColumn> next,
                                          CapturedLobTransactions graph,ArchivedDictionaryHistory.Timeline history,FrozenHeapBaseline.Snapshot snapshot,
                                          long priorScn,CapturedLobTransactions.Transaction transaction)throws RedoException {
        if(previous.size()!=next.size()+1 || !next.equals(previous.subList(0,next.size())))
            throw new RedoException("LOB_DROP_UNSUPPORTED_SCHEMA_TRANSITION");
        var removed=new java.util.HashSet<ArchivedHeapRows.RowKey>();
        for(var commit:history.commits())if(commit.identity().endRecordScn()>priorScn
                && commit.identity().endRecordScn()<=transaction.identity().endRecordScn())for(var entry:commit.original())
            if(entry.identity().objectId()==snapshot.heap().logicalObject()) {
                CapturedLobTransactions.At at=null;
                for(var record:graph.records())if(record.position(entry.position().vector()-1).equals(entry.position())){at=record;break;}
                if(at==null)throw new RedoException("LOB_DROP_ORIGINAL_MISSING");
                var drop=LobDroppedColumn.bind(at.record(),at.record().vectors().get(entry.position().vector()-1),snapshot);
                var key=new ArchivedHeapRows.RowKey(snapshot.heap().dataObject(),drop.dba(),drop.slot());var row=rows.get(key);
                if(drop.column()!=next.size() || !removed.add(key) || row==null || row.size()!=previous.size()
                        || !row.get(drop.column()).equals(new ArchivedHeapRows.Cell(State.VALUE,HexFormat.of().formatHex(drop.before()))))
                    throw new RedoException("LOB_DROP_ROW_BEFORE_OR_COLUMN_CONFLICT");
            }
        for(var row:rows.entrySet())if(row.getValue().get(next.size()).state()!=State.NULL && !removed.contains(row.getKey()))
            throw new RedoException("LOB_DROP_STORED_ROW_COVERAGE");
    }
    private static void remap(HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,
                              List<FrozenHeapBaseline.PhysicalColumn> previous,List<FrozenHeapBaseline.PhysicalColumn> next,
                              ArchivedDictionaryHistory.Timeline history,CapturedLobTransactions.Transaction transaction)throws RedoException {
        var positions=new ArrayList<Integer>();
        for(var column:next) {
            int at=previous.indexOf(column);
            if(at<0) {
                if(previous.stream().anyMatch(c->c.internal()==column.internal() || c.name().equals(column.name())))
                    throw new RedoException("LOB_HISTORICAL_COLUMN_IDENTITY_CHANGED");
                history.requireAddedNullDefault(column,transaction.identity().endRecordScn(),transaction.commit());
            }
            positions.add(at);
        }
        for(var key:rows.keySet()) {
            var prior=rows.get(key);if(prior.size()!=previous.size())throw new RedoException("LOB_HISTORICAL_ROW_WIDTH");
            var value=new ArrayList<ArchivedHeapRows.Cell>();
            for(int at:positions)value.add(at<0?new ArchivedHeapRows.Cell(State.NULL,""):prior.get(at));
            rows.put(key,List.copyOf(value));
        }
    }
}
