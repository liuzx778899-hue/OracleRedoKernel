package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;
/** Frozen ARRAY UPDATE pair/cancellation fields; diagnostic output never authorizes events. */
public class ArrayUpdateRollbackFieldsProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var path=Path.of(args[2]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            boolean scoped=r.vectors().stream().anyMatch(v->v.containerId()==snapshot.source().containerId() && v.objectId()==snapshot.heap().dataObject());
            if(!scoped)return;
            System.out.println("RECORD|"+r.block()+"|"+r.offset()+"|"+r.scn());
            for(var v:r.vectors()) {
                System.out.println("VECTOR|"+v.opcodeMajor()+"|"+v.opcodeMinor()+"|"+v.dba()+"|"+v.objectId());
                for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
        });
    }
}
