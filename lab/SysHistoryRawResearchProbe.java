import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Structural fields after an acquired SCN, for native comparison only; no histories or events. */
public class SysHistoryRawResearchProbe {
    public static void main(String[] args)throws Exception {
        var s=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var roles=Set.of(0L,4L,18L,21L,48L,49L,50L,s.heap().logicalObject(),s.index().logicalObject());
        for(int i=2;i<args.length;i++) {
            var path=Path.of(args[i]);var header=new ArchivedRedo().scan(path);s.requireSource(header);
            new RedoRecords().visit(path,header,r->{
                if(r.scn()<=s.scn() || r.containerUid()!=s.source().containerUid())return;
                boolean selected=r.vectors().stream().anyMatch(v->v.containerId()==s.source().containerId()&&v.opcodeMajor()==5&&v.opcodeMinor()==1&&v.fields().size()>1&&v.fields().get(1).length>=4&&roles.contains(java.nio.ByteBuffer.wrap(v.fields().get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()&0xffffffffL));
                if(!selected)return;
                System.out.println("RECORD|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn());
                for(int n=0;n<r.vectors().size();n++) {
                    var v=r.vectors().get(n);System.out.println("VECTOR|"+(n+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba()+"|"+v.containerId()+"|"+v.fields().stream().map(f->HexFormat.of().formatHex(f)).collect(Collectors.joining(",")));
                }
            });
        }
    }
}
