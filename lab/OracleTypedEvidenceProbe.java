import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.streamhouse.spi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.common.SnowflakeId;
import com.streamhouse.pipeline.service.*;
import com.streamhouse.pipeline.entity.*;
import com.streamhouse.pipeline.testsupport.*;
import java.nio.file.*;
import java.util.*;

/** Frozen verified byte inputs produce typed evidence; legacy envelope fields remain unavailable. */
public class OracleTypedEvidenceProbe {
    public static void main(String[] args)throws Exception {
        var inputs=new ArrayList<CaptureJournal.Verified>();
        for(int i=3;i<args.length;i+=2)inputs.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var batch=CapturedHeapBatch.read(inputs,Path.of(args[0]),args[1],Long.parseLong(args[2]));
        var projection=OracleBatchProjection.project(batch);
        for(var e:projection.entries()) {
            var v=e.envelope();
            if(v.source().server()!=null || v.source().database()!=null || v.source().databaseGuid()!=null
                    || v.source().recoveryFork()!=null || v.transaction().beginLsn()!=null
                    || v.transaction().recordLsn()!=null || v.transaction().commitLsn()!=null)
                throw new AssertionError("LEGACY_ORACLE_IDENTITY_WAS_FABRICATED");
        }
        var mapper=new ObjectMapper();String text=mapper.writeValueAsString(projection.evidence());
        if(!mapper.readValue(text,OracleBatchEvidence.class).equals(projection.evidence()))
            throw new AssertionError("TYPED_EVIDENCE_JSON_ROUNDTRIP");
        var batches=new FakePipelineBatchMapper();var ledger=new FakeEventLedgerMapper();
        var gates=new FakePublishGateMapper();var tasks=new FakeTaskMapper();var ids=new SnowflakeId(9);
        var envelopes=projection.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        var registry=new ConnectorRegistry();
        registry.register(new EngineConnector() {
            public ConnectorDescriptor descriptor() {return new ConnectorDescriptor("oracle","bounded","ORACAP01","lab typed transport only");}
            public WindowScanner windowScanner() {throw new UnsupportedOperationException();}
            public CapDecoder decoder() {return (path,context)->new CapDecoder.DecodeResult(envelopes.iterator(),0,
                    "bounded typed transport",Map.of(),null,projection.evidence());}
        });
        boolean[] seen={false},consumed={false};
        var gate=new PublishGateService(batches,ledger,gates,ids) {
            @Override public GateOutcome gate(PipelineBatch b,List<EventEnvelope> events,OracleBatchEvidence proof) {
                if(!projection.evidence().equals(proof))throw new AssertionError("FACADE_LOST_TYPED_EVIDENCE");
                seen[0]=true;return super.gate(b,events,proof);
            }
        };
        var cap=Path.of(args[3]);String name=cap.getFileName().toString();
        var window=new WindowDescriptor("oracle","oracle-typed-frozen","typed-window",List.of(name),Map.of(name,args[4]),1,1,0,true);
        WindowSource windows=new WindowSource() {
            public List<PendingWindow> pendingWindows() {return List.of(new PendingWindow(window,cap));}
            public void markConsumed(String sourceId,String key) {consumed[0]=true;}
        };
        var facade=new PipelineFacade(windows,new DecodeBatchService(registry,batches,ids),gate,tasks,ids,
                request->{throw new AssertionError("UNBOUND_ORACLE_REACHED_TARGET");},true);
        var result=facade.runNewTask("CONSUME","SOURCE","oracle-typed-frozen",w->new CapDecoder.DecodeContext(
                "oracle-typed-frozen",null,Map.of("allow_public_oracle","true")));
        if(!seen[0] || consumed[0] || result.finalState()!=TaskState.FAILED || !ledger.rows.isEmpty()
                || tasks.rows.get(0).getCheckpointJson()!=null
                || !gates.rows.get(0).getReasons().contains("ORACLE_TRUSTED_BINDING_REQUIRED"))
            throw new AssertionError("UNBOUND_ORACLE_GATE_DID_NOT_FAIL_CLOSED");
        var publicResult=new OracleConnector().decoder().decode(cap,new CapDecoder.DecodeContext(
                "oracle-typed-frozen",null,Map.of("allow_public_oracle","true")));
        if(publicResult.rc()==0 || publicResult.events().hasNext() || publicResult.oracleEvidence()!=null)
            throw new AssertionError("OPTIONS_GRANTED_ORACLE_PUBLIC_DECODE");
        System.out.println(mapper.writeValueAsString(Map.of("evidence",projection.evidence(),"pipeline",
                Map.of("typed_evidence_reached_gate",seen[0],"final_state",result.finalState().name(),
                        "ledger_written",ledger.rows.size(),"target_written",0,"checkpoint_advanced",false,
                        "source_consumed",consumed[0],"ordinary_options_authorized_public_decode",false))));
    }
}
