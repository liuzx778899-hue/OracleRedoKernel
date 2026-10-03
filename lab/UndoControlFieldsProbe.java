import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.HexFormat;

/** Research header fields only, selected by frozen record location. */
public class UndoControlFieldsProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);long block=Long.parseLong(args[1]);int offset=Integer.parseInt(args[2]);
        var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.block()!=block || r.offset()!=offset)return;
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.opcodeMajor()!=5 || (v.opcodeMinor()!=1 && v.opcodeMinor()!=2))continue;
                System.out.println("VECTOR|"+(i+1)+"|"+v.opcodeMinor()+"|"+v.dba()+"|"+r.scn()+"|"+v.containerId());
                int count=v.opcodeMinor()==1 ? 2 : v.fields().size();
                for(int f=0;f<count;f++)System.out.println("FIELD|"+f+"|"+v.fields().get(f).length+"|"+HexFormat.of().formatHex(v.fields().get(f)));
            }
        });
    }
}
