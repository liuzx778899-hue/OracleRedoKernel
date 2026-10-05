import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Frozen fixture windows only; each invocation has a single trusted baseline/leaf. */
public class OracleDmlCheckpointWorker {
    public static void main(String[] args) throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for (int i=8;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batches=new ArrayList<CapturedHeapBatch>();
        for (int n=1;n<=inputs.size();n++) batches.add(CapturedHeapBatch.read(inputs.subList(0,n),Path.of(args[4]),args[5],Long.parseLong(args[6])));
        var current=batches.get(Integer.parseInt(args[7])-1);
        if (args[0].equals("inspect")) {
            StringBuilder text=new StringBuilder();
            for (var b:batches) {
                var w=OracleConsumptionWindow.from(b);var p=OracleBatchProjection.project(b);
                text.append("WINDOW|").append(b.captures().size()).append('|').append(b.sha256()).append('|').append(b.schema().sha256()).append('|').append(b.events().size()).append('|').append(w.position().closedSequence()).append('|').append(w.position().closedBlocks()).append('|').append(w.position().endExclusiveScn()).append('\n');
                for (int i=0;i<p.entries().size();i++) {
                    var e=p.entries().get(i);var v=e.envelope();
                    text.append("ENTRY|").append(b.captures().size()).append('|').append(i).append('|').append(v.eventId()).append('|').append(e.payloadSha256()).append('|').append(v.operation()).append('|').append(OracleProjectionProbe.image(v.before())).append('|').append(OracleProjectionProbe.image(v.after())).append('|').append(String.join(",",v.changedColumns())).append('\n');
                }
            }
            System.out.print(text);return;
        }
        var secret=Files.readAllLines(Path.of(args[1]));String prefix=args[3];
        var result=new OracleConsumerCheckpoint().consume(Path.of(args[2]),new OracleTargetBatchWriter.Target(secret.get(0),prefix,"oracle-dml-"+prefix+"-20261005"),()->batches,current.sha256(),()->DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)));
        System.out.println("CONSUMED|"+result.position().closedSequence()+"|"+result.position().closedBlocks()+"|"+result.position().endExclusiveScn()+"|"+result.newApplications()+"|"+result.committedBatches().size());
    }
}
