import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.spi.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.ServiceLoader;

/** Real SPI binary plus the isolated adapter; buffer projection before any stdout. */
public class OracleProjectionProbe {
    public static void main(String[] args) throws Exception {
        var connector=ServiceLoader.load(EngineConnector.class).stream().map(ServiceLoader.Provider::get)
                .filter(c->c.descriptor().engineType().equals("ORACLE")).findFirst().orElseThrow();
        if (args[0].equals("public")) {
            var result=connector.decoder().decode(Path.of(args[1]),new CapDecoder.DecodeContext("source","",Map.of("authorize","true")));
            if (result.events().hasNext() || result.eventsPath()!=null || result.rc()==0) throw new AssertionError("PUBLIC_GATE_BYPASSED");
            System.err.println(result.rcReason());System.exit(result.rc());
        }
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for (int i=4;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=CapturedHeapBatch.read(inputs,Path.of(args[1]),args[2],Long.decode(args[3]));
        if (args[0].equals("kernel-only")) {
            System.out.println("RAW_BATCH|"+batch.events().size()+"|"+batch.events().get(batch.events().size()-1).after().get(1).hex());
            return;
        }
        var projected=OracleBatchProjection.project(batch);
        for (var entry:projected.entries()) {
            var e=entry.envelope();var t=entry.transaction();var p=entry.pairedUndoPosition();
            if (e.source().server()!=null || e.source().database()!=null || e.source().databaseGuid()!=null || e.source().recoveryFork()!=null
                    || e.transaction().beginLsn()!=null || e.transaction().recordLsn()!=null || e.transaction().commitLsn()!=null
                    || !e.metadata().beforeComplete() || !e.metadata().afterComplete()
                    || !e.metadata().schemaVersion().equals(batch.schema().sha256())
                    || !e.metadata().captureInput().equals("oracle-batch-sha256:"+batch.sha256())) throw new AssertionError("PROJECTION_CONTRACT");
        }
        System.out.println("BATCH|"+projected.inputBatchSha256()+"|"+projected.schema().sha256()+"|"+projected.source());
        for (var entry:projected.entries()) {
            var e=entry.envelope();var t=entry.transaction();var p=entry.pairedUndoPosition();
            System.out.println("PROJECTED|"+e.eventId()+"|"+entry.payloadSha256()+"|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()
                    +"|"+t.xid()+"|"+t.beginRecordScn()+"|"+t.endRecordScn()+"|"+e.transaction().sequence()+"|"+e.table().schema()+"|"+e.table().name()
                    +"|"+image(e.before())+"|"+image(e.after())+"|"+String.join(",",e.changedColumns()));
        }
        System.err.println("bounded_projection="+projected.entries().size()+";public_decoder_success=false;target_ack=false;legacy_lsn=null;commitAt=unavailable");
    }
    static String image(Map<String,Object> values) {
        var cells=new ArrayList<String>();
        for (var entry:values.entrySet()) {
            Object v=entry.getValue();String encoded;
            if (v==null) encoded="NULL";
            else if (v instanceof BigDecimal number) encoded="NUMBER:"+number.toPlainString();
            else if (v instanceof String text) encoded="VARCHAR2:"+Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            else throw new AssertionError("UNKNOWN_TYPED_VALUE");
            cells.add(entry.getKey()+"="+encoded);
        }
        return String.join(";",cells);
    }
}
