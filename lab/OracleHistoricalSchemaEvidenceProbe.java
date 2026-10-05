import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.spi.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;

/** Trusted history projection/DTO roundtrip only. Does not enroll or publish a product batch. */
public class OracleHistoricalSchemaEvidenceProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=4;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=OracleHistoricalBatchProjection.project(CapturedHistoricalHeapBatch.read(captures,Path.of(args[1]),args[2],Long.decode(args[3])));
        var expected=batch.evidence();OracleSchemaSequence.requireValid(expected);var json=new ObjectMapper();
        var tree=(ObjectNode)json.valueToTree(expected);var schemas=(ArrayNode)tree.get("eventSchemas");
        if(mode.equals("wrong-commitment"))tree.put("schemaSequenceSha256","0".repeat(64));
        else if(mode.equals("swapped-association")){var first=schemas.get(0).deepCopy();schemas.set(0,schemas.get(1));schemas.set(1,first);}
        else if(mode.equals("trim-added-column"))((ArrayNode)schemas.get(0).get("columns")).remove(3);
        else if(!mode.equals("normal"))throw new IllegalArgumentException("UNKNOWN_PROBE_MODE");
        var restored=json.treeToValue(tree,OracleBatchEvidence.class);OracleSchemaSequence.requireValid(restored);
        if(!expected.equals(restored))throw new IllegalStateException("RAW_FACTORY_SCHEMA_EVIDENCE_MISMATCH");
        var events=batch.entries().stream().map(e->e.event().envelope()).toList();
        System.out.println(json.writeValueAsString(Map.of("evidence",restored,"envelopes",events)));
        System.err.println("factory_history_events="+events.size()+";product_enrollment=false;target_checkpoint_ack=false");
    }
}
