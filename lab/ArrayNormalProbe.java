import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Complete post-acquisition array batch; no SQL/native text is accepted as parser input. */
public class ArrayNormalProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i++) {
            var path=Path.of(args[i]);var sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            captures.add(new CaptureJournal().readRawClosed(path,sha));
        }
        long leaf=0;
        for(var capture:captures)for(var record:new RedoRecords().readCaptured(capture))for(var v:record.vectors())
            if(v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject() && v.opcodeMinor()==2) {if(leaf!=0 && leaf!=v.dba())throw new AssertionError("ARRAY_MULTIPLE_LEAVES");leaf=v.dba();}
        var projected=OracleBatchProjection.project(CapturedHeapBatch.read(captures,Path.of(args[0]),args[1],leaf));
        var events=projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=20 || events.stream().map(e->e.eventId()).distinct().count()!=20)throw new AssertionError("ARRAY_EVENT_COUNT_OR_ID");
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",projected.evidence(),"events",events,"envelopesSha256",hash)));
    }
}
