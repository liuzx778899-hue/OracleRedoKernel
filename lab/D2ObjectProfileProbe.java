import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

public class D2ObjectProfileProbe {
    public static void main(String[] args)throws Exception {
        Path path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.scn()<=2899589 || r.containerUid()!=677737149)return;
            for(var v:r.vectors())if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.containerId()==3) {
                boolean cluster=args.length>1;
                if(v.fields().size()<2 || v.fields().get(1).length<4 || (cluster?v.fields().get(1)[0]!=21 && v.fields().get(1)[0]!=4:v.fields().get(1)[0]!=18)
                        || v.fields().get(1)[1]!=0 || v.fields().get(1)[2]!=0 || v.fields().get(1)[3]!=0)continue;
                var h=UndoHeader.decode(v);
                for(var row:r.vectors())if(row.opcodeMajor()==11 && row.opcodeMinor()>=2 && row.opcodeMinor()<=5) {
                    try {
                        if(cluster)ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS",h.objectId()==4?"TAB$":"COL$",h.objectId(),2),h.objectId()==4?1:5,h.objectId()==4?44:36);
                        else DictionaryObjectUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37);
                    }
                    catch(RedoException e) {
                        System.out.println("FAIL|"+r.block()+"|"+r.offset()+"|"+h+"|"+e.getMessage());
                        for(int i=0;i<row.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(row.fields().get(i)));
                        System.out.println("INVERSE|"+HexFormat.of().formatHex(v.fields().get(3)));
                    }
                }
            }
        });
    }
}
