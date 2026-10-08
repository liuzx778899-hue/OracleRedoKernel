import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.nio.*;
import java.util.*;

/** Unknown dictionary discovery layouts only; no scope exclusion or event authority. */
public class D2DictionaryUndoSurvey {
    static long u32(byte[] b,int at){return Integer.toUnsignedLong(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(at));}
    public static void main(String[] args)throws Exception {
        long after=Long.parseLong(args[0]);
        for(int i=1;i<args.length;i+=2) {
            var capture=new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]);
            for(var r:new RedoRecords().readCaptured(capture))if(r.scn()>after)
                for(var v:r.vectors())if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.containerId()==3 && v.fields().size()>3 && v.fields().get(1).length>=24) {
                    long object=u32(v.fields().get(1),0);
                    if(object!=4 && object!=18 && object!=21)continue;
                    var k=v.fields().get(3);int op=k.length>10?k[10]&255:-1;
                    boolean known=op==5&&(k.length==29||k.length==30) || (op==3||op==4)&&k.length==20 || op==2&&(k.length==49||k.length==52);
                    if(!known) {
                        System.out.println("UNKNOWN|"+capture.source().sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|object="+object+"|kdo="+op+"|bytes="+k.length);
                        for(int f=0;f<v.fields().size();f++)System.out.println("FIELD|"+f+"|"+HexFormat.of().formatHex(v.fields().get(f)));
                    }
                }
        }
    }
}
