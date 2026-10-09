package com.streamhouse.app.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.appdb.OraclePublicationLockMapper;
import com.streamhouse.app.baseline.*;
import com.streamhouse.common.Sha256;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.CapWindowMapper;
import com.streamhouse.pipeline.entity.*;
import com.streamhouse.pipeline.mapper.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.*;

/** Actual saved raw/binding/CPACK decoding; DB, target and acknowledgment ports are substitutes. */
public class SavedCommittedChainProbe {
    public static void main(String[] args)throws Exception {
        var json=new ObjectMapper();var entries=json.readTree(Files.readString(Path.of(args[0])));
        var locks=mock(OraclePublicationLockMapper.class);var tasks=mock(TaskMapper.class);
        var batches=mock(PipelineBatchMapper.class);var ledger=mock(EventLedgerMapper.class);
        var gates=mock(PublishGateMapper.class);var windows=mock(CapWindowMapper.class);
        var registry=mock(OracleInputBindingService.class);var target=mock(OracleTargetCommitVerifier.class);
        var acknowledgments=mock(OracleBoundedPublication.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var verifier=new OracleCommittedInput[1];
        var input=spy(new OracleApprovedInput(registry,json,()->verifier[0]));
        verifier[0]=new OracleCommittedInput(locks,tasks,batches,ledger,gates,windows,input,target,acknowledgments,json,manager);
        var expectedIds=new ArrayList<String>();PipelineBatch last=null;long lastTask=0;
        for(var entry:entries) {
            String saved=entry.get("checkpoint").asText();
            if(!Sha256.hex(saved.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(entry.get("ackSha256").asText()))throw new AssertionError("saved ACK changed");
            var cp=json.readTree(saved);String source=cp.get("sourceId").asText();long taskId=entry.get("taskId").longValue();
            var approved=json.treeToValue(entry.get("binding"),OracleInputBinding.class);
            var b=new PipelineBatch();b.setId(cp.get("lastBatchId").longValue());b.setSourceId(source);b.setEngineType("oracle");
            b.setState(BatchState.PUBLISHED);b.setRc(0);b.setWindowKey(cp.get("lastWindowKey").asText());
            b.setEventCount(approved.evidence().events().size());
            int members=approved.evidence().captures().size();b.setIssueSeqStart(members==2?1:members);b.setIssueSeqEnd(members);
            when(batches.findById(b.getId())).thenReturn(b);when(registry.approved(b)).thenReturn(approved);
            var task=new PipelineTask();task.setId(taskId);task.setState(TaskState.DONE);task.setRefType("SOURCE");task.setRefId(source);task.setCheckpointJson(saved);
            when(tasks.findById(taskId)).thenReturn(task);when(locks.lockSource(source)).thenReturn(List.of(12001L));
            var window=new CapWindow();window.setSourceId(source);window.setEngineType("oracle");window.setStatus(WindowStatus.CONSUMED);
            window.setFrozen(1);window.setIssueSeqStart(b.getIssueSeqStart());window.setIssueSeqEnd(b.getIssueSeqEnd());
            when(windows.findBySourceAndWindowKey(source,b.getWindowKey())).thenReturn(window);
            var gate=new PublishGateRecord();gate.setBatchId(b.getId());gate.setVerdict(GateVerdict.PASS);when(gates.findByBatchId(b.getId())).thenReturn(List.of(gate));
            var rows=new ArrayList<EventLedgerRecord>();
            for(var event:cp.get("oracleEvidence").get("events")) {
                String id=event.get("eventId").asText();expectedIds.add(id);var row=new EventLedgerRecord();
                row.setBatchId(b.getId());row.setSourceId(source);row.setTableName("ORKLAB.T_D2_BLOCKS_20261008");row.setOperation("UPDATE");
                rows.add(row);when(ledger.findByEventId(id)).thenReturn(row);
            }
            when(ledger.findByBatchId(b.getId())).thenReturn(rows);
            var receipt=json.treeToValue(cp.get("targetCommit"),OracleTargetCommitVerifier.Receipt.class);
            when(target.verify(eq(b),anyList(),any())).thenReturn(receipt);last=b;lastTask=taskId;
        }
        when(batches.findLastGatePassed(last.getSourceId())).thenReturn(last);
        var actual=verifier[0].verified(last.getSourceId(),lastTask);
        var projected=OracleBatchProjection.project(actual.historical());
        var actualIds=projected.entries().stream().map(e->e.envelope().eventId()).toList();
        if(actual.batchId()!=last.getId() || !actualIds.equals(expectedIds) || actualIds.size()!=46)throw new AssertionError("saved cumulative chain differs");
        verify(input,times(4)).readCommitted(any());verify(target,times(4)).verify(any(),anyList(),any());
        verify(acknowledgments,times(4)).verifyAcknowledged(any(),any());verify(locks,times(1)).lockSource(last.getSourceId());
        var proof=Map.of("batches",4,"events",46,"exactSavedEvidenceAndEnvelopeDigests",true,
                "eachBatchDecodedOnce",true,"databaseVerified",false,"targetVerifiedLive",false,"sourceActions",0,"targetActions",0);
        Files.writeString(Path.of(args[1]),json.writeValueAsString(proof));System.out.println(json.writeValueAsString(proof));
    }
}
