import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Original predecessor graph and native-selected end only; does not bypass lifecycle for events. */
public class IndexMaintenanceGraphResearchProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);var xid=new HeapRowPatch.Xid(9,9,660);
        var inventory=ArchivedUndoInventory.read(List.of(path),677737149,3,xid);
        var graph=new UndoHistory(inventory);var output=new ArrayList<String>();
        for(var e:inventory.entries())output.add("NODE|"+e.position()+"|"+e.identity()+"|previous="+graph.append(e));
        int[] ends={0};var header=inventory.sources().get(0);
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=677737149)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && v.dba()==37748992) {
                var end=TransactionEnd.decode(v);if(end.slot()!=9 || end.sequence()!=660)continue;
                if(end.rolledBack())throw new RedoException("RESEARCH_MAINTENANCE_ROLLED_BACK");
                var result=graph.close(TransactionEnd.undoTail(v));ends[0]++;
                output.add("END|"+header.sequence()+":"+r.block()+":"+r.offset()+"|scn="+r.scn()+"|"+end+"|original="+result.original().size()+"|live="+result.live().size()+"|tail="+result.allocationTail());
            }
        });
        if(ends[0]!=1 || inventory.entries().size()!=5)throw new RedoException("RESEARCH_MAINTENANCE_COVERAGE");
        if(!header.equals(new ArchivedRedo().scan(path)))throw new RedoException("RESEARCH_SOURCE_CHANGED");
        output.forEach(System.out::println);
        System.err.println("DIAGNOSTIC_ONLY|graph_closed=true|lifecycle_supported=false|events=0");
    }
}
