import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Raw structural KDO survey, deliberately does not claim patch/pair/row or event decoding. */
public class ColumnLayoutProbe {
    static long word(byte[] b,int p) {return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    static int half(byte[] b,int p) {return (b[p]&255)|((b[p+1]&255)<<8);}
    public static void main(String[] args) throws Exception {
        long object=Long.parseLong(args[0]),heap=Long.decode(args[1]);var path=Path.of(args[2]);var scanner=new ArchivedRedo();var source=scanner.scan(path);var output=new ArrayList<String>();
        new RedoRecords().visit(path,source,r->{
            if (r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if (v.containerId()!=3) continue;
                List<byte[]> fields;String side;
                if (v.opcodeMajor()==11 && v.dba()==heap) {fields=v.fields();side="FORWARD";}
                else if (v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>=4 && word(v.fields().get(1),4)==object) {fields=v.fields().subList(2,v.fields().size());side="INVERSE";}
                else continue;
                var kdo=fields.get(1);int op=kdo[10]&255;int ncol=op==2 ? kdo[18]&255 : op==5 ? kdo[22]&255 : -1;
                int changed=op==2 ? ncol : op==5 ? kdo[23]&255 : 0;int slot=half(kdo,op==2 ? 42 : op==5 ? 20 : 16);
                var values=new ArrayList<String>();
                for (int j=0;j<changed;j++) {
                    int column=op==2 ? j : half(fields.get(2),j*2);var bytes=fields.get(j+(op==2 ? 2 : 3));
                    values.add(column+":"+(bytes.length==0 ? "NULL:" : "VALUE:"+HexFormat.of().formatHex(bytes)));
                }
                output.add(source.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+side+"|"+op+"|"+ncol+"|"+changed+"|"+slot+"|"+String.join(",",values));
            }
        });
        if (!source.equals(scanner.scan(path)))throw new RedoException("COLUMN_SURVEY_INPUT_CHANGED");
        output.forEach(System.out::println);System.err.println("survey_vectors="+output.size()+";physical_layout_only;row_semantics_unverified;cdc_events=0");
    }
}
