import com.streamhouse.spi.CapDecoder;
import java.util.*;

/** Compile against the accepted old SPI; run against the new SPI to verify old constructor linkage. */
public class OracleLegacyDecodeResultProbe {
    public static void main(String[] args)throws Exception {
        var four=new CapDecoder.DecodeResult(Collections.emptyIterator(),2,"legacy-four",Map.of());
        var five=new CapDecoder.DecodeResult(Collections.emptyIterator(),2,"legacy-five",Map.of(),null);
        for(var result:List.of(four,five)) {
            if(result.rc()!=2 || result.events().hasNext()
                    || result.getClass().getMethod("oracleEvidence").invoke(result)!=null)
                throw new AssertionError("LEGACY_DECODE_RESULT_CHANGED");
        }
        System.out.println("LEGACY_CONSTRUCTORS_LINKED|4|5");
    }
}
