package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Fixed-schema, serialized bounded replay from an explicit complete AS OF baseline. Not a historical dictionary or CDC authorization. */
public final class ArchivedHeapRows {
    public record Source(long dbid,long activationId,long compatibility,int thread,long resetlogsId,
                         long resetlogsScn,long containerUid,long containerId) {
        public static Source of(ArchivedRedo.Header h,long uid,long container) {
            return new Source(h.dbid(),h.activationId(),h.compatibility(),h.thread(),h.resetlogsId(),h.resetlogsScn(),uid,container);
        }
    }
    public record Cell(State state,String hex) {
        public Cell {
            if (state==null || state==State.ABSENT || hex==null || (state==State.NULL && !hex.isEmpty()))
                throw new IllegalArgumentException("INCOMPLETE_BASELINE_CELL");
            hex=HexFormat.of().formatHex(HexFormat.of().parseHex(hex));
        }
    }
    public record RowKey(long dataObjectId,long dba,int slot) {}
    public record Baseline(Source source,long scn,long heapObject,int columns,Map<RowKey,List<Cell>> rows) {
        public Baseline {
            var copy=new HashMap<RowKey,List<Cell>>();rows.forEach((k,v)->copy.put(k,List.copyOf(v)));rows=Map.copyOf(copy);
        }
    }
    public record Change(ArchivedUndoInventory.Position position,Xid xid,RowKey key,Kind kind,List<Cell> before,List<Cell> after) {}
    public record Result(List<ArchivedRedo.Header> sources,List<Change> changes,Map<RowKey,List<Cell>> rows) {}

    public static Result read(List<Path> paths,Baseline baseline,long indexObject,long leaf) throws IOException {
        if (baseline==null || baseline.source()==null) throw new RedoException("MISSING_COMPLETE_BASELINE");
        return read(paths,baseline,HeapIndexScope.equal(baseline.heapObject(),indexObject),leaf);
    }
    public static Result readVerified(List<Path> paths,Path snapshotPath,String trustedAcquisitionSha256,long leaf) throws IOException {
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,new Baseline(snapshot.source(),snapshot.scn(),snapshot.heap().logicalObject(),snapshot.columns().size(),snapshot.rows()),scope,leaf);
    }
    private static Result read(List<Path> paths,Baseline baseline,HeapIndexScope scope,long leaf) throws IOException {
        if (baseline.columns()<1 || baseline.columns()>255 || baseline.rows().entrySet().stream().anyMatch(e->
                e.getKey().dataObjectId()!=scope.heap().dataObject() || e.getKey().dba()<=0 || e.getKey().slot()<0 || e.getValue().size()!=baseline.columns()))
            throw new RedoException("BASELINE_ROW_SCOPE_OR_COLUMNS_MISMATCH");
        paths=List.copyOf(paths);var source=baseline.source();
        var physical=ArchivedPhysicalTransactions.read(paths,source.containerUid(),source.containerId(),scope,leaf,baseline.columns());
        for (var h:physical.sources()) if (!source.equals(Source.of(h,source.containerUid(),source.containerId())))
            throw new RedoException("BASELINE_SOURCE_IDENTITY_MISMATCH");
        if (baseline.scn()<physical.sources().get(0).firstScn() || baseline.scn()>=physical.sources().get(physical.sources().size()-1).nextScn())
            throw new RedoException("BASELINE_SCN_OUTSIDE_INPUT_COVERAGE");
        var transactions=physical.transactions().stream().filter(t->t.identity().endRecordScn()>baseline.scn())
                .sorted(Comparator.comparingLong(t->t.identity().beginRecordScn())).toList();
        var selected=new HashMap<ArchivedUndoInventory.Position,ArchivedPhysicalTransactions.Transaction>();long previousEnd=baseline.scn();
        for (var t:transactions) {
            if (t.identity().beginRecordScn()<=baseline.scn()) throw new RedoException("BASELINE_CROSSES_TRANSACTION_BOUNDARY");
            if (t.identity().beginRecordScn()<=previousEnd) throw new RedoException("UNSUPPORTED_ROW_REPLAY_CONCURRENT_TRANSACTIONS");
            previousEnd=t.identity().endRecordScn();
            if (t.identity().rolledBack()) {
                if (!t.graph().live().isEmpty() || t.graph().original().isEmpty()
                        || t.graph().compensated().size()!=t.graph().original().size())
                    throw new RedoException("ROW_REPLAY_INCOMPLETE_ROLLBACK_GRAPH");
                continue;
            }
            // All original and compensation records already passed the complete physical gate.
            for (var entry:t.graph().live()) if (entry.identity().dataObjectId()==scope.heap().dataObject()) selected.put(entry.position(),t);
        }
        if (selected.isEmpty()) throw new RedoException("NO_ROW_CHANGES_AFTER_BASELINE");
        var rows=new HashMap<>(baseline.rows());var changes=new ArrayList<Change>();var seen=new java.util.HashSet<ArchivedUndoInventory.Position>();
        for (int i=0;i<paths.size();i++) {
            var path=paths.get(i);var h=physical.sources().get(i);
            new RedoRecords().visit(path,h,r->{
                int previousForward=-1;
                for (int v=0;v<r.vectors().size();v++) {
                    var at=new ArchivedUndoInventory.Position(h.sequence(),r.block(),r.offset(),v+1);var owner=selected.get(at);
                    if (owner==null) continue;
                    var undo=UndoHeader.decode(r.vectors().get(v));HeapUndoPair found=null;int forwardIndex=-1;
                    for (var f:r.vectors()) if (f.containerId()==source.containerId() && f.opcodeMajor()==11
                            && (f.opcodeMinor()==2 || f.opcodeMinor()==3 || f.opcodeMinor()==5)) {
                        var patch=new HeapRowPatch().decode(f,baseline.columns());
                        if (patch.xtype()!=1 || !undo.uba().equals(patch.uba())) continue;
                        if (found!=null) throw new RedoException("ROW_REPLAY_PAIR_AMBIGUOUS");
                        found=HeapUndoPair.bind(r,f,baseline.columns());forwardIndex=r.vectors().indexOf(f);
                    }
                    if (found==null || !undo.equals(found.undo().identity()) || !owner.identity().xid().equals(undo.xid()) || !seen.add(at))
                        throw new RedoException("ROW_REPLAY_PAIR_OWNER_OR_COVERAGE_MISMATCH");
                    if (forwardIndex<=previousForward) throw new RedoException("ROW_REPLAY_FORWARD_AND_UNDO_ORDER_DISAGREE");
                    previousForward=forwardIndex;
                    var p=found.forward();var key=new RowKey(undo.dataObjectId(),p.dba(),p.slot());
                    var before=rows.get(key);var after=apply(found,before,baseline.columns());
                    changes.add(new Change(at,undo.xid(),key,p.kind(),before==null ? List.of() : before,after));
                    if (after.isEmpty()) rows.remove(key);else rows.put(key,after);
                }
            });
            if (!h.equals(new ArchivedRedo().scan(path))) throw new RedoException("ROW_REPLAY_SOURCE_CHANGED");
        }
        if (seen.size()!=selected.size()) throw new RedoException("ROW_REPLAY_INCOMPLETE_SELECTED_INPUT");
        for (int i=0;i<paths.size();i++) if (!physical.sources().get(i).equals(new ArchivedRedo().scan(paths.get(i))))
            throw new RedoException("ROW_REPLAY_SOURCE_CHANGED");
        return new Result(physical.sources(),List.copyOf(changes),Map.copyOf(rows));
    }

    static List<Cell> apply(HeapUndoPair pair,List<Cell> current,int columns) throws RedoException {
        var forward=pair.forward();var inverse=pair.undo().inverse();
        if (forward.columns().size()!=columns || inverse.columns().size()!=columns || (forward.kind()!=Kind.DELETE && forward.extent()==null))
            throw new RedoException("ROW_REPLAY_COLUMN_OR_EXTENT_MISSING");
        if (forward.kind()==Kind.INSERT) {
            if (current!=null) throw new RedoException("ROW_REPLAY_INSERT_OVER_EXISTING_ROW");
            var inserted=new ArrayList<Cell>();
            for (var c:forward.columns()) {
                if (c.state()==State.ABSENT) throw new RedoException("ROW_REPLAY_INCOMPLETE_INSERT");
                inserted.add(cell(c));
            }
            return List.copyOf(inserted);
        }
        if (current==null || current.size()!=columns) throw new RedoException("ROW_REPLAY_MISSING_BASELINE_ROW");
        var prior=inverse.extent();
        if (prior==null || prior.storedColumns()!=stored(current)) throw new RedoException("ROW_REPLAY_BEFORE_EXTENT_MISMATCH");
        for (int i=0;i<columns;i++) if (inverse.columns().get(i).state()!=State.ABSENT && !cell(inverse.columns().get(i)).equals(current.get(i)))
            throw new RedoException("ROW_REPLAY_BEFORE_VALUE_MISMATCH");
        if (forward.kind()==Kind.DELETE) {
            if (inverse.columns().stream().anyMatch(c->c.state()==State.ABSENT)) throw new RedoException("ROW_REPLAY_INCOMPLETE_DELETE_INVERSE");
            return List.of();
        }
        var after=new ArrayList<>(current);
        for (int i=0;i<columns;i++) if (forward.columns().get(i).state()!=State.ABSENT) after.set(i,cell(forward.columns().get(i)));
        int removed=forward.extent().removedColumn();
        if (removed>=0) after.set(removed,new Cell(State.NULL,""));
        if (stored(after)!=forward.extent().storedColumns()) throw new RedoException("ROW_REPLAY_AFTER_EXTENT_MISMATCH");
        return List.copyOf(after);
    }
    private static Cell cell(Column c) {return new Cell(c.state(),HexFormat.of().formatHex(c.bytes()));}
    private static int stored(List<Cell> row) {
        int n=row.size();while (n>0 && row.get(n-1).state()==State.NULL)n--;return n;
    }
}
