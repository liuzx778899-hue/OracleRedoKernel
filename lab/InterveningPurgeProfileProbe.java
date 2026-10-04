import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

/** Frozen short-Z index PURGE and heap DRP, without transaction or event authorization. */
public class InterveningPurgeProfileProbe {
    public static void main(String[] args) throws Exception {
        String mode=args[0];
        if (!List.of("fields","index","heap").contains(mode)) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        if (!header.sha256().equals("f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80")) throw new RedoException("FROZEN_PURGE_SOURCE_MISMATCH");
        RedoRecords.Vector[] vectors=new RedoRecords.Vector[2];
        new RedoRecords().visit(path,header,r->{
            if (r.containerUid()!=677737149) return;
            if (r.block()==1286 && r.offset()==440) vectors[0]=r.vectors().get(0);
            if (r.block()==1287 && r.offset()==100) vectors[1]=r.vectors().get(0);
        });
        if (vectors[0]==null || vectors[1]==null || vectors[0].opcodeMajor()!=10 || vectors[0].opcodeMinor()!=3
                || vectors[0].dba()!=0x0300015bL || vectors[1].opcodeMajor()!=11 || vectors[1].opcodeMinor()!=3
                || vectors[1].dba()!=0x03000155L) throw new RedoException("FROZEN_PURGE_VECTOR_MISMATCH");
        if (!header.equals(new ArchivedRedo().scan(path))) throw new RedoException("FROZEN_PURGE_SOURCE_CHANGED");
        if (mode.equals("index")) { IndexCompensation.decodeRollback(vectors[0]);throw new AssertionError("INDEX_Z_PROFILE_EXPECTED_REFUSAL"); }
        if (mode.equals("heap")) { new HeapRowPatch().decode(vectors[1],2);throw new AssertionError("HEAP_Z_DELETE_EXPECTED_REFUSAL"); }
        for (int i=0;i<2;i++) {
            var v=vectors[i];var line=new StringBuilder(i==0 ? "21|1286|440|1" : "21|1287|100|1");
            line.append('|').append(v.opcodeMajor()).append('.').append(v.opcodeMinor()).append('|').append(v.dba());
            for (byte[] f:v.fields()) line.append('|').append(HexFormat.of().formatHex(f));
            System.out.println(line);
        }
    }
}
