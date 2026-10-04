import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

/** Independent state inspection of the verified row API; frozen snapshot is the production input. */
public class VerifiedHeapStateProbe {
    public static void main(String[] args) throws Exception {
        var paths=new ArrayList<Path>();
        for (int i=3;i<args.length;i++) paths.add(Path.of(args[i]));
        var result=ArchivedHeapRows.readVerified(paths,Path.of(args[0]),args[1],Long.decode(args[2]));
        var rows=new ArrayList<>(result.rows().entrySet());
        rows.sort(Comparator.comparingLong((java.util.Map.Entry<ArchivedHeapRows.RowKey,java.util.List<ArchivedHeapRows.Cell>> e)->e.getKey().dataObjectId()).thenComparingLong(e->e.getKey().dba()).thenComparingInt(e->e.getKey().slot()));
        for (var row:rows) {
            var k=row.getKey();System.out.println("STATE|"+k.dataObjectId()+"|"+k.dba()+"|"+k.slot()+"|"+VerifiedHeapRowsProbe.values(row.getValue()));
        }
        System.err.println("verified_changes="+result.changes().size()+";verified_rows="+rows.size()+";cdc_events=0");
    }
}
