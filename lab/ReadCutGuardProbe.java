import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.appdb.*;
import com.streamhouse.app.audit.AuditService;
import com.streamhouse.app.baseline.OracleRawPlanService;
import com.streamhouse.app.port.*;
import com.streamhouse.app.web.OracleReadCutController;
import com.streamhouse.common.SnowflakeId;
import com.streamhouse.gateway.entity.*;
import com.streamhouse.gateway.mapper.CapWindowMapper;
import com.streamhouse.pipeline.entity.PipelineBatch;
import com.streamhouse.pipeline.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.*;

/** Necessary pending-input and wrong-machine counterexamples against the compiled product classes; no DB or files. */
public class ReadCutGuardProbe {
    interface Action {void run()throws Exception;}
    static void refused(String reason,Action action)throws Exception {
        try {action.run();throw new AssertionError("unexpected acceptance: "+reason);}
        catch(IllegalArgumentException|IllegalStateException failure) {
            for(Throwable at=failure;at!=null;at=at.getCause())if(reason.equals(at.getMessage()))return;
            throw failure;
        }
    }
    public static void main(String[] args)throws Exception {
        var rows=mock(OracleRawEnrollmentMapper.class);var locks=mock(OraclePublicationLockMapper.class);
        var tasks=mock(TaskMapper.class);var batches=mock(PipelineBatchMapper.class);var windows=mock(CapWindowMapper.class);
        var committed=mock(OracleCommittedInput.class);var input=mock(OracleApprovedInput.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(locks.lockSource("s")).thenReturn(List.of(1L));
        var batch=new PipelineBatch();batch.setId(1L);batch.setWindowKey("old");when(batches.findLastGatePassed("s")).thenReturn(batch);
        var window=new CapWindow();window.setWindowKey("pending");window.setStatus(WindowStatus.PENDING);when(windows.findLastRegistered("s")).thenReturn(window);
        var cuts=spy(new OracleReadCutService(rows,locks,tasks,batches,windows,committed,input,new ObjectMapper(),new SnowflakeId(59),manager,Path.of(args[0]),mock(AuditService.class)));
        refused("ORACLE_READ_CUT_PENDING_WINDOW",()->cuts.prepare("s","probe"));
        verifyNoInteractions(committed,input,tasks);verify(rows,never()).insertDerived(any());
        var cut=new OracleReadCutService.Cut(10,"s",7,3,1,2,"a".repeat(64),"/cut","b".repeat(64),20,78,501,null,"c".repeat(64),List.of("/raw"));
        doReturn(cut).when(cuts).current("s");when(windows.count("PENDING","s")).thenReturn(1L);
        refused("ORACLE_READ_CUT_PENDING_WINDOW",()->cuts.reclaim("s",Path.of(args[0]),"probe"));
        verifyNoInteractions(committed,input,tasks);verify(rows,never()).insertDerived(any());
        var plan=mock(OracleRawPlanService.class);when(plan.plan()).thenReturn(new OracleRawPlanService.Plan("s","t",7,77,501,List.of(1L)));
        var guarded=mock(OracleReadCutService.class);var controller=new OracleReadCutController(plan,guarded);
        var request=mock(HttpServletRequest.class);when(request.getAttribute("sh.machine.sourceId")).thenReturn("foreign");
        refused("ORACLE_READ_CUT_MACHINE_SOURCE_REQUIRED",()->controller.prepare(Map.of(),request));
        refused("ORACLE_READ_CUT_MACHINE_SOURCE_REQUIRED",()->controller.reclaim(Map.of(),request));
        refused("ORACLE_READ_CUT_SERVER_SCOPE_REQUIRED",()->controller.reclaim(Map.of("path","/foreign"),request));
        verifyNoInteractions(guarded);
        System.out.println("PASS pending prepare/reclaim and wrong-source/client-path guards; zero input, commit, insert or deletion calls");
    }
}
