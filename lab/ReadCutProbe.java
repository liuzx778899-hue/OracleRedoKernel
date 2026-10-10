import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Frozen bytes only; the probe grants no deletion or target publication. */
public class ReadCutProbe {
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    public static void main(String[] args)throws Exception {
        Path old=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        Path baseline=old.resolve("snapshots/102185707342393344.snapshot");
        var raw=old.resolve("raw-ready/raw-members/oracle-online-normal");
        var reader=new CaptureJournal();var captures=new ArrayList<CaptureJournal.Verified>();
        for(String digest:List.of("f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820","3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5"))
            captures.add(reader.readRawClosed(raw.resolve(digest+".raw"),digest));
        Path producer=old.resolve("prefix-state/producer/102191771752329216-3f24a4f1c41b67b982eca9e4a4ca0ed3bc43e5f354c3704938460e02b98153c5.state");
        byte[] state=Files.readAllBytes(producer);String baselineSha=sha(Files.readAllBytes(baseline));
        var cut=HistoricalRecovery.readCutCaptured(captures,baseline,baselineSha,50359491,state,sha(state));
        Path file=out.resolve(cut.baselineSha256()+".snapshot");Files.write(file,cut.baseline(),StandardOpenOption.CREATE_NEW);
        var snapshot=FrozenHeapBaseline.read(file,cut.baselineSha256());
        if(snapshot.rows().size()!=20 || cut.requiredRawFirstSequence()!=101)throw new AssertionError("CUT_SCOPE");
        System.out.println("CUT|"+cut.scn()+"|"+cut.requiredRawFirstSequence()+"|"+cut.baselineSha256()+"|rows="+snapshot.rows().size());
        var result=CapturedHistoricalHeapBatch.read(List.of(captures.get(1)),file,cut.baselineSha256(),50359491);
        if(!result.events().isEmpty())throw new AssertionError("CUT_REPUBLISHED_HISTORY");
        System.out.println("RETAINED_ONLY|events=0|earlier_member_not_opened=true");
        var audit=HistoricalRecovery.auditState(captures.stream().map(CaptureJournal.Verified::source).toList(),baseline,baselineSha,50359491,state,sha(state));
        var original=HistoricalRecovery.readCaptured(captures,baseline,baselineSha,50359491,state,sha(state));
        if(!audit.sha256().equals(original.sha256()) || !audit.events().equals(original.events()))throw new AssertionError("AUDIT_STATE_CHANGED");
        System.out.println("STATE_ONLY_AUDIT|events="+audit.events().size()+"|digest_and_complete_images_equal=true");
        var changed=new ArrayList<>(captures.stream().map(CaptureJournal.Verified::source).toList());var s=changed.get(0);
        changed.set(0,new CaptureJournal.Source("0".repeat(64),s.journalBytes(),s.capacityBlocks(),s.blocks(),s.dbid(),s.activationId(),
                s.compatibility(),s.thread(),s.sequence(),s.resetlogsId(),s.resetlogsScn(),s.firstScn(),s.nextScn()));
        try {HistoricalRecovery.auditState(changed,baseline,baselineSha,50359491,state,sha(state));throw new AssertionError("FOREIGN_SOURCE_ACCEPTED");}
        catch(RedoException expected){System.out.println("SOURCE_REFUSED|"+expected.getMessage());}
        byte[] poison=state.clone();poison[poison.length/2]^=1;
        try {HistoricalRecovery.readCutCaptured(captures,baseline,baselineSha,50359491,poison,sha(state));throw new AssertionError("POISON_ACCEPTED");}
        catch(RedoException expected){System.out.println("POISON_REFUSED|"+expected.getMessage());}
    }
}
