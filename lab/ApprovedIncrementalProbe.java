package com.streamhouse.app.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.baseline.*;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.oracle.*;
import com.streamhouse.pipeline.entity.PipelineBatch;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Real frozen raw and product projection; committed registry remains a substitute, never runtime acceptance. */
public class ApprovedIncrementalProbe {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[4]);Files.createDirectory(output);var reader=new CaptureJournal();
        var captures=List.of(reader.readRawClosed(Path.of(args[2]),"44a08e6ec18c86b6a9601b137f1a37ead8c6797769cd4d3b70d67ace6016986b"),
                reader.readRawClosed(Path.of(args[3]),"e837cd97db88b0212275c1451686911949fb954161cd0d7cc84feecede6b4b2f"));
        var full=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491);
        var boundary=full.publicationBoundary(20);var suffix=full.afterPublished(boundary);
        var projected=OracleBatchProjection.project(suffix);var json=new ObjectMapper();
        var reference=new OracleInputBinding.Publication(3,5,20,boundary.sha256());
        var approved=new OracleInputBinding(Path.of(args[0]).toAbsolutePath().toString(),List.of(args[2],args[3]),projected.evidence(),
                OracleInputBindingService.envelopesSha256(json,OracleApprovedInput.events(projected)),null,null,OracleInputFormat.RAW_MEMBER,reference);
        var registry=mock(OracleInputBindingService.class);var batch=new PipelineBatch();batch.setId(7L);batch.setSourceId("s");
        when(registry.approved(batch)).thenReturn(approved);
        var previous=mock(CapturedHistoricalHeapBatch.class);when(previous.events()).thenReturn(full.events().subList(0,20));
        when(previous.publicationBoundary(20)).thenReturn(boundary);when(previous.captures()).thenReturn(full.captures());
        var committed=mock(OracleCommittedInput.class);when(committed.verified("s",3,7L)).thenReturn(new OracleCommittedInput.Receipt(5,previous));
        var input=new OracleApprovedInput(registry,json,()->committed);
        var actual=input.read(batch);
        if(!actual.evidence().equals(projected.evidence()) || !OracleApprovedInput.events(actual).equals(OracleApprovedInput.events(projected))
                || !input.readHistorical(batch).events().equals(full.events()))
            throw new AssertionError("incremental projection or cumulative history differs");
        when(committed.verified("s",3,7L)).thenReturn(new OracleCommittedInput.Receipt(6,previous));
        try {input.read(batch);throw new AssertionError("wrong prior batch accepted");}catch(IllegalStateException expected){}
        when(committed.verified("s",3,7L)).thenReturn(new OracleCommittedInput.Receipt(5,previous));
        when(previous.publicationBoundary(20)).thenReturn(new CapturedHistoricalHeapBatch.PublicationBoundary(20,"a".repeat(64)));
        try {input.read(batch);throw new AssertionError("poisoned boundary accepted");}catch(IllegalStateException expected){}
        try {new OracleApprovedInput(registry,json).read(batch);throw new AssertionError("missing commit verifier accepted");}catch(IllegalStateException expected){}
        String proof="{\"fullEvents\":40,\"publishedEvents\":20,\"remainingEvents\":20,\"exactProjectionEqual\":true,"
                +"\"wrongPriorBatchRefused\":true,\"poisonedBoundaryRefused\":true,\"missingVerifierRefused\":true,"
                +"\"databaseVerified\":false,\"sourceActions\":0,\"targetActions\":0}";
        Files.writeString(output.resolve("summary.json"),proof);System.out.println(proof);
    }
}
