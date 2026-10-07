import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Same autonomous physical closure from verified capture bytes, no events. */
public class CapturedDictionarySplitProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=2;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var splits=ArchivedDictionaryIndexSplit.readCaptured(captures,Path.of(args[0]),args[1]);var out=new ArrayList<String>();
        for(var c:splits)out.add("SPLIT|"+c.identity()+"|original="+c.original().size()+"|allocation="+c.allocationPosition()+"|end="+c.endPosition()+"|before="+c.before().rows().size()+"|new="+c.newLeaf().rows().size()+"|old="+c.oldLeaf().rows().size()+"|oldPrev="+c.oldLeaf().previousLink()+"|slot="+c.pendingSlot()+"|pending="+c.oldLeaf().rows().get(c.pendingSlot()));
        out.forEach(System.out::println);System.err.println("DIAGNOSTIC_ONLY|autonomous_splits="+splits.size()+"|events=0");
    }
}
