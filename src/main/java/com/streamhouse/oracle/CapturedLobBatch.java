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
    private record Locator(ArchivedUndoInventory.Position position,UndoHeader owner) {}
    private record Pending(ArchivedUndoInventory.Position original,List<ArchivedHeapRows.Cell> before) {}
    public static Result read(List<CaptureJournal.Verified> captures,CapturedLobTransactions.Scope scope)throws IOException {
        var graph=CapturedLobTransactions.read(captures,scope);
        var maintenance=CapturedLobMaintenance.read(graph);
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"B","BLOB",true,false),new FrozenHeapBaseline.PhysicalColumn(3,3,"C","CLOB",true,false));
        var source=ArchivedHeapRows.Source.of(graph.records().get(0).source(),scope.uid(),scope.container());
        String schema=CapturedHeapBatch.hash(out->{CapturedHeapBatch.text(out,"ORK_DECLARED_LOB_SCHEMA_V1");CapturedHeapBatch.text(out,scope.toString());});
        var basic=new ArrayList<CapturedBasicFileLobValues>();
        if(scope.storage()==LobLocator.Storage.BASICFILE)for(int c=1;c<3;c++)basic.add(CapturedBasicFileLobValues.read(captures,
                new CapturedBasicFileLobValues.Scope(scope.uid(),scope.container(),scope.heap(),scope.object(c),scope.index(c),scope.bitmap(c),c)));
        var rawRows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        var logicalRows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        var locators=new HashMap<ArchivedHeapRows.RowKey,Locator[]>();var events=new ArrayList<CapturedHeapBatch.Event>();var ids=new java.util.HashSet<String>();
        for(var transaction:graph.transactions()) {
            if(transaction.identity().rolledBack())continue;
            var pending=new LinkedHashMap<ArchivedHeapRows.RowKey,Pending>();
            for(var row:transaction.rows()) {
                var pair=row.pair();var owner=pair.undo().identity();
                if(transaction.graph().live().stream().noneMatch(e->e.identity().equals(owner)))continue;
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
                var after=ArchivedHeapRows.apply(pair,current,3,columns);
                if(after.isEmpty()) {rawRows.remove(key);locators.remove(key);continue;}
                OracleScalars.number(HexFormat.of().parseHex(after.get(0).hex()));
                rawRows.put(key,after);var locations=locators.computeIfAbsent(key,k->new Locator[2]);
                for(int c=1;c<3;c++)if(p.columns().get(c).state()!=State.ABSENT || (p.extent()!=null && p.extent().removedColumn()==c))
                    locations[c-1]=new Locator(row.position(),owner);
            }
            for(var entry:pending.entrySet()) {
                var key=entry.getKey();var p=entry.getValue();var raw=rawRows.get(key);var logical=new ArrayList<ArchivedHeapRows.Cell>();
                if(raw!=null) {
                    logical.add(raw.get(0));var locations=locators.get(key);
                    for(int c=1;c<3;c++) {
                        var cell=raw.get(c);if(cell.state()==State.NULL) {logical.add(cell);continue;}
                        var at=locations[c-1];if(at==null)throw new RedoException("LOB_BATCH_LOCATOR_ORIGIN_MISSING");
                        byte[] bytes;
                        if(scope.storage()==LobLocator.Storage.BASICFILE) {
                            var value=basic.get(c-1).valueAt(at.position());
                            for(var page:value.pages())if(graph.transaction(page.owner().xid()).graph().live().stream().noneMatch(e->e.identity().equals(page.owner())))
                                throw new RedoException("LOB_BATCH_PAGE_ALLOCATION_NOT_LIVE");
                            bytes=value.bytes();
                        } else bytes=CapturedSecureFileLobValues.value(graph,c,HexFormat.of().parseHex(cell.hex()),transaction.commit(),at.owner());
                        if(bytes==null)throw new RedoException("LOB_BATCH_VALUE_STATE_MISMATCH");
                        logical.add(new ArchivedHeapRows.Cell(State.VALUE,HexFormat.of().formatHex(bytes)));
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
}
