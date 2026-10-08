import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen input-owned maintenance inventory, including all original forward/inverse fields. */
public class D2ClusterMaintenanceProbe {
    public static void main(String[] args)throws Exception {
        Path path=Path.of(args[0]);
        String[] x=(args.length>1?args[1]:"10.3.3527").split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(path),677737149,3,xid);
        var header=inventory.sources().get(0);
        new RedoRecords().visit(path,header,r->{
            var entries=inventory.entries().stream().filter(e->e.position().block()==r.block() && e.position().offset()==r.offset()).toList();
            if(entries.isEmpty())return;
            System.out.println("RECORD|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+entries.get(0).identity());
            for(var v:r.vectors()) {
                System.out.println("VECTOR|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|dba="+v.dba()+"|class="+v.blockClass());
                for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
        });
    }
}
