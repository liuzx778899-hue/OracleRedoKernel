import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bounded raw-only head assignment audit. Neither graph closure nor resumed-effect authorization. */
public class UndoHeadUpdateProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);var state=new TransactionLifecycle();state.archive(header);
        var xid=new HeapRowPatch.Xid(10,2,1163);var output=new ArrayList<String>();
        RedoRecords.Record[] beginRecord={null},updateRecord={null};RedoRecords.Vector[] begin={null},update={null};
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=677737149)return;
            var selected=new ArrayList<RedoRecords.Vector>();
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().get(0).length==20) {
                byte[] b=v.fields().get(0);
                if(UndoAdvanceProbe.half(b,8)==xid.usn() && UndoAdvanceProbe.half(b,10)==xid.slot() && UndoAdvanceProbe.word(b,12)==xid.sequence())selected.add(v);
            }
            for(var u:selected)if(UndoHeader.decode(u).beginsTransaction())for(var v:r.vectors()) {
                if(v.opcodeMajor()!=5 || v.opcodeMinor()!=2 || v.containerId()!=3 || v.fields().get(0).length!=32)continue;
                byte[] b=v.fields().get(0);var h=UndoHeader.decode(u);
                if(UndoAdvanceProbe.word(b,8)==h.uba().dba() && UndoAdvanceProbe.half(b,12)==h.uba().sequence() && (b[14]&255)==h.uba().record()) {
                    state.begin(r,v);beginRecord[0]=r;begin[0]=v;
                }
            }
            for(var u:selected)state.undo(r,u);
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().get(0).length==32 && UndoAdvanceProbe.half(v.fields().get(0),16)==2) {
                var control=UndoHeadUpdate.decode(v);
                if(selected.stream().noneMatch(u->{try{return UndoHeader.decode(u).uba().equals(control.uba());}catch(RedoException e){throw new RuntimeException(e);}}))continue;
                var h=state.headUpdate(r,v);var u=selected.stream().filter(t->{try{return UndoHeader.decode(t).uba().equals(h.uba());}catch(RedoException e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
                byte[] b=u.fields().get(1);updateRecord[0]=r;update[0]=v;
                output.add("HEAD|"+r.block()+"|"+r.offset()+"|"+(r.vectors().indexOf(v)+1)+"|"+control.segmentDba()+"|"+control.slot()+"|"+UndoCursorProbe.address(h.uba())+"|10.2.1163|"+UndoAdvanceProbe.word(b,12)+"|"+(b[19]&255));
            }
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v))state.end(r,v);
        });state.finish();
        if(output.size()!=1 || begin[0]==null)throw new RedoException("HEAD_UPDATE_FIXTURE_COVERAGE_MISMATCH");
        if(args.length==2 && args[1].equals("--negatives")) {
            var original=updateRecord[0];var control=update[0];
            String[] reasons={"MISSING_UNDO_HEAD_UPDATE_UBA","AMBIGUOUS_UNDO_HEAD_UPDATE_UBA","UNDO_HEAD_UPDATE_WITHOUT_ACTIVE_SEGMENT_IDENTITY",
                    "UNDO_HEAD_UPDATE_WITHOUT_ACTIVE_SEGMENT_IDENTITY","UNSUPPORTED_UNDO_HEAD_UPDATE_FLAGS_OR_PARENT","UNDO_HEAD_UPDATE_IDENTITY_OR_LINK_MISMATCH",
                    "UNDO_HEAD_UPDATE_IDENTITY_OR_LINK_MISMATCH","UNDO_HEAD_UPDATE_NOT_IN_RECORD","AMBIGUOUS_UNDO_HEAD_UPDATE_CONTROL_UBA","UNDO_HEAD_UPDATE_WITHOUT_ACTIVE_SEGMENT_IDENTITY"};
            for(int mode=0;mode<10;mode++) {
                var vectors=new ArrayList<RedoRecords.Vector>();RedoRecords.Vector candidate=null;
                for(var v:original.vectors()) {
                    var fields=new ArrayList<byte[]>();for(var f:v.fields())fields.add(f.clone());
                    var clone=new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba()+(mode==3 && v==control ? 1 : 0),v.scn(),v.sequence(),v.containerId(),List.copyOf(fields));
                    if(v==control)candidate=clone;
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                        if(mode==0)continue;
                        if(mode==2)fields.get(0)[14]++;
                        if(mode==5)java.util.Arrays.fill(fields.get(1),12,16,(byte)0);
                        if(mode==6)fields.get(1)[19]=1;
                        vectors.add(clone);if(mode==1)vectors.add(clone);
                    } else {vectors.add(clone);if(mode==8 && v==control)vectors.add(clone);}
                }
                if(mode==4)candidate.fields().get(0)[16]=3;
                if(mode==7)vectors.remove(candidate);
                var record=new RedoRecords.Record(original.block(),original.offset(),original.length(),original.validity(),original.scn(),original.subScn(),original.containerUid()+(mode==9 ? 1 : 0),List.copyOf(vectors));
                var s=new TransactionLifecycle();s.archive(header);s.begin(beginRecord[0],begin[0]);
                try{s.headUpdate(record,candidate);throw new IllegalStateException("negative accepted: "+mode);}catch(RedoException expected) {
                    if(!expected.getMessage().equals(reasons[mode]))throw new IllegalStateException("wrong negative reason: "+mode,expected);
                    try{s.finish();throw new IllegalStateException("negative did not lock: "+mode);}catch(RedoException locked) {
                        if(!locked.getMessage().equals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED"))throw locked;
                    }
                    System.out.println("REFUSED|"+mode+"|"+expected.getMessage());
                }
            }
            System.err.println("derived_real_negatives=10;failure_latches=10;cdc_events=0");
        } else {output.forEach(System.out::println);System.err.println("head_updates=1;full_active_identity_only;cdc_events=0");}
    }
}
