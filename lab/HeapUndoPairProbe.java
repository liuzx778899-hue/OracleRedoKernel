import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Selected target inverse records identify candidate row locations; output only after all pairs pass. */
public class HeapUndoPairProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); var header=new ArchivedRedo().scan(path);
        var output=new ArrayList<String>();
        int[] undoCount={0};
        new RedoRecords().visit(path,header,r -> {
            var targets=new ArrayList<UndoRowPatch>();
            for (var v:r.vectors()) if (TransactionLifecycleProbe.targetUndo(v)) {
                targets.add(UndoRowPatch.decode(v,4)); undoCount[0]++;
            }
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.containerId()!=3 || v.opcodeMajor()!=11 || (v.opcodeMinor()!=2 && v.opcodeMinor()!=3 && v.opcodeMinor()!=5)) continue;
                if (targets.stream().noneMatch(u -> u.inverse().dba()==v.dba())) continue;
                var p=new HeapRowPatch().decode(v,4);
                if (p.xtype()!=1) continue;
                var pair=HeapUndoPair.bind(r,v,4); var h=pair.undo().identity();
                if (h.dataObjectId()!=73024) throw new RedoException("UNEXPECTED_PAIRED_OBJECT");
                output.add(String.format("%d|%d|%d|%s|%d|%d|%d|%d|%d|%d|%d|%d|%s|%s",r.block(),r.offset(),i+1,
                        p.kind(),p.dba(),p.slot(),h.xid().usn(),h.xid().slot(),h.xid().sequence(),h.uba().dba(),h.uba().sequence(),h.uba().record(),
                        cols(p),cols(pair.undo().inverse())));
            }
        });
        if (output.isEmpty() || output.size()!=undoCount[0]) throw new RedoException("UNPAIRED_SELECTED_UNDO_RECORDS");
        for (String line:output) System.out.println(line);
        System.err.println("bounded_exact_row_undo_pairs_only;cdc_events=0");
    }
    static String cols(HeapRowPatch.Patch p) {
        return p.columns().stream().map(c -> c.state()+":"+HexFormat.of().formatHex(c.bytes())).collect(Collectors.joining(","));
    }
}
