import com.streamhouse.app.StreamHouseApplication;
import com.streamhouse.app.port.OracleCommittedInput;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Diagnostic only: prints the original nested cause; does not create events or authorize deletion. */
public class ReadCutRuntimeDiagnostic {
    public static void main(String[] args) {
        try(var context=new SpringApplicationBuilder(StreamHouseApplication.class).run(args)) {
            try {System.out.println(context.getBean(OracleCommittedInput.class).retention("oracle-online-normal"));}
            catch(Throwable failure) {failure.printStackTrace();throw new IllegalStateException("READ_CUT_DIAGNOSTIC_REFUSED",failure);}
        }
    }
}
