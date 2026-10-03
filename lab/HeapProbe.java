import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Research harness: raw archive only; frozen dictionary column count is an explicit input. */
public class HeapProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);
        long dba=Long.decode(args[1]);
        int columns=Integer.parseInt(args[2]);
        var header=new ArchivedRedo().scan(path);
        var decoder=new HeapRowPatch();
        new RedoRecords().visit(path,header,r -> {
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.dba() != dba || v.opcodeMajor() != 11) continue;
                if (v.opcodeMinor() != 2 && v.opcodeMinor() != 3 && v.opcodeMinor() != 5) continue;
                var p=decoder.decode(v,columns);
                System.out.printf("%d|%d|%d|%s|%d|%d|%s|%s|%s%n",r.block(),r.offset(),i+1,p.kind(),p.slot(),p.xtype(),p.xid(),p.uba(),
                        p.columns().stream().map(c -> c.state()+":"+HexFormat.of().formatHex(c.bytes())).collect(Collectors.joining(",")));
            }
        });
        System.err.println("research_heap_patches_only;cdc_events=0");
    }
}
