import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded conservative window coverage; unrelated concurrent markers are not attributed. */
public class CompensationInventoryProbe {
    public static void main(String[] args)throws Exception {
        String[] x=args[1].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedCompensationInventory.read(List.of(Path.of(args[0])),677737149,3,xid);
        var selected=new ArrayList<>(inventory.entries().stream().map(ArchivedCompensationInventory.Entry::position).toList());
        if(args.length==3) switch(args[2]) {
            case "--omit-all" -> selected.clear();
            case "--omit-one" -> selected.remove(0);
            case "--reverse" -> Collections.reverse(selected);
            case "--duplicate" -> selected.add(selected.get(0));
            case "--foreign" -> selected.set(0,new ArchivedUndoInventory.Position(999,2,16,1));
            default -> throw new IllegalArgumentException(args[2]);
        }
        inventory.requireCompleteSelection(selected);
        for(var e:inventory.entries()) {
            var p=e.position();var m=e.marker();
            System.out.println("MARKER|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+m.dba()+"|"+m.objectId()+"|"+m.dataObjectId()+"|"+m.slot()+"|"+m.wrap()+"|"+m.flags()+"|"+m.previousDba()+"|"+m.predecessorRecord());
        }
        System.err.println("pdb_window_markers="+inventory.entries().size()+";ownership_not_certified;cdc_events=0");
    }
}
