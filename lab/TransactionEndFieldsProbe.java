import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
public class TransactionEndFieldsProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);var hex=HexFormat.of();
        var capture=new CaptureJournal().readRawClosed(raw,hex.formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw))));
        for(var r:new RedoRecords().readCaptured(capture))for(var v:r.vectors())if(v.opcodeMajor()==5 && v.opcodeMinor()==4) {
            try {TransactionEnd.decode(v);}catch(RedoException e) {
                System.out.println("RBA|"+r.block()+"|"+r.offset()+"|SCN|"+r.scn()+"|"+e.getMessage());
                for(var f:v.fields())System.out.println(hex.formatHex(f));
            }
        }
    }
}
