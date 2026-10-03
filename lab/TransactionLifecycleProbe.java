import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Selected-object identity experiment. Outputs buffered until the entire archive chain passes. */
public class TransactionLifecycleProbe {
    public static void main(String[] args) throws Exception {
        var lifecycle=new TransactionLifecycle();
        var output=new ArrayList<TransactionLifecycle.ClosedIdentity>();
        for (String arg:args) {
            Path path=Path.of(arg); var header=new ArchivedRedo().scan(path); lifecycle.archive(header);
            new RedoRecords().visit(path,header,r -> {
                if (r.containerUid()!=677737149) return;
                // First collect every selected begin, even if a commit precedes its undo vector.
                for (var v:r.vectors()) {
                    if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=2
                            || v.fields().isEmpty() || v.fields().get(0).length!=32) continue;
                    byte[] b=v.fields().get(0);
                    boolean selected=false;
                    for (var u:r.vectors()) {
                        if (!targetUndo(u)) continue;
                        byte[] ub=u.fields().get(0);
                        if (u.dba()==word(b,8) && half(ub,16)==half(b,12) && ub[18]==b[14]) selected=true;
                    }
                    if (selected) lifecycle.begin(r,v);
                }
                for (var v:r.vectors()) if (targetUndo(v)) lifecycle.undo(r,v);
                for (var v:r.vectors()) {
                    if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4
                            && lifecycle.knowsEnd(r,v)) output.add(lifecycle.end(r,v));
                }
            });
        }
        lifecycle.finish();
        if (output.size()!=11) throw new RedoException("FIXTURE_IDENTITY_COUNT_MISMATCH:"+output.size());
        for (var h:output) System.out.printf("%d|%d|%d|%d|%d|%d|%d|%s%n",h.xid().usn(),h.xid().slot(),
                h.xid().sequence(),h.containerUid(),h.containerId(),h.beginRecordScn(),h.endRecordScn(),h.rolledBack());
        System.err.println("bounded_identity_lifecycle_only;cdc_events=0");
    }
    static boolean targetUndo(RedoRecords.Vector v) {
        return v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>=2
                && v.fields().get(0).length==20 && v.fields().get(1).length>=8 && word(v.fields().get(1),4)==73024;
    }
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
}
