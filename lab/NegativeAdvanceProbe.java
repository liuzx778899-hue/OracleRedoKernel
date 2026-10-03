import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Copies real seq10 controls in memory; refusal audit only. */
public class NegativeAdvanceProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);var header=new ArchivedRedo().scan(p);
        RedoRecords.Record[] first={null};RedoRecords.Vector[] start={null},advance={null},end={null};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149 || r.scn()<2158699)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.dba()==0x02400080) {
                if(v.opcodeMinor()==2 && v.fields().get(0).length==32 && half(v.fields().get(0),0)==1) {
                    int flag=half(v.fields().get(0),16);
                    if(flag==0x52){first[0]=r;start[0]=v;}
                    if(flag==10 && advance[0]==null)advance[0]=v;
                } else if(v.opcodeMinor()==4 && v.fields().size()==4 && half(v.fields().get(0),0)==1)end[0]=v;
            }
        });
        if(first[0]==null || start[0]==null || advance[0]==null || end[0]==null || !first[0].vectors().contains(advance[0]))throw new RedoException("NEGATIVE_ADVANCE_FIXTURE_MISSING");
        String[] reasons={"MISSING_UNDO_ADVANCE_UBA","AMBIGUOUS_UNDO_ADVANCE_UBA","UNDO_ADVANCE_WITHOUT_ACTIVE_SEGMENT_IDENTITY",
                "UNDO_ADVANCE_WITHOUT_ACTIVE_SEGMENT_IDENTITY","UNDO_ADVANCE_WITHOUT_ACTIVE_SEGMENT_IDENTITY",
                "UNDO_ADVANCE_IDENTITY_OR_LINK_MISMATCH","UNDO_ADVANCE_NOT_IN_RECORD","AMBIGUOUS_UNDO_ADVANCE_CONTROL_UBA"};
        for(int test=0;test<8;test++) {
            var state=new TransactionLifecycle();state.archive(header);if(test!=2)state.begin(first[0],start[0]);
            var a=copy(advance[0]);var h=UndoBlockAdvance.decode(a).bindUndo(first[0]);var vectors=new ArrayList<RedoRecords.Vector>();
            for(var v:first[0].vectors())vectors.add(v==advance[0] ? a : copy(v));
            var u=vectors.stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.dba()==h.uba().dba() && v.fields().get(0).length==20 && (v.fields().get(0)[18]&255)==1).findFirst().orElseThrow();
            switch(test) {
                case 0 -> a.fields().get(0)[8]=(byte)0xff;
                case 1 -> vectors.add(copy(u));
                case 4 -> {u.fields().get(0)[12]++;u.fields().get(1)[22]++;}
                case 5 -> java.util.Arrays.fill(u.fields().get(1),12,16,(byte)0);
                case 6 -> vectors.remove(a);
                case 7 -> a.fields().get(0)[8]++;
            }
            var r=first[0];var changed=new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),test==3 ? 1 : r.containerUid(),vectors);
            try {state.advance(changed,a);throw new IllegalStateException("NEGATIVE_ACCEPTED:"+test);}
            catch(RedoException e){if(!e.getMessage().equals(reasons[test]))throw e;System.out.println(test+"|"+e.getMessage());}
            try {state.finish();throw new IllegalStateException("FAILURE_NOT_LATCHED");}
            catch(RedoException e){if(!e.getMessage().equals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED"))throw e;}
        }
        var poison=copy(end[0]);poison.fields().get(2)[8]++;
        try {TransactionEnd.decode(poison);throw new IllegalStateException("END_EXTENSION_ACCEPTED");}
        catch(RedoException e){if(!e.getMessage().equals("UNSUPPORTED_TRANSACTION_END_EXTENSION"))throw e;System.out.println("8|"+e.getMessage());}
        System.err.println("real_derived_refusals=9;failure_latches=8;cdc_events=0");
    }
    static RedoRecords.Vector copy(RedoRecords.Vector v){List<byte[]> f=new ArrayList<>();v.fields().forEach(b->f.add(b.clone()));return new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),f);}
    static int half(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
}
