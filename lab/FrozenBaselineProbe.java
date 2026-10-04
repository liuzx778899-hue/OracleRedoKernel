import com.streamhouse.oracle.*;
import java.nio.file.Path;

/** The digest must come from the acquisition handoff, not a field in the snapshot being read. */
public class FrozenBaselineProbe {
    public static void main(String[] args) throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);snapshot.requireSource(new ArchivedRedo().scan(Path.of(args[2])));
        if (args.length>3 && args[3].equals("--equal-object-scope"))snapshot.baselineForEqualObjectScope();
        System.out.println("SNAPSHOT|"+snapshot.scn()+"|"+snapshot.sha256()+"|"+snapshot.source().dbid()+"|"+snapshot.source().containerUid()+"|"+snapshot.source().containerId());
        System.out.println("HEAP|"+snapshot.heap().logicalObject()+"|"+snapshot.heap().dataObject());System.out.println("INDEX|"+snapshot.index().logicalObject()+"|"+snapshot.index().dataObject());
        for (var c:snapshot.columns())System.out.println("COLUMN|"+c.segment()+"|"+c.internal()+"|"+c.name()+"|"+c.type()+"|"+c.nullable()+"|"+c.hidden());
        snapshot.rows().entrySet().stream().sorted(java.util.Comparator.comparingLong(e->e.getKey().dba())).forEach(e->System.out.println("ROW|"+e.getKey().dataObjectId()+"|"+e.getKey().dba()+"|"+e.getKey().slot()+"|"+String.join(",",e.getValue().stream().map(c->c.state()+":"+c.hex()).toList())));
        System.err.println("snapshot_rows="+snapshot.rows().size()+";trusted_acquisition_digest_required;historical_dictionary_and_replay_generation_unaccepted;cdc_events=0");
    }
}
