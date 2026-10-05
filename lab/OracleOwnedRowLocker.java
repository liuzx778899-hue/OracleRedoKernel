import java.nio.file.*;
import java.sql.*;

/** No DML. Hold only a named owned target/control metadata row for crash-boundary observation. */
public class OracleOwnedRowLocker {
    public static void main(String[] args)throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));String table=args[1];
        if(!table.equals("ORKTGT_CMT_20261005.HTMIX_ROWS") && !table.equals("ORKHTTP_20261005.SH_CAP_WINDOW"))throw new IllegalArgumentException("OWNED_ROW_SCOPE_REQUIRED");
        try(var connection=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1))) {
            connection.setAutoCommit(false);
            if(table.equals("ORKTGT_CMT_20261005.HTMIX_ROWS")) {
                try(var statement=connection.createStatement()){statement.execute("LOCK TABLE "+table+" IN EXCLUSIVE MODE");}
            } else try(var statement=connection.prepareStatement("SELECT ID FROM "+table+" WHERE ID=? FOR UPDATE")) {
                statement.setLong(1,Long.parseLong(args[2]));try(var rows=statement.executeQuery()){if(!rows.next() || rows.next())throw new IllegalStateException("OWNED_ROW_NOT_UNIQUE");}
            }
            System.out.println("OWNED_METADATA_LOCK_HELD|"+table+"|"+args[2]);
            while(true)Thread.sleep(1000);
        }
    }
}
