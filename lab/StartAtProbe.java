import com.streamhouse.oracle.*;
import java.nio.file.Path;

/** Inspect one frozen start and its exact same-record begin UBA; not complete transaction acceptance. */
public class StartAtProbe {
    public static void main(String[] args) throws Exception {
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);var lines=new java.util.ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if (r.block()!=Long.parseLong(args[1]) || r.offset()!=Integer.parseInt(args[2])) return;
            for (var v:r.vectors()) if (v.opcodeMajor()==5 && v.opcodeMinor()==2) {
                var start=TransactionStart.decode(v);lines.add("START|"+start.bindBegin(r)+"|"+start.uba()+"|"+start.flags()+"|"+start.segmentDba());
            }
        });
        if (lines.size()!=1) throw new IllegalStateException("START_SELECTION_NOT_UNIQUE");
        lines.forEach(System.out::println);
    }
}
