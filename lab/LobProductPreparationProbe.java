import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Actual acquired baseline and raw context; old context events cannot publish. */
public class LobProductPreparationProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i++)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[i]))))));
        var records=new ArrayList<RedoRecords.Record>();
        for(var capture:captures)records.addAll(new RedoRecords().readCaptured(capture));
        long leaf=records.stream().flatMap(r->r.vectors().stream()).filter(v->v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject()).findFirst().orElseThrow().dba();
        var batch=CapturedHeapBatch.read(captures,Path.of(args[0]),args[1],leaf);
        var projected=OracleBatchProjection.project(batch);
        var events=projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=1)throw new AssertionError("LOB_NEW_EVENT_COUNT:"+events.size());
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",projected.evidence(),"events",events,"envelopesSha256",hash)));
    }
}
