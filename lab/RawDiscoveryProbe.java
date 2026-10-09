import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.appdb.OracleAcquisitionRow;
import com.streamhouse.app.appdb.*;
import com.streamhouse.app.baseline.*;
import com.streamhouse.common.Sha256;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.TransferSessionMapper;
import java.nio.file.*;
import java.util.*;
import com.streamhouse.common.SnowflakeId;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
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
        var inspected=service.inspect(closed.candidate(),null,null);
        require(inspected.state().equals("READY_FOR_REGISTRATION") && inspected.proposal()!=null && inspected.prefix().pendingTransactions()==0);
        var rows=mock(OracleRawEnrollmentMapper.class);var locks=mock(OraclePublicationLockMapper.class);
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var grant=new OracleInputBindingRow();grant.setId(5L);grant.setSourceId("s");grant.setConsumerId("c");
        grant.setSchemaGeneration("frozen-baseline");grant.setState("APPROVED_RAW_STREAM");grant.setIssueSeqStart(1);grant.setIssueSeqEnd(100);
        grant.setBindingJson(json.writeValueAsString(new OracleRawDiscoveryService.Grant("s","discovery",7,90,50359491)));
        when(rows.find(5)).thenReturn(grant);when(locks.lockSource("s")).thenReturn(List.of(5L));
        var stored=new HashMap<String,OracleInputBindingRow>();
        when(rows.findWindow(eq("s"),anyString())).thenAnswer(c->stored.get(c.getArgument(1)));
        when(rows.insertDerived(any())).thenAnswer(c->{OracleInputBindingRow row=c.getArgument(0);stored.put(row.getWindowKey(),row);return 1;});
        when(rows.updateDiscovery(anyLong(),anyLong(),anyString())).thenAnswer(c->{var row=stored.get("raw-discovery-5");row.setIssueSeqEnd(c.getArgument(1));row.setBindingJson(c.getArgument(2));return 1;});
        when(mapper.findFinishedRawBySource("discovery")).thenReturn(List.of(first));
        var durable=new OracleRawDiscoveryService(5,"c",service,rows,locks,json,new SnowflakeId(59),manager).discover("probe");
        when(mapper.findFinishedRawBySource("discovery")).thenReturn(List.of(second,first));
        var restarted=new OracleRawDiscoveryService(5,"c",service,rows,locks,json,new SnowflakeId(60),manager);
        var extended=restarted.discover("probe");require(durable.progressId().equals(extended.progressId()) && extended.discovery().nextSequence()==92);
        restarted.discover("probe");verify(rows,times(1)).insertDerived(any());verify(rows,times(1)).updateDiscovery(anyLong(),anyLong(),anyString());
        verify(mapper,never()).insert(any());verify(mapper,never()).updateStatus(anyLong(),any(),any());
        var proof=Map.of("gap",gap,"closed",closed,"durable",extended,"normalInspection",inspected.state(),"databaseVerified",false,"sourceActions",0,"targetActions",0);
        Files.writeString(root.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        System.out.println(json.writeValueAsString(proof));
    }
}
