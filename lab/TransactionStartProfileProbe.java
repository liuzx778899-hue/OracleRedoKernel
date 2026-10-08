import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Reports unsupported native start profiles together with their exact paired undo, without authorizing them. */
public class TransactionStartProfileProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);var header=new ArchivedRedo().scan(raw);
        new RedoRecords().visit(raw,header,record->{
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && vector.opcodeMinor()==2) {
                try {TransactionStart.decode(vector);}catch(RedoException refused) {
                    System.out.println("REFUSED|"+header.sequence()+"|"+record.block()+"|"+record.offset()+"|"+record.scn()+"|"+record.containerUid()+"|"+vector.containerId()+"|"+vector.dba()+"|"+refused.getMessage());
                    for(var field:vector.fields())System.out.println("START|"+HexFormat.of().formatHex(field));
                    for(var undo:record.vectors())if(undo.opcodeMajor()==5 && undo.opcodeMinor()==1) {
                        if(undo.fields().size()<2 || undo.fields().get(0).length!=20)continue;
                        UndoHeader h;
                        try {h=UndoHeader.decode(undo);}catch(RedoException ex){System.out.println("UNDOREFUSED|"+ex.getMessage());continue;}
                        System.out.println("UNDO|"+h);
                        System.out.println("UNDOFIELDS|"+undo.fields().stream().map(f->f.length).toList());
                        System.out.println("UNDOHEADER|"+HexFormat.of().formatHex(undo.fields().get(0)));
                        if(h.beginsTransaction())System.out.println("BEGINMETADATA|"+HexFormat.of().formatHex(undo.fields().get(1)));
                    }
                }
            }
        });
    }
}
