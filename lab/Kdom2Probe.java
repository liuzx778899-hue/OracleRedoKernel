import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Physical forward/inverse patches only; selected dedicated object/DBA, never CDC events. */
public class Kdom2Probe {
    public static void main(String[] args) throws Exception {
        Path p=Path.of(args[0]);var h=new ArchivedRedo().scan(p);var rows=new ArrayList<String>();
        int[] forward={0}, inverse={0},xr={0};
        new RedoRecords().visit(p,h,r->{
            if(r.containerUid()!=677737149) return;
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=3)continue;
                HeapRowPatch.Patch patch;String side;
                if(v.opcodeMajor()==11 && v.dba()==0x03000095 && (v.opcodeMinor()==2 || v.opcodeMinor()==5)) {
                    patch=new HeapRowPatch().decode(v,2);side="FORWARD";forward[0]++;if(patch.xtype()==2)xr[0]++;
                } else if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=8 && word(v.fields().get(1),4)==73043) {
                    patch=UndoRowPatch.decode(v,2).inverse();side="INVERSE";inverse[0]++;
                } else continue;
                String cols=patch.columns().stream().map(c->c.state()+":"+HexFormat.of().formatHex(c.bytes())).collect(Collectors.joining(","));
                rows.add(r.block()+"|"+r.offset()+"|"+(i+1)+"|"+side+"|"+patch.kind()+"|"+patch.dba()+"|"+patch.slot()+"|"+patch.xtype()+"|"+cols);
            }
        });
        if(forward[0]!=181 || inverse[0]!=121 || xr[0]!=60)throw new RedoException("KDOM2_FIXTURE_COVERAGE_MISMATCH:"+forward[0]+":"+inverse[0]+":"+xr[0]);
        rows.forEach(System.out::println);System.err.println("physical_patches_only;cross_block_chain_not_accepted;cdc_events=0");
    }
    static long word(byte[] b,int o){long n=0;for(int j=0;j<4;j++)n|=(long)(b[o+j]&255)<<(8*j);return n;}
}
