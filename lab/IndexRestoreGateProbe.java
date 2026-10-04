import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Records the existing kernel's unsupported RESTORE gate on actual new fixture records. */
public class IndexRestoreGateProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r -> {
            if (r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.containerId()!=3 || v.opcodeMajor()!=10 || v.opcodeMinor()!=5 || v.dba()!=0x0300012bL) continue;
                var markers=new ArrayList<RedoRecords.Vector>();
                for (var c:r.vectors()) if (c.containerId()==3 && c.opcodeMajor()==5 && c.opcodeMinor()==6
                        && c.fields().size()==2 && IndexVectorSurvey.word(c.fields().get(0),4)==73060) markers.add(c);
                if (markers.size()!=1) throw new RedoException("RESTORE_FIXTURE_MARKER_COUNT");
                try {
                    IndexCompensation.match(r,markers.get(0),v,List.of(),new TransactionLifecycle());
                    throw new IllegalStateException("RESTORE_UNEXPECTEDLY_ACCEPTED");
                } catch (RedoException e) {
                    if (!e.getMessage().equals("UNSUPPORTED_INDEX_PURGE_LAYOUT")) throw e;
                    output.add(header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+e.getMessage());
                }
            }
        });
        if (output.size()!=4) throw new RedoException("RESTORE_FIXTURE_COUNT_MISMATCH");
        output.forEach(System.out::println);System.err.println("unsupported_restore_vectors=4;cdc_events=0;no_active_identity_or_history_claim");
    }
}
