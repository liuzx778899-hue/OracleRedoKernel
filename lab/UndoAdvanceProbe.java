import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Dedicated long transaction control audit only; no cross-block predecessor resolution or CDC. */
public class UndoAdvanceProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);var header=new ArchivedRedo().scan(p);var state=new TransactionLifecycle();state.archive(header);
        var output=new ArrayList<String>();int[] nodes={0},advances={0},ends={0};
        var target=new HeapRowPatch.Xid(1,1,604);
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149 || r.scn()<2158699 || r.scn()>2158815)return;
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=3 || v.opcodeMajor()!=5)continue;
                if(v.opcodeMinor()==2 && v.dba()==0x02400080 && v.fields().get(0).length==32 && half(v.fields().get(0),0)==1) {
                    int flags=half(v.fields().get(0),16);
                    if(flags==0x52){state.begin(r,v);output.add("BEGIN|"+r.block()+"|"+r.offset()+"|"+(i+1));}
                    else if(flags==10){var h=state.advance(r,v);var a=UndoBlockAdvance.decode(v);
                        if(!h.xid().equals(target))throw new RedoException("UNEXPECTED_ADVANCE_XID");
                        output.add("ADVANCE|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+a.segmentDba()+"|"+a.slot()+"|"+h.xid().usn()+"|"+h.xid().sequence()+"|"+h.uba().dba()+"|"+h.uba().sequence()+"|"+h.uba().record());advances[0]++;}
                    else throw new RedoException("UNKNOWN_SELECTED_CONTROL_FLAGS");
                }
            }
            for(var v:r.vectors()) if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().get(0).length==20) {
                byte[] b=v.fields().get(0);
                if(half(b,8)==1 && half(b,10)==1 && word(b,12)==604){state.undo(r,v);nodes[0]++;}
            }
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=3 || v.opcodeMajor()!=5 || v.opcodeMinor()!=4 || !state.knowsEnd(r,v))continue;
                var closed=state.end(r,v);var tail=TransactionEnd.undoTail(v);
                output.add("END|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+closed.endRecordScn()+"|"+tail.dba()+"|"+tail.sequence()+"|"+tail.record()+"|"+closed.rolledBack());ends[0]++;
            }
        });state.finish();
        if(nodes[0]!=120 || advances[0]!=15 || ends[0]!=1)throw new RedoException("ADVANCE_COVERAGE_MISMATCH");
        output.forEach(System.out::println);System.err.println("undo_nodes=120;advances=15;closed_identity=1;cross_block_graph_not_accepted;cdc_events=0");
    }
    static int half(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    static long word(byte[] b,int o){return half(b,o)|((long)half(b,o+2)<<16);}
}
