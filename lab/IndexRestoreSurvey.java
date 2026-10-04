import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

/** New frozen 73060 fixture inventory, including unsupported restore vectors. No CDC events. */
public class IndexRestoreSurvey {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r -> {
            if (r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if (v.containerId()!=3) continue;
                int metadata=v.opcodeMinor()==1 ? 1 : 0;
                boolean selected=v.opcodeMajor()==10 && v.dba()==0x0300012bL;
                selected|=v.opcodeMajor()==5 && (v.opcodeMinor()==1 || v.opcodeMinor()==6 || v.opcodeMinor()==11)
                        && v.fields().size()>metadata && v.fields().get(metadata).length>=8
                        && IndexVectorSurvey.word(v.fields().get(metadata),4)==73060;
                if (!selected) continue;
                var line=new StringBuilder(header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba()+"|"+r.scn());
                for (byte[] field:v.fields()) line.append('|').append(HexFormat.of().formatHex(field));
                output.add(line.toString());
            }
        });
        output.forEach(System.out::println);System.err.println("frozen_index_vectors="+output.size()+";cdc_events=0");
    }
}
