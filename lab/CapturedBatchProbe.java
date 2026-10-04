import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Entire typed batch materializes before any stdout; only raw capture and trusted baseline inputs. */
public class CapturedBatchProbe {
    public static void main(String[] args) throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for (int i=3;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=CapturedHeapBatch.read(inputs,Path.of(args[0]),args[1],Long.decode(args[2]));
        System.out.println("BATCH|"+batch.sha256()+"|"+batch.baselineSha256()+"|"+batch.baselineScn()+"|"+batch.schema().sha256()+"|"+batch.source());
        for (var input:batch.captures()) System.out.println("INPUT|"+input.sequence()+"|"+input.journalSha256()+"|"+input.journalBytes()+"|"+input.firstScn()+"|"+input.nextScn());
        for (var event:batch.events()) {
            var p=event.pairedUndoPosition();var t=event.transaction();var k=event.rowKey();
            String position=p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector();
            System.out.println("EVENT|"+event.eventId()+"|"+event.payloadSha256()+"|"+position+"|"+t.xid()+"|"+t.beginRecordScn()+"|"+t.endRecordScn());
            System.out.println("ROW|"+position+"|"+t.xid()+"|"+k.dataObjectId()+"|"+k.dba()+"|"+k.slot()+"|"+event.operation()+"|"+VerifiedHeapRowsProbe.values(event.before())+"|"+VerifiedHeapRowsProbe.values(event.after()));
        }
        System.err.println("bounded_events="+batch.events().size()+";public_cdc_authorized=false;target_ack=false;commit_wall_time=unavailable");
    }
}
