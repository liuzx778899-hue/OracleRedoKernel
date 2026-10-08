import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.baseline.*;
import com.streamhouse.app.port.OracleApprovedInput;
import com.streamhouse.engines.oracle.OracleBatchProjection;
import com.streamhouse.oracle.*;
import com.streamhouse.pipeline.entity.PipelineBatch;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Actual approved-input loader against frozen raw; registry/DB remains a substitute. */
public class ApprovedHistoricalProbe {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[4]);Files.createDirectory(output);var reader=new CaptureJournal();
        var captures=List.of(reader.readRawClosed(Path.of(args[2]),"44a08e6ec18c86b6a9601b137f1a37ead8c6797769cd4d3b70d67ace6016986b"),
                reader.readRawClosed(Path.of(args[3]),"e837cd97db88b0212275c1451686911949fb954161cd0d7cc84feecede6b4b2f"));
        var expected=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491);
        var projection=OracleBatchProjection.project(expected);var json=new ObjectMapper();
        var binding=new OracleInputBinding(Path.of(args[0]).toAbsolutePath().toString(),List.of(args[2],args[3]),projection.evidence(),
                OracleInputBindingService.envelopesSha256(json,OracleApprovedInput.events(projection)),null,null,OracleInputFormat.RAW_MEMBER);
        var registry=mock(OracleInputBindingService.class);var batch=new PipelineBatch();when(registry.approved(batch)).thenReturn(binding);
        var input=new OracleApprovedInput(registry,json);var actual=input.readHistorical(batch);
        if(!actual.sha256().equals(expected.sha256()) || !actual.events().equals(expected.events()) || input.read(batch).entries().size()!=40)
            throw new AssertionError("historical approved input differs");
        String proof="{\"events\":40,\"fullEventsEqual\":true,\"batchSha256\":\""+actual.sha256()+"\",\"databaseVerified\":false,\"targetActions\":0,\"sourceActions\":0}";
        Files.writeString(output.resolve("summary.json"),proof);System.out.println(proof);
    }
}
