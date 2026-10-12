import com.fasterxml.jackson.databind.*;
import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen native acquisition and raw bytes only; SQL truth never produces events. */
public class LobRecoveryProductProbe {
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[0]);String hash=args[1];long leaf=Long.parseLong(args[2]);
        var s=FrozenHeapBaseline.read(baseline,hash);var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i++) {
            Path p=Path.of(args[i]);captures.add(new CaptureJournal().readRawClosed(p,sha(Files.readAllBytes(p))));
        }
        var batch=OracleBatchProjection.project(CapturedHistoricalHeapBatch.read(captures,baseline,hash,leaf));
        var events=batch.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=1)throw new AssertionError("NORMAL_LOB_EXPECTED_ONE_EVENT:"+events.size());
        var builder=new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new com.streamhouse.app.config.JacksonLongAsStringConfig().longAsStringCustomizer().customize(builder);
        var json=builder.build();var canonical=json.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
        var metadata=Map.of("source",s.source(),"scn",s.scn(),"heap",s.heap(),"index",s.index(),"columns",s.columns(),
                "rowCount",s.rows().size(),"dictionaryAnchors",s.dictionaryAnchors(),"dictionaryBaseline",s.dictionaryBaseline(),"lobs",s.lobs());
        System.out.println(json.writeValueAsString(Map.of("evidence",batch.evidence(),"events",events,
                "envelopesSha256",sha(canonical.writeValueAsBytes(events)),"metadata",metadata)));
    }
    private static String sha(byte[] value)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
    }
}
