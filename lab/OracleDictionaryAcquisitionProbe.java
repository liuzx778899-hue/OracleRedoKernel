import com.streamhouse.engines.oracle.OracleSnapshotAcquirer;
import com.streamhouse.spi.OracleBatchEvidence;
import java.nio.file.*;
import java.sql.*;

/** Permission refusal probe only. Does not grant privileges or write a snapshot. */
public class OracleDictionaryAcquisitionProbe {
    public static void main(String[] args) throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));
        var expected=new OracleBatchEvidence.Source(1276726821L,1276717861L,318767104L,1,1245693159L,1920977L,677737149L,3);
        var c=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));
        try {
            new OracleSnapshotAcquirer().acquireDictionaryBound(c,new OracleSnapshotAcquirer.Scope(args[1],args[2],args[3]),expected);
            throw new AssertionError("EXPECTED_DICTIONARY_PERMISSION_REFUSAL");
        } catch(SQLException ex) {
            if(ex.getErrorCode()!=942 && ex.getErrorCode()!=1031)throw ex;
            if(!c.isClosed())throw new AssertionError("SOURCE_CONNECTION_LEFT_OPEN");
            System.out.println("REFUSED|DICTIONARY_PERMISSION|"+ex.getErrorCode()+"|"+ex.getMessage().replace('\n',' '));
        }
    }
}
