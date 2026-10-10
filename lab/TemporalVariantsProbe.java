import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Diagnostic raw-byte probe only; accepts no SQL or native dump as parser input. */
public class TemporalVariantsProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);long object=Long.parseLong(args[1]);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        var capture=new CaptureJournal().readRawClosed(path,sha);var ids=new HashSet<String>();
        for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors()) {
            if(vector.objectId()!=object || vector.opcodeMajor()!=11 || vector.opcodeMinor()!=2)continue;
            var pair=HeapUndoPair.bind(record,vector,9);var cells=pair.forward().columns();
            String id=OracleScalars.number(cells.get(0).bytes()).toPlainString();
            if(!ids.add(id))throw new AssertionError("REPEATED_TEMPORAL_ID");
            var out=new StringBuilder("ROW|").append(id);
            for(int i=1;i<9;i++)out.append('|').append(HexFormat.of().formatHex(cells.get(i).bytes()));
            System.out.println(out);
            System.out.println("VALUE|"+id+"|D|"+OracleScalars.date(cells.get(1).bytes()));
            for(int i=2;i<=5;i++)System.out.println("VALUE|"+id+"|TS"+List.of(0,3,6,9).get(i-2)+"|"+OracleScalars.timestamp(cells.get(i).bytes()));
            System.out.println("VALUE|"+id+"|TZ|"+OracleScalars.timestampWithOffset(cells.get(6).bytes()));
        }
        if(!ids.equals(Set.of("1","2","3")))throw new AssertionError("TEMPORAL_ROW_COVERAGE");
    }
}
