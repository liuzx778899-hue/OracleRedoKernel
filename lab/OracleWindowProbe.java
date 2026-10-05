import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Real retained capture scopes; no database or target writes. */
public class OracleWindowProbe {
    public static void main(String[] args) throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for (int i=4;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var full=CapturedHeapBatch.read(inputs,Path.of(args[0]),args[1],0x0300019b);
        var prefix=CapturedHeapBatch.read(inputs.subList(0,2),Path.of(args[0]),args[1],0x0300019b);
        if (full.leaf()!=0x0300019b || prefix.leaf()!=full.leaf()) throw new AssertionError("LEAF_NOT_RETAINED");
        var a=OracleConsumptionWindow.from(full);var b=OracleConsumptionWindow.from(prefix);
        if (a.relativeTo(b)!=OracleConsumptionWindow.Relationship.EXTENDS_PREFIX
                || b.relativeTo(a)!=OracleConsumptionWindow.Relationship.COVERED_PREFIX
                || a.relativeTo(a)!=OracleConsumptionWindow.Relationship.SAME_RANGE) throw new AssertionError("WINDOW_RELATIONSHIP");
        var drift=OracleConsumptionWindow.from(CapturedHeapBatch.read(inputs,Path.of(args[2]),args[3],0x0300019b));
        try {a.relativeTo(drift);throw new AssertionError("BASELINE_DRIFT_NOT_REFUSED");}
        catch (IllegalArgumentException e) {if (!e.getMessage().equals("ORACLE_WINDOW_SCOPE_CHANGED")) throw e;}
        try {a.captures().clear();throw new AssertionError("MUTABLE_CAPTURE_SCOPE");}
        catch (UnsupportedOperationException expected) {}
        System.out.println("FULL|"+a.batchSha256()+"|"+a.position().closedSequence()+"|"+a.position().closedBlocks()+"|"+a.position().endExclusiveScn()+"|"+a.leaf());
        System.out.println("PREFIX|"+b.batchSha256()+"|"+b.position().closedSequence()+"|"+b.position().closedBlocks()+"|"+b.position().endExclusiveScn()+"|"+b.leaf());
        System.out.println("RELATIONSHIPS|EXTENDS_PREFIX|COVERED_PREFIX|SAME_RANGE");
        System.out.println("BASELINE_DRIFT|ORACLE_WINDOW_SCOPE_CHANGED");
    }
}
