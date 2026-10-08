import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Independently replays the frozen native bytes and compares the new complete event suffix. */
public class StreamIncrementalRuntimeProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var full=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491);
        var boundary=full.publicationBoundary(40);var projection=OracleBatchProjection.project(full.afterPublished(boundary));
        var events=projection.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(full.events().size()!=42 || events.size()!=2 || !events.get(0).before().get("NOTE").equals("anchor")
                || !events.get(0).after().get("NOTE").equals("stream-next") || !events.get(1).before().get("NOTE").equals("stream-next")
                || !events.get(1).after().get("NOTE").equals("anchor"))throw new AssertionError("incremental native event mismatch");
        System.out.println(new ObjectMapper().writeValueAsString(Map.of("fullEvents",42,"publishedEvents",40,"events",events,
                "evidence",projection.evidence(),"prefixSha256",boundary.sha256())));
    }
}
