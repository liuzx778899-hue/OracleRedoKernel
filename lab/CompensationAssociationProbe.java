import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Source-bound header association queries; does not match physical rows or cancel history. */
public class CompensationAssociationProbe {
    record Query(ArchivedRedo.Header source,RedoRecords.Record record,RedoRecords.Vector marker,ArchivedUndoInventory.Position at) {}
    public static void main(String[] args) throws Exception {
        String[] x=args[0].split("\\.");var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        String mode=args[1];if (!List.of("positive","omit_foreign","repeat","reorder","wrong_marker","wrong_source","wrong_position").contains(mode)) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[2]);var associations=ArchivedCompensationAssociations.read(List.of(path),677737149,3,xid);
        var header=new ArchivedRedo().scan(path);var queries=new ArrayList<Query>();
        new RedoRecords().visit(path,header,r->{
            for (int i=0;i<r.vectors().size();i++) {
                var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                if (associations.entries().stream().anyMatch(e->e.position().equals(at))) queries.add(new Query(header,r,r.vectors().get(i),at));
            }
        });
        if (mode.equals("omit_foreign")) queries.removeIf(q->associations.entries().stream().anyMatch(e->e.position().equals(q.at()) && !e.xid().equals(xid)));
        if (mode.equals("repeat")) queries.add(1,queries.get(0));
        if (mode.equals("reorder")) java.util.Collections.swap(queries,0,1);
        var output=new ArrayList<String>();
        for (var q:queries) {
            if (mode.equals("wrong_marker")) q.marker().fields().get(1)[7]^=1;
            var source=mode.equals("wrong_source") ? new ArchivedRedo().scan(Path.of(args[3])) : q.source();
            var at=mode.equals("wrong_position") ? new ArchivedUndoInventory.Position(q.at().archiveSequence()+1,q.at().block(),q.at().offset(),q.at().vector()) : q.at();
            var e=associations.associate(source,q.record(),q.marker(),at);var h=e.original();
            output.add("ASSOC|"+position(e.position())+"|"+e.xid().usn()+"."+e.xid().slot()+"."+e.xid().sequence()+"|"+position(e.originalPosition())
                    +"|"+h.uba().dba()+":"+h.uba().sequence()+":"+h.uba().record()+"|"+h.objectId()+"|"+h.dataObjectId()+"|"+h.beginsTransaction());
        }
        associations.finish();
        if (!header.equals(new ArchivedRedo().scan(path))) throw new RedoException("ASSOCIATION_LAB_INPUT_CHANGED");
        output.forEach(System.out::println);System.err.println("associations="+output.size()+";head_and_physical_match_not_certified;cdc_events=0");
    }
    static String position(ArchivedUndoInventory.Position p) {return p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector();}
}
