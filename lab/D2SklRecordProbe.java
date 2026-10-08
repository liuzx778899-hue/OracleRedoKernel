import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Two frozen native-matched records only; no authority to exclude maintenance. */
public class D2SklRecordProbe {
    public static void main(String[] args)throws Exception {
        var capture=new CaptureJournal().readRawClosed(Path.of(args[0]),args[1]);
        var snapshot=args.length==4?FrozenHeapBaseline.read(Path.of(args[2]),args[3]):null;
        if(snapshot!=null)snapshot.requireSource(new ArchivedRedo().scan(Path.of(args[0])));
        int pairs=0;
        for(var r:new RedoRecords().readCaptured(capture))if(r.block()==51452 && r.offset()==324 || r.block()==51453 && r.offset()==100) {
            if(snapshot!=null) {
                if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())throw new IllegalStateException("SOURCE_OR_WATERMARK");
                var role=snapshot.dictionaryBaseline().layouts().get("COL$").role();
                var pair=DictionaryClusterLinkPair.bind(r,r.vectors().get(1),role);
                System.out.println("PAIR|"+r.block()+"|"+r.offset()+"|"+pair);pairs++;continue;
            }
            System.out.println("RECORD|"+r.block()+"|"+r.offset()+"|"+r.scn());
            for(var v:r.vectors()) {
                System.out.println("VECTOR|"+v.opcodeMajor()+"|"+v.opcodeMinor()+"|"+v.blockClass()+"|"+v.file()+"|"+v.dba()+"|"+v.scn()+"|"+v.sequence()+"|"+v.containerId());
                for(var f:v.fields())System.out.println("FIELD|"+HexFormat.of().formatHex(f));
            }
        }
        if(snapshot!=null) {
            if(pairs!=2)throw new IllegalStateException("MISSING_OBSERVED_PAIRS");
            System.out.println("PAIR_COUNT|2|events=0|closure_authorized=false|scope_exclusion_authorized=false");
        }
    }
}
