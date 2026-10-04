import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Frozen seq21-24 routing: source-owned positions and independently closed A/B physical/head graphs in full mode. */
public class ConcurrentRollbackProbe {
    record Fixture(HeapRowPatch.Xid a,HeapRowPatch.Xid b,long heap,long index,long leaf,String sha) {}
    static Fixture fixture(String profile) {
        return switch(profile) {
            case "--fixture21" -> new Fixture(new HeapRowPatch.Xid(10,19,1233),new HeapRowPatch.Xid(9,22,584),73065,73066,0x0300015bL,"f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80");
            case "--fixture22" -> new Fixture(new HeapRowPatch.Xid(10,3,1236),new HeapRowPatch.Xid(7,11,587),73067,73068,0x0300016bL,"b1fcd87aeba90d31d6f281f3ea099f3c2f4643566756bf62c891d2eb4ced57b8");
            case "--fixture23" -> new Fixture(new HeapRowPatch.Xid(10,20,1240),new HeapRowPatch.Xid(9,30,584),73069,73070,0x0300017bL,"da6a0d7cfc919865a230c1f8d2357e48fedcf8c6334ea20dba0b009eff06313b");
            case "--fixture24" -> new Fixture(new HeapRowPatch.Xid(10,12,1212),new HeapRowPatch.Xid(9,5,585),73071,73072,0x0300018bL,"def82f0b1009a60142150e90726bfa8482d55c0a1a8a617603e4e3cf58395b09");
            default -> throw new IllegalArgumentException("UNKNOWN_FROZEN_FIXTURE");
        };
    }
    static class Context {
        final HeapRowPatch.Xid xid;
        final Fixture fixture;
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
        Context(Path path,HeapRowPatch.Xid xid,ArchivedRedo.Header header,Fixture fixture) throws Exception {
            this.xid=xid;this.fixture=fixture;inventory=ArchivedCompensationInventory.read(List.of(path),677737149,3,xid);
            if (!inventory.undo().sources().equals(List.of(header))) throw new RedoException("ROUTED_CONTEXT_SOURCE_CHANGED");
            graph=new UndoHistory(inventory.undo());
            positions=IndexLeafPositions.read(List.of(path),677737149,3,xid,fixture.index(),fixture.leaf());
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
                if (!((xid.equals(fixture.b()) && mode.equals("omit_B_node") || xid.equals(fixture.a()) && mode.equals("omit_A_node")) && h.dataObjectId()==fixture.index())) graph.append(e);
                int count=0;
                for (var row:r.vectors()) {
                    if (row.containerId()!=3) continue;
                    if (h.dataObjectId()==fixture.heap() && HeapCompensationProbe.heap(row)) {
                        var f=new HeapRowPatch().decode(row,2);
                        if (f.xtype()==1 && h.uba().equals(f.uba())) {var pair=HeapUndoPair.bind(r,row,2);state.requireActive(r,pair);heaps.add(pair);count++;}
                    } else if (h.dataObjectId()==fixture.index() && row.opcodeMajor()==10 && (row.opcodeMinor()==2 || row.opcodeMinor()==4)) {
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
                if (c.dataObjectId()==fixture.index() && row.opcodeMajor()==10 && (row.opcodeMinor()==3 || row.opcodeMinor()==5)) rows.add(row);
                if (c.dataObjectId()==fixture.heap() && HeapCompensationProbe.heap(row) && new HeapRowPatch().decode(row,2).xtype()==2) rows.add(row);
            }
            if (rows.size()!=1)throw new RedoException("ROUTED_COMPENSATION_ROW_COUNT");
            UndoHeader original;
            if (c.dataObjectId()==fixture.index()) {
                if (xid.equals(fixture.b()) && mode.equals("wrong_B_row_slot")) rows.get(0).fields().get(1)[2]^=1;
                original=IndexCompensation.match(r,marker,rows.get(0),indexes,state,positions,position(seq,r,rows.get(0))).undo().identity();
            } else if (c.dataObjectId()==fixture.heap()) {
                var pair=c.beginsTransaction() ? HeapCompensation.matchBegin(r,marker,rows.get(0),heaps,2,state) : HeapCompensation.match(r,marker,rows.get(0),heaps,2);
                state.requireActive(r,pair);original=pair.undo().identity();
            } else throw new RedoException("ROUTED_UNSUPPORTED_OBJECT");
            if (!original.equals(association.original())) throw new RedoException("ROUTED_PHYSICAL_MATCH_DIFFERS_FROM_ASSOCIATION");
            graph.compensate(original);
            if ((xid.equals(fixture.b()) && mode.equals("repeat_B_cancel") || xid.equals(fixture.a()) && mode.equals("repeat_A_cancel")) && c.dataObjectId()==fixture.index()) graph.compensate(original);
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
                    System.err.println("ROUTED_CLOSED|"+xid+"|"+result.original().size()+"|"+result.compensated().size()+"|"+result.live().size());
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        int base=args[0].startsWith("--fixture") ? 1 : 0;var fixture=fixture(base==1 ? args[0] : "--fixture21");
        String mode=args[base];if (!List.of("foreign-only","full","omit_B_node","omit_B_cancel","repeat_B_cancel","wrong_B_row_slot","wrong_B_marker","wrong_owner","omit_foreign_query","omit_A_node","omit_A_cancel","repeat_A_cancel").contains(mode))throw new IllegalArgumentException("UNKNOWN_MODE");
        boolean full=List.of("full","omit_A_node","omit_A_cancel","repeat_A_cancel").contains(mode);
        Path path=Path.of(args[base+1]);var header=new ArchivedRedo().scan(path);
        if (!header.sha256().equals(fixture.sha()))throw new RedoException("ROUTED_FROZEN_SOURCE_MISMATCH");
        var associations=ArchivedCompensationAssociations.read(List.of(path),677737149,3,fixture.a());
        var a=new Context(path,fixture.a(),header,fixture);var b=new Context(path,fixture.b(),header,fixture);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if (r.containerUid()!=677737149)return;
            try {
                a.originals(r,header.sequence(),mode);b.originals(r,header.sequence(),mode);
                for (var v:r.vectors()) {
                    var at=position(header.sequence(),r,v);
                    if (associations.entries().stream().noneMatch(e->e.position().equals(at)))continue;
                    var expected=associations.entries().stream().filter(e->e.position().equals(at)).findFirst().orElseThrow();
                    if (mode.equals("omit_foreign_query") && expected.xid().equals(fixture.b()))continue;
                    if (mode.equals("wrong_B_marker") && expected.xid().equals(fixture.b()))v.fields().get(1)[7]^=1;
                    var e=associations.associate(header,r,v,at);var h=e.original();
                    output.add("ASSOC|"+CompensationAssociationProbe.position(at)+"|"+e.xid().usn()+"."+e.xid().slot()+"."+e.xid().sequence()+"|"+CompensationAssociationProbe.position(e.originalPosition())+"|"+address(h.uba())+"|"+h.objectId()+"|"+h.dataObjectId()+"|"+h.beginsTransaction());
                    if (e.xid().equals(fixture.b())) {
                        if (full) a.markers.add(at);
                        if (mode.equals("omit_B_cancel") && h.dataObjectId()==fixture.index())continue;
                        (mode.equals("wrong_owner") ? a : b).compensate(r,v,header.sequence(),e,mode);
                    } else if (e.xid().equals(fixture.a())) {
                        if (mode.equals("omit_A_cancel") && h.dataObjectId()==fixture.index())continue;
                        if (full) a.compensate(r,v,header.sequence(),e,mode);
                    } else throw new RedoException("ROUTED_UNKNOWN_OWNER");
                }
                b.end(r,true);a.end(r,full);
            } catch (java.io.IOException e) {throw e;} catch (Exception e) {throw new java.io.IOException(e);}
        });
        associations.finish();a.state.finish();b.state.finish();
        if (!b.closed || (full && !a.closed) || a.ends!=1 || b.ends!=1 || a.next!=a.inventory.undo().entries().size() || b.next!=b.inventory.undo().entries().size())throw new RedoException("ROUTED_WINDOW_COVERAGE_INCOMPLETE");
        if (!header.equals(new ArchivedRedo().scan(path)))throw new RedoException("ROUTED_SOURCE_CHANGED");
        if (full) output.addAll(a.output);
        output.addAll(b.output);output.forEach(System.out::println);
        System.err.println((full ? "A_and_B_physical_graph_verified" : "A_physical_graph_not_processed;foreign_B_graph_verified")+";cdc_events=0");
    }
    static ArchivedUndoInventory.Position position(long seq,RedoRecords.Record r,RedoRecords.Vector v) {return new ArchivedUndoInventory.Position(seq,r.block(),r.offset(),r.vectors().indexOf(v)+1);}
    static String address(HeapRowPatch.Uba u) {return u.dba()+":"+u.sequence()+":"+u.record();}
}
