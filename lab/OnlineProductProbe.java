import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Independent frozen-byte comparison; never supplies production events or approval. */
public class OnlineProductProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var json=new ObjectMapper();var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i++) {
            Path p=Path.of(args[i]);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
            var observed=new CaptureJournal().readRawObserved(p,hash);
            if(observed.closed()!=null)captures.add(observed.closed());
        }
        if(captures.isEmpty())throw new AssertionError("NO_CLOSED_INPUT");
        captures.sort(Comparator.comparingLong(c->c.source().sequence()));
        var prefix=HistoricalRecovery.committedPrefixCaptured(captures,Path.of(args[0]),args[1],Long.parseLong(args[2]));
        var projection=OracleBatchProjection.project(prefix.completed());
        System.out.println(json.writeValueAsString(Map.of("evidence",projection.evidence(),"events",projection.entries().stream().map(OracleBatchProjection.Entry::envelope).toList(),
                "pendingTransactions",prefix.state().pendingTransactions())));
    }
}
