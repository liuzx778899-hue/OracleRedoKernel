import com.streamhouse.oracle.*;
import java.nio.file.Path;

/** Bounded fixture marker extraction from bytes; no transaction publication. */
public class TransactionProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);
        long firstBlock=Long.parseLong(args[1]);
        var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r -> {
            if (r.block()<firstBlock || r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.containerId()!=3 || v.opcodeMajor()!=5) continue;
                if (v.opcodeMinor()==4) {
                    var h=TransactionEnd.decode(v);
                    System.out.printf("%d|%d|%d|4|%d|%d|%d|%d|%d%n",r.block(),r.offset(),i+1,
                            r.scn(),h.segmentDba(),h.slot(),h.sequence(),h.flags());
                } else if (v.opcodeMinor()==6 || v.opcodeMinor()==11) {
                    var h=UndoCompensation.decode(v);
                    System.out.printf("%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%s|%d%n",
                            r.block(),r.offset(),i+1,v.opcodeMinor(),r.scn(),h.dba(),h.objectId(),
                            h.dataObjectId(),h.tablespace(),h.opcodeMajor(),h.opcodeMinor(),h.slot(),
                            h.predecessorRecord(),h.flags(),h.wrap(),h.beginsTransaction(),h.previousDba());
                }
            }
        });
        System.err.println("bounded_transaction_markers_only;cdc_events=0");
    }
}
