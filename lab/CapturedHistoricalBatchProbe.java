import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Complete materialization before stdout; source captures and trusted baseline only. */
public class CapturedHistoricalBatchProbe {
    static String cells(List<ArchivedHeapRows.Cell> cells){return String.join(",",cells.stream().map(c->c.state()+":"+c.hex()).toList());}
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],Long.decode(args[2]));
        System.out.println("BATCH|"+batch.sha256()+"|"+batch.baselineSha256()+"|"+batch.baselineScn());
        for(var event:batch.events()) {
            var e=event.row();var p=e.pairedUndoPosition();var t=e.transaction();var k=e.rowKey();String at=p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector();
            System.out.println("SCHEMA|"+at+"|"+event.schema().sha256()+"|"+event.schema().columns());
            System.out.println("EVENT|"+e.eventId()+"|"+e.payloadSha256()+"|"+at+"|"+t.xid()+"|"+t.beginRecordScn()+"|"+t.endRecordScn());
            System.out.println("ROW|"+at+"|"+k.dataObjectId()+"|"+k.dba()+"|"+k.slot()+"|"+e.operation()+"|"+cells(e.before())+"|"+cells(e.after()));
        }
        System.err.println("bounded_historical_events="+batch.events().size()+";public_cdc_authorized=false;target_ack=false");
    }
}
