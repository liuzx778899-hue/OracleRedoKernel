import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Full frozen original/control coverage and commit tail only. No SYS row values or CDC events. */
public class SysUndoHistoryProbe {
    static int half(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    static long word(byte[] b,int o){return half(b,o)|((long)half(b,o+2)<<16);}
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);String[] x=args[1].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]));
        var inventory=ArchivedUndoInventory.read(List.of(path),677737149,3,xid);
        var header=inventory.sources().get(0);var lifecycle=new TransactionLifecycle();lifecycle.archive(header);
        var graph=new UndoHistory(inventory);var output=new ArrayList<String>();
        var first=inventory.entries().get(0);TransactionStart[] start={null};int[] next={0},ends={0};
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=677737149)return;
            if(r.block()==first.position().block()&&r.offset()==first.position().offset()) {
                int begins=0;
                for(var v:r.vectors())if(v.containerId()==3&&v.opcodeMajor()==5&&v.opcodeMinor()==2) {
                    var s=TransactionStart.decode(v);if(!s.uba().equals(first.identity().uba()))continue;
                    if(!s.bindBegin(r).equals(xid))throw new RedoException("RESEARCH_BEGIN_XID_MISMATCH");
                    lifecycle.begin(r,v);start[0]=s;begins++;
                    output.add("CONTROL|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|5.2|"+s.flags());
                }
                if(begins!=1)throw new RedoException("RESEARCH_BEGIN_CONTROL_COVERAGE");
            } else if(start[0]!=null&&ends[0]==0) {
                for(var v:r.vectors())if(v.containerId()==3&&v.opcodeMajor()==5&&v.opcodeMinor()==2&&v.dba()==start[0].segmentDba()) {
                    if(v.fields().isEmpty()||v.fields().get(0).length!=32)throw new RedoException("RESEARCH_UNREADABLE_ACTIVE_CONTROL");
                    byte[] b=v.fields().get(0);if(half(b,0)!=xid.slot())continue;
                    lifecycle.observeSlotControl(r,v);
                    var root=inventory.controlRoot();
                    if(half(b,16)==10)lifecycle.advance(r,v);
                    else if(root==null||half(b,16)!=2||word(b,8)!=root.rowRoot().uba().dba()
                            ||half(b,12)!=root.rowRoot().uba().sequence()||(b[14]&255)!=root.rowRoot().uba().record())
                        throw new RedoException("RESEARCH_UNSUPPORTED_INTERVENING_CONTROL");
                    output.add("CONTROL|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|5.2|"+half(b,16));
                }
            }
            for(int index=0;index<r.vectors().size();index++) {
                var v=r.vectors().get(index);if(v.containerId()!=3||v.opcodeMajor()!=5||v.opcodeMinor()!=1)continue;
                if(v.fields().isEmpty()||v.fields().get(0).length!=20)throw new RedoException("RESEARCH_UNREADABLE_UNDO_IDENTITY");
                byte[] b=v.fields().get(0);
                if(half(b,8)!=xid.usn()||half(b,10)!=xid.slot()||word(b,12)!=xid.sequence())continue;
                lifecycle.undo(r,v);var e=inventory.entries().get(next[0]++);
                if(!e.position().equals(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),index+1)))
                    throw new RedoException("RESEARCH_POSITION_COVERAGE_MISMATCH");
                var predecessor=graph.append(e);
                output.add("NODE|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(index+1)+"|"+e.identity().uba()+"|"+predecessor);
            }
            for(var v:r.vectors())if(v.containerId()==3&&v.opcodeMajor()==5&&v.opcodeMinor()==4&&lifecycle.knowsEnd(r,v)) {
                var closed=lifecycle.end(r,v);if(closed.rolledBack())throw new RedoException("RESEARCH_ROLLBACK_UNSUPPORTED");
                var result=graph.close(TransactionEnd.undoTail(v));ends[0]++;
                output.add("END|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+closed.endRecordScn()+"|"+result.original().size()+"|"+result.live().size()+"|"+result.allocationTail());
            }
        });
        lifecycle.finish();if(ends[0]!=1||next[0]!=inventory.entries().size())throw new RedoException("RESEARCH_END_ORIGINAL_COVERAGE_MISMATCH");
        if(!header.equals(new ArchivedRedo().scan(path)))throw new RedoException("RESEARCH_INPUT_CHANGED");
        output.forEach(System.out::println);System.err.println("original_control_history_only;sys_row_values_unverified;cdc_events=0");
    }
}
