import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Fault fixtures vary retained inputs/registry; production checkpoint code unchanged. */
public class OracleCheckpointRegistryProbe {
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));String prefix=args[2];
        var result=new OracleConsumerCheckpoint().consume(Path.of(args[1]),new OracleTargetBatchWriter.Target(secret.get(0),prefix,"oracle-target-"+prefix+"-20261005"),()->{
            var captures=new ArrayList<CaptureJournal.Verified>();
            for (int i=7;i<args.length;i+=2) captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
            if (args[4].equals("prefix-only")) return List.of(CapturedHeapBatch.read(captures,Path.of(args[5]),args[6],0x0300019b));
            var full=CapturedHeapBatch.read(captures,Path.of(args[5]),args[6],0x0300019b);
            if (args[4].equals("full-only")) return List.of(full);
            return List.of(CapturedHeapBatch.read(captures.subList(0,2),Path.of(args[5]),args[6],0x0300019b),full);
        },args[3],()->DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)));
        System.out.println("CONSUMED|"+result.position().closedSequence()+"|"+result.newApplications()+"|"+result.committedBatches().size());
    }
}
