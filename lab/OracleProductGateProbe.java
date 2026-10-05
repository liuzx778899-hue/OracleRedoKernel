import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.spi.EventEnvelope;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;

/** Actual product gate source, frozen actual Oracle batch; no service/database writes. */
public class OracleProductGateProbe {
    public static void main(String[] args) throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=CapturedHeapBatch.read(inputs,Path.of(args[0]),args[1],Long.parseLong(args[2]));
        var projected=OracleBatchProjection.project(batch);
        var gate=Class.forName("com.streamhouse.pipeline.service.PublishGateService").getDeclaredMethod("checkEnvelope",EventEnvelope.class,List.class);gate.setAccessible(true);
        var reasons=new ArrayList<String>();
        try {
            for(var e:projected.entries())gate.invoke(null,e.envelope(),reasons);
        } catch(InvocationTargetException e) {
            System.err.println("product_gate_accumulated_reasons="+reasons+";batch_events="+projected.entries().size()+";target_writes=0;public_decoder_enabled=false");
            throw (Exception)e.getCause();
        }
        if(!reasons.isEmpty())throw new IllegalStateException("PRODUCT_GATE_REFUSED:"+reasons);
        if(projected.entries().isEmpty())throw new IllegalStateException("EMPTY_BATCH_DOES_NOT_EXERCISE_ENVELOPE_GATE");
        System.out.println("ENVELOPE_GATE_PASSED|"+projected.entries().size());
    }
}
