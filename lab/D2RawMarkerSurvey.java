import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Research fields only; scope/profile are not inferred from SQL text. */
public class D2RawMarkerSurvey {
    public static void main(String[] args)throws Exception {
        long after=Long.parseLong(args[0]);
        for(int i=1;i<args.length;i+=2) {
            var capture=new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]);
            for(var r:new RedoRecords().readCaptured(capture))if(r.scn()>after)
                for(var v:r.vectors())if(v.opcodeMajor()==24) {
                    System.out.println("MARKER|"+capture.source().sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+r.containerUid()+"|"+v.containerId()+"|"+v.opcodeMinor()+"|"+v.fields().size());
                    for(int f=0;f<v.fields().size();f++)System.out.println("FIELD|"+f+"|"+HexFormat.of().formatHex(v.fields().get(f)));
                }
        }
    }
}
