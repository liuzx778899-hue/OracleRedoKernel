import com.fasterxml.jackson.databind.*;
import com.streamhouse.app.appdb.*;
import com.streamhouse.app.baseline.*;
import com.streamhouse.common.SnowflakeId;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.oracle.*;
import com.streamhouse.pipeline.entity.*;
import com.streamhouse.pipeline.mapper.*;
import com.streamhouse.pipeline.service.*;
import com.streamhouse.spi.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Own six control schemas only; real JDBC/MyBatis plus actual frozen kernel results, no source business writes. */
public class OracleRegistryDbProbe {
    record Target(String dialect,String stage,String database,String url,String user,String password) {}
    record Case(String name,Path baseline,String baselineSha,long leaf,List<String> inputs,OracleBatchProjection.Batch projection) {}
    static final ObjectMapper JSON=new ObjectMapper();
    static final String SOURCE="oracle-registry-source";
    static String driver(String dialect) {return switch(dialect) {case "mysql"->"com.mysql.cj.jdbc.Driver";case "sqlserver"->"com.microsoft.sqlserver.jdbc.SQLServerDriver";default->"oracle.jdbc.OracleDriver";};}
    static Connection connect(Target t)throws SQLException {return DriverManager.getConnection(t.url(),t.user(),t.password());}
    static Flyway flyway(Target t,String target) {
        var cfg=Flyway.configure().dataSource(t.url(),t.user(),t.password()).locations(
                Arrays.stream(new String[]{"app","gateway","pipeline","recon","recovery","codetable","alert"})
                        .map(m->"classpath:db/"+t.dialect()+"/"+m).toArray(String[]::new));
        if(target!=null)cfg.target(target);return cfg.load();
    }
    static SqlSessionFactory mapper(Target t)throws Exception {
        var cfg=new Configuration(new Environment("owned-registry",new JdbcTransactionFactory(),
                new UnpooledDataSource(driver(t.dialect()),t.url(),t.user(),t.password())));
        cfg.setMapUnderscoreToCamelCase(true);cfg.setDatabaseId(t.dialect());
        for(String path:List.of("mapper/app/OracleInputBindingMapper.xml","mapper/pipeline/PipelineBatchMapper.xml",
                "mapper/pipeline/EventLedgerMapper.xml","mapper/pipeline/PublishGateMapper.xml","mapper/pipeline/TaskMapper.xml")) {
            try(var input=OracleRegistryDbProbe.class.getClassLoader().getResourceAsStream(path)) {
                if(input==null)throw new AssertionError("MAPPER_RESOURCE_MISSING:"+path);
                new XMLMapperBuilder(input,cfg,path,cfg.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(cfg);
    }
    static long count(Connection c,String sql,long batchId)throws Exception {
        try(var p=c.prepareStatement(sql)){p.setLong(1,batchId);try(var r=p.executeQuery()){if(!r.next())throw new AssertionError();return r.getLong(1);}}
    }
    static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static void provision(List<Target> targets)throws Exception {
        for(var t:targets)if(!t.dialect().equals("oracle")) {
            require(t.database().equals("orkctrl_new")||t.database().equals("orkctrl_upg"),"CONTROL_DATABASE_SCOPE");
            String url=t.dialect().equals("mysql")?t.url().replace("/"+t.database()+"?","/?"):t.url().replace(";databaseName="+t.database(),"");
            try(var c=DriverManager.getConnection(url,t.user(),t.password());var s=c.createStatement()) {s.execute("CREATE DATABASE "+t.database());}
        }
    }
    public static void main(String[] args)throws Exception {
        var targets=Arrays.asList(JSON.readValue(Files.readString(Path.of(args[0])),Target[].class));
        if(args.length==3)provision(targets);
        else {
            require(args[3].equals("--resume-migration"),"UNEXPECTED_RESUME_MODE");
            for(var t:targets)try(var c=connect(t)) {require(c.isValid(10),"OWNED_CONTROL_NOT_READY");}
        }
        var cases=new ArrayList<Case>();
        JsonNode configs=JSON.readTree(Files.readString(Path.of(args[1])));
        for(var config:configs) {
            var inputs=new ArrayList<CaptureJournal.Verified>();var pairs=new ArrayList<String>();
            for(int i=0;i<config.get("captures").size();i+=2) {
                String path=config.get("captures").get(i).asText(),hash=config.get("captures").get(i+1).asText();
                inputs.add(new CaptureJournal().read(Path.of(path),hash));pairs.add(path);pairs.add(hash);
            }
            Path baseline=Path.of(config.get("baseline_path").asText());String hash=config.get("baseline_sha256").asText();long leaf=config.get("leaf").asLong();
            cases.add(new Case(config.get("prefix").asText(),baseline,hash,leaf,pairs,
                    OracleBatchProjection.project(CapturedHeapBatch.read(inputs,baseline,hash,leaf))));
        }
        var results=new ArrayList<Map<String,Object>>();long enrollmentId=1;
        for(var t:targets) {
            if(t.stage().equals("UPG")) {
                flyway(t,"728").migrate();
                try(var c=connect(t);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM SH_BASELINE_CREDENTIAL")){require(r.next(),"PRE_UPGRADE_TABLE_MISSING");}
            }
            var migration=flyway(t,null);migration.migrate();migration.validate();require(migration.migrate().migrationsExecuted==0,"SECOND_MIGRATION_NOT_IDEMPOTENT");
            var factory=mapper(t);
            for(var input:cases) {
                String windowKey="bounded-"+input.name(),consumer="consumer-"+input.name();
                var projection=input.projection();var evidence=projection.evidence();
                var events=projection.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
                var paths=new ArrayList<String>();for(int i=0;i<input.inputs().size();i+=2)paths.add(input.inputs().get(i));
                var binding=new OracleInputBinding(input.baseline().toString(),paths,evidence,OracleInputBindingService.envelopesSha256(JSON,events));
                String body=JSON.writeValueAsString(binding);
                try(var c=connect(t);var s=c.prepareStatement("INSERT INTO SH_ORACLE_INPUT_BINDING (ID,SOURCE_ID,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,CONSUMER_ID,SCHEMA_GENERATION,STATE,BINDING_JSON,REGISTERED_AT,REGISTERED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                    s.setLong(1,enrollmentId++);s.setString(2,SOURCE);s.setString(3,windowKey);s.setLong(4,1);s.setLong(5,1);
                    s.setString(6,consumer);s.setString(7,evidence.schemaSha256());s.setString(8,"APPROVED_BOUNDED");
                    s.setCharacterStream(9,new StringReader(body),body.length());s.setTimestamp(10,Timestamp.valueOf(LocalDateTime.now()));s.setString(11,"bounded-approved-raw-receipts");s.executeUpdate();
                }
                try(var session=factory.openSession(true)) {
                    var registry=session.getMapper(OracleInputBindingMapper.class);
                    var service=new OracleInputBindingService(registry,JSON,consumer);
                    var names=paths.stream().map(p->Path.of(p).getFileName().toString()).toList();var hashes=new LinkedHashMap<String,String>();
                    for(int i=0;i<paths.size();i++)hashes.put(names.get(i),evidence.captures().get(i).journalSha256());
                    var window=new WindowDescriptor("oracle",SOURCE,windowKey,names,hashes,1,1,0,true);
                    require(service.check(window,Path.of(paths.get(0)),Map.of("allow_public_oracle","true","consumerId","forged","baselinePath","missing"))==null,"REGISTERED_FILE_GUARD_REJECTED");
                    var b=new PipelineBatch();var ids=new SnowflakeId(10);b.setId(ids.nextId());b.setSourceId(SOURCE);b.setEngineType("oracle");b.setWindowKey(windowKey);
                    b.setIssueSeqStart(1);b.setIssueSeqEnd(1);b.setRc(0);b.setEventCount(events.size());b.setInputSha256(evidence.captures().get(0).journalSha256());
                    b.setState(BatchState.GATED);b.setCreatedAt(LocalDateTime.now());b.setUpdatedAt(LocalDateTime.now());
                    require(service.check(b,events,evidence)==null,"REGISTERED_TYPED_GATE_REJECTED");
                    require(new OracleInputBindingService(registry,JSON,"other").check(b,events,evidence).contains("SCOPE"),"WRONG_CONSUMER_ACCEPTED");
                    b.setIssueSeqStart(44);require(service.check(b,events,evidence).contains("SCOPE"),"REDO_SEQUENCE_USED_AS_TRANSPORT");b.setIssueSeqStart(1);
                    require(service.check(b,events,null).contains("TYPED"),"MISSING_TYPED_EVIDENCE_ACCEPTED");
                    b.setInputSha256("0".repeat(64));require(service.check(b,events,evidence).contains("BINDING"),"WRONG_INPUT_ACCEPTED");b.setInputSha256(evidence.captures().get(0).journalSha256());
                    var forged=new ArrayList<>(events);forged.add(new EventEnvelope("forged",null,null,null,null,null,null,List.of(),null));
                    b.setEventCount(forged.size());require(service.check(b,forged,evidence).contains("BINDING"),"FORGED_ENVELOPE_ACCEPTED");b.setEventCount(events.size());
                    b.setWindowKey("absent");require(service.check(b,events,evidence).contains("MISSING"),"UNREGISTERED_WINDOW_ACCEPTED");b.setWindowKey(windowKey);
                    var batches=session.getMapper(PipelineBatchMapper.class);batches.insert(b);
                    var gate=new PublishGateService(batches,session.getMapper(EventLedgerMapper.class),session.getMapper(PublishGateMapper.class),ids);
                    gate.setOracleEvidenceValidator(service);var outcome=gate.gate(b,events,evidence);
                    require(outcome.verdict()==GateVerdict.REFUSED && outcome.reasons().equals(List.of("ORACLE_ATOMIC_TARGET_NOT_ACCEPTED")),"REGISTRATION_GRANTED_TARGET_PERMISSION");
                    try(var c=connect(t)) {
                        require(count(c,"SELECT COUNT(*) FROM SH_EVENT_LEDGER WHERE BATCH_ID=?",b.getId())==0,"REAL_LEDGER_WRITTEN");
                        require(count(c,"SELECT COUNT(*) FROM SH_PUBLISH_GATE WHERE BATCH_ID=?",b.getId())==1,"REAL_GATE_REFUSAL_NOT_PERSISTED");
                    }
                    results.add(Map.of("dialect",t.dialect(),"stage",t.stage(),"case",input.name(),"events",events.size(),
                            "guard_and_typed_binding_matched",true,"real_refusal_persisted",true,"ledger_written",0,"public_oracle_enabled",false));
                }
            }
        }
        Files.writeString(Path.of(args[2]),JSON.writeValueAsString(results));System.out.println("OWNED_REGISTRY_MATRIX_VERIFIED|"+results.size());
    }
}
