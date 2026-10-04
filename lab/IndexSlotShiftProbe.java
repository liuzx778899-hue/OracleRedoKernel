import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Frozen concurrent fixture: raw fields or existing matcher refusal, never CDC events. */
public class IndexSlotShiftProbe {
    public static void main(String[] args) throws Exception {
        String mode=args[0];boolean positioned=!mode.equals("fields") && !mode.equals("match");
        if (!List.of("fields","match","position","wrong-position-slot","wrong-position-marker","wrong-position-pair-key",
                "repeat-position","duplicate-position-original","omit-foreign-position","foreign-position-source").contains(mode)) throw new IllegalArgumentException("UNKNOWN_MODE");
        boolean match=!mode.equals("fields");
        Path path=Path.of(args[1]);var header=new ArchivedRedo().scan(path);
        var owned=positioned ? ArchivedIndexLeafInventory.read(List.of(path),677737149,3,new HeapRowPatch.Xid(10,23,1220),73062,0x0300013bL) : null;
        var positions=positioned ? new IndexLeafPositions(owned) : null;
        if (positioned && !header.equals(owned.window().undo().sources().get(0))) throw new RedoException("SHIFT_POSITION_INPUT_CHANGED");
        if (mode.equals("omit-foreign-position")) owned.requireCompleteSelection(owned.entries().stream().filter(e->e.original()==null || e.original().xid().equals(new HeapRowPatch.Xid(10,23,1220))).map(ArchivedIndexLeafInventory.Entry::position).toList());
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
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(row)+1);
                    if (mode.equals("wrong-position-slot")) row.fields().get(1)[2]^=1;
                    if (mode.equals("wrong-position-marker")) marker.fields().get(0)[24]^=1;
                    if (mode.equals("wrong-position-pair-key")) pairs.get(0).undo().key()[2]^=1;
                    if (mode.equals("foreign-position-source")) at=new ArchivedUndoInventory.Position(header.sequence()+1,r.block(),r.offset(),at.vector());
                    var candidates=mode.equals("duplicate-position-original") ? List.of(pairs.get(0),pairs.get(0)) : pairs;
                    var original=positioned ? IndexCompensation.match(r,marker,row,candidates,state,positions,at)
                            : IndexCompensation.match(r,marker,row,candidates,state);
                    if (mode.equals("repeat-position")) IndexCompensation.match(r,marker,row,candidates,state,positions,at);
                    if (positioned) output.add("MATCH|"+header.sequence()+":"+r.block()+":"+r.offset()+":"+(r.vectors().indexOf(row)+1)
                            +"|"+xid.usn()+"."+xid.slot()+"."+xid.sequence()+"|"+original.undo().identity().uba().dba()+":"+original.undo().identity().uba().sequence()+":"+original.undo().identity().uba().record()
                            +"|"+IndexCompensation.decodeRollback(row).kind()+"|"+original.forward().slot()+"|"+IndexCompensation.decodeRollback(row).slot());
                    matches[0]++;
                }
            }
        });
        if (match && matches[0]!=1) throw new RedoException("SHIFT_MATCH_COUNT");
        if (!header.equals(new ArchivedRedo().scan(path))) throw new RedoException("SHIFT_POSITION_INPUT_CHANGED_AFTER_TRAVERSAL");
        output.forEach(System.out::println);
        System.err.println("index_shift_vectors="+output.size()+";matches="+matches[0]+";cdc_events=0;complete_transaction_not_accepted");
    }
}
