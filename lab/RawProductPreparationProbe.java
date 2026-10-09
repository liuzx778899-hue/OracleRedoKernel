import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Metadata for the actual early acquisition, never a new or late source read. */
public class RawProductPreparationProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);long leaf=Long.parseLong(args[2]);
        if(args.length==7 && args[6].equals("product-metadata")) {
            var json=new ObjectMapper().registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(Long.class,com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance));
            System.out.println(json.writeValueAsString(Map.of("source",snapshot.source(),"scn",snapshot.scn(),"heap",snapshot.heap(),"index",snapshot.index(),
                    "columns",snapshot.columns(),"rowCount",snapshot.rows().size(),"dictionaryAnchors",snapshot.dictionaryAnchors(),"dictionaryBaseline",snapshot.dictionaryBaseline())));
            return;
        }
        var captures=List.of(new CaptureJournal().readRawClosed(Path.of(args[3]),args[4]));
        var batch=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],leaf);
        var projected=OracleBatchProjection.project(batch);
        var events=projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=4)throw new AssertionError("RAW_EVENT_COUNT");
        var state=HistoricalRecovery.prefixCaptured(captures,Path.of(args[0]),args[1],leaf);
        if(state.pendingTransactions()!=0)throw new AssertionError("RAW_PENDING");
        Files.write(Path.of(args[5]),state.checkpoint(),StandardOpenOption.CREATE_NEW);
        var json=new ObjectMapper();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(events)));
        var metadata=Map.of("source",snapshot.source(),"scn",snapshot.scn(),"heap",snapshot.heap(),"index",snapshot.index(),
                "columns",snapshot.columns(),"rowCount",snapshot.rows().size(),"dictionaryAnchors",snapshot.dictionaryAnchors(),"dictionaryBaseline",snapshot.dictionaryBaseline());
        System.out.println(json.writeValueAsString(Map.of("metadata",metadata,"evidence",projected.evidence(),"events",events,"envelopesSha256",hash)));
    }
}
