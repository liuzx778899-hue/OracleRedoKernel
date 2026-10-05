import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Archive-owned originals plus exact row associations; no parent or historical schema authority. */
public class ClusterPairsProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);Path path=Path.of(args[2]);String[] x=args[3].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(path),snapshot.source().containerUid(),snapshot.source().containerId(),xid);
        var header=inventory.sources().get(0);snapshot.requireSource(header);var out=new ArrayList<String>();var selected=new HashSet<ArchivedUndoInventory.Position>();
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var entry:inventory.entries()) {
                var at=entry.position();if(r.block()!=at.block()||r.offset()!=at.offset())continue;
                var h=entry.identity();var role=snapshot.dictionaryAnchors().stream().map(FrozenHeapBaseline.DictionaryAnchor::dictionary)
                        .filter(d->d.logicalObject()==h.objectId()&&d.dataObject()==h.dataObjectId()&&List.of("TAB$","COL$").contains(d.name())).findFirst();
                if(role.isEmpty())continue;
                int pairs=0,tab=role.get().name().equals("TAB$")?1:5,columns=tab==1?44:36;
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId()||v.opcodeMajor()!=11)continue;
                    var p=ClusterUndoPair.bind(r,v,role.get(),tab,columns);
                    if(!p.identity().equals(h))throw new RedoException("RESEARCH_CLUSTER_PAIR_FULL_IDENTITY_MISMATCH");
                    var f=p.forward().row();var b=p.inverse().row();pairs++;
                    out.add("PAIR|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+at.vector()+"|"+(i+1)+"|"+role.get().name()+"|"+p.identity().xid()+"|"+f.uba()+"|"+f.kind()+"|"+b.kind()+"|"+f.dba()+"|"+f.slot()+"|"+tab);
                }
                if(pairs!=1)throw new RedoException("RESEARCH_CLUSTER_PAIR_COVERAGE_MISMATCH");selected.add(at);
            }
        });
        long expected=inventory.entries().stream().filter(e->snapshot.dictionaryAnchors().stream().anyMatch(a->
                a.dictionary().logicalObject()==e.identity().objectId()&&a.dictionary().dataObject()==e.identity().dataObjectId()
                        &&List.of("TAB$","COL$").contains(a.dictionary().name()))).count();
        if(selected.size()!=expected)throw new RedoException("RESEARCH_CLUSTER_ORIGINAL_OMISSION");
        out.forEach(System.out::println);System.err.println("same_record_cluster_pairs_only;parent_object_unverified;events=0");
    }
}
