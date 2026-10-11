import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen raw generation associations only; does not close transactions or emit events. */
public class DictionaryGenerationProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);Path raw=Path.of(args[0]);
        var header=new ArchivedRedo().scan(raw);snapshot.requireSource(header);
        var roles=new LinkedHashMap<FrozenHeapBaseline.ObjectRole,Long>();roles.put(snapshot.heap(),2L);roles.put(snapshot.index(),1L);
        for(var lob:snapshot.lobs())roles.put(lob.lob(),21L);
        var baseline=snapshot.dictionaryBaseline();var objectLayout=baseline.layouts().get("OBJ$");
        var acquired=baseline.rows().stream().filter(r->r.role().equals("OBJ$") && r.target()==snapshot.heap().logicalObject()).findFirst().orElseThrow();
        int ownerColumn=0;while(!objectLayout.columns().get(ownerColumn).name().equals("OWNER#"))ownerColumn++;
        long owner=OracleScalars.number(HexFormat.of().parseHex(acquired.values().get(ownerColumn).hex())).longValueExact();
        var updates=new ArrayList<DictionaryObjectUndoPair>();var indexes=new ArrayList<DictionaryDataObjectIndexPair>();
        new RedoRecords().visit(raw,header,r->{if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
            for(var row:r.vectors())if(row.containerId()==snapshot.source().containerId()) {
                if(row.opcodeMajor()==10 && row.objectId()==39)indexes.add(DictionaryDataObjectIndexPair.bind(r,row));
                if(row.opcodeMajor()==11 && row.opcodeMinor()==5 && row.objectId()==18) {
                    var pair=DictionaryObjectUndoPair.bind(r,row,objectLayout.role(),37);
                    var id=pair.physical().forward().columns().get(0);
                    if(id.state()==HeapRowPatch.State.VALUE && roles.keySet().stream().anyMatch(role->{try{return role.logicalObject()==OracleScalars.number(id.bytes()).longValueExact();}catch(Exception e){throw new IllegalStateException(e);}}))updates.add(pair);
                }
            }
        });
        for(var entry:roles.entrySet()) {
            var matches=new ArrayList<FrozenHeapBaseline.ObjectRole>();
            for(var row:updates)for(var before:indexes)for(var after:indexes) {
                if(before.physical().forward().kind()!=IndexUndoPair.Kind.DELETE || after.physical().forward().kind()!=IndexUndoPair.Kind.INSERT
                        || before.dataObject()==null || before.dataObject()!=entry.getKey().dataObject()
                        || before.rowDba()!=row.physical().forward().dba() || before.rowSlot()!=row.physical().forward().slot()
                        || after.rowDba()!=before.rowDba() || after.rowSlot()!=before.rowSlot())continue;
                matches.add(DictionaryDataObjectIndexPair.requireGeneration(row,entry.getKey(),entry.getValue(),owner,before,after));
            }
            if(matches.size()!=1)throw new AssertionError("GENERATION_COVERAGE:"+entry.getKey()+":"+matches.size());
            System.out.println("GENERATION|"+entry.getKey().logicalObject()+"|"+entry.getKey().dataObject()+"|"+matches.get(0).dataObject());
        }
        if(!header.equals(new ArchivedRedo().scan(raw)))throw new AssertionError("GENERATION_SOURCE_CHANGED");
        System.out.println("GENERATIONS|"+roles.size());
    }
}
