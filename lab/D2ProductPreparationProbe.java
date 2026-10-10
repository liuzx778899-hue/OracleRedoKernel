import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Frozen-input projection for comparison and bounded enrollment, never source SQL events. */
public class D2ProductPreparationProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=5;i<args.length;i++) {
            var path=Path.of(args[i]);
            var hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            captures.add(new CaptureJournal().readRawClosed(path,hash));
        }
        var batch=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],Long.parseLong(args[2]));
        var projected=OracleBatchProjection.project(batch);
        var events=projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=Integer.parseInt(args[3]))throw new AssertionError("EVENT_COUNT");
        var state=HistoricalRecovery.prefixCaptured(captures,Path.of(args[0]),args[1],Long.parseLong(args[2]));
        if(state.pendingTransactions()!=0)throw new AssertionError("PENDING");
        Files.write(Path.of(args[4]),state.checkpoint(),StandardOpenOption.CREATE_NEW);
        var json=new ObjectMapper();
        var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        var hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",projected.evidence(),"events",events,"envelopesSha256",hash)));
    }
}
