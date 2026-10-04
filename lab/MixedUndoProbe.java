import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bounded original heap+index fixture graph; complete selected 5.1/compensation inventory, not all redo semantics. */
public class MixedUndoProbe {
    public static void main(String[] args) throws Exception {
        String[] x=args[0].split("\\.");var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var paths=List.of(Path.of(args[1]),Path.of(args[2]));
        String mode=args.length>3 ? args[3] : "positive";
        if (!List.of("positive","omit_index_node","wrong_index_purge_slot").contains(mode)) throw new IllegalArgumentException("UNKNOWN_RESEARCH_MODE");
        var inventory=ArchivedCompensationInventory.read(paths,677737149,3,xid);
        var graph=new UndoHistory(inventory.undo());var state=new TransactionLifecycle();
        var heaps=new ArrayList<HeapUndoPair>();var indexes=new ArrayList<IndexUndoPair>();
        var markers=new ArrayList<ArchivedUndoInventory.Position>();var output=new ArrayList<String>();
        int[] next={0},ends={0};boolean[] active={false};UndoHistory.Result[] closed={null};
        for (var path:paths) {
            var header=new ArchivedRedo().scan(path);state.archive(header);
            new RedoRecords().visit(path,header,r -> {
                if (r.containerUid()!=677737149) return;
                var originals=new ArrayList<RedoRecords.Vector>();
                for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1
                        && v.fields().get(0).length==20 && xid.equals(new HeapRowPatch.Xid(
                            TransactionLifecycleProbe.half(v.fields().get(0),8),TransactionLifecycleProbe.half(v.fields().get(0),10),
                            TransactionLifecycleProbe.word(v.fields().get(0),12)))) originals.add(v);
                for (var u:originals) {
                    var h=UndoHeader.decode(u);
                    if (!h.beginsTransaction()) continue;
                    int begins=0;
                    for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2
                            && v.fields().get(0).length==32 && TransactionStart.decode(v).uba().equals(h.uba())) {
                        state.begin(r,v);active[0]=true;begins++;
                    }
                    if (begins!=1) throw new RedoException("MIXED_BEGIN_CONTROL_COUNT");
                }
                for (var u:originals) {
                    state.undo(r,u);var h=UndoHeader.decode(u);
                    var entry=inventory.undo().entries().get(next[0]++);
                    var position=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(u)+1);
                    if (!entry.position().equals(position)) throw new RedoException("MIXED_ORIGINAL_POSITION_MISMATCH");
                    if (!(mode.equals("omit_index_node") && h.dataObjectId()==73025)) graph.append(entry);
                    int pairs=0;
                    for (var v:r.vectors()) {
                        if (v.containerId()!=3) continue;
                        if (h.dataObjectId()==73024 && HeapCompensationProbe.heap(v)) {
                            var f=new HeapRowPatch().decode(v,4);
                            if (f.xtype()==1 && h.uba().equals(f.uba())) {var pair=HeapUndoPair.bind(r,v,4);state.requireActive(r,pair);heaps.add(pair);pairs++;}
                        } else if (h.dataObjectId()==73025 && v.opcodeMajor()==10 && (v.opcodeMinor()==2 || v.opcodeMinor()==4)) {
                            var f=IndexUndoPair.decodeForward(v);
                            if (h.uba().equals(f.uba())) {var pair=IndexUndoPair.bind(r,v);state.requireActive(r,pair);indexes.add(pair);pairs++;}
                        }
                    }
                    if (pairs!=1) throw new RedoException("MIXED_ORIGINAL_PAIR_COUNT");
                    output.add("NODE|"+position.archiveSequence()+"|"+position.block()+"|"+position.offset()+"|"+position.vector()+"|"+address(h.uba())+"|"+h.dataObjectId());
                }
                if (active[0]) for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2) state.observeSlotControl(r,v);
                if (active[0]) for (var marker:r.vectors()) {
                    if (marker.containerId()!=3 || marker.opcodeMajor()!=5 || (marker.opcodeMinor()!=6 && marker.opcodeMinor()!=11)) continue;
                    var c=UndoCompensation.decode(marker);var rows=new ArrayList<RedoRecords.Vector>();UndoHeader original;
                    for (var v:r.vectors()) {
                        if (v.containerId()!=3) continue;
                        if (c.dataObjectId()==73025 && v.opcodeMajor()==10 && v.opcodeMinor()==3) rows.add(v);
                        if (c.dataObjectId()==73024 && HeapCompensationProbe.heap(v) && new HeapRowPatch().decode(v,4).xtype()==2) rows.add(v);
                    }
                    if (rows.size()!=1) throw new RedoException("MIXED_COMPENSATION_ROW_COUNT");
                    if (c.dataObjectId()==73025) {
                        if (mode.equals("wrong_index_purge_slot")) rows.get(0).fields().get(1)[2]^=1;
                        original=IndexCompensation.match(r,marker,rows.get(0),indexes,state).undo().identity();
                    }
                    else if (c.dataObjectId()==73024) {
                        var pair=c.beginsTransaction() ? HeapCompensation.matchBegin(r,marker,rows.get(0),heaps,4,state)
                                : c.crossesUndoBlock() ? HeapCompensation.matchCross(r,marker,rows.get(0),heaps,4,state)
                                : HeapCompensation.match(r,marker,rows.get(0),heaps,4);
                        state.requireActive(r,pair);original=pair.undo().identity();
                    } else throw new RedoException("UNSUPPORTED_MIXED_COMPENSATION_OBJECT");
                    graph.compensate(original);
                    var position=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(marker)+1);
                    markers.add(position);
                    output.add("CANCEL|"+position.archiveSequence()+"|"+position.block()+"|"+position.offset()+"|"+position.vector()+"|"+address(original.uba())+"|"+original.dataObjectId());
                }
                for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                    inventory.requireCompleteSelection(markers);
                    if (TransactionEnd.decode(v).rolledBack()) closed[0]=graph.closeRollback(state.end(r,v));
                    else {closed[0]=graph.close(TransactionEnd.undoTail(v));state.end(r,v);}
                    ends[0]++;active[0]=false;
                }
            });
        }
        state.finish();
        if (ends[0]!=1 || closed[0]==null || next[0]!=inventory.undo().entries().size()) throw new RedoException("MIXED_GRAPH_COVERAGE_MISMATCH");
        for (var e:closed[0].live()) output.add("LIVE|"+address(e.identity().uba())+"|"+e.identity().dataObjectId());
        output.forEach(System.out::println);
        System.err.println("mixed_originals="+next[0]+";compensated="+markers.size()+";live="+closed[0].live().size()+";cdc_events=0;all_redo_semantics_not_accepted");
    }
    static String address(HeapRowPatch.Uba u) { return u.dba()+":"+u.sequence()+":"+u.record(); }
}
