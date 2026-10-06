import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen-window diagnostics only; not an event producer or production authority. */
public class IndexMaintenanceResearchProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.block()<126580 || r.block()>126630)return;
            String at=header.sequence()+":"+r.block()+":"+r.offset();
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if(v.opcodeMajor()!=10 && v.opcodeMajor()!=5)continue;
                System.out.println("VECTOR|"+at+"|"+(i+1)+"|"+r.scn()+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba()+"|"+v.containerId());
                for(int f=0;f<v.fields().size();f++)System.out.println("FIELD|"+at+"|"+(i+1)+"|"+(f+1)+"|"+HexFormat.of().formatHex(v.fields().get(f)));
            }
        });
        System.err.println("DIAGNOSTIC_ONLY|events=0");
    }
}
