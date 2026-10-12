import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhouse.app.StreamHouseApplication;
import com.streamhouse.app.port.OracleReadCutService;
import com.streamhouse.pipeline.mapper.PipelineBatchMapper;
import com.streamhouse.pipeline.mapper.TaskMapper;
import com.streamhouse.pipeline.entity.TaskState;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;
import java.util.*;

/** Real Spring/MyBatis/target/CPACK/reclaim services, normal path only; no fault injection. */
public class LobRecoveryProductMain {
    public static void main(String[] args)throws Exception {
        try(var context=SpringApplication.run(StreamHouseApplication.class)) {
            if(args[0].equals("--initialize"))return;
            if(args[0].equals("--diagnose")) {
                var service=context.getBean(com.streamhouse.app.baseline.OracleAcquisitionService.class);
                service.verified(29801);
                var row=context.getBean(com.streamhouse.app.appdb.OracleInputBindingMapper.class).findApproved(args[1],"lob-recovery-0");
                var binding=context.getBean(ObjectMapper.class).readValue(row.getBindingJson(),com.streamhouse.app.baseline.OracleInputBinding.class);
                service.baselineFor(29801,args[1],binding.evidence());return;
            }
            var batches=context.getBean(PipelineBatchMapper.class);String source=args[0];
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            var tasks=context.getBean(TaskMapper.class);
            if(args.length==6 && args[5].equals("--resume")) {
                var failed=tasks.findByRef("SOURCE",source).stream().filter(t->t.getState()==TaskState.FAILED).toList();
                if(failed.size()!=1)throw new AssertionError("NORMAL_RESUME_REQUIRES_UNIQUE_FAILED_TASK");
                context.getBean(com.streamhouse.app.pipeline.PipelineRunService.class).resume(failed.get(0).getId(),"owned-lob-normal-proof");
            }
            while(batches.findLastGatePassed(source)==null || !args[4].equals(batches.findLastGatePassed(source).getWindowKey())
                    || tasks.findByRef("SOURCE",source).stream().noneMatch(t->t.getState()==TaskState.DONE && t.getCheckpointJson()!=null
                        && t.getCheckpointJson().contains("\"lastBatchId\":"+batches.findLastGatePassed(source).getId()))) {
                if(System.nanoTime()>deadline)throw new AssertionError("LOB_PRODUCT_PUBLICATION_TIMEOUT");
                Thread.sleep(1000);
            }
            var cuts=context.getBean(OracleReadCutService.class);var cut=cuts.prepare(source,"owned-lob-normal-proof");
            var result=cuts.reclaim(source,Path.of(args[1]),"owned-lob-normal-proof");
            if(result.removed()!=Integer.parseInt(args[3]))throw new AssertionError("RECLAIM_COUNT:"+result.removed());
            Files.writeString(Path.of(args[2]),new ObjectMapper().writeValueAsString(Map.of("cut",cut,"reclaimed",result)));
            System.out.println("LOB_NORMAL_TARGET_CPACK_READCUT_RECLAIM_PASS|removed="+result.removed());
        }
    }
}
