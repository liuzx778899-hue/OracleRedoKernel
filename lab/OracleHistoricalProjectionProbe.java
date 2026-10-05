import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Materialize the complete trusted projection before output. No product or target action. */
public class OracleHistoricalProjectionProbe {
    static String image(Map<String,Object> row) {
        if(row==null)return "ABSENT";
        var values=new ArrayList<String>();
        for(var e:row.entrySet())values.add(e.getKey()+"="+(e.getValue()==null?"NULL":e.getValue() instanceof BigDecimal n?"NUMBER:"+n.toPlainString():"VARCHAR2:"+HexFormat.of().formatHex(((String)e.getValue()).getBytes(StandardCharsets.UTF_8))));
        return String.join(",",values);
    }
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var input=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],Long.decode(args[2]));
        var batch=OracleHistoricalBatchProjection.project(input);
        System.out.println("INPUT|"+batch.inputBatchSha256()+"|"+batch.baselineSha256()+"|"+batch.baselineScn());
        System.out.println("SCHEMA_SEQUENCE|"+batch.schemaSequenceSha256());
        for(var e:batch.entries()) {
            var p=e.event().pairedUndoPosition();var t=e.event().transaction();var x=t.xid();var event=e.event().envelope();
            System.out.println("EVENT|"+event.eventId()+"|"+e.event().payloadSha256()+"|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()+"|"+x.usn()+"."+x.slot()+"."+x.sequence()+"|"+t.beginRecordScn()+"|"+t.endRecordScn()+"|"+e.schema().sha256()+"|"+event.metadata().schemaVersion()+"|"+event.changedColumns()+"|"+event.transaction().sequence());
            System.out.println("BEFORE|"+image(event.before()));System.out.println("AFTER|"+image(event.after()));
            System.out.println("BINDING|"+event.metadata().captureInput()+"|"+event.metadata().beforeComplete()+"|"+event.metadata().afterComplete()+"|"+event.transaction().beginLsn()+"|"+event.transaction().recordLsn()+"|"+event.transaction().commitLsn());
        }
        System.err.println("historical_projected_events="+batch.entries().size()+";product_http=false;target_checkpoint_ack=false");
    }
}
