import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Raw physical fragments only; omitted columns remain ABSENT, never implicit events. */
public class RawDmlRowsResearchProbe {
    static String columns(List<HeapRowPatch.Column> values) {
        return String.join(",",values.stream().map(c->c.state()+":"+HexFormat.of().formatHex(c.bytes())).toList());
    }
    public static void main(String[] args) throws Exception {
        var s=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var output=new ArrayList<String>();
        for (int i=2;i<args.length;i+=2) {
            var c=new CaptureJournal().read(Path.of(args[i]),args[i+1]);
            for (var r:new RedoRecords().readCaptured(c)) {
                if (r.containerUid()!=s.source().containerUid() || r.scn()<=s.scn()) continue;
                boolean owned=false;
                for (var v:r.vectors()) if (v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                    var h=UndoHeader.decode(v);
                    if (h.objectId()==s.heap().logicalObject() && h.dataObjectId()==s.heap().dataObject()) owned=true;
                }
                if (!owned) continue;
                for (var v:r.vectors()) if (v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3)) {
                    var pair=HeapUndoPair.bind(r,v,s.columns().size());var h=pair.undo().identity();
                    if (h.objectId()!=s.heap().logicalObject() || h.dataObjectId()!=s.heap().dataObject()) continue;
                    var f=pair.forward();var u=pair.undo().inverse();
                    output.add("PAIR|"+c.source().sequence()+":"+r.block()+":"+r.offset()+":"+(r.vectors().indexOf(v)+1)
                            +"|"+h.xid()+"|"+r.scn()+"|"+f.kind()+"|"+f.dba()+"|"+f.slot()+"|"+f.extent()
                            +"|"+columns(f.columns())+"|"+u.kind()+"|"+u.extent()+"|"+columns(u.columns()));
                }
            }
        }
        output.forEach(System.out::println);System.err.println("research_only=true;events=0;physical_pairs="+output.size());
    }
}
