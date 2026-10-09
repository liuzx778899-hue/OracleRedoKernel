import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Two frozen publication windows, with the boundary taken from the actual first window. */
public class ProducerPublicationProjectionProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var first=HistoricalRecovery.committedPrefixCaptured(captures.subList(0,4),Path.of(args[0]),args[1],50359491);
        var boundary=first.completed().publicationBoundary(44);
        var next=HistoricalRecovery.extendPrefixCaptured(captures,Path.of(args[0]),args[1],50359491,first.state().checkpoint(),sha(first.state().checkpoint()));
        var a=OracleBatchProjection.project(first.completed());var b=OracleBatchProjection.project(next.completed().afterPublished(boundary));
        if(first.state().pendingTransactions()!=1 || next.state().pendingTransactions()!=0 || a.entries().size()!=44 || b.entries().size()!=2)throw new AssertionError("NORMAL_PUBLICATION_WINDOWS_CHANGED");
        var json=new ObjectMapper().configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        var rows=new ArrayList<Object>();for(var p:List.of(a,b)) {
            var events=p.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
            rows.add(Map.of("evidence",p.evidence(),"envelopes",events,"envelopesSha256",sha(json.writeValueAsBytes(events))));
        }
        System.out.println(json.writeValueAsString(Map.of("windows",rows,"prefixSha256",boundary.sha256())));
    }
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
