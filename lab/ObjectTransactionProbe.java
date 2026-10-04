import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Generic raw object discovery; no caller XID list, SQL/dump input, or CDC output. */
public class ObjectTransactionProbe {
    public static void main(String[] args) throws Exception {
        var found=ArchivedObjectTransactions.read(List.of(Path.of(args[2])),677737149,3,Set.of(Long.parseLong(args[0]),Long.parseLong(args[1])));
        for (var transaction:found.transactions()) {
            var xid=transaction.xid();var a=transaction.associations();var w=a.window();
            System.out.println("DISCOVER|"+xid.usn()+"."+xid.slot()+"."+xid.sequence()+"|"+w.undo().entries().size()+"|"+CompensationAssociationProbe.position(w.firstRecord())+"|"+CompensationAssociationProbe.position(w.lastRecord()));
            for (var e:a.entries()) {
                var h=e.original();System.out.println("ASSOC|"+CompensationAssociationProbe.position(e.position())+"|"+e.xid().usn()+"."+e.xid().slot()+"."+e.xid().sequence()+"|"+CompensationAssociationProbe.position(e.originalPosition())+"|"+h.uba().dba()+":"+h.uba().sequence()+":"+h.uba().record()+"|"+h.objectId()+"|"+h.dataObjectId()+"|"+h.beginsTransaction());
            }
        }
        System.err.println("discovered_transactions="+found.transactions().size()+";header_routes_only;physical_graph_unverified;cdc_events=0");
    }
}
