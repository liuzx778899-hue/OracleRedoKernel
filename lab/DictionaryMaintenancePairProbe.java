import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Typed raw pair diagnostics only; does not emit events or assert complete split coverage. */
public class DictionaryMaintenancePairProbe {
    static long word(byte[] b,int at){return (b[at]&255L)|((b[at+1]&255L)<<8)|((b[at+2]&255L)<<16)|((b[at+3]&255L)<<24);}
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);var scope=snapshot.dictionaryBaseline().indexes().stream().filter(x->x.role().name().equals("I_COL3")).findFirst().orElseThrow();
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);snapshot.requireSource(header);
        var out=new ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
            boolean selected=false;
            for(var u:r.vectors())if(u.opcodeMajor()==5 && u.opcodeMinor()==1 && u.containerId()==snapshot.source().containerId()) {
                if(u.fields().size()<2 || u.fields().get(1).length<8)throw new RedoException("RESEARCH_UNREADABLE_INDEX_SCOPE");
                if(word(u.fields().get(1),0)==scope.role().logicalObject()) {UndoHeader.decode(u);selected=true;}
            }
            if(!selected)return;
            for(var v:r.vectors())if(v.opcodeMajor()==10 && List.of(6,11,15).contains(v.opcodeMinor()))
                out.add(header.sequence()+":"+r.block()+":"+r.offset()+"|"+DictionaryIndexMaintenancePair.bind(r,v,scope.role(),scope.indexDba()));
        });
        if(out.size()!=3)throw new RedoException("RESEARCH_MAINTENANCE_PAIR_COUNT");
        out.forEach(System.out::println);System.err.println("DIAGNOSTIC_ONLY|typed_pairs=3|complete_split=false|events=0");
    }
}
