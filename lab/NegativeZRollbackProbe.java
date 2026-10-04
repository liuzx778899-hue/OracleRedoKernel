import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Single mutations of the frozen seq21 vectors; no raw file mutation or CDC output. */
public class NegativeZRollbackProbe {
    public static void main(String[] args) throws Exception {
        String mode=args[0];
        if (!List.of("index-version","index-tail","index-restore","index-forward-mode","index-extra",
                "heap-xa","heap-block","heap-opcode","heap-extra","heap-short-ktb","heap-short-kdo").contains(mode)) throw new IllegalArgumentException("UNKNOWN_MODE");
        boolean index=mode.startsWith("index-");var vectors=InterveningPurgeProfileProbe.read(Path.of(args[1]));var v=vectors[index ? 0 : 1];
        var fields=new ArrayList<>(v.fields());int opcode=v.opcodeMinor();
        switch (mode) {
            case "index-version" -> fields.get(0)[1]^=1;
            case "index-tail" -> fields.get(0)[7]=1;
            case "index-restore" -> opcode=5;
            case "index-forward-mode" -> fields.get(1)[1]=0;
            case "index-extra","heap-extra" -> fields.add(new byte[]{1});
            case "heap-xa" -> fields.get(1)[11]=1;
            case "heap-block" -> fields.get(1)[0]^=1;
            case "heap-opcode" -> fields.get(1)[10]=2;
            case "heap-short-ktb" -> fields.set(0,Arrays.copyOf(fields.get(0),7));
            case "heap-short-kdo" -> fields.set(1,Arrays.copyOf(fields.get(1),19));
        }
        var changed=new RedoRecords.Vector(v.opcodeMajor(),opcode,v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),fields);
        if (index) IndexCompensation.decodeRollback(changed);else new HeapRowPatch().decode(changed,2);
        throw new AssertionError("DERIVED_Z_ROLLBACK_SHOULD_HAVE_BEEN_REFUSED");
    }
}
