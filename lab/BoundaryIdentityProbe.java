import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.Arrays;

/** Buffered raw transaction identities, separate from row replay and snapshot acceptance. */
public class BoundaryIdentityProbe {
    public static void main(String[] args) throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var source=snapshot.source();
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
        var result=ArchivedPhysicalTransactions.read(Arrays.stream(args).skip(2).map(Path::of).toList(),source.containerUid(),source.containerId(),scope,0x0300019b,snapshot.columns().size());
        for (var h:result.sources()) snapshot.requireSource(h);
        for (var t:result.transactions()) {
            var x=t.identity();var id=x.xid();
            System.out.println("BOUNDARY|"+id.usn()+"."+id.slot()+"."+id.sequence()+"|"+x.beginRecordScn()+"|"+snapshot.scn()+"|"+x.endRecordScn()+"|"+x.rolledBack());
        }
    }
}
