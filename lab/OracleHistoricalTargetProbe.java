import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Owned historical target probe; no source mutation, product HTTP or ACK emulation. */
public class OracleHistoricalTargetProbe {
    static int rowUpdates;
    static boolean rollback;
    static Object invoke(Object object, Method method, Object[] args) throws Throwable {
        try { return method.invoke(object,args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
    public static void main(String[] args) throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=7;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=OracleBatchProjection.project(CapturedHistoricalHeapBatch.read(captures,Path.of(args[4]),args[5],Long.decode(args[6])));
        if(batch.entries().size()!=2 || !batch.evidence().schemaSequenceSha256().equals("c23904a9efaed17aaf82a68d8d88478dcf84d89804c14376b6c29aa9f077a2e4"))
            throw new IllegalStateException("FROZEN_HISTORY_MISMATCH");
        var secret=Files.readAllLines(Path.of(args[1]));
        var target=new OracleTargetBatchWriter.Target(secret.get(0),args[2],args[3]);
        var writer=new OracleTargetBatchWriter();
        if(args[0].equals("verify")) {
            var receipt=writer.verifyCommitted(DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)),target,batch);
            System.out.println("VERIFIED|"+receipt.batchSha256()+"|"+receipt.schemaSha256()+"|"+receipt.schemaSequenceSha256()+"|"+receipt.events());
            return;
        }
        var raw=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));
        var traced=(Connection)Proxy.newProxyInstance(OracleHistoricalTargetProbe.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,values)->{
            Object result=invoke(raw,method,values);
            if(method.getName().equals("rollback"))rollback=true;
            if(method.getName().equals("prepareStatement") && ((String)values[0]).startsWith("update "+target.owner()+"."+target.prefix()+"_ROWS ")) {
                var statement=(PreparedStatement)result;
                return Proxy.newProxyInstance(OracleHistoricalTargetProbe.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,v)->{
                    Object answer=invoke(statement,m,v);
                    if(m.getName().equals("executeUpdate"))rowUpdates+=(Integer)answer;
                    return answer;
                });
            }
            return result;
        });
        try {
            var result=writer.apply(traced,target,batch,args[0].equals("repeat"));
            var receipt=result.receipt();
            if(!writer.verifyCommitted(DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)),target,batch).equals(receipt))
                throw new IllegalStateException("RECEIPT_VERIFY_MISMATCH");
            System.out.println("COMMITTED|"+result.newApplications()+"|"+receipt.batchSha256()+"|"+receipt.schemaSha256()+"|"+receipt.schemaSequenceSha256()+"|"+receipt.events());
        } catch(Exception failure) {
            System.err.println("OBSERVED_ROW_UPDATES="+rowUpdates+";ROLLBACK="+rollback);
            throw failure;
        }
    }
}
