import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen native bytes prove that an open transaction cannot suppress another transaction's commit. */
public class StreamPendingRuntimeProbe {
    public static void main(String[] args)throws Exception {
        int published=Integer.parseInt(args[2]),pending=Integer.parseInt(args[3]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=4;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var result=HistoricalRecovery.committedPrefixCaptured(captures,Path.of(args[0]),args[1],50359491);
        var full=result.completed();var boundary=full.publicationBoundary(published);
        var projection=OracleBatchProjection.project(full.afterPublished(boundary));
        var events=projection.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        String note=pending==1?"closed-B":"pending-A";
        if(result.state().pendingTransactions()!=pending || full.events().size()!=published+2 || events.size()!=2
                || !events.get(0).before().get("NOTE").equals("anchor") || !events.get(0).after().get("NOTE").equals(note)
                || !events.get(1).before().get("NOTE").equals(note) || !events.get(1).after().get("NOTE").equals("anchor"))
            throw new AssertionError("pending/committed native suffix mismatch");
        if(pending==0 && !full.events().equals(CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491).events()))
            throw new AssertionError("complete history differs from strict full replay");
        System.out.println(new ObjectMapper().writeValueAsString(Map.of("fullEvents",full.events().size(),"publishedEvents",published,
                "pendingTransactions",pending,"events",events,"evidence",projection.evidence(),"prefixSha256",boundary.sha256())));
    }
}
