import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

/** Frozen concurrent fixture: raw fields or existing matcher refusal, never CDC events. */
public class IndexSlotShiftProbe {
    public static void main(String[] args) throws Exception {
        boolean match=args[0].equals("match");
        if (!match && !args[0].equals("fields")) throw new IllegalArgumentException("UNKNOWN_MODE");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        var state=new TransactionLifecycle();state.archive(header);
        var xid=new HeapRowPatch.Xid(10,23,1220);
        var pairs=new ArrayList<IndexUndoPair>();var output=new ArrayList<String>();int[] matches={0};
        new RedoRecords().visit(path,header,r -> {
            if (r.containerUid()!=677737149) return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if (v.containerId()!=3) continue;
                int metadata=v.opcodeMinor()==1 ? 1 : 0;
                boolean selected=v.opcodeMajor()==10 && v.dba()==0x0300013bL;
                selected|=v.opcodeMajor()==5 && (v.opcodeMinor()==1 || v.opcodeMinor()==6 || v.opcodeMinor()==11)
                        && v.fields().size()>metadata && v.fields().get(metadata).length>=8
                        && IndexVectorSurvey.word(v.fields().get(metadata),4)==73062;
                if (!match && selected) {
                    var line=new StringBuilder(header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba()+"|"+r.scn());
                    for (byte[] field:v.fields()) line.append('|').append(HexFormat.of().formatHex(field));
                    output.add(line.toString());
                }
            }
            if (!match) return;
            for (var u:r.vectors()) {
                if (u.containerId()!=3 || u.opcodeMajor()!=5 || u.opcodeMinor()!=1 || u.fields().get(0).length!=20) continue;
                byte[] b=u.fields().get(0);
                if (TransactionLifecycleProbe.half(b,8)!=10 || TransactionLifecycleProbe.half(b,10)!=23
                        || TransactionLifecycleProbe.word(b,12)!=1220) continue;
                var h=UndoHeader.decode(u);
                if (h.beginsTransaction()) {
                    int starts=0;
                    for (var v:r.vectors()) if (v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2
                            && TransactionStart.decode(v).uba().equals(h.uba())) {state.begin(r,v);starts++;}
                    if (starts!=1) throw new RedoException("SHIFT_BEGIN_COUNT");
                }
                state.undo(r,u);
                if (h.dataObjectId()==73062) for (var v:r.vectors()) if (v.opcodeMajor()==10 && v.opcodeMinor()==4) {
                    var pair=IndexUndoPair.bind(r,v);state.requireActive(r,pair);pairs.add(pair);
                }
            }
            for (var marker:r.vectors()) if (marker.containerId()==3 && marker.opcodeMajor()==5 && marker.opcodeMinor()==6) {
                var c=UndoCompensation.decode(marker);
                if (c.dataObjectId()!=73062 || c.slot()!=xid.slot() || c.wrap()!=xid.sequence()) continue;
                for (var row:r.vectors()) if (row.containerId()==3 && row.opcodeMajor()==10 && row.opcodeMinor()==5) {
                    IndexCompensation.match(r,marker,row,pairs,state);matches[0]++;
                }
            }
        });
        if (match && matches[0]!=1) throw new RedoException("SHIFT_MATCH_COUNT");
        output.forEach(System.out::println);
        System.err.println("index_shift_vectors="+output.size()+";matches="+matches[0]+";cdc_events=0;complete_transaction_not_accepted");
    }
}
