import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Single mutations of the frozen seq24 RESTORE; no database actions or CDC events. */
public class NegativeZRestoreProbe {
    public static void main(String[] args) throws Exception {
        int mode=Integer.parseInt(args[0]);if (mode<0 || mode>12) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        if (header.sequence()!=24 || !header.sha256().equals("def82f0b1009a60142150e90726bfa8482d55c0a1a8a617603e4e3cf58395b09")) throw new RedoException("RESTORE_SOURCE_MISMATCH");
        var selected=new ArrayList<RedoRecords.Vector>();
        new RedoRecords().visit(path,header,r->{if (r.block()==182 && r.offset()==456) selected.add(r.vectors().get(0));});
        if (selected.size()!=1) throw new RedoException("RESTORE_VECTOR_NOT_UNIQUE");
        var v=selected.get(0);var fields=new ArrayList<>(v.fields());int opcode=v.opcodeMinor();
        if (mode<8) fields.get(0)[mode]^=1;
        else if (mode==8) opcode=3;
        else if (mode<11) fields.get(1)[mode-9]=0;
        else if (mode==11) fields.add(new byte[0]);
        else fields.set(0,Arrays.copyOf(fields.get(0),7));
        IndexCompensation.decodeRollback(new RedoRecords.Vector(v.opcodeMajor(),opcode,v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),fields));
        throw new AssertionError("DERIVED_Z_RESTORE_SHOULD_HAVE_BEEN_REFUSED");
    }
}
