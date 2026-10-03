import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

/** Dedicated two-column fixtures. Object filter and row locations come from raw undo, not dump. */
public class HeapMultiProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);long object=Long.parseLong(args[1]);int inserts=Integer.parseInt(args[2]);
        var header=new ArchivedRedo().scan(p);var rows=new ArrayList<String>();var locations=new HashSet<Long>();
        int[] forward={0},inverse={0},xr={0};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149)return;
            for(var v:r.vectors())if(target(v,object))locations.add(UndoRowPatch.decode(v,2).inverse().dba());
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);HeapRowPatch.Patch patch;String side;
                if(target(v,object)){patch=UndoRowPatch.decode(v,2).inverse();side="INVERSE";inverse[0]++;}
                else if(v.containerId()==3 && v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==5) && locations.contains(v.dba())) {
                    patch=new HeapRowPatch().decode(v,2);side="FORWARD";forward[0]++;if(patch.xtype()==2)xr[0]++;
                } else continue;
                rows.add(r.block()+"|"+r.offset()+"|"+(i+1)+"|"+side+"|"+patch.kind()+"|"+patch.dba()+"|"+patch.slot()+"|"+patch.xtype()+"|"+HeapUndoPairProbe.cols(patch));
            }
        });
        if(forward[0]!=inserts+180 || inverse[0]!=inserts+120 || xr[0]!=60)throw new RedoException("MULTI_PATCH_COVERAGE_MISMATCH:"+forward[0]+":"+inverse[0]+":"+xr[0]);
        rows.forEach(System.out::println);System.err.println("raw_physical_patches_only;cdc_events=0");
    }
    static boolean target(RedoRecords.Vector v,long object){return v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=8 && UndoAdvanceProbe.word(v.fields().get(1),4)==object;}
}
