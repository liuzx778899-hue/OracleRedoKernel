import com.streamhouse.oracle.*;
import java.nio.file.Path;

/** Research identity extractor; reads raw redo, never diagnostic text. */
public class UndoProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);
        long object=Long.parseLong(args[1]);
        var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r -> {
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                if (v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.fields().size()<2) continue;
                byte[] b=v.fields().get(1);
                if (b.length<8) throw new RedoException("TRUNCATED_UNDO_OBJECT");
                long dataObject=0;
                for (int n=0;n<4;n++) dataObject|=(long)(b[n+4]&255)<<(8*n);
                if (dataObject!=object) continue;
                var h=UndoHeader.decode(v);
                System.out.printf("%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|%s%n",r.block(),r.offset(),i+1,
                        h.xid().usn(),h.xid().slot(),h.xid().sequence(),h.uba().dba(),h.uba().sequence(),h.uba().record(),
                        h.objectId(),h.dataObjectId(),h.tablespace(),h.beginsTransaction());
            }
        });
        System.err.println("undo_identity_only;cdc_events=0");
    }
}
