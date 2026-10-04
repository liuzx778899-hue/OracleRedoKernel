import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Fixed two-column restore fixture heap pairs, buffered and counted independently from index graph output. */
public class RestoreHeapPairProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);var h=new ArchivedRedo().scan(path);var output=new ArrayList<String>();int[] count={0};
        new RedoRecords().visit(path,h,r -> {
            if (r.containerUid()!=677737149) return;
            var inverses=new ArrayList<UndoRowPatch>();
            for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1
                    && v.fields().size()>=2 && TransactionLifecycleProbe.word(v.fields().get(1),4)==73059) {
                inverses.add(UndoRowPatch.decode(v,2));count[0]++;
            }
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (!HeapCompensationProbe.heap(v) || inverses.stream().noneMatch(u -> u.inverse().dba()==v.dba())) continue;
                var p=new HeapRowPatch().decode(v,2);if (p.xtype()!=1) continue;
                var pair=HeapUndoPair.bind(r,v,2);var u=pair.undo().identity();
                if (u.dataObjectId()!=73059) throw new RedoException("RESTORE_HEAP_OBJECT_MISMATCH");
                output.add(String.format("%d|%d|%d|%s|%d|%d|%d|%d|%d|%d|%d|%d|%s|%s",r.block(),r.offset(),i+1,
                        p.kind(),p.dba(),p.slot(),u.xid().usn(),u.xid().slot(),u.xid().sequence(),u.uba().dba(),u.uba().sequence(),u.uba().record(),
                        HeapUndoPairProbe.cols(p),HeapUndoPairProbe.cols(pair.undo().inverse())));
            }
        });
        if (output.size()!=9 || count[0]!=9) throw new RedoException("RESTORE_HEAP_PAIR_COVERAGE_MISMATCH");
        output.forEach(System.out::println);System.err.println("restore_fixture_heap_pairs=9;cdc_events=0");
    }
}
