import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
public class LobHistoricalFieldsProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);long heap=Long.parseLong(args[1]);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw)));
        var capture=new CaptureJournal().readRawClosed(raw,sha);
        for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.vectors().stream().noneMatch(v->v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=4
                    && Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(v.fields().get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt())==heap))continue;
            for(var v:record.vectors())if(v.opcodeMajor()==10 || v.opcodeMajor()==11 || v.opcodeMajor()==13 || v.opcodeMajor()==14 || v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                System.out.println("RBA|"+record.block()+"|"+record.offset()+"|SCN|"+record.scn()+"|OP|"+v.opcodeMajor()+"."+v.opcodeMinor());
                for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+(i+1)+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
        }
    }
}
