package com.streamhouse.app.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.baseline.*;
import com.streamhouse.common.Sha256;
import com.streamhouse.pipeline.entity.PipelineBatch;
import com.streamhouse.spi.OracleBatchEvidence;
import java.nio.file.*;
import java.util.*;

/** Product entry over existing frozen inputs; no database, target write, or ACK creation. */
public class ProductProducerProgressProbe {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),out=Path.of(args[1]);var json=new ObjectMapper();
        var projection=json.readTree(Files.readAllBytes(root.resolve("lab/out/d3-stream-runtime-20261009/producer-product-r109/projection.stdout")));
        var evidence=json.treeToValue(projection.get("evidence"),OracleBatchEvidence.class);
        var paths=new ArrayList<String>();for(int i=90;i<=94;i++)paths.add(root.resolve(i<92?"lab/out/d2-blocks-source-20261008/"+i+".raw":"lab/out/d3-stream-runtime-20261009/new-inputs/"+i+".raw").toString());
        Path state=root.resolve("lab/out/d3-stream-runtime-20261009/producer-recovery-r112/business.state");
        var approved=new OracleInputBinding(root.resolve("lab/out/d2-blocks-runtime-20261008/snapshots/101673191742861312.snapshot").toString(),paths,evidence,
                projection.get("envelopesSha256").asText(),101673191742861312L,new OracleInputBinding.Recovery(state.toString(),Sha256.hexOfFile(state)),OracleInputFormat.RAW_MEMBER);
        var binding=new OracleInputBindingService(null,json,"frozen-probe") {
            @Override public OracleInputBinding approved(PipelineBatch batch){return approved;}
            @Override public String check(PipelineBatch batch,List<com.streamhouse.spi.EventEnvelope> events,OracleBatchEvidence actual) {
                try{return actual.equals(evidence) && batch.getEventCount()==events.size() && OracleInputBindingService.envelopesSha256(json,events).equals(approved.envelopesSha256())?null:"FROZEN_PRODUCT_PROJECTION_MISMATCH";}
                catch(Exception ex){throw new IllegalStateException(ex);}
            }
        };
        var batch=new PipelineBatch();batch.setId(119L);batch.setSourceId("oracle-d2-blocks");batch.setEventCount(46);
        var input=new OracleApprovedInput(binding,json);var producer=input.producer(batch);
        byte[] expected=Files.readAllBytes(Path.of(state+".extended"));
        if(!Arrays.equals(expected,producer.state().checkpoint()) || producer.state().pendingTransactions()!=0 || producer.historical().events().size()!=46)
            throw new AssertionError("PRODUCT_PRODUCER_EXTENSION_CHANGED");
        if(!input.read(batch).evidence().equals(evidence))throw new AssertionError("PRODUCT_READ_CHANGED");
        Path directory=out.toRealPath();String digest=Sha256.hex(expected);Path file=directory.resolve(batch.getId()+"-"+digest+".state");Files.write(file,expected,StandardOpenOption.CREATE_NEW);
        var boundary=producer.historical().publicationBoundary(46);
        var receipt=new OracleProducerCheckpoint.Receipt(file.toString(),digest,5,94,90,0,producer.state().completedTransactions(),46,boundary.sha256());
        var cp=json.createObjectNode();cp.set("producerState",json.valueToTree(receipt));
        var verifier=new OracleProducerCheckpoint(input,directory,json);if(!receipt.equals(verifier.prepare(batch,true,cp)))throw new AssertionError("PRODUCT_PRODUCER_RECEIPT_CHANGED");
        System.out.println("EVENTS=46;PENDING=0;PRODUCT_STATE_EQUAL_FROZEN=true;REPLAY_RECEIPT_EQUAL=true;SOURCE_ACTIONS=0;TARGET_ACTIONS=0");
        System.out.println("STATE_SHA256="+digest);System.out.println("PUBLISHED_PREFIX_SHA256="+boundary.sha256());
    }
}
