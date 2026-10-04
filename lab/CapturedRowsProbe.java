import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Receipts and raw capture only in production arguments. Complete batch before stdout. */
public class CapturedRowsProbe {
    public static void main(String[] args) throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for (int i=4;i<args.length;i+=2) captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        Path snapshotPath=Path.of(args[1]);long leaf=Long.decode(args[3]);
        if (args[0].equals("physical")) {
            var snapshot=FrozenHeapBaseline.read(snapshotPath,args[2]);var source=snapshot.source();
            var scope=new HeapIndexScope(new HeapIndexScope.Generation(snapshot.heap().logicalObject(),snapshot.heap().dataObject()),
                    new HeapIndexScope.Generation(snapshot.index().logicalObject(),snapshot.index().dataObject()));
            var result=ArchivedPhysicalTransactions.readCaptured(captures,source.containerUid(),source.containerId(),scope,leaf,snapshot.columns().size());
            for (var h:result.sources()) snapshot.requireSource(h);
            for (var t:result.transactions()) {
                var x=t.identity().xid();var g=t.graph();System.out.println("TX|"+x.usn()+"."+x.slot()+"."+x.sequence()+"|"+g.original().size()+"|"+g.compensated().size()+"|"+g.live().size()+"|"+t.identity().rolledBack());
                for (var op:t.operations()) {var p=op.position();var h=op.original();System.out.println((op.compensation()?"CANCEL":"NODE")+"|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+PhysicalTransactionProbe.address(h.uba())+"|"+h.dataObjectId());}
                for (var e:g.live()) System.out.println("LIVE|"+PhysicalTransactionProbe.address(e.identity().uba())+"|"+e.identity().dataObjectId());
            }
            System.err.println("verified_sources="+result.sources()+";physical_transactions="+result.transactions().size()+";cdc_events=0;consumer_ack=false");
        } else {
            var result=ArchivedHeapRows.readCaptured(captures,snapshotPath,args[2],leaf);
            if (args[0].equals("rows")) for (var c:result.changes()) {var p=c.position();System.out.println("ROW|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()+"|"+c.xid()+"|"+c.key().dataObjectId()+"|"+c.key().dba()+"|"+c.key().slot()+"|"+c.kind()+"|"+VerifiedHeapRowsProbe.values(c.before())+"|"+VerifiedHeapRowsProbe.values(c.after()));}
            else {
                var rows=new ArrayList<>(result.rows().entrySet());rows.sort(Comparator.comparingLong((java.util.Map.Entry<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> e)->e.getKey().dataObjectId()).thenComparingLong(e->e.getKey().dba()).thenComparingInt(e->e.getKey().slot()));
                for (var row:rows) {var k=row.getKey();System.out.println("STATE|"+k.dataObjectId()+"|"+k.dba()+"|"+k.slot()+"|"+VerifiedHeapRowsProbe.values(row.getValue()));}
            }
            System.err.println("verified_sources="+result.sources()+";verified_changes="+result.changes().size()+";verified_rows="+result.rows().size()+";cdc_events=0;consumer_ack=false");
        }
    }
}
