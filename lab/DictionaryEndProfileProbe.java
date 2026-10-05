import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;

/** Diagnostic field inventory only; does not authorize any transaction or event. */
public class DictionaryEndProfileProbe {
    public static void main(String[] args) throws Exception {
        long baseline=Long.parseLong(args[0]);
        for(int i=1;i<args.length;i+=2) {
            var capture=new CaptureJournal().read(Path.of(args[i]),args[i+1]);
            for(var r:new RedoRecords().readCaptured(capture)) {
                if(r.scn()<=baseline || r.containerUid()!=677737149L)continue;
                for(int v=0;v<r.vectors().size();v++) {
                    var vector=r.vectors().get(v);
                    if(vector.containerId()!=3 || vector.opcodeMajor()!=5 || vector.opcodeMinor()!=4)continue;
                    try {TransactionEnd.decode(vector);}
                    catch(RedoException ex) {
                        System.out.println("UNSUPPORTED_END|"+capture.source().sequence()+"|"+r.block()+"|"+r.offset()+"|"+(v+1)+"|"+r.scn()+"|"+ex.getMessage());
                        for(int f=0;f<vector.fields().size();f++)System.out.println("FIELD|"+f+"|"+HexFormat.of().formatHex(vector.fields().get(f)));
                    }
                }
            }
        }
    }
}
