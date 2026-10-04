import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.Arrays;

/** Generic bounded physical entry. No fixture SHA, caller XID list, native/SQL input or event publication. */
public class PhysicalTransactionProbe {
    public static void main(String[] args) throws Exception {
        var paths=Arrays.stream(args).skip(4).map(Path::of).toList();
        ArchivedPhysicalTransactions.Result result;
        if (args[0].equals("--snapshot")) {
            var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);var source=snapshot.source();
            var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                    new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
            result=ArchivedPhysicalTransactions.read(paths,source.containerUid(),source.containerId(),scope,Long.decode(args[3]),snapshot.columns().size());
            for (var h:result.sources())snapshot.requireSource(h);
        } else result=ArchivedPhysicalTransactions.read(paths,677737149,3,Long.parseLong(args[0]),Long.parseLong(args[1]),Long.decode(args[2]),Integer.parseInt(args[3]));
        for (var t:result.transactions()) {
            var x=t.identity().xid();var g=t.graph();
            System.out.println("TX|"+x.usn()+"."+x.slot()+"."+x.sequence()+"|"+g.original().size()+"|"+g.compensated().size()+"|"+g.live().size()+"|"+t.identity().rolledBack());
            for (var op:t.operations()) {
                var p=op.position();var h=op.original();
                System.out.println((op.compensation() ? "CANCEL" : "NODE")+"|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+address(h.uba())+"|"+h.dataObjectId());
            }
            for (var e:g.live()) System.out.println("LIVE|"+address(e.identity().uba())+"|"+e.identity().dataObjectId());
        }
        System.err.println("physical_transactions="+result.transactions().size()+";bounded_physical_graph_only;dictionary_and_dml_unaccepted;cdc_events=0");
    }
    static String address(HeapRowPatch.Uba u) {return u.dba()+":"+u.sequence()+":"+u.record();}
}
