import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Raw bytes and the acquired initial snapshot only; no SQL or dump as event input. */
public class AcquiredDmlProjectionProbe {
    public static void main(String[] args) throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var p=OracleBatchProjection.project(CapturedHeapBatch.read(captures,Path.of(args[0]),args[1],Long.parseLong(args[2])));
        var events=p.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",p.evidence(),"envelopes",events,"envelopesSha256",hash)));
    }
}
