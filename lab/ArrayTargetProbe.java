import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
/** Real owned target transaction and independent commit verification from a frozen raw batch. */
public class ArrayTargetProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var capture=new CaptureJournal().readRawClosed(Path.of(args[2]),args[3]);
        long leaf=new RedoRecords().readCaptured(capture).stream().flatMap(r->r.vectors().stream())
                .filter(v->v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject()).findFirst().orElseThrow().dba();
        var batch=OracleBatchProjection.project(CapturedHeapBatch.read(List.of(capture),Path.of(args[0]),args[1],leaf));
        if(batch.entries().size()!=20)throw new AssertionError("ARRAY_TARGET_EVENT_COUNT");
        var properties=new HashMap<String,String>();
        for(var line:Files.readAllLines(Path.of(args[4]))) {int at=line.indexOf('=');if(at>0)properties.put(line.substring(0,at),line.substring(at+1));}
        var target=new OracleTargetBatchWriter.Target(args[5],args[6],"oracle-array-"+args[6]);
        var writer=new OracleTargetBatchWriter();var url="jdbc:oracle:thin:@//127.0.0.1:1521/ORKPDB";
        String user=properties.get("SPRING_DATASOURCE_USERNAME"),password=properties.get("SPRING_DATASOURCE_PASSWORD");
        var first=writer.apply(DriverManager.getConnection(url,user,password),target,batch,false);
        if(first.newApplications()!=20)throw new AssertionError("ARRAY_TARGET_FIRST_APPLICATION");
        var verified=writer.verifyCommitted(DriverManager.getConnection(url,user,password),target,batch);
        if(!verified.equals(first.receipt()))throw new AssertionError("ARRAY_TARGET_INDEPENDENT_COMMIT");
        var resumed=writer.apply(DriverManager.getConnection(url,user,password),target,batch,true);
        if(resumed.newApplications()!=0 || !resumed.receipt().equals(verified))throw new AssertionError("ARRAY_TARGET_RETRY_DUPLICATE");
        System.out.println("ARRAY_TARGET_PASS|20|0|"+verified.batchSha256()+"|"+verified.schemaSha256());
    }
}
