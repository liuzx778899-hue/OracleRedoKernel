package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;
/** Diagnostic raw profiles only; never grants schema or publication authority. */
public class CommonFieldsMarkerProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        for(int n=2;n<args.length;n++) {
            var path=Path.of(args[n]);var header=new ArchivedRedo().scan(path);
            new RedoRecords().visit(path,header,r->{
                if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
                for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==24) {
                    try {HistoricalSchemaBarrier.requireMarkerProfile(r,v);}
                    catch(RedoException failure) {
                        System.out.println("REFUSED|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+v.opcodeMinor()+"|"+failure.getMessage());
                        var f=v.fields();System.out.println("LENGTHS|"+f.stream().map(b->Integer.toString(b.length)).toList());
                        for(int i=0;i<Math.min(14,f.size());i++)if(i!=7)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(f.get(i)));
                    }
                }
            });
        }
    }
}
