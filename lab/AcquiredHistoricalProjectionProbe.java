import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Frozen raw history plus the real early product baseline; no SQL/dump event production. */
public class AcquiredHistoricalProjectionProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=OracleBatchProjection.project(CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],Long.decode(args[2])));
        var events=batch.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",batch.evidence(),"envelopes",events,"envelopesSha256",digest)));
        System.err.println("historical_raw_factory_events="+events.size()+";product_http=false;checkpoint_ack=false");
    }
}
