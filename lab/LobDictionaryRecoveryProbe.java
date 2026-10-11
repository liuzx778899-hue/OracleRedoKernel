import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Separate JVM source-bound dictionary restoration; retained raw LOB origins remain required. */
public class LobDictionaryRecoveryProbe {
    static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),state=Path.of(args[3]);String baselineSha=args[2];
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);var captures=new ArrayList<CaptureJournal.Verified>();long leaf=0;
        for(int i=4;i<args.length;i++) {
            Path raw=Path.of(args[i]);var capture=new CaptureJournal().readRawClosed(raw,sha(Files.readAllBytes(raw)));captures.add(capture);
            for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
                if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.objectId()==snapshot.index().dataObject()) {
                    if(leaf!=0 && leaf!=vector.dba())throw new AssertionError("LOB_MULTIPLE_INDEX_LEAVES");leaf=vector.dba();
                }
        }
        if(args[0].equals("enroll")) {
            byte[] checkpoint=ArchivedDictionaryHistory.readCaptured(captures,baseline,baselineSha).checkpoint();
            Files.write(state,checkpoint,StandardOpenOption.CREATE_NEW);System.out.println("CHECKPOINT_SHA256="+sha(checkpoint));
        }else {
            byte[] checkpoint=Files.readAllBytes(state);
            var restored=CapturedHistoricalHeapBatch.readRestored(captures,baseline,baselineSha,leaf,checkpoint,sha(checkpoint));
            var direct=CapturedHistoricalHeapBatch.read(captures,baseline,baselineSha,leaf);
            if(!restored.events().equals(direct.events()) || !restored.sha256().equals(direct.sha256()))throw new AssertionError("LOB_RESTORE_CHANGED");
            System.out.println("EVENTS="+restored.events().size()+"\nRESTORED_EQUALS_DIRECT=true\nBATCH_SHA256="+restored.sha256());
            checkpoint[checkpoint.length-2]^=1;
            try {CapturedHistoricalHeapBatch.readRestored(captures,baseline,baselineSha,leaf,checkpoint,sha(checkpoint));throw new AssertionError("POISON_ACCEPTED");}
            catch(RedoException expected){System.out.println("POISON_REJECTED="+expected.getMessage());}
        }
    }
}
