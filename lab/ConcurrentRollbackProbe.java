import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Frozen seq21 routing: proves B's own physical/head graph; A PURGE positioning remains refused. */
public class ConcurrentRollbackProbe {
    static final HeapRowPatch.Xid A=new HeapRowPatch.Xid(10,19,1233),B=new HeapRowPatch.Xid(9,22,584);
    static class Context {
        final HeapRowPatch.Xid xid;
        final ArchivedCompensationInventory inventory;
        final UndoHistory graph;
        final TransactionLifecycle state=new TransactionLifecycle();
        final IndexLeafPositions positions;
        final List<HeapUndoPair> heaps=new ArrayList<>();
        final List<IndexUndoPair> indexes=new ArrayList<>();
        final List<ArchivedUndoInventory.Position> markers=new ArrayList<>();
        final List<String> output=new ArrayList<>();
        int next,ends;
        boolean active,closed;
        Context(Path path,HeapRowPatch.Xid xid,ArchivedRedo.Header header) throws Exception {
            this.xid=xid;inventory=ArchivedCompensationInventory.read(List.of(path),677737149,3,xid);
            if (!inventory.undo().sources().equals(List.of(header))) throw new RedoException("ROUTED_CONTEXT_SOURCE_CHANGED");
            graph=new UndoHistory(inventory.undo());
            positions=new IndexLeafPositions(ArchivedIndexLeafInventory.read(List.of(path),677737149,3,xid,73066,0x0300015bL));
            state.archive(header);
        }
        void originals(RedoRecords.Record r,long seq,String mode) throws Exception {
            var originals=new ArrayList<RedoRecords.Vector>();
            for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1
                    && v.fields().get(0).length==20 && xid.equals(new HeapRowPatch.Xid(
                    TransactionLifecycleProbe.half(v.fields().get(0),8),TransactionLifecycleProbe.half(v.fields().get(0),10),TransactionLifecycleProbe.word(v.fields().get(0),12)))) originals.add(v);
            for (var u:originals) {
                var h=UndoHeader.decode(u);
                if (h.beginsTransaction()) {
                    int count=0;
                    for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2
                            && TransactionStart.decode(v).uba().equals(h.uba())) {state.begin(r,v);active=true;count++;}
                    if (count!=1) throw new RedoException("ROUTED_BEGIN_COUNT");
                }
            }
            for (var u:originals) {
                state.undo(r,u);var h=UndoHeader.decode(u);var at=position(seq,r,u);
                var e=inventory.undo().entries().get(next++);
                if (!e.position().equals(at) || !e.identity().equals(h)) throw new RedoException("ROUTED_ORIGINAL_COVERAGE_CHANGED");
                if (!(xid.equals(B) && mode.equals("omit_B_node") && h.dataObjectId()==73066)) graph.append(e);
                int count=0;
                for (var row:r.vectors()) {
                    if (row.containerId()!=3) continue;
                    if (h.dataObjectId()==73065 && HeapCompensationProbe.heap(row)) {
                        var f=new HeapRowPatch().decode(row,2);
                        if (f.xtype()==1 && h.uba().equals(f.uba())) {var pair=HeapUndoPair.bind(r,row,2);state.requireActive(r,pair);heaps.add(pair);count++;}
                    } else if (h.dataObjectId()==73066 && row.opcodeMajor()==10 && (row.opcodeMinor()==2 || row.opcodeMinor()==4)) {
                        if (h.uba().equals(IndexUndoPair.decodeForward(row).uba())) {var pair=IndexUndoPair.bind(r,row);state.requireActive(r,pair);indexes.add(pair);count++;}
                    }
                }
                if (count!=1) throw new RedoException("ROUTED_ORIGINAL_PAIR_COUNT");
                output.add("NODE|"+at.archiveSequence()+"|"+at.block()+"|"+at.offset()+"|"+at.vector()+"|"+address(h.uba())+"|"+h.dataObjectId());
            }
            if (active) for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2) state.observeSlotControl(r,v);
        }
        void compensate(RedoRecords.Record r,RedoRecords.Vector marker,long seq,ArchivedCompensationAssociations.Entry association,String mode) throws Exception {
            if (!active || !association.xid().equals(xid)) throw new RedoException("ROUTED_OWNER_NOT_ACTIVE_OR_CHANGED");
            var c=UndoCompensation.decode(marker);var rows=new ArrayList<RedoRecords.Vector>();
            for (var row:r.vectors()) {
                if (row.containerId()!=3)continue;
                if (c.dataObjectId()==73066 && row.opcodeMajor()==10 && (row.opcodeMinor()==3 || row.opcodeMinor()==5)) rows.add(row);
                if (c.dataObjectId()==73065 && HeapCompensationProbe.heap(row) && new HeapRowPatch().decode(row,2).xtype()==2) rows.add(row);
            }
            if (rows.size()!=1)throw new RedoException("ROUTED_COMPENSATION_ROW_COUNT");
            UndoHeader original;
            if (c.dataObjectId()==73066) {
                if (xid.equals(B) && mode.equals("wrong_B_row_slot")) rows.get(0).fields().get(1)[2]^=1;
                original=IndexCompensation.match(r,marker,rows.get(0),indexes,state,positions,position(seq,r,rows.get(0))).undo().identity();
            } else if (c.dataObjectId()==73065) {
                var pair=c.beginsTransaction() ? HeapCompensation.matchBegin(r,marker,rows.get(0),heaps,2,state) : HeapCompensation.match(r,marker,rows.get(0),heaps,2);
                state.requireActive(r,pair);original=pair.undo().identity();
            } else throw new RedoException("ROUTED_UNSUPPORTED_OBJECT");
            if (!original.equals(association.original())) throw new RedoException("ROUTED_PHYSICAL_MATCH_DIFFERS_FROM_ASSOCIATION");
            graph.compensate(original);
            if (xid.equals(B) && mode.equals("repeat_B_cancel") && c.dataObjectId()==73066) graph.compensate(original);
            var at=position(seq,r,marker);markers.add(at);
            output.add("CANCEL|"+at.archiveSequence()+"|"+at.block()+"|"+at.offset()+"|"+at.vector()+"|"+address(original.uba())+"|"+original.dataObjectId());
        }
        void end(RedoRecords.Record r,boolean graphRequired) throws Exception {
            for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                var identity=state.end(r,v);ends++;active=false;
                if (graphRequired) {
                    inventory.requireCompleteSelection(markers);
                    var result=graph.closeRollback(identity);closed=true;
                    for (var e:result.live())output.add("LIVE|"+address(e.identity().uba())+"|"+e.identity().dataObjectId());
                    System.err.println("ROUTED_B_CLOSED|"+result.original().size()+"|"+result.compensated().size()+"|"+result.live().size());
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];if (!List.of("foreign-only","full","omit_B_node","omit_B_cancel","repeat_B_cancel","wrong_B_row_slot","wrong_B_marker","wrong_owner","omit_foreign_query").contains(mode))throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        if (!header.sha256().equals("f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80"))throw new RedoException("ROUTED_FROZEN_SOURCE_MISMATCH");
        var associations=ArchivedCompensationAssociations.read(List.of(path),677737149,3,A);
        var a=new Context(path,A,header);var b=new Context(path,B,header);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if (r.containerUid()!=677737149)return;
            try {
                a.originals(r,header.sequence(),mode);b.originals(r,header.sequence(),mode);
                for (var v:r.vectors()) {
                    var at=position(header.sequence(),r,v);
                    if (associations.entries().stream().noneMatch(e->e.position().equals(at)))continue;
                    var expected=associations.entries().stream().filter(e->e.position().equals(at)).findFirst().orElseThrow();
                    if (mode.equals("omit_foreign_query") && expected.xid().equals(B))continue;
                    if (mode.equals("wrong_B_marker") && expected.xid().equals(B))v.fields().get(1)[7]^=1;
                    var e=associations.associate(header,r,v,at);var h=e.original();
                    output.add("ASSOC|"+CompensationAssociationProbe.position(at)+"|"+e.xid().usn()+"."+e.xid().slot()+"."+e.xid().sequence()+"|"+CompensationAssociationProbe.position(e.originalPosition())+"|"+address(h.uba())+"|"+h.objectId()+"|"+h.dataObjectId()+"|"+h.beginsTransaction());
                    if (e.xid().equals(B)) {
                        if (mode.equals("omit_B_cancel") && h.dataObjectId()==73066)continue;
                        (mode.equals("wrong_owner") ? a : b).compensate(r,v,header.sequence(),e,mode);
                    } else if (e.xid().equals(A)) {
                        if (mode.equals("full")) a.compensate(r,v,header.sequence(),e,mode);
                    } else throw new RedoException("ROUTED_UNKNOWN_OWNER");
                }
                b.end(r,true);a.end(r,false);
            } catch (java.io.IOException e) {throw e;} catch (Exception e) {throw new java.io.IOException(e);}
        });
        associations.finish();a.state.finish();b.state.finish();
        if (!b.closed || a.ends!=1 || b.ends!=1 || a.next!=a.inventory.undo().entries().size() || b.next!=b.inventory.undo().entries().size())throw new RedoException("ROUTED_WINDOW_COVERAGE_INCOMPLETE");
        if (!header.equals(new ArchivedRedo().scan(path)))throw new RedoException("ROUTED_SOURCE_CHANGED");
        output.addAll(b.output);output.forEach(System.out::println);
        System.err.println("A_physical_graph_not_processed;foreign_B_graph_verified;cdc_events=0");
    }
    static ArchivedUndoInventory.Position position(long seq,RedoRecords.Record r,RedoRecords.Vector v) {return new ArchivedUndoInventory.Position(seq,r.block(),r.offset(),r.vectors().indexOf(v)+1);}
    static String address(HeapRowPatch.Uba u) {return u.dba()+":"+u.sequence()+":"+u.record();}
}
