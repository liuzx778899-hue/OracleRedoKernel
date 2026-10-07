import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.HexFormat;

/** Raw profile research only: no event production or schema authority. */
public class MediaRecoveryMarkerProbe {
    public static void main(String[] args)throws Exception {
        Path path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            for(var v:r.vectors())if(v.opcodeMajor()==24 && v.opcodeMinor()==4) {
                System.out.println("MARKER|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+r.validity()+"|"+r.containerUid()+"|"+v.containerId());
                for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
        });
    }
}
