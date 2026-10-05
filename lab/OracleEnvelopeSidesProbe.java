import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.spi.EventEnvelope;
import java.nio.file.*;
import java.util.*;

/** Actual frozen projection sides; absent image is null, SQL NULL is a present column. */
public class OracleEnvelopeSidesProbe {
    public static void main(String[] args)throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var projected=OracleBatchProjection.project(CapturedHeapBatch.read(inputs,Path.of(args[0]),args[1],Long.parseLong(args[2])));
        var text=new StringBuilder();
        for(var entry:projected.entries()) {
            var e=entry.envelope();boolean insert=e.operation()==EventEnvelope.Operation.INSERT,delete=e.operation()==EventEnvelope.Operation.DELETE;
            if((e.before()==null)!=insert || (e.after()==null)!=delete)throw new AssertionError("PRODUCT_IMAGE_SIDE_MISMATCH");
            long nulls=0;
            for(var map:Arrays.asList(e.before(),e.after()))if(map!=null) {
                if(map.size()!=projected.schema().columns().size())throw new AssertionError("PRESENT_IMAGE_COLUMN_COVERAGE");
                nulls+=map.values().stream().filter(Objects::isNull).count();
            }
            text.append("SIDE|").append(e.eventId()).append('|').append(entry.payloadSha256()).append('|').append(e.operation()).append('|').append(insert).append('|').append(delete).append('|').append(nulls).append('\n');
        }
        System.out.print(text);
    }
}
