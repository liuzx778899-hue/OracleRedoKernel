import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Raw selected-object inverse patches; no row event production. */
public class UndoRowProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r -> {
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (!TransactionLifecycleProbe.targetUndo(v)) continue;
                var u=UndoRowPatch.decode(v,4); var p=u.inverse(); var xid=u.identity().xid();
                String cols=p.columns().stream().map(c -> c.state()+":"+HexFormat.of().formatHex(c.bytes())).collect(Collectors.joining(","));
                System.out.printf("%d|%d|%d|%s|%d|%d|%d|%d|%d|%s|%d|%d|%d%n",r.block(),r.offset(),i+1,
                        p.kind(),p.dba(),p.slot(),xid.usn(),xid.slot(),xid.sequence(),cols,
                        u.previousDba(),u.predecessorRecord(),u.wrap());
            }
        });
        System.err.println("bounded_inverse_patches_only;cdc_events=0");
    }
}
