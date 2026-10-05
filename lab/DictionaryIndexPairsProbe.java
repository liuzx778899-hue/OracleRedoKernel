import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen research scope from independent ASOF SQL, not a published V3 acquisition or event authority. */
public class DictionaryIndexPairsProbe {
    record Scope(FrozenHeapBaseline.ObjectRole role,long dba) {}
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);Path path=Path.of(args[2]);String[] x=args[3].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));var scopes=new HashMap<Long,Scope>();
        for(String line:Files.readAllLines(Path.of(args[4])))if(line.startsWith("INDEX_SCOPE|")) {
            String[] p=line.split("\\|");long logical=Long.parseLong(p[1]),data=Long.parseLong(p[2]);
            var scope=new Scope(new FrozenHeapBaseline.ObjectRole("SYS",p[3],logical,data),(Long.parseLong(p[4])<<22)|Long.parseLong(p[5]));
            if(scopes.putIfAbsent(logical,scope)!=null)throw new RedoException("RESEARCH_DUPLICATE_INDEX_SCOPE");
        }
        if(scopes.size()!=3 || !scopes.values().stream().map(s->s.role().name()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("I_COL1","I_COL2","I_COL3")))
            throw new RedoException("RESEARCH_INDEX_SCOPE_INCOMPLETE");
        var inventory=ArchivedUndoInventory.read(List.of(path),snapshot.source().containerUid(),snapshot.source().containerId(),xid);
        var header=inventory.sources().get(0);snapshot.requireSource(header);var out=new ArrayList<String>();var selected=new HashSet<ArchivedUndoInventory.Position>();
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var e:inventory.entries()) {
                var at=e.position();if(r.block()!=at.block()||r.offset()!=at.offset()||e.opcodeMajor()!=10||e.opcodeMinor()!=22)continue;
                var scope=scopes.get(e.identity().objectId());if(scope==null)throw new RedoException("UNKNOWN_RESEARCH_DICTIONARY_INDEX_ORIGINAL");
                int count=0;
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId()||v.opcodeMajor()!=10)continue;
                    var p=DictionaryIndexUndoPair.bind(r,v,scope.role(),scope.dba(),"AL32UTF8");
                    if(!p.physical().undo().identity().equals(e.identity()))throw new RedoException("RESEARCH_DICTIONARY_INDEX_FULL_IDENTITY_MISMATCH");
                    var f=p.physical().forward();var k=p.key();count++;
                    out.add("INDEX|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+at.vector()+"|"+(i+1)+"|"+p.role().name()+"|"+e.identity().xid()+"|"+f.uba()+"|"+f.kind()+"|"+p.indexDba()+"|"+f.block()+"|"+f.itl()+"|"+f.slot()+"|"+f.rowSize()+"|"+k.objectId()+"|"+k.columnName()+"|"+k.columnNumber()+"|"+k.rowDba()+"|"+k.rowSlot());
                }
                if(count!=1)throw new RedoException("RESEARCH_DICTIONARY_INDEX_PAIR_COVERAGE");selected.add(at);
            }
        });
        if(selected.size()!=inventory.entries().stream().filter(e->e.opcodeMajor()==10&&e.opcodeMinor()==22).count())
            throw new RedoException("RESEARCH_DICTIONARY_INDEX_ORIGINAL_OMISSION");
        out.forEach(System.out::println);System.err.println("research_index_scope_only;V3_acquisition_missing;parent_history_permission=false;events=0");
    }
}
