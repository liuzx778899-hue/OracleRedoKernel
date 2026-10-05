import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Actual Oracle target proof, retained raw scope, Linux consumer state. No source writes. */
public class OracleCheckpointProbe {
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));Path dir=Path.of(args[1]);
        var result=new OracleConsumerCheckpoint().consume(dir,new OracleTargetBatchWriter.Target(secret.get(0),"MIXD","oracle-target-MIXD-20261005"),()->{
            var inputs=new ArrayList<CaptureJournal.Verified>();
            for (int i=5;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
            return List.of(CapturedHeapBatch.read(inputs.subList(0,2),Path.of(args[3]),args[4],0x0300019b),
                    CapturedHeapBatch.read(inputs,Path.of(args[3]),args[4],0x0300019b));
        },args[2],()->DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)));
        System.out.println("CONSUMED|"+result.position().closedSequence()+"|"+result.position().closedBlocks()+"|"+result.position().endExclusiveScn()+"|"+result.newApplications()+"|"+result.committedBatches().size());
    }
}
