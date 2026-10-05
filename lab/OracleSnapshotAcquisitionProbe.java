import com.streamhouse.engines.oracle.OracleSnapshotAcquirer;
import com.streamhouse.oracle.FrozenHeapBaseline;
import com.streamhouse.spi.OracleBatchEvidence;
import java.nio.file.*;
import java.sql.*;

/** New source SELECT acquisition only; no publication, target writes or fixture DML. */
public class OracleSnapshotAcquisitionProbe {
    public static void main(String[] args)throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));String name=args[1],mode=args[5];Path output=Path.of(args[6]);
        var scope=new OracleSnapshotAcquirer.Scope(args[2],args[3],args[4]);
        var expected=new OracleBatchEvidence.Source(1276726821L+(mode.equals("wrong-source")?1:0),1276717861L,318767104L,1,1245693159L,1920977L,677737149L,3);
        var connection=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1));
        if(mode.equals("old-transaction"))connection.setAutoCommit(false);
        try {
            boolean dictionary=mode.equals("dictionary-normal");
            var acquirer=new OracleSnapshotAcquirer();
            var acquired=dictionary?acquirer.acquireDictionaryBound(connection,scope,expected):acquirer.acquire(connection,scope,expected);
            if(!mode.equals("normal") && !dictionary)throw new AssertionError("NEGATIVE_ACQUISITION_ACCEPTED");
            if(!connection.isClosed())throw new AssertionError("CONNECTION_NOT_CLOSED");
            byte[] first=acquired.snapshot(),changed=acquired.snapshot();changed[0]=0;
            if(first[0]==0 || acquired.snapshot()[0]==0)throw new AssertionError("MUTABLE_ACQUISITION_BYTES");
            Files.write(output,first);var snapshot=FrozenHeapBaseline.read(output,acquired.sha256());
            if(snapshot.scn()!=acquired.scn() || !snapshot.heap().owner().equals(scope.owner()) || !snapshot.heap().name().equals(scope.table()) || !snapshot.index().name().equals(scope.index()))throw new AssertionError("ACQUISITION_IMPORT_SCOPE_MISMATCH");
            if(dictionary && snapshot.dictionaryAnchors().size()!=snapshot.columns().size()+3)throw new AssertionError("DICTIONARY_ANCHOR_COVERAGE");
            System.out.println("ACQUIRED|"+name+"|"+acquired.scn()+"|"+acquired.sha256()+"|"+snapshot.columns().size()+"|"+snapshot.rows().size()+"|"+snapshot.columns().stream().filter(FrozenHeapBaseline.PhysicalColumn::hidden).count());
        } catch(SQLException ex) {
            if(mode.equals("normal") || mode.equals("dictionary-normal"))throw ex;
            if(Files.exists(output) || !connection.isClosed())throw new AssertionError("PARTIAL_ACQUISITION_OR_OPEN_CONNECTION");
            System.out.println("REFUSED|"+name+"|"+mode+"|"+ex.getErrorCode()+"|"+ex.getMessage().replace('\n',' '));
        }
    }
}
