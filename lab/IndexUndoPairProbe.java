import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

/** Frozen same-record index association, buffered until selected undo coverage passes. */
public class IndexUndoPairProbe {
    public static void main(String[] args) throws Exception {
        var output=new ArrayList<String>();
        int[] count={0};
        for (String filename:args) {
            Path path=Path.of(filename); var h=new ArchivedRedo().scan(path);
            new RedoRecords().visit(path,h,r -> {
                if (r.containerUid()!=677737149) return;
                var selected=new ArrayList<RedoRecords.Vector>();
                for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1
                        && v.fields().size()>=2 && v.fields().get(1).length>=8
                        && IndexVectorSurvey.word(v.fields().get(1),4)==73025) selected.add(v);
                count[0]+=selected.size();
                int paired=0;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);
                    if (v.containerId()!=3 || v.opcodeMajor()!=10 || (v.opcodeMinor()!=2 && v.opcodeMinor()!=4)
                            || v.dba()!=0x0300008bL) continue;
                    var p=IndexUndoPair.bind(r,v); var f=p.forward(); var u=p.undo(); var identity=u.identity();
                    if (identity.dataObjectId()!=73025) throw new RedoException("UNEXPECTED_INDEX_OBJECT");
                    var matches=new ArrayList<Integer>();
                    for (int j=0;j<r.vectors().size();j++) if (selected.contains(r.vectors().get(j))
                            && UndoHeader.decode(r.vectors().get(j)).uba().equals(identity.uba())) matches.add(j+1);
                    if (matches.size()!=1) throw new RedoException("SELECTED_INDEX_UNDO_POSITION_AMBIGUOUS");
                    output.add(String.join("|",Long.toString(h.sequence()),Long.toString(r.block()),Integer.toString(r.offset()),
                            Integer.toString(i+1),Integer.toString(matches.get(0)),f.kind().toString(),Long.toString(f.block()),
                            Integer.toString(f.itl()),Integer.toString(f.slot()),Integer.toString(f.rowSize()),
                            identity.xid().usn()+":"+identity.xid().slot()+":"+identity.xid().sequence(),
                            identity.uba().dba()+":"+identity.uba().sequence()+":"+identity.uba().record(),
                            u.kind().toString(),Long.toString(u.indexId()),Integer.toString(u.flags()),Integer.toString(u.sdc()),
                            Long.toString(u.previousDba()),Integer.toString(u.predecessorRecord()),Integer.toString(u.wrap()),
                            hex(u.key()),hex(u.keydata()),hex(f.key()),hex(f.keydata())));
                    paired++;
                }
                if (paired!=selected.size()) throw new RedoException("UNPAIRED_SELECTED_INDEX_UNDO_RECORDS");
            });
        }
        if (count[0]!=14 || output.size()!=14) throw new RedoException("FIXTURE_INDEX_PAIR_COUNT_MISMATCH");
        output.forEach(System.out::println);
        System.err.println("bounded_index_pairs=14;cdc_events=0");
    }
    static String hex(byte[] bytes) { return bytes==null ? "NONE" : HexFormat.of().formatHex(bytes); }
}
