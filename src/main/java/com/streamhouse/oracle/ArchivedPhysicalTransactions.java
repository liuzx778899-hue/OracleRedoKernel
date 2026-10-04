package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Source-owned bounded physical graphs. Declared object/leaf/column scope is not a historical dictionary. */
public final class ArchivedPhysicalTransactions {
    public record Operation(ArchivedUndoInventory.Position position,UndoHeader original,boolean compensation) {}
    public record Transaction(TransactionLifecycle.ClosedIdentity identity,List<Operation> operations,UndoHistory.Result graph) {}
    public record Result(List<ArchivedRedo.Header> sources,List<Transaction> transactions) {}
    private static final class Context {
        final ArchivedObjectTransactions.Transaction discovered;
        final UndoHistory graph;
        final IndexLeafPositions positions;
        final List<HeapUndoPair> heaps=new ArrayList<>();
        final List<IndexUndoPair> indexes=new ArrayList<>();
        final List<ArchivedUndoInventory.Position> markers=new ArrayList<>();
        final List<Operation> operations=new ArrayList<>();
        Transaction result;
        long segment;
        int next;
        Context(ArchivedObjectTransactions.Transaction discovered,IndexLeafPositions positions) {
            this.discovered=discovered;this.positions=positions;graph=new UndoHistory(discovered.associations().window().undo());
        }
        boolean inWindow(ArchivedUndoInventory.Position at) {
            var w=discovered.associations().window();return compareRecord(at,w.firstRecord())>=0 && compareRecord(at,w.lastRecord())<=0;
        }
    }
    public static Result read(List<Path> paths,long uid,long container,long heapObject,long indexObject,long leaf,int columns) throws IOException {
        if (heapObject==indexObject || columns<=0) throw new RedoException("INVALID_PHYSICAL_TRANSACTION_SCOPE");
        return read(paths,uid,container,HeapIndexScope.equal(heapObject,indexObject),leaf,columns);
    }
    public static Result read(List<Path> paths,long uid,long container,HeapIndexScope scope,long leaf,int columns) throws IOException {
        if (columns<=0) throw new RedoException("INVALID_PHYSICAL_TRANSACTION_SCOPE");
        long heapObject=scope.heap().dataObject(),indexObject=scope.index().dataObject();
        paths=List.copyOf(paths);
        var found=ArchivedObjectTransactions.read(paths,uid,container,scope);
        if (found.transactions().isEmpty()) throw new RedoException("NO_PHYSICAL_OBJECT_TRANSACTIONS");
        var contexts=new ArrayList<Context>();var owners=new HashMap<HeapRowPatch.Xid,Context>();
        var originals=new HashMap<ArchivedUndoInventory.Position,Context>();
        var routes=new HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
        for (var t:found.transactions()) {
            var entries=t.associations().window().undo().entries();
            boolean hasIndex=entries.stream().anyMatch(e->e.identity().dataObjectId()==indexObject);
            var c=new Context(t,hasIndex ? IndexLeafPositions.read(paths,uid,container,t.xid(),scope.index().logicalObject(),indexObject,leaf) : null);
            contexts.add(c);owners.put(t.xid(),c);
            for (var e:entries) if (originals.putIfAbsent(e.position(),c)!=null) throw new RedoException("PHYSICAL_ORIGINAL_OWNER_AMBIGUOUS");
            for (var e:t.associations().entries()) {
                var previous=routes.putIfAbsent(e.position(),e);
                if (previous!=null && !previous.equals(e)) throw new RedoException("PHYSICAL_MARKER_OWNER_AMBIGUOUS");
            }
        }
        var state=new TransactionLifecycle();var heapBlocks=new HashSet<Long>();var undoBlocks=new HashSet<Long>();
        var scanner=new ArchivedRedo();
        // Raw inverse fields establish the bounded physical block set; caller cannot omit individual nodes.
        for (int source=0;source<paths.size();source++) {
            var header=scanner.scan(paths.get(source));requireSource(found,source,header);
            new RedoRecords().visit(paths.get(source),header,r->{
                if (r.containerUid()!=uid) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (!originals.containsKey(position(header,r,i))) continue;
                    var h=UndoHeader.decode(v);undoBlocks.add(v.dba());
                    if (h.dataObjectId()==heapObject) heapBlocks.add(UndoRowPatch.decode(v,columns).inverse().dba());
                    else if (IndexUndoPair.decodeUndo(v).block()!=leaf) throw new RedoException("PHYSICAL_INDEX_OUTSIDE_DECLARED_LEAF");
                }
            });
            requireSource(found,source,scanner.scan(paths.get(source)));
        }
        for (int source=0;source<paths.size();source++) {
            var path=paths.get(source);var header=scanner.scan(path);requireSource(found,source,header);state.archive(header);
            new RedoRecords().visit(path,header,r->{
                if (r.containerUid()!=uid) return;
                var paired=new HashSet<Integer>();var starts=new HashSet<Integer>();
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);var at=position(header,r,i);var c=originals.get(at);if (c==null) continue;
                    var h=UndoHeader.decode(v);
                    if (h.beginsTransaction()) {
                        int count=0;
                        for (int j=0;j<r.vectors().size();j++) {
                            var control=r.vectors().get(j);
                            if (control.containerId()!=container || control.opcodeMajor()!=5 || control.opcodeMinor()!=2) continue;
                            var start=TransactionStart.decode(control);
                            if (!start.uba().equals(h.uba())) continue;
                            state.begin(r,control);c.segment=start.segmentDba();starts.add(j);count++;
                        }
                        if (count!=1) throw new RedoException("PHYSICAL_BEGIN_CONTROL_COUNT");
                    }
                    state.undo(r,v);var e=c.discovered.associations().window().undo().entries().get(c.next++);
                    if (!e.position().equals(at) || !e.identity().equals(h)) throw new RedoException("PHYSICAL_ORIGINAL_COVERAGE_CHANGED");
                    c.graph.append(e);int count=0;
                    for (int j=0;j<r.vectors().size();j++) {
                        var row=r.vectors().get(j);if (row.containerId()!=container) continue;
                        if (h.dataObjectId()==heapObject && heap(row)) {
                            var f=new HeapRowPatch().decode(row,columns);
                            if (f.xtype()!=1 || !h.uba().equals(f.uba())) continue;
                            var pair=HeapUndoPair.bind(r,row,columns);state.requireActive(r,pair);c.heaps.add(pair);
                        } else if (h.dataObjectId()==indexObject && row.opcodeMajor()==10 && (row.opcodeMinor()==2 || row.opcodeMinor()==4)) {
                            if (!h.uba().equals(IndexUndoPair.decodeForward(row).uba())) continue;
                            var pair=IndexUndoPair.bind(r,row);state.requireActive(r,pair);c.indexes.add(pair);
                        } else continue;
                        if (!paired.add(j)) throw new RedoException("PHYSICAL_ROW_PAIRED_TWICE");count++;
                    }
                    if (count!=1) throw new RedoException("PHYSICAL_ORIGINAL_PAIR_COUNT");
                    c.operations.add(new Operation(at,h,false));
                }
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);var at=position(header,r,i);var route=routes.get(at);if (route==null) continue;
                    // Every conservative window must consume foreign markers, while only the true owner cancels.
                    for (var c:contexts) if (c.discovered.associations().entries().stream().anyMatch(e->e.position().equals(at))) {
                        if (!route.equals(c.discovered.associations().associate(header,r,v,at))) throw new RedoException("PHYSICAL_ASSOCIATION_CHANGED");
                        c.markers.add(at);
                    }
                    var c=owners.get(route.xid());if (c==null) throw new RedoException("PHYSICAL_UNDISCOVERED_MARKER_OWNER");
                    var marker=UndoCompensation.decode(v);UndoHeader matched=null;int rowIndex=-1;
                    for (int j=0;j<r.vectors().size();j++) {
                        var row=r.vectors().get(j);if (row.containerId()!=container) continue;
                        if (marker.dataObjectId()==indexObject && row.opcodeMajor()==10 && (row.opcodeMinor()==3 || row.opcodeMinor()==5)) {
                            if (matched!=null) throw new RedoException("PHYSICAL_COMPENSATION_ROW_AMBIGUOUS");
                            matched=IndexCompensation.match(r,v,row,c.indexes,state,c.positions,position(header,r,j)).undo().identity();
                        } else if (marker.dataObjectId()==heapObject && heap(row) && new HeapRowPatch().decode(row,columns).xtype()==2) {
                            if (matched!=null) throw new RedoException("PHYSICAL_COMPENSATION_ROW_AMBIGUOUS");
                            var pair=marker.beginsTransaction() ? HeapCompensation.matchBegin(r,v,row,c.heaps,columns,state) : HeapCompensation.match(r,v,row,c.heaps,columns);
                            state.requireActive(r,pair);matched=pair.undo().identity();
                        } else continue;
                        rowIndex=j;
                    }
                    if (matched==null || !matched.equals(route.original())) throw new RedoException("PHYSICAL_COMPENSATION_NOT_ASSOCIATED_ORIGINAL");
                    if (!paired.add(rowIndex)) throw new RedoException("PHYSICAL_ROW_PAIRED_TWICE");
                    c.graph.compensate(matched);c.operations.add(new Operation(at,matched,true));
                }
                var recordAt=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),0);
                boolean inWindow=contexts.stream().anyMatch(c->c.inWindow(recordAt));
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (v.containerId()!=container) continue;
                    if (inWindow && (v.dba()==leaf || heapBlocks.contains(v.dba())) && !paired.contains(i)) {
                        if (heapBlocks.contains(v.dba()) && v.opcodeMajor()==4 && v.opcodeMinor()==1)
                            EmptyHeapCleanout.require(r,v);
                        else throw new RedoException("UNBOUND_PHYSICAL_WINDOW_OPERATION:"+v.opcodeMajor()+"."+v.opcodeMinor());
                    }
                    boolean control=contexts.stream().anyMatch(c->c.segment==v.dba() && c.inWindow(recordAt));
                    if (!control && !(inWindow && undoBlocks.contains(v.dba()))) continue;
                    if (v.opcodeMajor()!=5) throw new RedoException("UNSUPPORTED_PHYSICAL_CONTROL_OPCODE");
                    if (v.opcodeMinor()==2) {
                        state.observeSlotControl(r,v);
                        if (!starts.contains(i)) throw new RedoException("UNSUPPORTED_PHYSICAL_NONBEGIN_SLOT_CONTROL");
                    } else if (v.opcodeMinor()!=1 && v.opcodeMinor()!=4 && v.opcodeMinor()!=6 && v.opcodeMinor()!=11)
                        throw new RedoException("UNSUPPORTED_PHYSICAL_CONTROL_OPCODE");
                }
                for (var v:r.vectors()) if (v.containerId()==container && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                    var end=state.end(r,v);var c=owners.get(end.xid());
                    if (c==null || c.result!=null) throw new RedoException("PHYSICAL_END_OWNER_CHANGED_OR_REPEATED");
                    c.discovered.associations().window().requireCompleteSelection(c.markers);
                    var result=end.rolledBack() ? c.graph.closeRollback(end) : c.graph.close(TransactionEnd.undoTail(v));
                    c.result=new Transaction(end,List.copyOf(c.operations),result);
                }
            });
            requireSource(found,source,scanner.scan(path));
        }
        state.finish();
        for (var c:contexts) {
            c.discovered.associations().finish();
            if (c.result==null || c.next!=c.discovered.associations().window().undo().entries().size()) throw new RedoException("PHYSICAL_TRANSACTION_COVERAGE_INCOMPLETE");
        }
        for (int i=0;i<paths.size();i++) requireSource(found,i,scanner.scan(paths.get(i)));
        return new Result(found.sources(),contexts.stream().map(c->c.result).toList());
    }
    private static boolean heap(RedoRecords.Vector v) {return v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==5);}
    private static ArchivedUndoInventory.Position position(ArchivedRedo.Header h,RedoRecords.Record r,int i) {return new ArchivedUndoInventory.Position(h.sequence(),r.block(),r.offset(),i+1);}
    private static void requireSource(ArchivedObjectTransactions found,int i,ArchivedRedo.Header h) throws RedoException {
        if (!found.sources().get(i).equals(h)) throw new RedoException("PHYSICAL_TRANSACTION_SOURCE_CHANGED");
    }
    private static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if (c==0)c=Long.compare(a.block(),b.block());return c==0 ? Integer.compare(a.offset(),b.offset()) : c;
    }
}
