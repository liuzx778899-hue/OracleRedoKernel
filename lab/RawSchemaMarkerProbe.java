import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Raw marker fields only. No DDL interpretation or event production. */
public class RawSchemaMarkerProbe {
    public static void main(String[] args) throws Exception {
        for(int i=0;i<args.length;i+=2) {
            var capture=new CaptureJournal().read(Path.of(args[i]),args[i+1]);
            for(var r:new RedoRecords().readCaptured(capture)) for(var v:r.vectors())
                if(v.opcodeMajor()==24 && v.opcodeMinor()==1) {
                    System.out.println("MARKER|"+capture.source().sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+r.containerUid()+"|"+v.containerId());
                    for(int f=0;f<v.fields().size();f++) System.out.println("FIELD|"+f+"|"+HexFormat.of().formatHex(v.fields().get(f)));
                }
        }
    }
}
