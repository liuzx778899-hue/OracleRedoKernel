import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Complete owned predecessor graph only; physical dependency parsing remains required for events. */
public class TruncateUndoGraphProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);var xid=new HeapRowPatch.Xid(10,Integer.parseInt(args[1]),Long.parseLong(args[2]));
        var inventory=ArchivedUndoInventory.read(List.of(raw),677737149,3,xid);var graph=new UndoHistory(inventory);
        for(var entry:inventory.entries())graph.append(entry);
        var header=inventory.sources().get(0);int[] ends={0};
        new RedoRecords().visit(raw,header,r->{if(r.containerUid()!=inventory.containerUid())return;
            for(var v:r.vectors())if(v.containerId()==inventory.containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==4
                    && v.dba()==inventory.controlRoot().start().segmentDba()) {
                var end=TransactionEnd.decode(v);if(end.slot()!=xid.slot() || end.sequence()!=xid.sequence())continue;
                if(end.rolledBack())throw new AssertionError("TRUNCATE_ROLLED_BACK");
                var closed=graph.close(TransactionEnd.undoTail(v));
                System.out.println("GRAPH|"+xid+"|"+closed.original().size()+"|"+closed.live().size()+"|"+r.block()+"|"+r.offset()+"|"+r.scn());ends[0]++;
            }
        });
        if(ends[0]!=1 || inventory.entries().size()!=Integer.parseInt(args[3]))throw new AssertionError("TRUNCATE_GRAPH_COVERAGE");
        if(!header.equals(new ArchivedRedo().scan(raw)))throw new AssertionError("TRUNCATE_GRAPH_SOURCE_CHANGED");
        System.out.println("DIAGNOSTIC_ONLY|events=0");
    }
}
