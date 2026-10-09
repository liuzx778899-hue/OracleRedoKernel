import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
public class RawValueLayoutProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var capture=new CaptureJournal().readRawClosed(Path.of(args[2]),args[3]);
        for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn())continue;
            boolean compensation=false;
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && (vector.opcodeMinor()==6 || vector.opcodeMinor()==11))
                compensation|=UndoCompensation.decode(vector).dataObjectId()==snapshot.heap().dataObject();
            if(compensation)for(var vector:record.vectors())if(vector.opcodeMajor()==11) {
                System.out.println("COMPENSATION|"+record.scn()+"|"+record.block()+"|"+record.offset()+"|"+vector.opcodeMinor());
                for(int i=0;i<vector.fields().size();i++)System.out.println("ROW_FIELD|"+i+"|"+HexFormat.of().formatHex(vector.fields().get(i)));
            }
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && vector.opcodeMinor()==1) {
                var header=UndoHeader.decode(vector);if(header.objectId()!=snapshot.heap().logicalObject())continue;
                System.out.println("RECORD|"+record.scn()+"|"+record.block()+"|"+record.offset()+"|"+header);
                for(int i=0;i<vector.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(vector.fields().get(i)));
                try {System.out.println("PATCH|"+UndoRowPatch.decode(vector,2));}
                catch(Exception ex){System.out.println("REFUSED|"+ex.getMessage());}
            }
        }
    }
}
