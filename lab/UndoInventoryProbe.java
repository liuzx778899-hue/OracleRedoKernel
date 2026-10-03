import com.streamhouse.oracle.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Archive-owned identity/position coverage only; no inferred predecessor or CDC events. */
public class UndoInventoryProbe {
    public static void main(String[] args)throws Exception {
        String[] x=args[1].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(Path.of(args[0])),677737149,3,xid);
        var header=inventory.sources().get(0);
        var selected=new ArrayList<ArchivedUndoInventory.Position>();
        if(args.length==3) {
            for(var line:Files.readAllLines(Path.of(args[2])))if(line.startsWith("NODE|")) {
                String[] p=line.split("\\|");
                selected.add(new ArchivedUndoInventory.Position(header.sequence(),Long.parseLong(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])));
            }
        } else for(var e:inventory.entries())selected.add(e.position());
        inventory.requireCompleteSelection(selected);
        for(var e:inventory.entries()) {
            var p=e.position();var u=e.identity().uba();
            System.out.println("NODE|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+u.dba()+":"+u.sequence()+":"+u.record()+"|"+e.previousDba()+"|"+e.rci()+"|"+e.opcodeMajor()+"."+e.opcodeMinor());
        }
        System.err.println("selected_undo="+inventory.entries().size()+";input_sha256="+header.sha256()+";archive_owned_coverage_only;cdc_events=0");
    }
}
