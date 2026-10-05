import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** No source actions or event publication. Caller must separately prove baseline acquisition authority. */
public class ArchivedDictionaryHistoryEvidenceProbe {
    public static void main(String[] args)throws Exception {
        ArchivedDictionaryHistory.Timeline timeline;
        if(args.length>2 && args[2].equals("captures")) {
            var captures=new ArrayList<CaptureJournal.Verified>();
            for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
            timeline=ArchivedDictionaryHistory.readCaptured(captures,Path.of(args[0]),args[1]);
        } else {
            var paths=new ArrayList<Path>();for(int i=2;i<args.length;i++)paths.add(Path.of(args[i]));
            timeline=ArchivedDictionaryHistory.read(paths,Path.of(args[0]),args[1]);
        }
        var output=new ArrayList<String>();
        for(var commit:timeline.commits()) {
            long scn=commit.identity().endRecordScn();
            output.add("COMMIT|"+scn+"|"+commit.original().size()+"|"+commit.rowPairs()+"|"+commit.indexPairs()+"|"+commit.controls());
            var id=commit.identity();output.add("IDENTITY|"+scn+"|"+id.xid().usn()+"|"+id.xid().slot()+"|"+id.xid().sequence()+"|"+id.beginRecordScn()+"|"+id.rolledBack());
            for(var e:commit.original()) {var p=e.position();var h=e.identity();output.add("NODE|"+scn+"|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+h.objectId()+"|"+h.dataObjectId()+"|"+h.uba().dba()+"|"+h.uba().sequence()+"|"+h.uba().record()+"|"+e.previousDba()+"|"+e.rci());}
            for(var row:commit.rows())output.add("ROW|"+scn+"|"+row.role()+"|"+row.target()+"|"+row.key().dba()+"|"+row.key().slot()+"|"+row.values().stream().map(v->v.state()==HeapRowPatch.State.NULL?"NULL":v.hex()).collect(java.util.stream.Collectors.joining("|")));
        }
        for(var commit:timeline.commits()) {
            long scn=commit.identity().endRecordScn();
            try {output.add("SCHEMA|"+scn+"|"+timeline.schemaAt(scn));}
            catch(RedoException e){output.add("SCHEMA_REFUSED|"+scn+"|"+e.getMessage());}
        }
        output.forEach(System.out::println);
        System.err.println("dictionary_history_only;events=0;baseline_authority_requires_independent_acquisition");
    }
}
