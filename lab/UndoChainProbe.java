import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/** Audits original heap+index undo graphs for the dedicated target transactions. */
public class UndoChainProbe {
    public static void main(String[] args) throws Exception {
        var state=new TransactionLifecycle(); var chains=new HashMap<HeapRowPatch.Xid,UndoChain>();
        var output=new ArrayList<String>(); int[] nodes={0};
        for (String arg:args) {
            Path path=Path.of(arg); var header=new ArchivedRedo().scan(path); state.archive(header);
            new RedoRecords().visit(path,header,r -> {
                if (r.containerUid()!=677737149) return;
                for (var v:r.vectors()) {
                    if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=2 || v.fields().isEmpty() || v.fields().get(0).length!=32) continue;
                    byte[] b=v.fields().get(0); boolean target=false;
                    for (var u:r.vectors()) if (TransactionLifecycleProbe.targetUndo(u)) {
                        byte[] ub=u.fields().get(0);
                        if (u.dba()==word(b,8) && half(ub,16)==half(b,12) && ub[18]==b[14]) target=true;
                    }
                    if (!target) continue;
                    var start=TransactionStart.decode(v); var xid=start.bindBegin(r); state.begin(r,v);
                    if (chains.putIfAbsent(xid,new UndoChain(v.containerId(),xid,start.uba()))!=null) throw new RedoException("DUPLICATE_SELECTED_UNDO_CHAIN");
                }
                for (var v:r.vectors()) {
                    if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.fields().isEmpty() || v.fields().get(0).length<16) continue;
                    byte[] b=v.fields().get(0); var xid=new HeapRowPatch.Xid(half(b,8),half(b,10),word(b,12));
                    var chain=chains.get(xid);
                    if (chain==null) {
                        if (TransactionLifecycleProbe.targetUndo(v)) throw new RedoException("SELECTED_UNDO_WITHOUT_CHAIN_BEGIN");
                        continue;
                    }
                    state.undo(r,v); chain.add(v); nodes[0]++;
                }
                for (var v:r.vectors()) {
                    if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=4 || !state.knowsEnd(r,v)) continue;
                    var closed=state.end(r,v); var chain=chains.remove(closed.xid());
                    if (chain==null) throw new RedoException("END_WITHOUT_SELECTED_UNDO_CHAIN");
                    HeapRowPatch.Uba tail=null;
                    if (v.fields().size()==3) tail=TransactionEnd.undoTail(v);
                    else if (!closed.rolledBack()) throw new RedoException("COMMIT_WITHOUT_NATIVE_UNDO_TAIL");
                    var links=chain.close(tail);
                    String addresses=links.stream().map(n -> n.identity().uba().dba()+":"+n.identity().uba().sequence()+":"+
                            n.identity().uba().record()+":"+n.opcodeMajor()+":"+n.opcodeMinor()+":"+n.identity().dataObjectId()).collect(Collectors.joining(","));
                    output.add(String.format("%d|%d|%d|%d|%s|%s|%d|%s",closed.xid().usn(),closed.xid().slot(),closed.xid().sequence(),
                            closed.endRecordScn(),closed.rolledBack(),tail!=null,links.size(),addresses));
                }
            });
        }
        state.finish();
        if (!chains.isEmpty() || output.size()!=11 || nodes[0]!=32) throw new RedoException("FIXTURE_UNDO_CHAIN_COVERAGE_MISMATCH:"+nodes[0]);
        for (String line:output) System.out.println(line);
        System.err.println("original_undo_graphs_only;index_effects_cross_block_recovery_not_accepted;cdc_events=0");
    }
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
}
