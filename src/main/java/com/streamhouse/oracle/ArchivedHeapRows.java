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

/** Fixed-schema, commit-ordered bounded replay from an explicit complete AS OF baseline. Not a historical dictionary or CDC authorization. */
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
    public record Result(List<ArchivedRedo.Header> sources,List<Change> changes,Map<RowKey,List<Cell>> rows,
                         Map<RowKey,ArchivedUndoInventory.Position> lastPositions) {
        public Result(List<ArchivedRedo.Header> sources,List<Change> changes,Map<RowKey,List<Cell>> rows){this(sources,changes,rows,Map.of());}
    }
    private record Pending(ArchivedUndoInventory.Position position,Xid xid,RowKey key,HeapUndoPair pair) {}
    private static final Comparator<ArchivedUndoInventory.Position> POSITION_ORDER=
            Comparator.comparingLong(ArchivedUndoInventory.Position::archiveSequence)
                    .thenComparingLong(ArchivedUndoInventory.Position::block)
                    .thenComparingInt(ArchivedUndoInventory.Position::offset)
                    .thenComparingInt(ArchivedUndoInventory.Position::vector);
    static List<ArchivedPhysicalTransactions.Transaction> commitOrdered(List<ArchivedPhysicalTransactions.Transaction> transactions) {
        return transactions.stream().sorted(Comparator.comparingLong((ArchivedPhysicalTransactions.Transaction t)->t.identity().endRecordScn())
                .thenComparing(ArchivedPhysicalTransactions.Transaction::commitPosition,POSITION_ORDER)).toList();
    }

    public static Result read(List<Path> paths,Baseline baseline,long indexObject,long leaf) throws IOException {
        if (baseline==null || baseline.source()==null) throw new RedoException("MISSING_COMPLETE_BASELINE");
        return read(RedoBatch.archives(paths),baseline,HeapIndexScope.equal(baseline.heapObject(),indexObject),leaf);
    }
    public static Result readVerified(List<Path> paths,Path snapshotPath,String trustedAcquisitionSha256,long leaf) throws IOException {
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        var inputs=RedoBatch.archives(paths);
        return read(inputs,new Baseline(snapshot.source(),snapshot.scn(),snapshot.heap().logicalObject(),snapshot.columns().size(),snapshot.rows()),scope,leaf,
                ArchivedPhysicalTransactions.readVerifiedBaseline(inputs,snapshotPath,trustedAcquisitionSha256,leaf),snapshot.columns());
    }
    public static Result readCaptured(List<CaptureJournal.Verified> captures,Path snapshotPath,String trustedAcquisitionSha256,long leaf) throws IOException {
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        var inputs=RedoBatch.captures(captures);
        return read(inputs,new Baseline(snapshot.source(),snapshot.scn(),snapshot.heap().logicalObject(),snapshot.columns().size(),snapshot.rows()),scope,leaf,
                ArchivedPhysicalTransactions.readVerifiedBaseline(inputs,snapshotPath,trustedAcquisitionSha256,leaf),snapshot.columns());
    }
    private static Result read(RedoBatch paths,Baseline baseline,HeapIndexScope scope,long leaf) throws IOException {
        var source=baseline.source();
        return read(paths,baseline,scope,leaf,ArchivedPhysicalTransactions.read(paths,source.containerUid(),source.containerId(),scope,leaf,baseline.columns()),null);
    }
    private static Result read(RedoBatch paths,Baseline baseline,HeapIndexScope scope,long leaf,ArchivedPhysicalTransactions.Result physical,
                               List<FrozenHeapBaseline.PhysicalColumn> schema) throws IOException {
        return read(paths,baseline,scope,leaf,physical,schema,null);
    }
    static Result readHistory(RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,long leaf,ArchivedDictionaryHistory.Timeline history,
                              ArchivedPhysicalTransactions.Result physical)throws IOException {
        history.requireBoundTo(paths,snapshot);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,new Baseline(snapshot.source(),snapshot.scn(),snapshot.heap().logicalObject(),snapshot.columns().size(),snapshot.rows()),scope,leaf,physical,snapshot.columns(),history);
    }
    private static Result read(RedoBatch paths,Baseline baseline,HeapIndexScope scope,long leaf,ArchivedPhysicalTransactions.Result physical,
                               List<FrozenHeapBaseline.PhysicalColumn> schema,ArchivedDictionaryHistory.Timeline history) throws IOException {
        return read(paths,baseline,scope,leaf,physical,schema,history,null);
    }
    static Result readHistoryRestored(RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,long leaf,ArchivedDictionaryHistory.Timeline history,
                                      ArchivedPhysicalTransactions.Result physical,HistoricalRowState restored)throws IOException {
        history.requireBoundTo(paths,snapshot);restored.requirePrefix(snapshot,leaf,physical);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,new Baseline(snapshot.source(),snapshot.scn(),snapshot.heap().logicalObject(),snapshot.columns().size(),snapshot.rows()),
                scope,leaf,physical,snapshot.columns(),history,restored);
    }
    private static Result read(RedoBatch paths,Baseline baseline,HeapIndexScope scope,long leaf,ArchivedPhysicalTransactions.Result physical,
                               List<FrozenHeapBaseline.PhysicalColumn> schema,ArchivedDictionaryHistory.Timeline history,HistoricalRowState restored) throws IOException {
        if (baseline.columns()<1 || baseline.columns()>255 || baseline.rows().entrySet().stream().anyMatch(e->
                e.getKey().dataObjectId()!=scope.heap().dataObject() || e.getKey().dba()<=0 || e.getKey().slot()<0 || e.getValue().size()!=baseline.columns()))
            throw new RedoException("BASELINE_ROW_SCOPE_OR_COLUMNS_MISMATCH");
        var source=baseline.source();
        for (var h:physical.sources()) if (!source.equals(Source.of(h,source.containerUid(),source.containerId())))
            throw new RedoException("BASELINE_SOURCE_IDENTITY_MISMATCH");
        if (baseline.scn()<physical.sources().get(0).firstScn() || baseline.scn()>=physical.sources().get(physical.sources().size()-1).nextScn())
            throw new RedoException("BASELINE_SCN_OUTSIDE_INPUT_COVERAGE");
        var transactions=commitOrdered(physical.transactions().stream().filter(t->t.identity().endRecordScn()>baseline.scn()
                && (restored==null || restored.after(t))).toList());
        var selected=new HashMap<ArchivedUndoInventory.Position,ArchivedPhysicalTransactions.Transaction>();
        var commits=new java.util.HashSet<ArchivedUndoInventory.Position>();
        for (var t:transactions) {
            if (t.identity().rolledBack()) {
                if (!t.graph().live().isEmpty() || t.graph().original().isEmpty()
                        || t.graph().compensated().size()!=t.graph().original().size())
                    throw new RedoException("ROW_REPLAY_INCOMPLETE_ROLLBACK_GRAPH");
                continue;
            }
            if (!commits.add(t.commitPosition())) throw new RedoException("AMBIGUOUS_ROW_REPLAY_COMMIT_POSITION");
            // All original and compensation records already passed the complete physical gate.
            for (var entry:t.graph().live()) if (entry.identity().dataObjectId()==scope.heap().dataObject()) selected.put(entry.position(),t);
        }
        if (selected.isEmpty() && transactions.stream().anyMatch(t->!t.graph().live().isEmpty()))
            throw new RedoException("NO_ROW_CHANGES_AFTER_BASELINE");
        var rows=new HashMap<>(restored==null?baseline.rows():restored.rows());var changes=new ArrayList<Change>();var seen=new java.util.HashSet<ArchivedUndoInventory.Position>();
        var pending=new HashMap<Xid,List<Pending>>();
        for (int i=0;i<paths.size();i++) {
            var path=paths.get(i);var h=physical.sources().get(i);
            path.visit(h,r->{
                int previousForward=-1;
                for (int v=0;v<r.vectors().size();v++) {
                    var at=new ArchivedUndoInventory.Position(h.sequence(),r.block(),r.offset(),v+1);var owner=selected.get(at);
                    if (owner==null) continue;
                    var recordSchema=history==null?schema:history.schemaAt(Math.max(r.scn(),baseline.scn()),r.scn()<=baseline.scn()?null:at);
                    if(history!=null && !recordSchema.equals(history.schemaAt(owner.identity().endRecordScn(),owner.commitPosition())))
                        throw new RedoException("UNSUPPORTED_ROW_RECORD_SCHEMA_TRANSITION");
                    int rowColumns=history==null?baseline.columns():recordSchema.size();
                    var undo=UndoHeader.decode(r.vectors().get(v));HeapUndoPair found=null;int forwardIndex=-1;
                    for (var f:r.vectors()) if (f.containerId()==source.containerId() && f.opcodeMajor()==11
                            && (f.opcodeMinor()==2 || f.opcodeMinor()==3 || f.opcodeMinor()==4 || f.opcodeMinor()==5)) {
                        var patch=new HeapRowPatch().decode(f,rowColumns);
                        if (patch.xtype()!=1 || !undo.uba().equals(patch.uba())) continue;
                        if (found!=null) throw new RedoException("ROW_REPLAY_PAIR_AMBIGUOUS");
                        found=HeapUndoPair.bind(r,f,rowColumns);forwardIndex=r.vectors().indexOf(f);
                    }
                    if (found==null || !undo.equals(found.undo().identity()) || !owner.identity().xid().equals(undo.xid()) || !seen.add(at))
                        throw new RedoException("ROW_REPLAY_PAIR_OWNER_OR_COVERAGE_MISMATCH");
                    if (forwardIndex<=previousForward) throw new RedoException("ROW_REPLAY_FORWARD_AND_UNDO_ORDER_DISAGREE");
                    previousForward=forwardIndex;
                    var p=found.forward();var key=new RowKey(undo.dataObjectId(),p.dba(),p.slot());
                    pending.computeIfAbsent(undo.xid(),ignored->new ArrayList<>()).add(new Pending(at,undo.xid(),key,found));
                }
            });
            if (!h.equals(path.header())) throw new RedoException("ROW_REPLAY_SOURCE_CHANGED");
        }
        if (seen.size()!=selected.size()) throw new RedoException("ROW_REPLAY_INCOMPLETE_SELECTED_INPUT");
        var lastRowPosition=new HashMap<RowKey,ArchivedUndoInventory.Position>(restored==null?Map.of():restored.lastPositions());
        var replaySchema=restored==null?schema:restored.columns();
        for (var t:transactions) {
            if(history!=null) {
                var next=history.schemaAt(t.identity().endRecordScn(),t.commitPosition());
                rows=new HashMap<>(remapRows(rows,replaySchema,next,history,t.identity().endRecordScn(),t.commitPosition()));replaySchema=next;
            }
            for (var p:pending.getOrDefault(t.identity().xid(),List.of())) {
            var previous=lastRowPosition.put(p.key(),p.position());
            if(previous!=null && POSITION_ORDER.compare(previous,p.position())>=0)
                throw new RedoException("ROW_REPLAY_COMMIT_AND_ROW_ORDER_DISAGREE");
            var before=rows.get(p.key());var after=apply(p.pair(),before,history==null?baseline.columns():replaySchema.size(),replaySchema);
            if(history!=null && !after.isEmpty())requireCompleteValues(after,replaySchema);
            if (p.pair().forward().kind()==Kind.LOCK) continue;
            changes.add(new Change(p.position(),p.xid(),p.key(),p.pair().forward().kind(),before==null ? List.of() : before,after));
            if (after.isEmpty()) rows.remove(p.key());else rows.put(p.key(),after);
            }
        }
        for (int i=0;i<paths.size();i++) if (!physical.sources().get(i).equals(paths.get(i).header()))
            throw new RedoException("ROW_REPLAY_SOURCE_CHANGED");
        return new Result(physical.sources(),List.copyOf(changes),Map.copyOf(rows),Map.copyOf(lastRowPosition));
    }

    static Map<RowKey,List<Cell>> remapRows(Map<RowKey,List<Cell>> rows,List<FrozenHeapBaseline.PhysicalColumn> previous,
                                           List<FrozenHeapBaseline.PhysicalColumn> next,ArchivedDictionaryHistory.Timeline history,long scn)throws RedoException {
        return remapRows(rows,previous,next,history,scn,null);
    }
    static Map<RowKey,List<Cell>> remapRows(Map<RowKey,List<Cell>> rows,List<FrozenHeapBaseline.PhysicalColumn> previous,
                                           List<FrozenHeapBaseline.PhysicalColumn> next,ArchivedDictionaryHistory.Timeline history,long scn,
                                           ArchivedUndoInventory.Position position)throws RedoException {
        if(previous.equals(next))return rows;
        var positions=new ArrayList<Integer>();
        for(var column:next) {
            int at=previous.indexOf(column);
            if(at<0) {
                if(previous.stream().anyMatch(c->c.internal()==column.internal() || c.name().equals(column.name())))
                    throw new RedoException("UNSUPPORTED_HISTORICAL_COLUMN_IDENTITY_CHANGE");
                history.requireAddedNullDefault(column,scn,position);
            }
            positions.add(at);
        }
        var result=new HashMap<RowKey,List<Cell>>();
        for(var entry:rows.entrySet()) {
            if(entry.getValue().size()!=previous.size())throw new RedoException("HISTORICAL_ROW_SCHEMA_WIDTH_MISMATCH");
            var row=new ArrayList<Cell>();for(int at:positions)row.add(at<0?new Cell(State.NULL,""):entry.getValue().get(at));
            requireCompleteValues(row,next);result.put(entry.getKey(),List.copyOf(row));
        }
        return Map.copyOf(result);
    }
    static void requireCompleteValues(List<Cell> row,List<FrozenHeapBaseline.PhysicalColumn> schema)throws RedoException {
        if(row.size()!=schema.size())throw new RedoException("HISTORICAL_ROW_SCHEMA_WIDTH_MISMATCH");
        for(int i=0;i<row.size();i++) {
            var cell=row.get(i);var column=schema.get(i);
            if(cell.state()==State.NULL) {if(!column.nullable())throw new RedoException("HISTORICAL_REQUIRED_ROW_VALUE_MISSING");continue;}
            byte[] raw=HexFormat.of().parseHex(cell.hex());
            if(column.type().equals("NUMBER"))OracleScalars.number(raw);
            else if(column.type().equals("VARCHAR2"))OracleScalars.varchar2(raw,"AL32UTF8");
            else throw new RedoException("UNSUPPORTED_HISTORICAL_ROW_VALUE_TYPE");
        }
    }

    static List<Cell> apply(HeapUndoPair pair,List<Cell> current,int columns) throws RedoException {
        return apply(pair,current,columns,null);
    }
    static List<Cell> apply(HeapUndoPair pair,List<Cell> current,int columns,List<FrozenHeapBaseline.PhysicalColumn> schema) throws RedoException {
        var forward=pair.forward();var inverse=pair.undo().inverse();
        if (forward.kind()==Kind.LOCK) {
            if (current==null || current.size()!=columns || inverse.kind()!=Kind.LOCK
                    || !Integer.valueOf(1).equals(forward.lockTo()) || !Integer.valueOf(0).equals(inverse.lockTo())
                    || forward.columns().stream().anyMatch(c->c.state()!=State.ABSENT)
                    || inverse.columns().stream().anyMatch(c->c.state()!=State.ABSENT))
                throw new RedoException("ROW_REPLAY_UNVERIFIED_LOCK");
            return List.copyOf(current);
        }
        if (forward.columns().size()!=columns || inverse.columns().size()!=columns || (forward.kind()!=Kind.DELETE && forward.extent()==null))
            throw new RedoException("ROW_REPLAY_COLUMN_OR_EXTENT_MISSING");
        if (forward.kind()==Kind.INSERT) {
            if (current!=null) throw new RedoException("ROW_REPLAY_INSERT_OVER_EXISTING_ROW");
            return completeInsert(forward,columns,schema,"ROW_REPLAY_INCOMPLETE_INSERT");
        }
        if (current==null || current.size()!=columns) throw new RedoException("ROW_REPLAY_MISSING_BASELINE_ROW");
        var prior=inverse.extent();
        if (prior==null || prior.storedColumns()!=stored(current)) throw new RedoException("ROW_REPLAY_BEFORE_EXTENT_MISMATCH");
        for (int i=0;i<columns;i++) if (inverse.columns().get(i).state()!=State.ABSENT && !cell(inverse.columns().get(i)).equals(current.get(i)))
            throw new RedoException("ROW_REPLAY_BEFORE_VALUE_MISMATCH");
        if (forward.kind()==Kind.DELETE) {
            var restored=completeInsert(inverse,columns,schema,"ROW_REPLAY_INCOMPLETE_DELETE_INVERSE");
            if (!restored.equals(current)) throw new RedoException("ROW_REPLAY_BEFORE_VALUE_MISMATCH");
            return List.of();
        }
        var after=new ArrayList<>(current);
        for (int i=0;i<columns;i++) if (forward.columns().get(i).state()!=State.ABSENT) after.set(i,cell(forward.columns().get(i)));
        int removed=forward.extent().removedColumn();
        if (removed>=0) after.set(removed,new Cell(State.NULL,""));
        if (stored(after)!=forward.extent().storedColumns()) throw new RedoException("ROW_REPLAY_AFTER_EXTENT_MISMATCH");
        return List.copyOf(after);
    }
    private static List<Cell> completeInsert(Patch patch,int columns,List<FrozenHeapBaseline.PhysicalColumn> schema,String incomplete) throws RedoException {
        var image=new ArrayList<Cell>();var extent=patch.extent();
        if (patch.kind()!=Kind.INSERT || extent==null || extent.storedColumns()<0 || extent.storedColumns()>columns
                || extent.changedColumns()!=extent.storedColumns() || patch.columns().size()!=columns)
            throw new RedoException(incomplete);
        for (int i=0;i<columns;i++) {
            var c=patch.columns().get(i);
            if (c.state()==State.ABSENT) {
                if (i<extent.storedColumns() || schema==null || schema.size()!=columns || !schema.get(i).nullable())
                    throw new RedoException(incomplete);
                image.add(new Cell(State.NULL,""));
            } else {
                if (i>=extent.storedColumns() || (schema!=null && c.state()==State.NULL && !schema.get(i).nullable()))
                    throw new RedoException(incomplete);
                image.add(cell(c));
            }
        }
        return List.copyOf(image);
    }
    private static Cell cell(Column c) {return new Cell(c.state(),HexFormat.of().formatHex(c.bytes()));}
    private static int stored(List<Cell> row) {
        int n=row.size();while (n>0 && row.get(n-1).state()==State.NULL)n--;return n;
    }
}
