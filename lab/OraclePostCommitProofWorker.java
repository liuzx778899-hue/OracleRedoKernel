import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Fault is in the post-commit read connection only; the writer and commit remain unmodified. */
public class OraclePostCommitProofWorker {
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));String prefix=args[1],mode=args[3];Path directory=Path.of(args[2]);
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=7;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var input=CapturedHeapBatch.read(captures,Path.of(args[4]),args[5],Long.decode(args[6]));
        int[] opened={0};var connections=(OracleConsumerCheckpoint.Connections)()->{
            var raw=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));int ordinal=++opened[0];
            return (Connection)Proxy.newProxyInstance(OraclePostCommitProofWorker.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
                try {
                    if(mode.equals("resume") && Set.of("commit","rollback","setAutoCommit","createStatement").contains(m.getName()))throw new AssertionError("RECOVERY_ATTEMPTED_WRITE");
                    Object value=m.invoke(raw,a);
                    if(mode.equals("fail") && ordinal==3 && m.getName().equals("prepareStatement")){
                        if(!((String)a[0]).startsWith("select "))throw new AssertionError("POST_COMMIT_PROOF_NOT_SELECT");
                        PreparedStatement ps=(PreparedStatement)value;
                        return Proxy.newProxyInstance(OraclePostCommitProofWorker.class.getClassLoader(),new Class[]{PreparedStatement.class},(q,n,b)->{
                            try {
                                if(n.getName().equals("executeQuery"))throw new SQLException("ORACLE_POSTCOMMIT_PROOF_UNAVAILABLE");
                                return n.invoke(ps,b);
                            }catch(InvocationTargetException e){throw e.getCause();}
                        });
                    }
                    return value;
                }catch(InvocationTargetException e){throw e.getCause();}
            });
        };
        var target=new OracleTargetBatchWriter.Target(secret.get(0),prefix,"oracle-dml-"+prefix+"-20261005");
        var result=new OracleConsumerCheckpoint().consume(directory,target,()->List.of(input),input.sha256(),connections);
        if(mode.equals("fail"))throw new AssertionError("POST_COMMIT_PROOF_FAILURE_WAS_IGNORED");
        if(result.newApplications()!=0)throw new AssertionError("RECOVERY_REPLAYED_BUSINESS_DML");
        System.out.println("POST_COMMIT_RECOVERED|"+prefix+"|"+result.newApplications()+"|"+result.committedBatches().size());
    }
}
