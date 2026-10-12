import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Independent JVM producer restore; no target, ACK, or deletion authority. */
public class LobProducerRestartProbe {
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),state=Path.of(args[2]);String baselineSha=sha(Files.readAllBytes(baseline));
        if(args[0].equals("enroll")) {
            var captures=new ArrayList<CaptureJournal.Verified>();long leaf=0;var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);
            for(int i=3;i<args.length;i++) {
                Path file=Path.of(args[i]);var capture=new CaptureJournal().readRawClosed(file,sha(Files.readAllBytes(file)));captures.add(capture);
                for(var r:new RedoRecords().readCaptured(capture))for(var v:r.vectors())if(v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject())leaf=v.dba();
            }
            var enrolled=HistoricalRecovery.committedPrefixCaptured(captures,baseline,baselineSha,leaf);
            byte[] bytes=enrolled.state().checkpoint();Files.write(state,bytes,StandardOpenOption.CREATE_NEW);
            String proof=leaf+"\n"+sha(bytes)+"\n"+enrolled.completed().sha256()+"\n"+enrolled.completed().events().size()+"\n";
            Files.writeString(Path.of(state+".proof"),proof,StandardOpenOption.CREATE_NEW);System.out.print(proof);
        } else {
            var proof=Files.readAllLines(Path.of(state+".proof"));long leaf=Long.parseLong(proof.get(0));byte[] bytes=Files.readAllBytes(state);
            var captures=new ArrayList<CaptureJournal.Verified>();
            for(int i=3;i<args.length;i++){Path raw=Path.of(args[i]);captures.add(new CaptureJournal().readRawClosed(raw,sha(Files.readAllBytes(raw))));}
            var restored=HistoricalRecovery.extendPrefixCaptured(captures,baseline,baselineSha,leaf,bytes,proof.get(1));
            if(!restored.completed().sha256().equals(proof.get(2)) || restored.completed().events().size()!=Integer.parseInt(proof.get(3)) || !Arrays.equals(bytes,restored.state().checkpoint()))throw new AssertionError("RESTORE_CHANGED");
            System.out.println("INDEPENDENT_JVM|events="+restored.completed().events().size()+"|state_and_batch_equal=true|raw_inputs="+captures.size());
            if(args[0].equals("restore-only"))return;
            var cut=HistoricalRecovery.readCutCaptured(captures,baseline,baselineSha,leaf,bytes,proof.get(1));Path file=Path.of(state+".snapshot");Files.write(file,cut.baseline(),StandardOpenOption.CREATE_NEW);
            long cutLeaf=cut.leaf()>0?cut.leaf():leaf;
            var retained=captures.isEmpty()?List.<CaptureJournal.Verified>of():List.of(captures.get(captures.size()-1));
            if(!CapturedHistoricalHeapBatch.read(retained,file,cut.baselineSha256(),cutLeaf).events().isEmpty())throw new AssertionError("CUT_REPUBLISHED");
            System.out.println("READ_CUT|leaf="+cutLeaf+"|sha256="+cut.baselineSha256());
        }
    }
}
