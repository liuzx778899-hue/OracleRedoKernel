import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Finite owned leaf coverage; never a position model or CDC event producer. */
public class IndexLeafInventoryProbe {
    public static void main(String[] args) throws Exception {
        String[] x=args[0].split("\\.");var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        long object=Long.parseLong(args[1]),block=Long.decode(args[2]);
        String mode=args[3];var paths=new ArrayList<Path>();for (int i=4;i<args.length;i++) paths.add(Path.of(args[i]));
        var inventory=ArchivedIndexLeafInventory.read(paths,677737149,3,xid,object,block);
        var positions=new ArrayList<>(inventory.entries().stream().map(ArchivedIndexLeafInventory.Entry::position).toList());
        if (mode.equals("omit_foreign")) {
            for (var e:inventory.entries()) if (e.original()!=null && !e.original().xid().equals(xid)) {positions.remove(e.position());break;}
        } else if (mode.equals("duplicate")) positions.add(1,positions.get(0));
        else if (mode.equals("reorder")) {var p=positions.get(0);positions.set(0,positions.get(1));positions.set(1,p);}
        else if (mode.equals("foreign_sequence")) {var p=positions.get(0);positions.set(0,new ArchivedUndoInventory.Position(p.archiveSequence()+1,p.block(),p.offset(),p.vector()));}
        else if (!mode.equals("positive")) throw new IllegalArgumentException("UNKNOWN_MODE");
        inventory.requireCompleteSelection(positions);
        var window=inventory.window();System.out.println("WINDOW|"+position(window.firstRecord())+"|"+position(window.lastRecord()));
        for (var e:inventory.entries()) {
            var h=e.original();var c=e.marker();
            System.out.println("LEAF|"+position(e.position())+"|"+e.recordScn()+"|"+e.kind()+"|"+e.block()+"|"+e.itl()+"|"+e.slot()+"|"+e.rowSize()
                    +"|"+(h==null ? "-" : h.xid().usn()+"."+h.xid().slot()+"."+h.xid().sequence())
                    +"|"+(h==null ? "-" : h.uba().dba()+":"+h.uba().sequence()+":"+h.uba().record())
                    +"|"+(e.key()==null ? "-" : e.key())+"|"+(e.keydata()==null ? "-" : e.keydata())
                    +"|"+(c==null ? "-" : c.slot()+":"+c.wrap()+":"+c.previousDba()+":"+c.predecessorRecord()));
        }
        System.err.println("leaf_operations="+positions.size()+";cdc_events=0;positions_not_replayed;complete_DML_not_accepted");
    }
    static String position(ArchivedUndoInventory.Position p) {return p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector();}
}
