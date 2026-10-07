import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Autonomous frozen split diagnostics only, never business events. */
public class ArchivedDictionarySplitProbe {
    public static void main(String[] args)throws Exception {
        var archives=new ArrayList<Path>();for(int i=2;i<args.length;i++)archives.add(Path.of(args[i]));
        var splits=ArchivedDictionaryIndexSplit.read(archives,Path.of(args[0]),args[1]);var out=new ArrayList<String>();
        for(var c:splits)out.add("SPLIT|"+c.identity()+"|original="+c.original().size()+"|allocation="+c.allocationPosition()+"|end="+c.endPosition()+"|before="+c.before().rows().size()+"|new="+c.newLeaf().rows().size()+"|old="+c.oldLeaf().rows().size()+"|oldPrev="+c.oldLeaf().previousLink()+"|slot="+c.pendingSlot()+"|pending="+c.oldLeaf().rows().get(c.pendingSlot()));
        out.forEach(System.out::println);System.err.println("DIAGNOSTIC_ONLY|autonomous_splits="+splits.size()+"|events=0");
    }
}
