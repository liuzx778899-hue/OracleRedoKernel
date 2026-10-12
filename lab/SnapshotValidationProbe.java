import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Validates an already acquired snapshot without reacquiring or changing its source. */
public class SnapshotValidationProbe {
    public static void main(String[] args)throws Exception {
        Path file=Path.of(args[0]);String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        var snapshot=FrozenHeapBaseline.read(file,digest);
        if(!snapshot.heap().name().equals(args[1]) || snapshot.columns().size()!=3 || snapshot.rows().size()!=1)throw new AssertionError("SNAPSHOT_SCOPE_CHANGED");
        System.out.println("VALIDATED|"+snapshot.scn()+"|"+digest+"|columns=3|rows=1");
    }
}
