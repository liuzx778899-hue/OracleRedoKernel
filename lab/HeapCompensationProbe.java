import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

/** Bounded 5.6/5.11 experiment. Complete undo chain acceptance remains separate. */
public class HeapCompensationProbe {
    public static void main(String[] args) throws Exception {
        var state=new TransactionLifecycle(); var history=new ArrayList<HeapUndoPair>();
        var cancelled=new HashSet<HeapUndoPair>(); var output=new ArrayList<String>();
        int[] undoCount={0},endCount={0};
        for (String arg:args) {
            Path path=Path.of(arg); var header=new ArchivedRedo().scan(path); state.archive(header);
            new RedoRecords().visit(path,header,r -> {
                if (r.containerUid()!=677737149) return;
                var targetUndo=new ArrayList<RedoRecords.Vector>();
                for (var v:r.vectors()) if (TransactionLifecycleProbe.targetUndo(v)) targetUndo.add(v);
                for (var v:r.vectors()) {
                    if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=2
                            || v.fields().isEmpty() || v.fields().get(0).length!=32) continue;
                    byte[] b=v.fields().get(0);
                    for (var u:targetUndo) {
                        byte[] ub=u.fields().get(0);
                        if (u.dba()==word(b,8) && half(ub,16)==half(b,12) && ub[18]==b[14]) state.begin(r,v);
                    }
                }
                var inverses=new ArrayList<UndoRowPatch>();
                for (var v:targetUndo) { state.undo(r,v); inverses.add(UndoRowPatch.decode(v,4)); undoCount[0]++; }
                for (var v:r.vectors()) {
                    if (!heap(v) || inverses.stream().noneMatch(u -> u.inverse().dba()==v.dba())) continue;
                    var p=new HeapRowPatch().decode(v,4);
                    if (p.xtype()!=1) continue;
                    var pair=HeapUndoPair.bind(r,v,4); state.requireActive(r,pair); history.add(pair);
                }
                for (var marker:r.vectors()) {
                    if (marker.containerId()!=3 || marker.opcodeMajor()!=5 || (marker.opcodeMinor()!=6 && marker.opcodeMinor()!=11)
                            || marker.fields().isEmpty() || marker.fields().get(0).length<8
                            || word(marker.fields().get(0),4)!=73024) continue;
                    var rows=new ArrayList<RedoRecords.Vector>();
                    for (var v:r.vectors()) if (heap(v) && new HeapRowPatch().decode(v,4).xtype()==2) rows.add(v);
                    if (rows.size()!=1) throw new RedoException("FIXTURE_COMPENSATION_ROW_COUNT");
                    var pair=marker.opcodeMinor()==11
                            ? HeapCompensation.matchBegin(r,marker,rows.get(0),history,4,state)
                            : HeapCompensation.match(r,marker,rows.get(0),history,4);
                    state.requireActive(r,pair);
                    if (!cancelled.add(pair)) throw new RedoException("REPEATED_HEAP_COMPENSATION");
                    var h=pair.undo().identity(); var p=pair.forward();
                    output.add(String.format("cancel|%d|%d|%d|%d|%d|%d|%d|%d|%s|%d|%d",r.block(),r.offset(),
                            h.xid().usn(),h.xid().slot(),h.xid().sequence(),h.uba().dba(),h.uba().sequence(),h.uba().record(),p.kind(),p.dba(),p.slot()));
                }
                for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                    state.end(r,v); endCount[0]++;
                }
            });
        }
        state.finish();
        if (history.size()!=18 || undoCount[0]!=18 || endCount[0]!=11 || cancelled.size()!=4)
            throw new RedoException("FIXTURE_COMPENSATION_COVERAGE_MISMATCH");
        var savepoint=new HeapRowPatch.Xid(10,5,1133);
        var retained=history.stream().filter(p -> p.undo().identity().xid().equals(savepoint) && !cancelled.contains(p)).toList();
        if (retained.size()!=1) throw new RedoException("FIXTURE_SAVEPOINT_RETAINED_COUNT_MISMATCH");
        var keep=retained.get(0); var u=keep.undo().identity(); var p=keep.forward();
        output.add(String.format("retain|%d|%d|%d|%d|%d|%d|%s|%d|%d|%s",u.xid().usn(),u.xid().slot(),u.xid().sequence(),
                u.uba().dba(),u.uba().sequence(),u.uba().record(),p.kind(),p.dba(),p.slot(),HeapUndoPairProbe.cols(p)));
        for (String line:output) System.out.println(line);
        System.err.println("bounded_5.6_5.11_matching_only;full_undo_chain_not_accepted;cdc_events=0");
    }
    static boolean heap(RedoRecords.Vector v) { return v.containerId()==3 && v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==5); }
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
}
