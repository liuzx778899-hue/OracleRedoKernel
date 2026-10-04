import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

/** Frozen index research inventory; emits raw fields, never CDC events. */
public class IndexVectorSurvey {
    public static void main(String[] args) throws Exception {
        long object=Long.parseLong(args[0]);
        if (object!=73025) throw new RedoException("FROZEN_INDEX_SURVEY_OBJECT_MISMATCH");
        var output=new ArrayList<String>();
        for (int a=1;a<args.length;a++) {
            Path path=Path.of(args[a]); var header=new ArchivedRedo().scan(path);
            new RedoRecords().visit(path,header,r -> {
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);
                    if (v.containerId()!=3 || r.containerUid()!=677737149) continue;
                    boolean index=v.opcodeMajor()==10 && v.dba()==0x0300008bL;
                    int metadata=v.opcodeMinor()==1 ? 1 : 0;
                    boolean undo=v.opcodeMajor()==5 && (v.opcodeMinor()==1 || v.opcodeMinor()==6 || v.opcodeMinor()==11)
                            && v.fields().size()>metadata && v.fields().get(metadata).length>=8
                            && word(v.fields().get(metadata),4)==object;
                    if (!index && !undo) continue;
                    StringBuilder line=new StringBuilder();
                    line.append(header.sequence()).append('|').append(r.block()).append('|').append(r.offset())
                        .append('|').append(i+1).append('|').append(v.opcodeMajor()).append('.').append(v.opcodeMinor())
                        .append('|').append(v.dba()).append('|').append(r.scn());
                    for (byte[] field:v.fields()) line.append('|').append(HexFormat.of().formatHex(field));
                    output.add(line.toString());
                }
            });
        }
        output.forEach(System.out::println);
        System.err.println("index_raw_field_inventory="+output.size()+";cdc_events=0");
    }
    static long word(byte[] b,int o) {
        return (b[o]&255L)|((b[o+1]&255L)<<8)|((b[o+2]&255L)<<16)|((b[o+3]&255L)<<24);
    }
}
