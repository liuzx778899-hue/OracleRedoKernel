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
    record PendingGraph(HeapRowPatch.Xid xid,long segment,int next,List<Operation> operations,List<HeapUndoPair> heaps,
                        List<IndexUndoPair> indexes,List<ArchivedUndoInventory.Position> markers,String graphCheckpoint) {}
    record PendingState(List<ArchivedRedo.Header> sources,List<PendingGraph> pending,String identityCheckpoint) {}
    record Prefix(Result completed,PendingState state) {}
    private static final class Context {
        final ArchivedObjectTransactions.Transaction discovered;
        UndoHistory graph;
        final IndexLeafPositions positions;
        final List<HeapUndoPair> heaps=new ArrayList<>();
        final List<IndexUndoPair> indexes=new ArrayList<>();
        final List<ArchivedUndoInventory.Position> markers=new ArrayList<>();
        final List<Operation> operations=new ArrayList<>();
        Transaction result;
        boolean coveredByBaseline;
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
        return read(RedoBatch.archives(paths),uid,container,scope,leaf,columns);
    }
    public static Result readCaptured(List<CaptureJournal.Verified> captures,long uid,long container,HeapIndexScope scope,long leaf,int columns) throws IOException {
        return read(RedoBatch.captures(captures),uid,container,scope,leaf,columns);
    }
    static Result read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns) throws IOException {
        return read(paths,uid,container,scope,leaf,columns,null);
    }
    static Result readVerifiedBaseline(RedoBatch paths,Path snapshotPath,String trustedAcquisitionSha256,long leaf) throws IOException {
        var snapshot=FrozenHeapBaseline.read(snapshotPath,trustedAcquisitionSha256);
        for (var path:paths) snapshot.requireSource(path.header());
        if (paths.isEmpty() || snapshot.scn()<paths.get(0).header().firstScn()
                || snapshot.scn()>=paths.get(paths.size()-1).header().nextScn())
            throw new RedoException("BASELINE_SCN_OUTSIDE_INPUT_COVERAGE");
        HistoricalSchemaBarrier.requireUnchanged(paths,snapshot);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,snapshot.source().containerUid(),snapshot.source().containerId(),scope,leaf,snapshot.columns().size(),snapshot);
    }
    private static Result read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns,FrozenHeapBaseline.Snapshot snapshot) throws IOException {
        return read(paths,uid,container,scope,leaf,columns,snapshot,null);
    }
    static Result readHistory(RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryHistory.Timeline history,long leaf)throws IOException {
        history.requireBoundTo(paths,snapshot);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,snapshot.source().containerUid(),snapshot.source().containerId(),scope,leaf,snapshot.columns().size(),snapshot,history);
    }
    static Prefix readHistoryPrefix(RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryHistory.Timeline history,long leaf)throws IOException {
        history.requireBoundTo(paths,snapshot);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        PendingState[] pending={null};
        var completed=read(paths,snapshot.source().containerUid(),snapshot.source().containerId(),scope,leaf,snapshot.columns().size(),snapshot,history,p->pending[0]=p);
        return new Prefix(completed,pending[0]);
    }
    static Prefix readPrefix(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns)throws IOException {
        PendingState[] pending={null};
        var completed=read(paths,uid,container,scope,leaf,columns,null,null,p->pending[0]=p);
        return new Prefix(completed,pending[0]);
    }
    static Result readHistoryRestored(RedoBatch paths,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryHistory.Timeline history,long leaf,Prefix prefix)throws IOException {
        history.requireBoundTo(paths,snapshot);
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        return read(paths,snapshot.source().containerUid(),snapshot.source().containerId(),scope,leaf,snapshot.columns().size(),snapshot,history,null,prefix);
    }
    private static Result read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryHistory.Timeline history) throws IOException {
        return read(paths,uid,container,scope,leaf,columns,snapshot,history,null);
    }
    private static Result read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns,FrozenHeapBaseline.Snapshot snapshot,
                               ArchivedDictionaryHistory.Timeline history,java.util.function.Consumer<PendingState> savePending) throws IOException {
        return read(paths,uid,container,scope,leaf,columns,snapshot,history,savePending,null);
    }
    private static Result read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long leaf,int columns,FrozenHeapBaseline.Snapshot snapshot,
                               ArchivedDictionaryHistory.Timeline history,java.util.function.Consumer<PendingState> savePending,Prefix restored) throws IOException {
        if (columns<=0) throw new RedoException("INVALID_PHYSICAL_TRANSACTION_SCOPE");
        long heapObject=scope.heap().dataObject(),indexObject=scope.index().dataObject();
        var found=savePending==null?ArchivedObjectTransactions.read(paths,uid,container,scope):ArchivedObjectTransactions.readPending(paths,uid,container,scope);
        if (found.transactions().isEmpty() && !(savePending!=null && history!=null && !history.pending().isEmpty()))
            throw new RedoException("NO_PHYSICAL_OBJECT_TRANSACTIONS");
        var contexts=new ArrayList<Context>();var owners=new HashMap<HeapRowPatch.Xid,Context>();
        var originals=new HashMap<ArchivedUndoInventory.Position,Context>();
        var routes=new HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
        for (var t:found.transactions()) {
            var entries=t.associations().window().undo().entries();
            boolean hasIndex=entries.stream().anyMatch(e->e.identity().dataObjectId()==indexObject);
            var c=new Context(t,hasIndex ? IndexLeafPositions.read(paths,uid,container,t.xid(),scope.index().logicalObject(),indexObject,leaf,savePending!=null) : null);
            contexts.add(c);owners.put(t.xid(),c);
            for (var e:entries) if (originals.putIfAbsent(e.position(),c)!=null) throw new RedoException("PHYSICAL_ORIGINAL_OWNER_AMBIGUOUS");
            for (var e:t.associations().entries()) {
                var previous=routes.putIfAbsent(e.position(),e);
                if (previous!=null && !previous.equals(e)) throw new RedoException("PHYSICAL_MARKER_OWNER_AMBIGUOUS");
            }
        }
        int prefixCount=restored==null?0:restored.state().sources().size();
        if(prefixCount>paths.size())throw new RedoException("PHYSICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        for(int i=0;i<prefixCount;i++)if(!restored.state().sources().get(i).equals(found.sources().get(i)))throw new RedoException("PHYSICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        var state=restored==null?new TransactionLifecycle():TransactionLifecycle.restore(utf8(restored.state().identityCheckpoint()),recoveryDigest(utf8(restored.state().identityCheckpoint())));
        if(restored!=null) {
            long last=found.sources().get(prefixCount-1).sequence();int pendingCount=0,closedCount=0;
            for(var c:contexts) {
                var inventory=c.discovered.associations().window().undo();
                var pending=restored.state().pending().stream().filter(p->p.xid().equals(c.discovered.xid())).findFirst().orElse(null);
                var closed=restored.completed().transactions().stream().filter(t->t.identity().xid().equals(c.discovered.xid())).findFirst().orElse(null);
                c.next=(int)inventory.entries().stream().filter(e->e.position().archiveSequence()<=last).count();
                if(pending!=null) {
                    if(c.next!=pending.next())throw new RedoException("PHYSICAL_RECOVERY_ORIGINAL_PREFIX_CHANGED");
                    c.graph=UndoHistory.restore(inventory,utf8(pending.graphCheckpoint()),recoveryDigest(utf8(pending.graphCheckpoint())));
                    c.segment=pending.segment();c.operations.addAll(pending.operations());c.heaps.addAll(pending.heaps());c.indexes.addAll(pending.indexes());pendingCount++;
                } else if(closed!=null) {c.result=closed;c.operations.addAll(closed.operations());closedCount++;}
                else if(c.next>0) {
                    if(!c.discovered.associations().window().complete() || c.discovered.associations().window().lastRecord().archiveSequence()>last)
                        throw new RedoException("PHYSICAL_RECOVERY_PREFIX_OWNER_MISSING");
                    c.coveredByBaseline=true;
                }
            }
            if(pendingCount!=restored.state().pending().size() || closedCount!=restored.completed().transactions().size())throw new RedoException("PHYSICAL_RECOVERY_PREFIX_OWNER_MISSING");
        }
        var heapBlocks=new HashSet<Long>();var undoBlocks=new HashSet<Long>();

        // Raw inverse fields establish the bounded physical block set; caller cannot omit individual nodes.
        for (int source=0;source<paths.size();source++) {
            var header=paths.get(source).header();requireSource(found,source,header);
            paths.get(source).visit(header,r->{
                if (r.containerUid()!=uid) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (!originals.containsKey(position(header,r,i))) continue;
                    var h=UndoHeader.decode(v);undoBlocks.add(v.dba());
                    if (h.dataObjectId()==heapObject) heapBlocks.add(UndoRowPatch.decode(v,columnsAt(history,snapshot,r.scn(),columns)).inverse().dba());
                    else if (IndexUndoPair.decodeUndo(v).block()!=leaf) throw new RedoException("PHYSICAL_INDEX_OUTSIDE_DECLARED_LEAF");
                }
            });
            requireSource(found,source,paths.get(source).header());
        }
        // Rehydrate association cursors from the audited prefix, without feeding it into restored identities/graphs.
        for(int source=0;source<prefixCount;source++) {
            var path=paths.get(source);var header=path.header();requireSource(found,source,header);
            path.visit(header,r->{
                if(r.containerUid()!=uid)return;
                for(int i=0;i<r.vectors().size();i++) {
                    var at=position(header,r,i);if(!routes.containsKey(at))continue;
                    for(var c:contexts)if(c.discovered.associations().entries().stream().anyMatch(e->e.position().equals(at))) {
                        if(!routes.get(at).equals(c.discovered.associations().associate(header,r,r.vectors().get(i),at)))throw new RedoException("PHYSICAL_ASSOCIATION_CHANGED");
                        c.markers.add(at);
                    }
                }
            });requireSource(found,source,path.header());
        }
        for (int source=prefixCount;source<paths.size();source++) {
            var path=paths.get(source);var header=path.header();requireSource(found,source,header);state.archive(header);
            path.visit(header,r->{
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
                            int rowColumns=columnsAt(history,snapshot,r.scn(),columns);
                            var f=new HeapRowPatch().decode(row,rowColumns);
                            if (f.xtype()!=1 || !h.uba().equals(f.uba())) continue;
                            var pair=HeapUndoPair.bind(r,row,rowColumns);state.requireActive(r,pair);c.heaps.add(pair);
                        } else if (h.dataObjectId()==indexObject && row.opcodeMajor()==10 && (row.opcodeMinor()==2 || row.opcodeMinor()==4 || row.opcodeMinor()==5 && row.fields().size()==3)) {
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
                        } else if (marker.dataObjectId()==heapObject && heap(row) && new HeapRowPatch().decode(row,columnsAt(history,snapshot,r.scn(),columns)).xtype()==2) {
                            if (matched!=null) throw new RedoException("PHYSICAL_COMPENSATION_ROW_AMBIGUOUS");
                            int rowColumns=columnsAt(history,snapshot,r.scn(),columns);
                            var pair=marker.beginsTransaction() ? HeapCompensation.matchBegin(r,v,row,c.heaps,rowColumns,state) : HeapCompensation.match(r,v,row,c.heaps,rowColumns);
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
                        if (v.opcodeMajor()==4 && v.opcodeMinor()==1)
                            SingleEntryBlockCleanout.require(r,v);
                        else throw new RedoException("UNBOUND_PHYSICAL_WINDOW_OPERATION:"+v.opcodeMajor()+"."+v.opcodeMinor());
                    }
                    boolean control=contexts.stream().anyMatch(c->c.segment==v.dba() && c.inWindow(recordAt));
                    if (!control && !(inWindow && undoBlocks.contains(v.dba()))) continue;
                    if (v.opcodeMajor()!=5) throw new RedoException("UNSUPPORTED_PHYSICAL_CONTROL_OPCODE");
                    if (v.opcodeMinor()==2) {
                        state.observeSlotControl(r,v);
                        if (!starts.contains(i)) requireForeignBegin(r,v,scope,owners,recordAt);
                    } else if (v.opcodeMinor()!=1 && v.opcodeMinor()!=4 && v.opcodeMinor()!=6 && v.opcodeMinor()!=11)
                        throw new RedoException("UNSUPPORTED_PHYSICAL_CONTROL_OPCODE");
                }
                for (var v:r.vectors()) if (v.containerId()==container && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                    var end=state.end(r,v);var c=owners.get(end.xid());
                    if (c==null || c.result!=null || c.coveredByBaseline) throw new RedoException("PHYSICAL_END_OWNER_CHANGED_OR_REPEATED");
                    c.discovered.associations().window().requireCompleteSelection(c.markers);
                    // Keep all contexts and foreign routes until their raw lifecycle closes. The verified
                    // acquisition already covers old values; it does not authorize any post-baseline graph.
                    if (snapshot!=null && end.endRecordScn()<=snapshot.scn()) {
                        c.coveredByBaseline=true;continue;
                    }
                    // An initial snapshot excludes uncommitted values; retain the entire raw-owned graph.
                    // Row replay still must match its first before-image against that committed snapshot.
                    if(history!=null && !history.schemaAt(Math.max(end.beginRecordScn(),snapshot.scn())).equals(history.schemaAt(end.endRecordScn())))
                        throw new RedoException("UNSUPPORTED_TRANSACTION_CROSSES_SCHEMA_CHANGE");
                    var result=end.rolledBack() ? c.graph.closeRollback(end)
                            : v.fields().size()==2 && TransactionEnd.decode(v).flags()==0
                                ? c.graph.closeCommitWithoutNativeTail(end) : c.graph.close(TransactionEnd.undoTail(v));
                    c.result=new Transaction(end,List.copyOf(c.operations),result);
                }
            });
            requireSource(found,source,path.header());
        }
        if(savePending==null)state.finish();
        var pending=new ArrayList<PendingGraph>();
        for (var c:contexts) {
            c.discovered.associations().finish();
            boolean active=savePending!=null && !c.discovered.associations().window().complete() && c.result==null && !c.coveredByBaseline;
            if ((c.result==null && !c.coveredByBaseline && !active) || c.next!=c.discovered.associations().window().undo().entries().size()) throw new RedoException("PHYSICAL_TRANSACTION_COVERAGE_INCOMPLETE");
            if(active)pending.add(new PendingGraph(c.discovered.xid(),c.segment,c.next,List.copyOf(c.operations),List.copyOf(c.heaps),List.copyOf(c.indexes),
                    List.copyOf(c.markers),new String(c.graph.checkpoint(),java.nio.charset.StandardCharsets.UTF_8)));
        }
        if(savePending!=null)savePending.accept(new PendingState(found.sources(),List.copyOf(pending),new String(state.checkpoint(),java.nio.charset.StandardCharsets.UTF_8)));
        for (int i=0;i<paths.size();i++) requireSource(found,i,paths.get(i).header());
        return new Result(found.sources(),contexts.stream().filter(c->!c.coveredByBaseline && c.result!=null).map(c->c.result).toList());
    }
    private static boolean heap(RedoRecords.Vector v) {return v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==4 || v.opcodeMinor()==5);}
    private static byte[] utf8(String value){return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private static String recoveryDigest(byte[] bytes)throws RedoException {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new RedoException("SHA256_UNAVAILABLE");}
    }
    private static int columnsAt(ArchivedDictionaryHistory.Timeline history,FrozenHeapBaseline.Snapshot snapshot,long scn,int columns)throws RedoException {
        return history==null || scn<=snapshot.scn()?columns:history.schemaAt(scn).size();
    }
    private static void requireForeignBegin(RedoRecords.Record record,RedoRecords.Vector vector,HeapIndexScope scope,
                                           java.util.Map<HeapRowPatch.Xid,Context> owners,ArchivedUndoInventory.Position at) throws RedoException {
        var start=TransactionStart.decode(vector);var xid=start.bindBegin(record);
        if (owners.values().stream().anyMatch(c->c.inWindow(at) && c.segment==start.segmentDba()
                && c.discovered.xid().slot()==start.slot()))
            throw new RedoException("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE");
        if (owners.containsKey(xid)) throw new RedoException("UNBOUND_TARGET_BEGIN_CONTROL");
        var roles=scope.generations();int controls=0;
        for (var candidate:record.vectors()) {
            if (candidate.containerId()!=vector.containerId()) continue;
            if (candidate.opcodeMajor()==5 && candidate.opcodeMinor()==2 && candidate.dba()==vector.dba()
                    && TransactionStart.decode(candidate).uba().equals(start.uba())) controls++;
            if (candidate.opcodeMajor()!=5 || candidate.opcodeMinor()!=1 || candidate.dba()!=start.uba().dba()) continue;
            var undo=UndoHeader.decode(candidate);if (!undo.uba().equals(start.uba())) continue;
            if (roles.containsKey(undo.objectId()) || roles.containsValue(undo.objectId())
                    || roles.containsKey(undo.dataObjectId()) || roles.containsValue(undo.dataObjectId()))
                throw new RedoException("FOREIGN_BEGIN_INTERSECTS_TARGET_SCOPE");
        }
        if (controls!=1) throw new RedoException("AMBIGUOUS_FOREIGN_BEGIN_CONTROL");
    }
    private static ArchivedUndoInventory.Position position(ArchivedRedo.Header h,RedoRecords.Record r,int i) {return new ArchivedUndoInventory.Position(h.sequence(),r.block(),r.offset(),i+1);}
    private static void requireSource(ArchivedObjectTransactions found,int i,ArchivedRedo.Header h) throws RedoException {
        if (!found.sources().get(i).equals(h)) throw new RedoException("PHYSICAL_TRANSACTION_SOURCE_CHANGED");
    }
    private static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if (c==0)c=Long.compare(a.block(),b.block());return c==0 ? Integer.compare(a.offset(),b.offset()) : c;
    }
}
