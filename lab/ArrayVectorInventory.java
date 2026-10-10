import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Raw byte inventory for the owned array fixture; never an event input. */
public class ArrayVectorInventory {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);var sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        var capture=new CaptureJournal().readRawClosed(path,sha);
        long object=Long.parseLong(args[1]);
        for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
            if(vector.objectId()==object && vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.fields().size()==5) {
                for(var v:record.vectors()) {
                    System.out.println("VECTOR|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|object="+v.objectId()+"|dba="+v.dba());
                    for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                }
            }
    }
}
