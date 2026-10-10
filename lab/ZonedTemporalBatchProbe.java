import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Trusted baseline plus raw only; target mode independently verifies the real JDBC commit. */
public class ZonedTemporalBatchProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);
        var capture=new CaptureJournal().readRawClosed(Path.of(args[3]),args[4]);long leaf=0;
        for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
            if(vector.opcodeMajor()==10 && vector.objectId()==snapshot.index().dataObject() && vector.opcodeMinor()==2) {
                if(leaf!=0 && leaf!=vector.dba())throw new AssertionError("ZONED_INDEX_LEAF_COUNT");leaf=vector.dba();
            }
        var batch=OracleBatchProjection.project(CapturedHeapBatch.read(List.of(capture),Path.of(args[1]),args[2],leaf));
        if(batch.entries().size()!=3 || batch.entries().get(0).envelope().operation()!=com.streamhouse.spi.EventEnvelope.Operation.UPDATE)
            throw new AssertionError("ZONED_EVENT_COUNT_OR_OPERATION");
        if(args[0].equals("project")) {
            System.out.println(new ObjectMapper().writeValueAsString(Map.of("evidence",batch.evidence(),"events",batch.entries().stream().map(OracleBatchProjection.Entry::envelope).toList())));return;
        }
        if(!args[0].equals("target"))throw new AssertionError("ZONED_PROBE_MODE");
        var props=new HashMap<String,String>();
        for(String line:Files.readAllLines(Path.of(args[5]))) {int at=line.indexOf('=');if(at>0)props.put(line.substring(0,at),line.substring(at+1));}
        String url="jdbc:oracle:thin:@//127.0.0.1:1521/ORKPDB",user=props.get("SPRING_DATASOURCE_USERNAME"),password=props.get("SPRING_DATASOURCE_PASSWORD");
        var target=new OracleTargetBatchWriter.Target(user,args[6],"oracle-zoned-"+args[6]);var writer=new OracleTargetBatchWriter();
        var first=writer.apply(DriverManager.getConnection(url,user,password),target,batch,false);
        var committed=writer.verifyCommitted(DriverManager.getConnection(url,user,password),target,batch);
        var retry=writer.apply(DriverManager.getConnection(url,user,password),target,batch,true);
        if(first.newApplications()!=3 || retry.newApplications()!=0 || !first.receipt().equals(committed) || !committed.equals(retry.receipt()))
            throw new AssertionError("ZONED_TARGET_COMMIT_OR_RETRY");
        System.out.println("ZONED_TARGET_PASS|3|0|"+committed.batchSha256());
    }
}
