import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Bounded two-column long-transaction heap matcher; no index effects or accepted cross-block graph. */
public class MultiCompensationProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);long object=Long.parseLong(args[1]);String[] x=args[2].split("\\.");
        int expectedOriginals=args.length>=4 ? Integer.parseInt(args[3]) : 120;
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var header=new ArchivedRedo().scan(p);var state=new TransactionLifecycle();state.archive(header);
        var inventory=ArchivedUndoInventory.read(List.of(p),677737149,3,xid);var graph=new UndoHistory(inventory);
        var edges=new ArrayList<String>();int[] next={0};UndoHistory.Result[] closed={null};
        var history=new ArrayList<HeapUndoPair>();var cancelled=new HashSet<HeapUndoPair>();var output=new ArrayList<String>();
        int[] ends={0};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149)return;
            var inverses=new ArrayList<UndoRowPatch>();
            for(var u:r.vectors())if(HeapMultiProbe.target(u,object) && UndoHeader.decode(u).xid().equals(xid))inverses.add(UndoRowPatch.decode(u,2));
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().get(0).length==32) {
                for(var u:inverses)if(u.identity().beginsTransaction() && UndoAdvanceProbe.word(v.fields().get(0),8)==u.identity().uba().dba()
                        && UndoAdvanceProbe.half(v.fields().get(0),12)==u.identity().uba().sequence() && (v.fields().get(0)[14]&255)==u.identity().uba().record())state.begin(r,v);
            }
            for(var v:r.vectors())if(HeapMultiProbe.target(v,object) && UndoHeader.decode(v).xid().equals(xid)) {
                state.undo(r,v);var entry=inventory.entries().get(next[0]);
                if(!entry.position().equals(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(v)+1)))throw new RedoException("HISTORY_SOURCE_POSITION_MISMATCH");
                if(entry.previousDba()!=0) {
                    int controls=0;
                    for(var control:r.vectors())if(control.containerId()==3 && control.opcodeMajor()==5 && control.opcodeMinor()==2 && control.fields().get(0).length==32) {
                        byte[] b=control.fields().get(0);var u=entry.identity().uba();
                        if(UndoAdvanceProbe.word(b,8)!=u.dba() || UndoAdvanceProbe.half(b,12)!=u.sequence() || (b[14]&255)!=u.record())continue;
                        var bound=u.record()==1 ? state.advance(r,control) : state.headUpdate(r,control);
                        if(!bound.equals(entry.identity()))throw new RedoException("HISTORY_CONTROL_IDENTITY_MISMATCH");controls++;
                    }
                    if(controls!=1)throw new RedoException("HISTORY_CONTROL_COUNT");
                }
                var previous=graph.append(entry);next[0]++;var inverse=UndoRowPatch.decode(v,2).inverse();
                byte[] ktb=v.fields().get(2);HeapRowPatch.Uba historical=null;
                if(ktb[0]==2 && ktb.length==16)historical=new HeapRowPatch.Uba(UndoAdvanceProbe.word(ktb,8),UndoAdvanceProbe.half(ktb,12),ktb[14]&255);
                edges.add("NODE|"+r.block()+"|"+r.offset()+"|"+(r.vectors().indexOf(v)+1)+"|"+UndoCursorProbe.address(entry.identity().uba())+"|"+UndoCursorProbe.address(previous)+"|"+entry.previousDba()+"|"+entry.rci()+"|"+inverse.dba()+"|"+inverse.slot()+"|"+UndoCursorProbe.address(historical));
            }
            for(var v:r.vectors())if(heap(v) && inverses.stream().anyMatch(u->u.inverse().dba()==v.dba())) {
                var row=new HeapRowPatch().decode(v,2);if(row.xtype()!=1)continue;
                var pair=HeapUndoPair.bind(r,v,2);state.requireActive(r,pair);
                if(!pair.undo().identity().xid().equals(xid))throw new RedoException("MULTI_PAIR_XID_MISMATCH");history.add(pair);
            }
            for(var marker:r.vectors())if(marker.containerId()==3 && marker.opcodeMajor()==5 && (marker.opcodeMinor()==6 || marker.opcodeMinor()==11)
                    && marker.fields().get(0).length>=8 && UndoAdvanceProbe.word(marker.fields().get(0),4)==object) {
                var rows=new ArrayList<RedoRecords.Vector>();
                for(var v:r.vectors())if(heap(v) && new HeapRowPatch().decode(v,2).xtype()==2)rows.add(v);
                if(rows.size()!=1)throw new RedoException("MULTI_XR_ROW_COUNT");
                var pair=UndoCompensation.decode(marker).crossesUndoBlock()
                        ? HeapCompensation.matchCross(r,marker,rows.get(0),history,2,state)
                        : HeapCompensation.match(r,marker,rows.get(0),history,2);state.requireActive(r,pair);
                int index=history.indexOf(pair);
                int active=history.size()-1;
                while(active>=0 && cancelled.contains(history.get(active)))active--;
                if(index!=active || !cancelled.add(pair))throw new RedoException("MULTI_COMPENSATION_SUFFIX_OR_DUPLICATE:rba="+r.block()+":"+r.offset()+":index="+index+":expected="+active+":history="+history.size()+":cancelled="+cancelled.size()+":uba="+UndoCursorProbe.address(pair.undo().identity().uba()));
                graph.compensate(pair.undo().identity());
                output.add("CANCEL|"+r.block()+"|"+r.offset()+"|"+UndoCursorProbe.address(pair.undo().identity().uba())+"|"+index);
            }
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                var tail=TransactionEnd.undoTail(v);closed[0]=graph.close(tail);edges.add("END|"+r.block()+"|"+r.offset()+"|"+UndoCursorProbe.address(tail));state.end(r,v);ends[0]++;
            }
        });state.finish();
        if(history.size()!=expectedOriginals || cancelled.size()!=60 || ends[0]!=1)throw new RedoException("MULTI_COMPENSATION_COVERAGE_MISMATCH");
        var live=new HashSet<HeapRowPatch.Uba>();for(var e:closed[0].live())live.add(e.identity().uba());
        if(live.size()!=history.size()-cancelled.size())throw new RedoException("HISTORY_LIVE_SET_COUNT_MISMATCH");
        for(int i=0;i<history.size();i++) {
            var pair=history.get(i);if(!live.contains(pair.undo().identity().uba()))continue;
            output.add("RETAIN|"+UndoCursorProbe.address(pair.undo().identity().uba())+"|"+i+"|"+pair.forward().dba()+"|"+pair.forward().slot()+"|"+HeapUndoPairProbe.cols(pair.forward()));
        }
        if(args.length==5)java.nio.file.Files.write(Path.of(args[4]),edges);
        output.forEach(System.out::println);System.err.println("heap_pairs="+history.size()+";cancelled_suffix=60;retained_heap_patches="+live.size()+";ordered_5_1_history_only;all_transaction_opcodes_index_recovery_not_accepted;cdc_events=0");
    }
    static boolean heap(RedoRecords.Vector v){return v.containerId()==3 && v.opcodeMajor()==11 && (v.opcodeMinor()==2 || v.opcodeMinor()==3 || v.opcodeMinor()==5);}
}
