import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Full bounded row replay takes a real frozen snapshot and its acquisition handoff digest; no inline row values. */
public class VerifiedHeapRowsProbe {
    public static void main(String[] args) throws Exception {
        var paths=new ArrayList<Path>();paths.add(Path.of(args[0]));
        for (int i=4;i<args.length;i++) paths.add(Path.of(args[i]));
        var result=ArchivedHeapRows.readVerified(paths,Path.of(args[1]),args[2],Long.decode(args[3]));
        for (var c:result.changes()) {
            var p=c.position();System.out.println("ROW|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()+"|"+c.xid()+"|"+c.key().dataObjectId()+"|"+c.key().dba()+"|"+c.key().slot()+"|"+c.kind()+"|"+values(c.before())+"|"+values(c.after()));
        }
        System.err.println("verified_snapshot_row_changes="+result.changes().size()+";fixed_schema_typed_scope_only;historical_dictionary_and_cdc_unaccepted;cdc_events=0");
    }
    static String values(List<ArchivedHeapRows.Cell> row) {return String.join(",",row.stream().map(c->c.state()+":"+c.hex()).toList());}
}
