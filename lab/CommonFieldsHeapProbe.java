package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;
/** Frozen raw heap-profile diagnostic only. */
public class CommonFieldsHeapProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var path=Path.of(args[2]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.objectId()==snapshot.heap().dataObject() && v.opcodeMajor()==11) {
                try {new HeapRowPatch().decodeLob(v,snapshot.columns().size());}
                catch(RedoException e) {
                    System.out.println("REFUSED|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|11."+v.opcodeMinor()+"|"+v.dba()+"|"+e.getMessage());
                    for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                }
            }
        });
    }
}
