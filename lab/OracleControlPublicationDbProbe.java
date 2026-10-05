import com.fasterxml.jackson.databind.*;
import com.streamhouse.app.appdb.*;
import com.streamhouse.app.baseline.*;
import com.streamhouse.app.port.*;
import com.streamhouse.common.SnowflakeId;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.oracle.*;
import com.streamhouse.spi.OracleBatchEvidence;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.*;
import com.streamhouse.pipeline.entity.*;
import com.streamhouse.pipeline.mapper.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/** Real product control transaction plus independent binary target verifier. Not a full Facade run. */
public class OracleControlPublicationDbProbe {
    static final ObjectMapper JSON=new ObjectMapper();
    record Case(String name,OracleBatchProjection.Batch projection,Path baseline,List<String> paths) {}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static long count(Connection c,String table,long id)throws Exception{return OracleRegistryDbProbe.count(c,"SELECT COUNT(*) FROM "+table+" WHERE BATCH_ID=?",id);}
    public static void main(String[] args)throws Exception {
        var targets=Arrays.asList(JSON.readValue(Files.readString(Path.of(args[0])),OracleRegistryDbProbe.Target[].class));
        boolean resume=args.length==6 && args[5].equals("--resume");
        if(!resume)for(var t:targets)if(t.stage().equals("NEW") && !t.dialect().equals("oracle")){
            require(t.database().equals("orkpub_new"),"NEW_DATABASE_SCOPE");
            String url=t.dialect().equals("mysql")?t.url().replace("/orkpub_new?","/?"):t.url().replace(";databaseName=orkpub_new","");
            try(var c=DriverManager.getConnection(url,t.user(),t.password());var s=c.createStatement()){s.execute("CREATE DATABASE orkpub_new");}
        }
        var cases=new ArrayList<Case>();
        for(var config:JSON.readTree(Files.readString(Path.of(args[1])))){
            var captures=new ArrayList<CaptureJournal.Verified>();var paths=new ArrayList<String>();
            for(int i=0;i<config.get("captures").size();i+=2){String p=config.get("captures").get(i).asText();paths.add(p);captures.add(new CaptureJournal().read(Path.of(p),config.get("captures").get(i+1).asText()));}
            Path baseline=Path.of(config.get("baseline_path").asText());
            cases.add(new Case(config.get("prefix").asText(),OracleBatchProjection.project(CapturedHeapBatch.read(captures,baseline,config.get("baseline_sha256").asText(),config.get("leaf").asLong())),baseline,paths));
        }
        var secret=Files.readAllLines(Path.of(args[2]));var ids=new SnowflakeId(13);var results=new ArrayList<Map<String,Object>>();Path progress=Path.of(args[4]);
        if(Files.exists(progress))results.addAll(JSON.readValue(Files.readString(progress),List.class));
        for(var t:targets){
            var migration=OracleRegistryDbProbe.flyway(t,null);migration.migrate();migration.validate();require(migration.migrate().migrationsExecuted==0,"MIGRATION_NOT_IDEMPOTENT");
            var factory=OracleRegistryDbProbe.mapper(t);var cfg=factory.getConfiguration();var ds=cfg.getEnvironment().getDataSource();
            cfg.setEnvironment(new Environment("oracle-control",new SpringManagedTransactionFactory(),ds));
            for(String path:List.of("mapper/app/OraclePublicationLockMapper.xml","mapper/gateway/CapWindowMapper.xml"))try(var input=OracleControlPublicationDbProbe.class.getClassLoader().getResourceAsStream(path)){require(input!=null,"MAPPER_MISSING");new XMLMapperBuilder(input,cfg,path,cfg.getSqlFragments()).parse();}
            var session=new SqlSessionTemplate(factory);var bm=session.getMapper(PipelineBatchMapper.class);var lm=session.getMapper(EventLedgerMapper.class);var gm=session.getMapper(PublishGateMapper.class);var wm=session.getMapper(CapWindowMapper.class);var tm=session.getMapper(TaskMapper.class);var rm=session.getMapper(OracleInputBindingMapper.class);var locks=session.getMapper(OraclePublicationLockMapper.class);
            for(var input:cases){
                String key=t.dialect()+":"+t.stage()+":"+input.name();if(results.stream().anyMatch(x->key.equals(x.get("key"))))continue;
                String source="oracle-journal-"+input.name(),window="journal-"+input.name(),consumer="consumer-"+input.name();var p=input.projection();var e=p.evidence();var events=p.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
                var approved=new OracleInputBinding(input.baseline().toString(),input.paths(),e,OracleInputBindingService.envelopesSha256(JSON,events));
                if(rm.findApproved(source,window)==null)try(var c=OracleRegistryDbProbe.connect(t);var s=c.prepareStatement("INSERT INTO SH_ORACLE_INPUT_BINDING (ID,SOURCE_ID,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,CONSUMER_ID,SCHEMA_GENERATION,STATE,BINDING_JSON,REGISTERED_AT,REGISTERED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){
                    String body=JSON.writeValueAsString(approved);s.setLong(1,ids.nextId());s.setString(2,source);s.setString(3,window);s.setLong(4,1);s.setLong(5,1);s.setString(6,consumer);s.setString(7,e.schemaSha256());s.setString(8,"APPROVED_BOUNDED");s.setCharacterStream(9,new java.io.StringReader(body),body.length());s.setTimestamp(10,Timestamp.valueOf(LocalDateTime.now()));s.setString(11,"bounded-control-transaction-fixture");s.executeUpdate();
                }
                var existing=bm.findBySourceAndWindowKey(source,window);PipelineBatch batch;
                if(existing.isEmpty()){
                    batch=new PipelineBatch();batch.setId(ids.nextId());batch.setSourceId(source);batch.setWindowKey(window);batch.setEngineType("oracle");batch.setIssueSeqStart(1);batch.setIssueSeqEnd(1);batch.setRc(0);batch.setEventCount(events.size());batch.setInputSha256(e.captures().get(0).journalSha256());batch.setState(BatchState.GATED);batch.setCreatedAt(LocalDateTime.now());batch.setUpdatedAt(LocalDateTime.now());bm.insert(batch);
                }else{require(existing.size()==1,"UNEXPECTED_BATCHES");batch=existing.get(0);}
                var tasks=tm.findByRef("SOURCE",source);PipelineTask task;
                if(tasks.isEmpty()){
                    task=new PipelineTask();task.setId(ids.nextId());task.setTaskType("CONSUME");task.setRefType("SOURCE");task.setRefId(source);task.setState(TaskState.RUNNING);task.setAttempts(1);task.setCreatedAt(LocalDateTime.now());task.setUpdatedAt(LocalDateTime.now());tm.insert(task);
                }else{require(tasks.size()==1,"UNEXPECTED_TASKS");task=tasks.get(0);}
                if(wm.findBySourceAndWindowKey(source,window)==null){
                    var w=new CapWindow();w.setId(ids.nextId());w.setSourceId(source);w.setWindowKey(window);w.setEngineType("oracle");w.setIssueSeqStart(1);w.setIssueSeqEnd(1);w.setHeadPath(input.paths().get(0));w.setFrozen(1);w.setStatus(WindowStatus.PENDING);w.setRegisteredAt(LocalDateTime.now());wm.insert(w);
                }
                var binding=new OracleInputBindingService(rm,JSON,consumer);String prefix=switch(input.name()){case "SNON"->"PCNON";case "SNUL"->"PCNUL";case "SMIX"->"PCMIX";default->"PCEMP";};String targetId="oracle-dml-"+prefix+"-20261005";
                OracleTargetCommitVerifier verifier=(b,ev,typed)->{
                    try{
                        var receipt=new OracleTargetBatchWriter().verifyCommitted(DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1)),new OracleTargetBatchWriter.Target(secret.get(0),prefix,targetId),p);
                        return new OracleTargetCommitVerifier.Receipt(receipt.targetId(),p.evidence().source(),p.evidence().baselineSha256(),receipt.schemaSha256(),receipt.batchSha256(),OracleInputBindingService.envelopesSha256(JSON,events),receipt.events());
                    }catch(Exception ex){throw new IllegalStateException("REAL_TARGET_PROOF_FAILED",ex);}
                };
                var manager=new DataSourceTransactionManager(ds);
                if(wm.findBySourceAndWindowKey(source,window).getStatus()==WindowStatus.PENDING){
                    TaskMapper fault=(TaskMapper)Proxy.newProxyInstance(OracleControlPublicationDbProbe.class.getClassLoader(),new Class[]{TaskMapper.class},(proxy,method,values)->{
                        try{Object result=method.invoke(tm,values);if(method.getName().equals("updateCheckpoint"))throw new IllegalStateException("LAB_AFTER_CHECKPOINT_SQL_BEFORE_CONTROL_COMMIT");return result;}catch(InvocationTargetException ex){throw ex.getCause();}
                    });
                    var failed=new OracleControlPublication(manager,locks,binding,verifier,bm,lm,gm,wm,fault,ids,JSON,targetId);
                    try{failed.publish(task.getId(),batch,events,e);throw new AssertionError("CONTROL_FAILURE_IGNORED");}catch(IllegalStateException ex){require(ex.getMessage().equals("LAB_AFTER_CHECKPOINT_SQL_BEFORE_CONTROL_COMMIT"),"UNEXPECTED_CONTROL_REFUSAL:"+ex.getMessage());}
                    try(var c=OracleRegistryDbProbe.connect(t)){require(count(c,"SH_EVENT_LEDGER",batch.getId())==0 && count(c,"SH_PUBLISH_GATE",batch.getId())==0,"PARTIAL_CONTROL_ROWS_PUBLISHED");}
                    require(tm.findById(task.getId()).getCheckpointJson()==null && wm.findBySourceAndWindowKey(source,window).getStatus()==WindowStatus.PENDING && bm.findById(batch.getId()).getState()==BatchState.GATED,"CONTROL_ROLLBACK_DID_NOT_RESTORE_STATE");
                }
                var journal=new OracleControlPublication(manager,locks,binding,verifier,bm,lm,gm,wm,tm,ids,JSON,targetId);
                long taskId=task.getId();var current=batch;var pool=Executors.newFixedThreadPool(2);List<Long> written;
                try{
                    var start=new CountDownLatch(1);var a=pool.submit(()->{start.await();return journal.publish(taskId,current,events,e);});var b=pool.submit(()->{start.await();return journal.publish(taskId,current,events,e);});start.countDown();written=List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS));
                }finally{pool.shutdownNow();}
                require(written.stream().mapToLong(Long::longValue).sum()==(task.getCheckpointJson()==null?events.size():0),"CONCURRENT_LEDGER_NOT_SINGLE_PUBLICATION");
                try(var c=OracleRegistryDbProbe.connect(t)){require(count(c,"SH_EVENT_LEDGER",batch.getId())==events.size() && count(c,"SH_PUBLISH_GATE",batch.getId())==1,"WRONG_COMMITTED_CONTROL_ROWS");}
                var checkpoint=tm.findById(taskId).getCheckpointJson();require(JSON.treeToValue(JSON.readTree(checkpoint).get("oracleEvidence"),OracleBatchEvidence.class).equals(e),"CONTROL_CHECKPOINT_TYPED_FIELDS_CHANGED");
                require(wm.findBySourceAndWindowKey(source,window).getStatus()==WindowStatus.CONSUMED,"WINDOW_NOT_CONSUMED");
                require(journal.publish(taskId,current,events,e)==0 && checkpoint.equals(tm.findById(taskId).getCheckpointJson()),"CONTROL_REPLAY_NOT_STABLE");
                results.add(Map.of("key",key,"events",events.size(),"real_checkpoint_sql_failure_rolled_back_all",true,"concurrent_publication_single",true,"replay_ledger_written",0,"target_dml",0,"facade_executed",false));
                Files.writeString(progress,JSON.writeValueAsString(results));System.out.println("CONTROL_CASE_VERIFIED|"+key);
            }
        }
        require(results.size()==24,"INCOMPLETE_MATRIX");Files.writeString(Path.of(args[3]),JSON.writeValueAsString(results));System.out.println("ORACLE_CONTROL_PUBLICATION_MATRIX_VERIFIED|24");
    }
}
