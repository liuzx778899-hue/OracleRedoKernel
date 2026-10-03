import com.streamhouse.oracle.*;
import java.nio.file.Path;

/** Exact 5.2 UBA / 5.1 begin identity, bounded to the dedicated fixture. */
public class TransactionBeginProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); long firstBlock=Long.parseLong(args[1]);
        var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r -> {
            if (r.block()<firstBlock || r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=2) continue;
                // Select target-object UBA candidates before requiring a supported 5.2 form.
                boolean target=false;
                if (v.fields().isEmpty() || v.fields().get(0).length!=32) continue;
                byte[] first=v.fields().get(0);
                for (var u:r.vectors()) {
                    if (u.opcodeMajor()!=5 || u.opcodeMinor()!=1 || u.containerId()!=3
                            || u.fields().size()<2 || u.fields().get(0).length!=20 || u.fields().get(1).length<8)
                        continue;
                    byte[] ub=u.fields().get(0), obj=u.fields().get(1);
                    if (u.dba()==word(first,8) && half(ub,16)==half(first,12) && ub[18]==first[14]
                            && word(obj,4)==73024) target=true;
                }
                if (!target) continue;
                var h=TransactionStart.decode(v); var xid=h.bindBegin(r);
                System.out.printf("%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d%n",r.block(),r.offset(),i+1,
                        h.segmentDba(),h.slot(),h.sequence(),h.uba().dba(),h.uba().sequence(),h.uba().record(),
                        h.flags(),h.containerUid(),xid.usn(),xid.sequence());
            }
        });
        System.err.println("bounded_begin_binding_only;cdc_events=0");
    }
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
}
