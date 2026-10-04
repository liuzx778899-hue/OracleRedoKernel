import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Dedicated target fixture worker. Production port has no fault injection or ACK file code. */
public class OracleTargetWorker {
    static String stage,prefix;static Path ready;
    static void pause(String at) throws Exception {
        if (stage.equals(at)) {Files.writeString(ready,at);while (true) Thread.sleep(1000);}
    }
    public static void main(String[] args) throws Exception {
        // secret contains user/password/URL, never printed or supplied on CLI.
        List<String> secret=Files.readAllLines(Path.of(args[0]));prefix=args[1];String targetId=args[2];
        Path ack=Path.of(args[3]);stage=args[4];ready=Path.of(args[5]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for (int i=9;i<args.length;i+=2) captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=OracleBatchProjection.project(CapturedHeapBatch.read(captures,Path.of(args[6]),args[7],Long.decode(args[8])));
        var receipt=new OracleTargetBatchWriter.CommitReceipt(targetId,batch.inputBatchSha256(),batch.schema().sha256(),batch.entries().size());
        String content="ORK_ORACLE_TARGET_ACK_V1|"+receipt.targetId()+"|"+receipt.batchSha256()+"|"+receipt.schemaSha256()+"|"+receipt.events()+"\n";
        if (Files.exists(ack) && !Files.readString(ack).equals(content)) throw new IllegalStateException("ORACLE_TARGET_ACK_CONFLICT");
        Connection raw=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));
        final int[] updates={0};
        Connection fault=(Connection)Proxy.newProxyInstance(OracleTargetWorker.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,values)->{
            try {
                if (method.getName().equals("commit")) {
                    pause("before-commit");Object result=method.invoke(raw,values);pause("after-commit");return result;
                }
                Object result=method.invoke(raw,values);
                if (stage.equals("late-before") && method.getName().equals("prepareStatement") && ((String)values[0]).startsWith("update ")) {
                    PreparedStatement actual=(PreparedStatement)result;
                    return Proxy.newProxyInstance(OracleTargetWorker.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,v)->{
                        try {
                            if (m.getName().equals("executeUpdate") && ++updates[0]==3) actual.setString(5,"deliberate-wrong-before");
                            return m.invoke(actual,v);
                        } catch (InvocationTargetException e) {throw e.getCause();}
                    });
                }
                return result;
            } catch (InvocationTargetException e) {throw e.getCause();}
        });
        var result=new OracleTargetBatchWriter().apply(fault,new OracleTargetBatchWriter.Target(secret.get(0),prefix,targetId),batch,Files.exists(ack));
        Path temp=ack.resolveSibling(ack.getFileName()+".tmp");
        try (var channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            var bytes=StandardCharsets.UTF_8.encode(content);while (bytes.hasRemaining()) channel.write(bytes);channel.force(true);
        }
        Files.move(temp,ack,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        // Linux helper directory barrier; real power failure still not claimed.
        try (var directory=FileChannel.open(ack.getParent(),StandardOpenOption.READ)) {directory.force(true);}
        pause("after-ack");
        System.out.println("COMMITTED|"+result.newApplications()+"|"+content.strip());
    }
}
