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
            System.out.println("PENDING="+prefix.pendingTransactions());System.out.println("COMPLETED="+prefix.completedTransactions());
            return;
        }
        var restored=HistoricalRecovery.readCaptured(captures,baseline,args[4],leaf,Files.readAllBytes(state),args[2]);
        var reference=CapturedHistoricalHeapBatch.read(captures,baseline,args[4],leaf);
        if(!restored.events().equals(reference.events()) || !restored.sha256().equals(reference.sha256()))throw new AssertionError("COMPLETE_EVENT_OR_BATCH_CHANGED");
        for(var event:restored.events())System.out.println("EVENT="+event.row().eventId()+"|"+event.row().payloadSha256());
        System.out.println("EVENTS="+restored.events().size());System.out.println("BATCH_SHA256="+restored.sha256());
    }
}
