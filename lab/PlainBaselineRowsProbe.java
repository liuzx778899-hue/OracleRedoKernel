import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Even a verified file converted to the legacy row-only Baseline grants no nullable-tail schema authority. */
public class PlainBaselineRowsProbe {
    public static void main(String[] args) throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var paths=Arrays.stream(args).skip(3).map(Path::of).toList();
        var result=ArchivedHeapRows.read(paths,snapshot.baselineForEqualObjectScope(),snapshot.index().dataObject(),Long.decode(args[2]));
        System.out.println("changes="+result.changes().size());
    }
}
