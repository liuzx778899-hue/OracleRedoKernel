import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.HexFormat;

/** Independent record inventory from frozen archives; no event production. */
public class ProductActiveRecordProbe {
    public static void main(String[] args)throws Exception {
        long lower=Long.parseLong(args[0]);
        for(int i=1;i<args.length;i++) {
            Path path=Path.of(args[i]);var h=new ArchivedRedo().scan(path);
            new RedoRecords().visit(path,h,r->{
                if(r.scn()<lower)return;
                System.out.println("RECORD|"+h.sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+r.validity()+"|"+r.containerUid());
                for(int n=0;n<r.vectors().size();n++) {
                    var v=r.vectors().get(n);
                    System.out.println("VECTOR|"+(n+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.containerId()+"|"+v.dba());
                    if(v.opcodeMajor()==5 || v.opcodeMajor()==11 || v.opcodeMajor()==24)
                        for(int k=0;k<v.fields().size();k++)System.out.println("FIELD|"+k+"|"+HexFormat.of().formatHex(v.fields().get(k)));
                }
            });
        }
    }
}
