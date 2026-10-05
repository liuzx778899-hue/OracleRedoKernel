import com.fasterxml.jackson.databind.*;
import com.streamhouse.app.appdb.*;
import com.streamhouse.app.baseline.*;
import com.streamhouse.app.port.*;
import com.streamhouse.common.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.*;
import com.streamhouse.pipeline.entity.*;
import com.streamhouse.pipeline.mapper.*;
import com.streamhouse.pipeline.service.*;
import com.streamhouse.spi.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Actual product Facade and runtime on owned Oracle control/target schemas; no source DML. */
public class OracleBoundedFacadeProbe {
    static final ObjectMapper JSON=new ObjectMapper();
    static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    static long sourceCount(Connection c,String source)throws Exception {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM SH_EVENT_LEDGER WHERE SOURCE_ID=?")) {
            s.setString(1,source);try(var rs=s.executeQuery()){rs.next();return rs.getLong(1);}
        }
    }
    public static void main(String[] args)throws Exception {
        var targets=JSON.readValue(Files.readString(Path.of(args[0])),OracleRegistryDbProbe.Target[].class);
        var owned=Arrays.stream(targets).filter(t->t.dialect().equals("oracle") && t.stage().equals("NEW")).findFirst().orElseThrow();
        var oldTargets=JSON.readValue(Files.readString(Path.of(args[4])),OracleRegistryDbProbe.Target[].class);
        var registry=Arrays.stream(oldTargets).filter(t->t.dialect().equals("oracle") && t.stage().equals("NEW")).findFirst().orElseThrow();
        var migration=OracleRegistryDbProbe.flyway(owned,null);migration.migrate();migration.validate();require(migration.migrate().migrationsExecuted==0,"RUNTIME_MIGRATION_NOT_IDEMPOTENT");
        var factory=OracleRegistryDbProbe.mapper(owned);var cfg=factory.getConfiguration();var ds=cfg.getEnvironment().getDataSource();
        cfg.setEnvironment(new Environment("real-oracle-facade",new SpringManagedTransactionFactory(),ds));
        for(String name:List.of("mapper/app/OraclePublicationLockMapper.xml","mapper/gateway/CapWindowMapper.xml"))try(var stream=OracleBoundedFacadeProbe.class.getClassLoader().getResourceAsStream(name)){new XMLMapperBuilder(stream,cfg,name,cfg.getSqlFragments()).parse();}
        var session=new SqlSessionTemplate(factory);var bm=session.getMapper(PipelineBatchMapper.class);var lm=session.getMapper(EventLedgerMapper.class);var gm=session.getMapper(PublishGateMapper.class);var wm=session.getMapper(CapWindowMapper.class);var tm=session.getMapper(TaskMapper.class);var rm=session.getMapper(OracleInputBindingMapper.class);var locks=session.getMapper(OraclePublicationLockMapper.class);
        var secret=Files.readAllLines(Path.of(args[1]));var targetDs=new DriverManagerDataSource(secret.get(2),secret.get(0),secret.get(1));var ids=new SnowflakeId(14);var results=new ArrayList<Map<String,Object>>();
        boolean resume=args.length==6 && args[5].equals("--resume");
        for(String name:List.of("SNON","SNUL","SMIX","EMPT")){
            String source="oracle-runtime-"+name,key="runtime-"+name,consumer="runtime-consumer-"+name,prefix=switch(name){case "SNON"->"RTNON";case "SNUL"->"RTNUL";case "SMIX"->"RTMIX";default->"RTEMP";};
            String targetId="oracle-dml-"+prefix+"-20261005";String acceptedBody;
            try(var c=OracleRegistryDbProbe.connect(registry);var s=c.prepareStatement("SELECT BINDING_JSON FROM SH_ORACLE_INPUT_BINDING WHERE SOURCE_ID=? AND WINDOW_KEY=?")) {
                s.setString(1,"oracle-journal-"+name);s.setString(2,"journal-"+name);try(var rs=s.executeQuery()){require(rs.next(),"ACCEPTED_BINDING_MISSING");acceptedBody=rs.getString(1);require(!rs.next(),"ACCEPTED_BINDING_AMBIGUOUS");}
            }
            var approved=JSON.readValue(acceptedBody,OracleInputBinding.class);var e=approved.evidence();
            boolean existing=rm.findApproved(source,key)!=null;
            require(!existing || (resume && name.equals("SNON") && tm.findByRef("SOURCE",source).size()==1
                    && tm.findByRef("SOURCE",source).get(0).getCheckpointJson()==null),"RUNTIME_ALREADY_REGISTERED_DO_NOT_RERUN");
            if(!existing)
            try(var c=OracleRegistryDbProbe.connect(owned);var s=c.prepareStatement("INSERT INTO SH_ORACLE_INPUT_BINDING (ID,SOURCE_ID,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,CONSUMER_ID,SCHEMA_GENERATION,STATE,BINDING_JSON,REGISTERED_AT,REGISTERED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){
                s.setLong(1,ids.nextId());s.setString(2,source);s.setString(3,key);s.setLong(4,1);s.setLong(5,1);s.setString(6,consumer);s.setString(7,e.schemaSha256());s.setString(8,"APPROVED_BOUNDED");String body=acceptedBody;s.setCharacterStream(9,new java.io.StringReader(body),body.length());s.setTimestamp(10,Timestamp.valueOf(LocalDateTime.now()));s.setString(11,"frozen-runtime-fixture");s.executeUpdate();
            }
            if(!existing){var window=new CapWindow();window.setId(ids.nextId());window.setSourceId(source);window.setWindowKey(key);window.setEngineType("oracle");window.setIssueSeqStart(1);window.setIssueSeqEnd(1);window.setHeadPath(approved.capturePaths().get(0));window.setFrozen(1);window.setStatus(WindowStatus.PENDING);window.setRegisteredAt(LocalDateTime.now());wm.insert(window);}
            var names=new ArrayList<String>();var hashes=new LinkedHashMap<String,String>();for(int i=0;i<approved.capturePaths().size();i++){String file=Path.of(approved.capturePaths().get(i)).getFileName().toString();names.add(file);hashes.put(file,e.captures().get(i).journalSha256());}
            var descriptor=new WindowDescriptor("oracle",source,key,names,hashes,1,1,0,true);
            WindowSource windows=()->wm.findBySourceAndWindowKey(source,key).getStatus()==WindowStatus.PENDING?List.of(new WindowSource.PendingWindow(descriptor,Path.of(approved.capturePaths().get(0)))):List.of();
            var binding=new OracleInputBindingService(rm,JSON,consumer);var input=new OracleApprovedInput(binding,JSON);var target=new OracleJdbcTarget(targetDs,new OracleTargetBatchWriter.Target(secret.get(0),prefix,targetId),input,JSON);
            var decoder=new DecodeBatchService(new ConnectorRegistry(),bm,ids,binding);decoder.setOracleDecoder(input);var gate=new PublishGateService(bm,lm,gm,ids);
            var manager=new DataSourceTransactionManager(ds);Path ackDir=Path.of(args[2],name);Files.createDirectories(ackDir);
            TaskMapper fault=(TaskMapper)Proxy.newProxyInstance(OracleBoundedFacadeProbe.class.getClassLoader(),new Class[]{TaskMapper.class},(proxy,method,values)->{
                try{Object result=method.invoke(tm,values);if(method.getName().equals("updateCheckpoint"))throw new IllegalStateException("RUNTIME_AFTER_CHECKPOINT_SQL_BEFORE_COMMIT");return result;}catch(InvocationTargetException ex){throw ex.getCause();}
            });
            var failedControl=new OracleControlPublication(manager,locks,binding,target,bm,lm,gm,wm,fault,ids,JSON,targetId);
            var firstPort=new OracleBoundedPublication(input,failedControl,bm,tm,ackDir,JSON);
            var firstFacade=new PipelineFacade(windows,decoder,gate,tm,ids);firstFacade.setOraclePublication(new OraclePublicationPort(){
                public void publish(long id,WindowDescriptor w,DecodeBatchService.DecodeOutcome d){try{firstPort.publish(id,w,d);}catch(RuntimeException ex){ex.printStackTrace();throw ex;}}
                public void recover(PipelineTask task){firstPort.recover(task);}
            });
            var first=existing?firstFacade.resumeTask(tm.findByRef("SOURCE",source).get(0).getId(),w->CapDecoder.DecodeContext.of(source,""))
                    :firstFacade.runNewTask("CONSUME","SOURCE",source,w->CapDecoder.DecodeContext.of(source,""));
            System.out.println("FIRST_FACADE_RESULT|"+JSON.writeValueAsString(first));
            require(first.runs().stream().flatMap(r->r.reasons().stream()).anyMatch(r->r.contains("RUNTIME_AFTER_CHECKPOINT_SQL_BEFORE_COMMIT")),"DID_NOT_REACH_REAL_CONTROL_SQL_FAILURE");
            require(first.finalState()==TaskState.FAILED && tm.findById(first.taskId()).getCheckpointJson()==null && wm.findBySourceAndWindowKey(source,key).getStatus()==WindowStatus.PENDING,"CONTROL_FAILURE_ADVANCED_STATE");
            try(var files=Files.list(ackDir)){require(files.findAny().isEmpty(),"ACK_CREATED_BEFORE_CONTROL_COMMIT");}
            try(var c=OracleRegistryDbProbe.connect(owned)){require(sourceCount(c,source)==0,"CONTROL_FAILURE_PUBLISHED_LEDGER");}
            int applied;
            try(var c=targetDs.getConnection();var s=c.createStatement();var rs=s.executeQuery("select count(*) from "+secret.get(0)+"."+prefix+"_APPLIED")){rs.next();applied=rs.getInt(1);require(applied==e.events().size(),"TARGET_FIRST_COMMIT_EVENTS_MISMATCH");}
            var control=new OracleControlPublication(manager,locks,binding,target,bm,lm,gm,wm,tm,ids,JSON,targetId);
            var facade=new PipelineFacade(windows,decoder,gate,tm,ids);facade.setOraclePublication(new OracleBoundedPublication(input,control,bm,tm,ackDir,JSON));
            String ackName=Sha256.hex((source+"\0"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".ack.json";Path ack=ackDir.resolve(ackName);Files.writeString(ack,"deliberate-ack-conflict");
            var second=facade.resumeTask(first.taskId(),w->CapDecoder.DecodeContext.of(source,""));
            require(second.runs().stream().flatMap(r->r.reasons().stream()).anyMatch(r->r.contains("ORACLE_DURABLE_ACK_FAILED")),"DID_NOT_REACH_REAL_ACK_FAILURE");
            require(second.finalState()==TaskState.FAILED && tm.findById(first.taskId()).getCheckpointJson()!=null && windows.pendingWindows().isEmpty(),"ACK_FAILURE_DID_NOT_PRESERVE_COMMITTED_CONTROL");
            String checkpoint=tm.findById(first.taskId()).getCheckpointJson();Files.move(ack,ack.resolveSibling(ackName+".failed-retained"));
            // Recreate runtime instances to exercise recovery without the old Facade's in-memory state.
            var restarted=new PipelineFacade(windows,new DecodeBatchService(new ConnectorRegistry(),bm,ids),gate,tm,ids);
            restarted.setOraclePublication(new OracleBoundedPublication(new OracleApprovedInput(binding,JSON),control,bm,tm,ackDir,JSON));
            var third=restarted.resumeTask(first.taskId(),w->null);require(third.finalState()==TaskState.DONE && third.runs().isEmpty(),"EMPTY_QUEUE_RECOVERY_FAILED");
            require(checkpoint.equals(tm.findById(first.taskId()).getCheckpointJson()) && JSON.readTree(Files.readString(ack)).equals(JSON.readTree(checkpoint)),"RECOVERY_ACK_OR_CHECKPOINT_CHANGED");
            byte[] bytes=Files.readAllBytes(ack);require(restarted.resumeTask(first.taskId(),w->null).finalState()==TaskState.DONE && Arrays.equals(bytes,Files.readAllBytes(ack)),"DONE_REPLAY_ACK_CHANGED");
            try(var c=OracleRegistryDbProbe.connect(owned)){require(sourceCount(c,source)==e.events().size(),"FINAL_LEDGER_COUNT_MISMATCH");}
            results.add(Map.of("case",name,"first_target_events",applied,"actual_control_sql_failure_no_checkpoint_or_ack",true,"ack_conflict_after_control_commit",true,"empty_queue_recovery_and_done_repeat",true,"checkpoint",JSON.readTree(checkpoint),"ack_sha256",Sha256.hex(bytes)));
            Files.writeString(Path.of(args[3]),JSON.writeValueAsString(results));System.out.println("ORACLE_REAL_FACADE_CASE_VERIFIED|"+name);
        }
        System.out.println("ORACLE_REAL_FACADE_VERIFIED|4");
    }
}
