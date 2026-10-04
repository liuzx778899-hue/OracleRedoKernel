import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;

/** Frozen source fields for the newly observed heap cleanout-C record. Research only. */
public class RestoreHeapSurvey {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r -> {
            if (r.block()!=10176 || r.offset()!=16) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.opcodeMajor()!=11 && !(v.opcodeMajor()==5 && v.opcodeMinor()==1
                        && IndexVectorSurvey.word(v.fields().get(1),4)==73059)) continue;
                var out=new StringBuilder(i+1+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba());
                for (byte[] field:v.fields()) out.append('|').append(HexFormat.of().formatHex(field));
                System.out.println(out);
            }
        });
    }
}
