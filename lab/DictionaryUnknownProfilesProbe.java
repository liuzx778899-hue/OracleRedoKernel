import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.nio.*;

/** Diagnostic field shapes only, no metadata or publication authority. */
public class DictionaryUnknownProfilesProbe {
    static long u32(byte[] b,int n){return Integer.toUnsignedLong(ByteBuffer.wrap(b,n,4).order(ByteOrder.LITTLE_ENDIAN).getInt());}
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);long minimum=Long.parseLong(args[1]);var header=new ArchivedRedo().scan(raw);
        new RedoRecords().visit(raw,header,r->{if(r.containerUid()!=677737149 || r.scn()<=minimum)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>=4) {
                var m=v.fields().get(1);long object=u32(m,0);if(!List.of(4L,18L,21L).contains(object))continue;
                var kdo=v.fields().get(3);if(kdo.length<20)continue;int op=kdo[10]&255;
                boolean known=op==5 && (kdo.length==29 || kdo.length==30) || (op==3 || op==4) && kdo.length==20
                        || op==2 && (kdo.length==49 || kdo.length==52) || object==4 && (op==10 || op==6 || op==2 && kdo.length==54);
                if(object==18 && op==5 && kdo.length>=24 && !List.of(6,8,9).contains(kdo[23]&255))known=false;
                if(known)continue;
                System.out.println("UNKNOWN|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+object+"|"+op+"|"+kdo.length);
                System.out.println("KDO|"+HexFormat.of().formatHex(kdo));
                for(var f:r.vectors())if(f.opcodeMajor()==11)System.out.println("FORWARD|"+f.opcodeMinor()+"|"+f.fields().size()+"|"+f.fields().get(1).length);
            }
        });
    }
}
