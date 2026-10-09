import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen recovery through the same unified API used by product checkpoint control. */
public class ProducerRecoveryProbe {
    public static void main(String[] args)throws Exception {
        Path state=Path.of(args[1]),baseline=Path.of(args[3]);long leaf=Long.parseLong(args[5]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=6;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        if(args[0].equals("export")) {
            var prefix=HistoricalRecovery.prefixCaptured(captures,baseline,args[4],leaf);
            Files.write(state,prefix.checkpoint(),StandardOpenOption.CREATE_NEW);
            for(String line:new String(prefix.checkpoint(),java.nio.charset.StandardCharsets.UTF_8).split("\n"))if(line.startsWith("K|")) {
                var in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(line.substring(2))));
                in.readUTF();System.out.println("DICTIONARY_COMMITS="+in.readInt());
            }
            System.out.println("PENDING="+prefix.pendingTransactions());System.out.println("COMPLETED="+prefix.completedTransactions());
            return;
        }
        if(args[0].equals("verify")) {
            var prefix=HistoricalRecovery.verifyPrefixCaptured(captures,baseline,args[4],leaf,Files.readAllBytes(state),args[2]);
            System.out.println("PENDING="+prefix.pendingTransactions());System.out.println("COMPLETED="+prefix.completedTransactions());return;
        }
        if(args[0].equals("verify-pending")) {
            byte[] input=Files.readAllBytes(state);
            var advanced=HistoricalRecovery.extendPrefixCaptured(captures,baseline,args[4],leaf,input,args[2]);
            var reference=HistoricalRecovery.committedPrefixCaptured(captures,baseline,args[4],leaf);
            if(!Arrays.equals(input,advanced.state().checkpoint()) || !Arrays.equals(input,reference.state().checkpoint())
                    || !advanced.completed().events().equals(reference.completed().events()) || !advanced.completed().sha256().equals(reference.completed().sha256()))
                throw new AssertionError("PENDING_PREFIX_STATE_OR_COMPLETE_EVENTS_CHANGED");
            System.out.println("STATE_UNCHANGED=true");System.out.println("PENDING="+advanced.state().pendingTransactions());
            System.out.println("EVENTS="+advanced.completed().events().size());System.out.println("STATE_SHA256="+sha(input));return;
        }
        CapturedHistoricalHeapBatch restored;
        if(args[0].equals("extend") || args[0].equals("extend-verify")) {
            var advanced=HistoricalRecovery.extendPrefixCaptured(captures,baseline,args[4],leaf,Files.readAllBytes(state),args[2]);
            var fresh=HistoricalRecovery.prefixCaptured(captures,baseline,args[4],leaf);
            if(!Arrays.equals(advanced.state().checkpoint(),fresh.checkpoint()))throw new AssertionError("EXTENDED_STATE_NOT_FRESH_EQUIVALENT");
            var again=HistoricalRecovery.extendPrefixCaptured(captures,baseline,args[4],leaf,advanced.state().checkpoint(),sha(advanced.state().checkpoint()));
            if(!Arrays.equals(again.state().checkpoint(),advanced.state().checkpoint()) || !again.completed().events().equals(advanced.completed().events()))
                throw new AssertionError("REPEATED_EXTENSION_CHANGED_STATE_OR_EVENTS");
            if(args[0].equals("extend"))Files.write(Path.of(args[1]+".extended"),advanced.state().checkpoint(),StandardOpenOption.CREATE_NEW);
            System.out.println("EXTENDED_SHA256="+sha(advanced.state().checkpoint()));
            System.out.println("PENDING="+advanced.state().pendingTransactions());
            System.out.println("FRESH_STATE_EQUAL=true");System.out.println("REPEAT_UNCHANGED=true");restored=advanced.completed();
        } else restored=HistoricalRecovery.readCaptured(captures,baseline,args[4],leaf,Files.readAllBytes(state),args[2]);
        var reference=CapturedHistoricalHeapBatch.read(captures,baseline,args[4],leaf);
        if(!restored.events().equals(reference.events()) || !restored.sha256().equals(reference.sha256()))throw new AssertionError("COMPLETE_EVENT_OR_BATCH_CHANGED");
        for(var event:restored.events())System.out.println("EVENT="+event.row().eventId()+"|"+event.row().payloadSha256());
        System.out.println("EVENTS="+restored.events().size());System.out.println("BATCH_SHA256="+restored.sha256());
    }
    static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
