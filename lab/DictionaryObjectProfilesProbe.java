import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.nio.*;

/** Native field-profile diagnostics; never a dictionary or event input. */
public class DictionaryObjectProfilesProbe {
    public static void main(String[] args)throws Exception {
        Path path=Path.of(args[0]);long minimum=Long.parseLong(args[1]);var h=new ArchivedRedo().scan(path);
        var role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
        new RedoRecords().visit(path,h,r->{if(r.containerUid()!=677737149 || r.scn()<=minimum)return;
            if(r.vectors().stream().noneMatch(v->v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1
                    && v.fields().get(1).length>=24 && ByteBuffer.wrap(v.fields().get(1)).order(ByteOrder.LITTLE_ENDIAN).getInt()==18))return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==11)try{DictionaryObjectUndoPair.bind(r,v,role,37);}
            catch(RedoException failure) {
                System.out.println("REFUSED|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+v.opcodeMinor()+"|"+failure.getMessage());
                System.out.println("KDO|"+HexFormat.of().formatHex(v.fields().get(1)));
                System.out.println("FIELDS|"+v.fields().stream().map(b->b.length).toList());
            }
        });
    }
}
