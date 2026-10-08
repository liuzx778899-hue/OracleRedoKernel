import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Actual immutable archive prefix and full input only; no SQL/dump event authority. */
public class D2PendingSysRawProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path state=Path.of(args[1]);String stateSha=args[2];
        Path baseline=Path.of(args[3]);String baselineSha=args[4];
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=5;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var lines=new ArrayList<String>();
        if(mode.equals("prefix")) {
            var timeline=ArchivedDictionaryHistory.prefixCaptured(captures,baseline,baselineSha);
            if(timeline.pending().isEmpty())throw new IllegalStateException("EXPERIMENT_HAS_NO_PENDING_SYS");
            for(var p:timeline.pending())lines.add("PENDING_SYS|"+p.xid()+"|"+p.beginScn()+"|"+p.originals()+"|"+p.rowPairs()+"|"+p.indexPairs()+"|"+p.controls());
            try {
                timeline.schemaAt(timeline.sources().get(timeline.sources().size()-1).nextScn()-1);
                throw new IllegalStateException("UNCOMMITTED_SCHEMA_WAS_ALLOWED");
            } catch(RedoException e) {
                if(!e.getMessage().equals("DICTIONARY_SCHEMA_PENDING_SYS"))throw e;
                lines.add("SCHEMA_REFUSED|"+e.getMessage());
            }
            var prefix=HistoricalRecovery.prefixCaptured(captures,baseline,baselineSha,50342539);
            if(prefix.completedTransactions()!=0 || prefix.pendingTransactions()!=timeline.pending().size())
                throw new IllegalStateException("UNIFIED_PENDING_SCOPE_MISMATCH");
            byte[] bytes=prefix.checkpoint();
            try(var out=java.nio.channels.FileChannel.open(state,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
            }
            lines.add("WAITING|"+prefix.pendingTransactions()+"|"+prefix.completedTransactions()+"|events=0");
        } else {
            var batch=mode.equals("restored")
                ?HistoricalRecovery.readCaptured(captures,baseline,baselineSha,50342539,Files.readAllBytes(state),stateSha)
                :CapturedHistoricalHeapBatch.read(captures,baseline,baselineSha,50342539);
            for(var event:batch.events()) {
                var e=event.row();var p=e.pairedUndoPosition();
                lines.add("SCHEMA|"+event.schema().sha256()+"|"+event.schema().columns());
                lines.add("EVENT|"+e.eventId()+"|"+e.payloadSha256()+"|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()+"|"+e.transaction().xid()+"|"+e.operation());
                lines.add("BEFORE|"+e.before());lines.add("AFTER|"+e.after());
            }
            lines.add("EVENT_COUNT|"+batch.events().size());
        }
        lines.forEach(System.out::println);
    }
}
