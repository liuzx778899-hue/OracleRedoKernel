package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;
/** Frozen-input physical cancellation inventory only. */
public class CommonFieldsRollbackProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var capture=new CaptureJournal().readRawClosed(Path.of(args[2]),args[3]);
        var inputs=RedoBatch.captures(List.of(capture));var records=new RedoRecords().readCaptured(capture);
        var xids=new HashSet<HeapRowPatch.Xid>();
        for(var r:records)for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=24 && u32(v.fields().get(1),0)==snapshot.heap().dataObject())xids.add(UndoHeader.decode(v).xid());
        for(var xid:xids) {
            var associations=ArchivedCompensationAssociations.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),xid);
            for(var entry:associations.window().undo().entries())if(entry.opcodeMajor()==13) {
                System.out.println("CONTROL|"+xid+"|"+entry.position()+"|"+entry.opcodeMinor()+"|"+entry.identity());
                for(var r:records)if(r.block()==entry.position().block() && r.offset()==entry.position().offset()) {
                    var v=r.vectors().get(entry.position().vector()-1);
                    for(int i=1;i<v.fields().size();i++)System.out.println("ORIGINAL|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                    for(var forward:r.vectors())if(forward.opcodeMajor()==13)System.out.println("FORWARD|"+forward.opcodeMinor()+"|"+forward.dba()+"|"+HexFormat.of().formatHex(forward.fields().get(0)));
                }
            }
            for(var route:associations.entries()) {
                System.out.println("ROUTE|"+xid+"|"+route.position()+"|"+route.original());
                for(var entry:associations.window().undo().entries())if(entry.identity().equals(route.original()))
                    for(var r:records)if(r.block()==entry.position().block() && r.offset()==entry.position().offset()) {
                        var v=r.vectors().get(entry.position().vector()-1);
                        for(int i=1;i<v.fields().size();i++)System.out.println("ORIGINAL|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                    }
                for(var r:records)if(r.block()==route.position().block() && r.offset()==route.position().offset())for(var v:r.vectors()) {
                    System.out.println("VECTOR|"+r.scn()+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.objectId()+"|"+v.dba()+"|"+v.blockClass());
                    for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                }
            }
        }
    }
}
