import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Complete materialization before stdout; source captures and trusted baseline only. */
public class UnifiedRecoveryProbe {
    static String cells(List<ArchivedHeapRows.Cell> cells){return String.join(",",cells.stream().map(c->c.state()+":"+c.hex()).toList());}
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path state=Path.of(args[1]);String digest=args[2];args=Arrays.copyOfRange(args,3,args.length);
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        if(mode.equals("prefix")) {
            byte[] bytes=HistoricalRecovery.checkpointCaptured(captures,Path.of(args[0]),args[1],Long.decode(args[2]));
            try(var out=java.nio.channels.FileChannel.open(state,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
            }
            System.out.println("UNIFIED_CHECKPOINT|"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            System.out.flush();Runtime.getRuntime().halt(23);
        }
        var batch=HistoricalRecovery.readCaptured(captures,Path.of(args[0]),args[1],Long.decode(args[2]),Files.readAllBytes(state),digest);
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
