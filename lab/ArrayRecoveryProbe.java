import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Separate JVM enrollment/resume of frozen complete ARRAY transactions. */
public class ArrayRecoveryProbe {
    private static String sha(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),state=Path.of(args[3]);String baselineSha=args[2];
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);
        var captures=new ArrayList<CaptureJournal.Verified>();long leaf=0;
        for(int i=4;i<args.length;i++) {
            Path raw=Path.of(args[i]);var capture=new CaptureJournal().readRawClosed(raw,sha(Files.readAllBytes(raw)));
            captures.add(capture);
            for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
                if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.objectId()==snapshot.index().dataObject()) {
                    if(leaf!=0 && leaf!=vector.dba())throw new AssertionError("ARRAY_MULTIPLE_LEAVES");
                    leaf=vector.dba();
                }
        }
        if(leaf==0)throw new AssertionError("ARRAY_INDEX_LEAF_MISSING");
        if(args[0].equals("enroll") || args[0].equals("pending")) {
            var prefix=HistoricalRecovery.committedPrefixCaptured(captures,baseline,baselineSha,leaf);
            if(args[0].equals("enroll") && prefix.state().pendingTransactions()!=0)throw new AssertionError("CLOSED_ARRAY_PENDING");
            if(args[0].equals("pending") && (prefix.state().pendingTransactions()==0 || !prefix.completed().events().isEmpty()))
                throw new AssertionError("ARRAY_PENDING_REQUIRED_WITHOUT_EVENTS");
            Files.write(state,prefix.state().checkpoint(),StandardOpenOption.CREATE_NEW);
            System.out.println("CHECKPOINT_SHA256="+sha(prefix.state().checkpoint()));
            System.out.println("PENDING_TRANSACTIONS="+prefix.state().pendingTransactions());
            System.out.println("COMPLETED_EVENTS="+prefix.completed().events().size());
        }else if(args[0].equals("resume")) {
            byte[] bytes=Files.readAllBytes(state);
            var restored=HistoricalRecovery.readCaptured(captures,baseline,baselineSha,leaf,bytes,sha(bytes));
            var direct=CapturedHistoricalHeapBatch.read(captures,baseline,baselineSha,leaf);
            var projected=OracleBatchProjection.project(restored);
            var directProjection=OracleBatchProjection.project(direct);
            if(!restored.events().equals(direct.events()) || !restored.sha256().equals(direct.sha256())
                    || !projected.entries().equals(directProjection.entries())
                    || !projected.evidence().equals(directProjection.evidence()))throw new AssertionError("ARRAY_RESTORE_CHANGED");
            var json=new ObjectMapper().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
            System.out.println("EVENTS="+projected.entries().size());
            System.out.println("EVENTS_SHA256="+sha(json.writeValueAsBytes(projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList())));
            Files.write(state.resolveSibling(state.getFileName()+".events.json"),
                    json.writeValueAsBytes(projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList()),StandardOpenOption.CREATE_NEW);
            System.out.println("RESTORED_EQUALS_DIRECT=true");
        }else throw new IllegalArgumentException("mode");
    }
}
