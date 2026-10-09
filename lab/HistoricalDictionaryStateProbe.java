import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Two-process comparison against raw-derived dictionary queries; restore receives no raw paths. */
public class HistoricalDictionaryStateProbe {
    static String sha(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String values(HistoricalDictionaryState state,long scn,ArchivedUndoInventory.Position at)throws Exception {
        String schema;
        try{schema=state.schemaAt(scn,at).toString();}catch(RedoException e){schema="REFUSED:"+e.getMessage();}
        return sha((state.rowsAt(scn,at).toString()+"\n"+schema).getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),file=Path.of(args[3]);String baselineSha=args[2];
        if(args[0].equals("export")) {
            var captures=new ArrayList<CaptureJournal.Verified>();
            for(int i=4;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
            var timeline=ArchivedDictionaryHistory.readCaptured(captures,baseline,baselineSha);
            var state=timeline.materializedState();byte[] bytes=state.checkpoint();Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            var queries=new StringBuilder();var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);
            queries.append(snapshot.scn()).append("|0|0|0|0|").append(values(state,snapshot.scn(),null)).append('\n');
            for(var commit:timeline.commits()) {
                long scn=commit.identity().endRecordScn();var at=commit.commitPosition();
                String original,materialized;
                try{original=timeline.schemaAt(scn,at).toString();}catch(RedoException e){original="REFUSED:"+e.getMessage();}
                try{materialized=state.schemaAt(scn,at).toString();}catch(RedoException e){materialized="REFUSED:"+e.getMessage();}
                if(!original.equals(materialized))throw new AssertionError("schema outcome differs from raw history");
                if(!commit.rows().equals(state.rowsAt(scn,at)))throw new AssertionError("full values differ from raw history");
                queries.append(scn).append('|').append(at.archiveSequence()).append('|').append(at.block()).append('|')
                        .append(at.offset()).append('|').append(at.vector()).append('|').append(values(state,scn,at)).append('\n');
            }
            Files.writeString(Path.of(args[3]+".queries"),queries,StandardOpenOption.CREATE_NEW);
            System.out.println("STATE_SHA256="+sha(bytes));System.out.println("STATE_BYTES="+bytes.length);
            System.out.println("COMMITTED_DICTIONARY_VERSIONS="+timeline.commits().size());
            System.out.println("PENDING_SYS="+timeline.pending().size());
        } else if(args[0].equals("restore")) {
            byte[] bytes=Files.readAllBytes(file);
            var state=HistoricalDictionaryState.restore(bytes,args[4],baseline,baselineSha);int count=0;
            for(String query:Files.readAllLines(Path.of(args[3]+".queries"))) {
                String[] f=query.split("\\|");long scn=Long.parseLong(f[0]);
                var at=f[1].equals("0")?null:new ArchivedUndoInventory.Position(Long.parseLong(f[1]),Long.parseLong(f[2]),Integer.parseInt(f[3]),Integer.parseInt(f[4]));
                if(!values(state,scn,at).equals(f[5]))throw new AssertionError("dictionary query changed across JVM");count++;
            }
            if(!Arrays.equals(bytes,state.checkpoint()))throw new AssertionError("state bytes changed");
            System.out.println("RESTORED_QUERIES="+count);System.out.println("RAW_PATH_ARGUMENTS=0");
        } else throw new IllegalArgumentException("mode");
    }
}
