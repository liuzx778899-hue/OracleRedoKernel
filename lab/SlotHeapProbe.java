import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

/** Frozen 73061 pair/short-Z research. No logical row or CDC publication. */
public class SlotHeapProbe {
    public static void main(String[] args) throws Exception {
        boolean fields=args[0].equals("fields");if (!fields && !args[0].equals("pairs")) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);var output=new ArrayList<String>();int[] count={0};
        new RedoRecords().visit(path,header,r -> {
            if (r.containerUid()!=677737149) return;
            if (fields) {
                if (r.block()!=225422 || r.offset()!=240) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (v.containerId()!=3) continue;
                    var line=new StringBuilder(header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba());
                    for (byte[] b:v.fields()) line.append('|').append(HexFormat.of().formatHex(b));output.add(line.toString());
                }
                return;
            }
            var inverses=new ArrayList<UndoRowPatch>();
            for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1
                    && v.fields().size()>=2 && TransactionLifecycleProbe.word(v.fields().get(1),4)==73061) {
                inverses.add(UndoRowPatch.decode(v,2));count[0]++;
            }
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (!HeapCompensationProbe.heap(v) || inverses.stream().noneMatch(u -> u.inverse().dba()==v.dba())) continue;
                var p=new HeapRowPatch().decode(v,2);if (p.xtype()!=1) continue;
                var pair=HeapUndoPair.bind(r,v,2);var h=pair.undo().identity();
                if (h.dataObjectId()!=73061) throw new RedoException("SLOT_HEAP_OBJECT_MISMATCH");
                output.add(String.format("%d|%d|%d|%s|%d|%d|%d|%d|%d|%d|%d|%d|%s|%s",r.block(),r.offset(),i+1,
                        p.kind(),p.dba(),p.slot(),h.xid().usn(),h.xid().slot(),h.xid().sequence(),h.uba().dba(),h.uba().sequence(),h.uba().record(),
                        HeapUndoPairProbe.cols(p),HeapUndoPairProbe.cols(pair.undo().inverse())));
            }
        });
        if (!fields && (output.size()!=4 || count[0]!=4)) throw new RedoException("SLOT_HEAP_PAIR_COVERAGE_MISMATCH");
        output.forEach(System.out::println);System.err.println("frozen_heap_outputs="+output.size()+";cdc_events=0");
    }
}
