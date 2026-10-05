import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Real retained target receipts; result corruption is lab-only and never modifies target SQL. */
public class OracleReadOnlyReceiptProbe {
    static Connection guarded(List<String> secret,int corrupt) throws Exception {
        Connection raw=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));
        return (Connection)Proxy.newProxyInstance(OracleReadOnlyReceiptProbe.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            try {
                if (Set.of("commit","rollback","setAutoCommit","createStatement","setReadOnly").contains(m.getName())) throw new AssertionError("NON_SELECT_CONNECTION_ACTION: "+m.getName());
                Object value=m.invoke(raw,a);
                if (!m.getName().equals("prepareStatement")) return value;
                String sql=(String)a[0];if (!sql.startsWith("select ") || sql.contains("for update")) throw new AssertionError("NOT_READ_ONLY_SQL");
                PreparedStatement ps=(PreparedStatement)value;
                return Proxy.newProxyInstance(OracleReadOnlyReceiptProbe.class.getClassLoader(),new Class[]{PreparedStatement.class},(q,n,b)->{
                    try {
                        if (Set.of("executeUpdate","execute","executeBatch").contains(n.getName())) throw new AssertionError("NON_SELECT_STATEMENT_ACTION");
                        Object result=n.invoke(ps,b);if (!n.getName().equals("executeQuery")) return result;
                        ResultSet rs=(ResultSet)result;
                        return Proxy.newProxyInstance(OracleReadOnlyReceiptProbe.class.getClassLoader(),new Class[]{ResultSet.class},(s,k,c)->{
                            try {
                                Object actual=k.invoke(rs,c);
                                if (c!=null && c.length==1 && c[0] instanceof Integer column && column==corrupt) {
                                    if (k.getName().equals("getString")) return corrupt==4?null:"corrupt";
                                    if (k.getName().equals("getInt")) return ((Integer)actual)+1;
                                    if (k.getName().equals("getObject")) return 99;
                                }
                                return actual;
                            } catch (InvocationTargetException e) {throw e.getCause();}
                        });
                    } catch (InvocationTargetException e) {throw e.getCause();}
                });
            } catch (InvocationTargetException e) {throw e.getCause();}
        });
    }
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));String prefix=args[1];Path out=Path.of(args[2]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for (int i=6;i<args.length;i+=2) captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var input=CapturedHeapBatch.read(captures,Path.of(args[3]),args[4],Long.decode(args[5]));
        var batch=OracleBatchProjection.project(input);var target=new OracleTargetBatchWriter.Target(secret.get(0),prefix,"oracle-dml-"+prefix+"-20261005");
        var writer=new OracleTargetBatchWriter();var receipt=writer.verifyCommitted(guarded(secret,0),target,batch);
        if (receipt.events()!=batch.entries().size()) throw new AssertionError("WRONG_EVENTS");
        int rejected=0;
        for (int column=1;column<=(batch.entries().isEmpty()?8:12);column++) {
            try {writer.verifyCommitted(guarded(secret,column),target,batch);throw new AssertionError("CORRUPTION_ACCEPTED: "+column);}
            catch (SQLException expected) {rejected++;}
        }
        Files.createDirectory(out);Path failed=Files.createDirectory(out.resolve("failed"));
        Path healthy=Files.createDirectory(out.resolve("healthy"));
        var connections=(OracleConsumerCheckpoint.Connections)()->{try {return guarded(secret,0);}catch(Exception e){throw new SQLException(e);}};
        var consumer=new OracleConsumerCheckpoint();var result=consumer.consume(healthy,target,()->List.of(input),input.sha256(),connections);
        if(result.newApplications()!=0) throw new AssertionError("TARGET_DML_REPLAYED");
        byte[] checkpoint=Files.readAllBytes(healthy.resolve("checkpoint"));
        Files.copy(healthy.resolve("checkpoint"),failed.resolve("checkpoint"));
        Files.copy(healthy.resolve(input.sha256()+".ack"),failed.resolve(input.sha256()+".ack"));
        try {
            new OracleConsumerCheckpoint().consume(failed,target,()->List.of(input),input.sha256(),()->{
                try {return guarded(secret,4);}catch (Exception e){throw new SQLException(e);}
            });throw new AssertionError("MISSING_COMMIT_PUBLISHED");
        } catch (SQLException expected) {
            if (!expected.getMessage().equals("ORACLE_TARGET_ACK_WITHOUT_COMMIT")) throw expected;
        }
        if (!Arrays.equals(checkpoint,Files.readAllBytes(failed.resolve("checkpoint")))
                || !Arrays.equals(Files.readAllBytes(healthy.resolve(input.sha256()+".ack")),Files.readAllBytes(failed.resolve(input.sha256()+".ack")))) throw new AssertionError("FAILED_PROOF_ADVANCED_STATE");
        if(consumer.consume(healthy,target,()->List.of(input),input.sha256(),connections).newApplications()!=0
                || !Arrays.equals(checkpoint,Files.readAllBytes(healthy.resolve("checkpoint")))) throw new AssertionError("REPEAT_ADVANCED_STATE");
        System.out.println("READ_ONLY_RECEIPT_VERIFIED|"+prefix+"|"+receipt.events()+"|"+rejected+"|NO_DML|REPEAT_ZERO");
    }
}
