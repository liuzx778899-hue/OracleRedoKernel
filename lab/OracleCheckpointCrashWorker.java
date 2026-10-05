import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.lang.reflect.*;

/** Owned fixture worker. Commit pauses wrap the vendor connection; fsync pauses belong to the lab agent. */
public class OracleCheckpointCrashWorker {
    static String stage;static Path ready;
    static void pause(String point) throws Exception {
        if (stage.equals(point)) {Files.writeString(ready,point);while (true) Thread.sleep(1000);}
    }
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));Path dir=Path.of(args[1]);String prefix=args[2];stage=args[3];ready=dir.resolve("ready");
        var result=new OracleConsumerCheckpoint().consume(dir,new OracleTargetBatchWriter.Target(secret.get(0),prefix,"oracle-target-"+prefix+"-20261005"),()->{
            var inputs=new ArrayList<CaptureJournal.Verified>();
            for (int i=7;i<args.length;i+=2) inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
            return List.of(CapturedHeapBatch.read(inputs.subList(0,2),Path.of(args[5]),args[6],0x0300019b),
                    CapturedHeapBatch.read(inputs,Path.of(args[5]),args[6],0x0300019b));
        },args[4],()->{
            Connection raw=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));boolean[] writes={false};
            return (Connection)Proxy.newProxyInstance(OracleCheckpointCrashWorker.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,values)->{
                try {
                    if (method.getName().equals("prepareStatement") && ((String)values[0]).startsWith("update "+secret.get(0)+"."+prefix+"_ROWS ")) writes[0]=true;
                    if (method.getName().equals("commit") && writes[0]) {pause("before-target-commit");Object value=method.invoke(raw,values);pause("after-target-commit");return value;}
                    return method.invoke(raw,values);
                } catch (InvocationTargetException e) {throw e.getCause();}
            });
        });
        System.out.println("CONSUMED|"+result.position().closedSequence()+"|"+result.position().closedBlocks()+"|"+result.position().endExclusiveScn()+"|"+result.newApplications()+"|"+result.committedBatches().size());
    }
}
