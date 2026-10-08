import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.appdb.OracleAcquisitionRow;
import com.streamhouse.app.baseline.*;
import com.streamhouse.common.Sha256;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.TransferSessionMapper;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Read-only frozen-input verification; mapper/acquisition substitutes are not DB acceptance. */
public class RawDiscoveryProbe {
    static TransferSession row(long id,Path raw,Path directory)throws Exception {
        String digest=Sha256.hexOfFile(raw);Files.copy(raw,directory.resolve(digest+".raw"));
        var row=new TransferSession();row.setId(id);row.setSourceId("discovery");row.setEngineType("oracle");
        row.setInputFormat("RAW_MEMBER");row.setTotalSha256(digest);row.setFileName(digest+".raw");
        row.setTotalLength(Files.size(raw));row.setReceivedOffset(row.getTotalLength());row.setStatus(TransferStatus.FINISHED);return row;
    }
    static void require(boolean result){if(!result)throw new AssertionError("discovery proof mismatch");}
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[0]).toRealPath(),root=Path.of(args[4]).toAbsolutePath().normalize();
        Files.createDirectory(root);Path directory=Files.createDirectories(root.resolve("raw-members/discovery"));
        var acquired=new OracleAcquisitionRow();acquired.setSnapshotSha256(args[1]);
        var acquisition=mock(OracleAcquisitionService.class);when(acquisition.verified(7)).thenReturn(acquired);
        when(acquisition.verifiedBaseline(7,"s")).thenReturn(baseline);
        var mapper=mock(TransferSessionMapper.class);var json=new ObjectMapper();
        var service=new OracleRawPlanService(new OracleRawPlanService.Plan("s","discovery",7,90,50359491,List.of(20L)),root,mapper,acquisition,json);
        var first=row(20,Path.of(args[2]),directory);var second=row(10,Path.of(args[3]),directory);
        when(mapper.findFinishedRawBySource("discovery")).thenReturn(List.of(second));
        var gap=service.discover();require(gap.state().equals("WAITING_GAP") && gap.candidate()==null && gap.nextSequence()==90);
        when(mapper.findFinishedRawBySource("discovery")).thenReturn(List.of(second,first,second));
        var closed=service.discover();require(closed.state().equals("CANDIDATE") && closed.nextSequence()==92
                && closed.candidate().sessions().equals(List.of(20L,10L)) && closed.members().size()==2);
        when(mapper.findById(20)).thenReturn(first);when(mapper.findById(10)).thenReturn(second);
        var inspected=new OracleRawPlanService(closed.candidate(),root,mapper,acquisition,json).inspect();
        require(inspected.state().equals("READY_FOR_REGISTRATION") && inspected.proposal()!=null && inspected.prefix().pendingTransactions()==0);
        verify(mapper,never()).insert(any());verify(mapper,never()).updateStatus(anyLong(),any(),any());
        var proof=Map.of("gap",gap,"closed",closed,"normalInspection",inspected.state(),"databaseVerified",false,"sourceActions",0,"targetActions",0);
        Files.writeString(root.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        System.out.println(json.writeValueAsString(proof));
    }
}
