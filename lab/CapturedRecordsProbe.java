import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Complete buffered structural capture output only, no transaction publication. */
public class CapturedRecordsProbe {
    public static void main(String[] args) throws Exception {
        var capture=new CaptureJournal().read(Path.of(args[0]),args[1]);
        var records=new RedoRecords().readCaptured(capture);
        for (var r:records) {
            if (args[2].equals("vectors")) {
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);
                    System.out.printf("%d|%d|%d|%d.%d|%d|%d|%d|%d|%d|%d|%s%n",r.block(),r.offset(),i+1,
                            v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),
                            v.fields().stream().map(f->HexFormat.of().formatHex(f)).collect(Collectors.joining(",")));
                }
            } else System.out.printf("%d|%d|%d|%d|%d|%d|%d|%s%n",r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),
                    r.vectors().stream().map(v->v.opcodeMajor()+"."+v.opcodeMinor()).collect(Collectors.joining(",")));
        }
        System.err.println("capture="+capture.source()+";structural_records="+records.size()+";cdc_events=0;consumer_ack=false");
    }
}
