import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Frozen raw dictionary history check; external acquisition is only the authenticated starting anchor. */
public class LobHistoricalSchemaProbe {
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[0]);String sha=args[1];
        var snapshot=FrozenHeapBaseline.read(baseline,sha);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i++) {
            Path raw=Path.of(args[i]);String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw)));
            captures.add(new CaptureJournal().readRawClosed(raw,digest));
        }
        var history=ArchivedDictionaryHistory.readCaptured(captures,baseline,sha);
        System.out.println("DICTIONARY_COMMITS="+history.commits().size());
        System.out.println("BASELINE_COLUMNS="+snapshot.columns());
        for(var commit:history.commits()) {
            var layout=snapshot.dictionaryBaseline().layouts().get("COL$");
            int nameAt=java.util.stream.IntStream.range(0,layout.columns().size()).filter(i->layout.columns().get(i).name().equals("NAME")).findFirst().orElseThrow();
            for(var row:commit.rows())if(row.role().equals("COL$"))System.out.println("COLUMN_NAME|"+commit.identity().endRecordScn()+"|"+OracleScalars.varchar2(HexFormat.of().parseHex(row.values().get(nameAt).hex()),"AL32UTF8"));
        }
        System.out.println("FINAL_SCHEMA="+history.schemaAt(captures.get(captures.size()-1).source().nextScn()-1));
    }
}
