import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.appdb.OracleAcquisitionRow;
import com.streamhouse.app.baseline.*;
import com.streamhouse.app.port.OracleApprovedInput;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.TransferSessionMapper;
import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Frozen raw proposal path; registry substitutes do not establish continuous database acceptance. */
public class RawStreamProposalProbe {
    public static void main(String[] args)throws Exception {
        var json=new ObjectMapper();Path output=Path.of(args[4]);Files.createDirectory(output);
        Path directory=Files.createDirectories(output.resolve("raw-members/stream"));
        var acquisition=mock(OracleAcquisitionService.class);var acquired=new OracleAcquisitionRow();acquired.setSnapshotSha256(args[1]);
        when(acquisition.verified(7)).thenReturn(acquired);when(acquisition.verifiedBaseline(7,"s")).thenReturn(Path.of(args[0]));
        var sessions=mock(TransferSessionMapper.class);var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=0;i<2;i++) {
            Path raw=Path.of(args[2+i]);String sha=com.streamhouse.common.Sha256.hexOfFile(raw);Path stored=directory.resolve(sha+".raw");Files.copy(raw,stored);
            captures.add(new CaptureJournal().readRawClosed(stored,sha));
            var row=new TransferSession();row.setId(10L+i);row.setSourceId("stream");row.setEngineType("oracle");row.setInputFormat("RAW_MEMBER");
            row.setTotalSha256(sha);row.setFileName(sha+".raw");row.setTotalLength(Files.size(raw));row.setReceivedOffset(row.getTotalLength());row.setStatus(TransferStatus.FINISHED);
            when(sessions.findById(10L+i)).thenReturn(row);
        }
        var plan=new OracleRawPlanService.Plan("s","stream",7,90,50359491,List.of(10L));
        var candidate=new OracleRawPlanService.Plan("s","stream",7,90,50359491,List.of(10L,11L));
        var input=new OracleRawPlanService(plan,output,sessions,acquisition,json);
        var full=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491);
        var boundary=full.publicationBoundary(20);var previous=mock(CapturedHistoricalHeapBatch.class);
        when(previous.captures()).thenReturn(full.captures());when(previous.events()).thenReturn(full.events().subList(0,20));when(previous.publicationBoundary(20)).thenReturn(boundary);
        var publication=new OracleInputBinding.Publication(30,20,20,boundary.sha256());
        var receipt=input.inspect(candidate,previous,publication);var expected=OracleBatchProjection.project(full.afterPublished(boundary));
        if(receipt.proposal()==null || !receipt.proposal().evidence().equals(expected.evidence()) || !receipt.proposal().publication().equals(publication)
                || !receipt.proposal().envelopesSha256().equals(OracleInputBindingService.envelopesSha256(json,OracleApprovedInput.events(expected))))
            throw new AssertionError("raw stream incremental proposal differs");
        when(previous.publicationBoundary(20)).thenReturn(new CapturedHistoricalHeapBatch.PublicationBoundary(20,"a".repeat(64)));
        try {input.inspect(candidate,previous,publication);throw new AssertionError("poisoned boundary accepted");}catch(IllegalStateException expectedFailure){}
        var proof=Map.of("fullEvents",full.events().size(),"remainingEvents",expected.entries().size(),"exactTypedProposal",true,
                "poisonedPrefixRefused",true,"databaseVerified",false,"sourceActions",0,"targetActions",0);
        Files.writeString(output.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));System.out.println(json.writeValueAsString(proof));
    }
}
