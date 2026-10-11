import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Raw-derived historical product enrollment; external SQL is only a comparison ruler. */
public class ArrayTemporalProductProbe {
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[0]);var snapshot=FrozenHeapBaseline.read(baseline,args[1]);
        var captures=new ArrayList<CaptureJournal.Verified>();long leaf=0;
        for(int i=3;i<args.length;i++) {
            Path path=Path.of(args[i]);String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            var capture=new CaptureJournal().readRawClosed(path,sha);captures.add(capture);
            for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
                if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.objectId()==snapshot.index().dataObject()) {
                    if(leaf!=0 && leaf!=vector.dba())throw new AssertionError("PRODUCT_MULTIPLE_INDEX_LEAVES");leaf=vector.dba();
                }
        }
        if(leaf==0)throw new AssertionError("PRODUCT_INDEX_LEAF_MISSING");
        var batch=OracleBatchProjection.project(CapturedHistoricalHeapBatch.read(captures,baseline,args[1],leaf));
        if(batch.entries().size()!=Integer.parseInt(args[2]))throw new AssertionError("PRODUCT_EVENT_COUNT");
        var events=batch.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        System.out.println(json.writeValueAsString(Map.of("evidence",batch.evidence(),"events",events,"envelopesSha256",sha)));
    }
}
