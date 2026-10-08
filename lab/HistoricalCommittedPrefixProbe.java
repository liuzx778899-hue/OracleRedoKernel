import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen 90/91/92 correctness: pending graphs never leak rows; closed projection stays identical. */
public class HistoricalCommittedPrefixProbe {
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[0]);String sha=args[1];var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i+=2) {
            captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
            var receipt=HistoricalRecovery.committedPrefixCaptured(captures,baseline,sha,50359491);
            var prefix=HistoricalRecovery.prefixCaptured(captures,baseline,sha,50359491);
            if(!Arrays.equals(receipt.state().checkpoint(),prefix.checkpoint()))throw new AssertionError("prefix state changed");
            if(captures.size()==1) {
                if(receipt.state().pendingTransactions()==0 || !receipt.completed().events().isEmpty())
                    throw new AssertionError("pending rows leaked");
            } else {
                var closed=CapturedHistoricalHeapBatch.read(captures,baseline,sha,50359491);
                if(receipt.state().pendingTransactions()!=0 || !closed.events().equals(receipt.completed().events())
                        || !closed.sha256().equals(receipt.completed().sha256()) || closed.events().size()!=(captures.size()==2?40:42))
                    throw new AssertionError("closed complete events changed");
            }
            System.out.println("PREFIX|"+captures.size()+"|"+receipt.state().pendingTransactions()+"|"+receipt.completed().events().size()+"|"+receipt.completed().sha256());
        }
    }
}
