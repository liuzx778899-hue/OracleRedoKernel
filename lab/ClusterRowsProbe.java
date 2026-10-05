import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Trusted role scope, archive-owned originals, physical clustered patches only; no pair/history authorization. */
public class ClusterRowsProbe {
    public static void main(String[] args) throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);Path path=Path.of(args[2]);String[] x=args[3].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(path),snapshot.source().containerUid(),snapshot.source().containerId(),xid);
        var header=inventory.sources().get(0);snapshot.requireSource(header);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var entry:inventory.entries()) {
                var at=entry.position();if(r.block()!=at.block()||r.offset()!=at.offset())continue;
                var undo=r.vectors().get(at.vector()-1);var h=UndoHeader.decode(undo);
                var role=snapshot.dictionaryAnchors().stream().map(FrozenHeapBaseline.DictionaryAnchor::dictionary)
                        .filter(d->d.logicalObject()==h.objectId()&&d.dataObject()==h.dataObjectId()).findFirst();
                if(role.isEmpty()||!List.of("TAB$","COL$").contains(role.get().name()))continue;
                if(undo.fields().size()<4||undo.fields().get(1)[16]!=11||undo.fields().get(1)[17]!=1)
                    throw new RedoException("RESEARCH_CLUSTER_INVERSE_MISSING");
                int tab=role.get().name().equals("TAB$")?1:5,columns=tab==1?44:36;
                var inverse=ClusterRowPatch.decode(undo.fields().subList(2,undo.fields().size()),undo.fields().get(3)[10]&255,
                        word(undo.fields().get(3),0),tab,columns);
                emit(output,header,r,at.vector(),role.get().name(),"inverse",inverse);
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId()||v.opcodeMajor()!=11||v.dba()!=inverse.row().dba())continue;
                    var forward=ClusterRowPatch.decode(v.fields(),v.opcodeMinor(),v.dba(),tab,columns);
                    emit(output,header,r,i+1,role.get().name(),"forward",forward);
                }
            }
        });
        output.forEach(System.out::println);System.err.println("physical_patches_only;row_pairs_and_parent_identity_unverified;events=0");
    }
    static long word(byte[] b,int o){long v=0;for(int i=0;i<4;i++)v|=(long)(b[o+i]&255)<<(8*i);return v;}
    static void emit(List<String> out,ArchivedRedo.Header h,RedoRecords.Record r,int vector,String role,String side,ClusterRowPatch.Patch p) {
        var row=p.row();out.add("ROW|"+h.sequence()+"|"+r.block()+"|"+r.offset()+"|"+vector+"|"+role+"|"+side+"|"+row.kind()+"|"+row.slot()+"|"+p.tableNumber()+"|"+p.clusterKeyIndex());
        for(int i=0;i<row.columns().size();i++) {
            var c=row.columns().get(i);out.add("COLUMN|"+i+"|"+c.state()+"|"+HexFormat.of().formatHex(c.bytes()));
        }
    }
}
