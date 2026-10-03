import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real multi-block vectors copied in memory. Success means refusal audit only. */
public class NegativeCrossCompensationProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);var header=new ArchivedRedo().scan(p);var xid=new HeapRowPatch.Xid(3,26,669);
        var history=new ArrayList<HeapUndoPair>();RedoRecords.Record[] begin={null},comp={null};RedoRecords.Vector[] start={null},marker={null},row={null};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149)return;
            var selected=new ArrayList<UndoRowPatch>();
            for(var u:r.vectors())if(HeapMultiProbe.target(u,73047) && UndoHeader.decode(u).xid().equals(xid))selected.add(UndoRowPatch.decode(u,2));
            for(var v:r.vectors()) {
                if(v.containerId()!=3)continue;
                if(v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().get(0).length==32)
                    for(var u:selected)if(u.identity().beginsTransaction() && UndoAdvanceProbe.word(v.fields().get(0),8)==u.identity().uba().dba() && (v.fields().get(0)[14]&255)==u.identity().uba().record()){begin[0]=r;start[0]=v;}
                if(MultiCompensationProbe.heap(v) && selected.stream().anyMatch(u->u.inverse().dba()==v.dba()) && new HeapRowPatch().decode(v,2).xtype()==1)history.add(HeapUndoPair.bind(r,v,2));
                if(marker[0]==null && v.opcodeMajor()==5 && v.opcodeMinor()==11 && v.fields().get(0).length==28 && UndoAdvanceProbe.word(v.fields().get(0),4)==73047 && UndoCompensation.decode(v).crossesUndoBlock()) {
                    comp[0]=r;marker[0]=v;
                    for(var candidate:r.vectors())if(MultiCompensationProbe.heap(candidate) && new HeapRowPatch().decode(candidate,2).xtype()==2){if(row[0]!=null)throw new RedoException("NEGATIVE_XR_AMBIGUOUS");row[0]=candidate;}
                }
            }
        });
        if(begin[0]==null || comp[0]==null || row[0]==null || history.size()!=120)throw new RedoException("NEGATIVE_CROSS_FIXTURE_MISSING");
        var positive=state(header,begin[0],start[0]);var hit=HeapCompensation.matchCross(comp[0],marker[0],row[0],history,2,positive);
        String[] reasons={"MISSING_HEAP_COMPENSATION_ORIGINAL","AMBIGUOUS_HEAP_COMPENSATION_ORIGINAL","MISSING_HEAP_COMPENSATION_ORIGINAL",
                "CROSS_COMPENSATION_WITHOUT_ACTIVE_IDENTITY","MISSING_HEAP_COMPENSATION_ORIGINAL","MISSING_HEAP_COMPENSATION_ORIGINAL",
                "NOT_CROSS_COMPENSATION_IN_RECORD","CROSS_COMPENSATION_WITHOUT_ACTIVE_IDENTITY","UNSUPPORTED_COMPENSATION_FLAGS"};
        for(int test=0;test<reasons.length;test++) {
            var m=NegativeAdvanceProbe.copy(marker[0]);var xr=NegativeAdvanceProbe.copy(row[0]);var candidates=new ArrayList<>(history);
            switch(test) {
                case 0 -> candidates.remove(hit);
                case 1 -> candidates.add(hit);
                case 2 -> {var u=hit.undo();var h=u.identity();var other=new UndoHeader(new HeapRowPatch.Xid(3,26,669+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),false);
                    candidates.set(candidates.indexOf(hit),new HeapUndoPair(hit.containerUid(),hit.containerId(),hit.forward(),new UndoRowPatch(other,u.inverse(),u.previousDba(),u.predecessorRecord(),u.wrap())));}
                case 3 -> m=new RedoRecords.Vector(m.opcodeMajor(),m.opcodeMinor(),m.blockClass(),m.file(),m.dba()+1,m.scn(),m.sequence(),m.containerId(),m.fields());
                case 4 -> xr.fields().get(3)[3]++;
                case 5 -> m.fields().get(0)[12]++;
                case 8 -> java.util.Arrays.fill(m.fields().get(0),12,16,(byte)0);
            }
            var original=comp[0];var vectors=new ArrayList<RedoRecords.Vector>(List.of(xr));if(test!=6)vectors.add(m);
            var changed=new RedoRecords.Record(original.block(),original.offset(),original.length(),original.validity(),original.scn(),original.subScn(),test==7 ? 1 : original.containerUid(),vectors);
            var state=state(header,begin[0],start[0]);
            try {HeapCompensation.matchCross(changed,m,xr,candidates,2,state);throw new IllegalStateException("NEGATIVE_CROSS_ACCEPTED:"+test);}
            catch(RedoException e){if(!e.getMessage().equals(reasons[test]))throw e;System.out.println(test+"|"+e.getMessage());}
            try {state.finish();throw new IllegalStateException("CROSS_FAILURE_NOT_LATCHED");}
            catch(RedoException e){if(!e.getMessage().equals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED"))throw e;}
        }
        System.err.println("real_derived_cross_refusals=9;failure_latches=9;cdc_events=0");
    }
    static TransactionLifecycle state(ArchivedRedo.Header h,RedoRecords.Record begin,RedoRecords.Vector start)throws Exception{var state=new TransactionLifecycle();state.archive(h);state.begin(begin,start);return state;}
}
