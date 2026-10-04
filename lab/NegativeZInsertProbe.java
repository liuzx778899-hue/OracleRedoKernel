import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One frozen real XR INSERT vector with a single derived refusal; exits before any CDC output. */
public class NegativeZInsertProbe {
    public static void main(String[] args) throws Exception {
        String mode=args[0];
        if (!List.of("xa","kdo-op","split-piece","missing-column","wrong-vector-op","block","ktb-version").contains(mode)) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        if (!header.sha256().equals("497d09a556faed6b96307f9a439413e08109d0920b75bbeacb909cccbd582d63")) throw new RedoException("FROZEN_Z_INSERT_SOURCE_MISMATCH");
        RedoRecords.Vector[] found={null};
        new RedoRecords().visit(path,header,r -> {
            if (r.block()==225422 && r.offset()==240 && r.containerUid()==677737149) found[0]=r.vectors().get(0);
        });
        var v=found[0];
        if (v==null || v.opcodeMajor()!=11 || v.opcodeMinor()!=2 || v.containerId()!=3 || v.dba()!=50331955
                || v.fields().size()!=4 || v.fields().get(0).length!=8 || v.fields().get(1).length!=49
                || v.fields().get(1)[11]!=2) throw new RedoException("FROZEN_Z_INSERT_PROFILE_MISMATCH");
        var fields=new ArrayList<>(v.fields());int opcode=2;
        switch (mode) {
            case "xa" -> fields.get(1)[11]=1;
            case "kdo-op" -> fields.get(1)[10]=5;
            case "split-piece" -> fields.get(1)[16]=0x20;
            case "missing-column" -> fields.remove(3);
            case "wrong-vector-op" -> opcode=5;
            case "block" -> fields.get(1)[0]^=1;
            case "ktb-version" -> fields.get(0)[1]^=1;
        }
        var derived=new RedoRecords.Vector(11,opcode,v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),fields);
        new HeapRowPatch().decode(derived,2);
        throw new AssertionError("DERIVED_Z_INSERT_SHOULD_HAVE_BEEN_REFUSED");
    }
}
