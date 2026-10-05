import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Complete physical SYS row-pair research from archive-owned original inventory; no historical publication. */
public class SysDictionaryRowPairsProbe {
    static void patch(List<String> out,String side,HeapRowPatch.Patch p) {
        out.add("PATCH|"+side+"|"+p.kind()+"|"+p.dba()+"|"+p.slot()+"|"+p.extent()+"|"+p.lockTo());
        for(int i=0;i<p.columns().size();i++) {var c=p.columns().get(i);out.add("CELL|"+side+"|"+i+"|"+c.state()+"|"+HexFormat.of().formatHex(c.bytes()));}
    }
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);Path archive=Path.of(args[2]);var x=args[3].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(archive),snapshot.source().containerUid(),snapshot.source().containerId(),xid);
        var header=inventory.sources().get(0);snapshot.requireSource(header);var out=new ArrayList<String>();var seen=new HashSet<ArchivedUndoInventory.Position>();
        new RedoRecords().visit(archive,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var e:inventory.entries()) {
                var at=e.position();if(r.block()!=at.block()||r.offset()!=at.offset())continue;
                var h=e.identity();var role=snapshot.dictionaryAnchors().stream().map(FrozenHeapBaseline.DictionaryAnchor::dictionary).filter(d->d.logicalObject()==h.objectId()).findFirst();
                if(role.isEmpty()) {
                    if(e.opcodeMajor()==5&&e.opcodeMinor()==7&&h.beginsTransaction())out.add("DISPOSITION|CONTROL|"+at);
                    else if(e.opcodeMajor()==10&&e.opcodeMinor()==22)out.add("DISPOSITION|INDEX|"+at);
                    else throw new RedoException("UNKNOWN_RESEARCH_SYS_ORIGINAL");
                    seen.add(at);continue;
                }
                if(role.get().dataObject()!=h.dataObjectId())throw new RedoException("SYS_ROW_GENERATION_MISMATCH");
                int pairs=0;
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId()||v.opcodeMajor()!=11)continue;
                    HeapRowPatch.Patch forward,inverse;UndoHeader identity;
                    if(role.get().name().equals("OBJ$")) {
                        var p=DictionaryObjectUndoPair.bind(r,v,role.get(),37).physical();forward=p.forward();inverse=p.undo().inverse();identity=p.undo().identity();
                    } else {
                        int tab=role.get().name().equals("TAB$")?1:5;var p=ClusterUndoPair.bind(r,v,role.get(),tab,tab==1?44:36);
                        forward=p.forward().row();inverse=p.inverse().row();identity=p.identity();
                    }
                    if(!identity.equals(h))throw new RedoException("SYS_ROW_PAIR_IDENTITY_MISMATCH");
                    pairs++;out.add("ROW|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+at.vector()+"|"+(i+1)+"|"+r.scn()+"|"+role.get().name()+"|"+xid+"|"+h.uba());
                    patch(out,"forward",forward);patch(out,"inverse",inverse);
                }
                if(pairs!=1)throw new RedoException("SYS_ROW_PAIR_COVERAGE_MISMATCH");seen.add(at);
            }
        });
        if(seen.size()!=inventory.entries().size())throw new RedoException("SYS_ORIGINAL_DISPOSITION_COVERAGE");
        out.forEach(System.out::println);System.err.println("physical_pair_research_only;index_and_control_dispositions_not_new_authority;events=0");
    }
}
