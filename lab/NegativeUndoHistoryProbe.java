import com.streamhouse.oracle.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

/** Real inventory + decoder-matched physical cancellation timeline, deliberately poisoned in memory. */
public class NegativeUndoHistoryProbe {
    record Event(long block,int offset,ArchivedUndoInventory.Entry entry,UndoHeader cancel) {}
    public static void main(String[] args)throws Exception {
        var input=ArchivedUndoInventory.read(java.util.List.of(Path.of(args[0])),677737149,3,new HeapRowPatch.Xid(10,2,1163));
        var events=new ArrayList<Event>();
        for(var e:input.entries())events.add(new Event(e.position().block(),e.position().offset(),e,null));
        for(var line:Files.readAllLines(Path.of(args[1])))if(line.startsWith("CANCEL|")) {
            var p=line.split("\\|");var h=input.entries().stream().map(ArchivedUndoInventory.Entry::identity).filter(t->UndoCursorProbe.address(t.uba()).equals(p[3])).findFirst().orElseThrow();
            events.add(new Event(Long.parseLong(p[1]),Integer.parseInt(p[2]),null,h));
        }
        events.sort(Comparator.comparingLong(Event::block).thenComparingInt(Event::offset));
        var tail=input.entries().get(input.entries().size()-1).identity().uba();
        String[] reasons={"UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH","UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH","UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH",
                "UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD","UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD","UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD",
                "UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT","UNDO_HISTORY_ALLOCATION_TAIL_MISMATCH","UNDO_HISTORY_CROSS_BLOCK_HEAD_MISMATCH"};
        for(int mode=0;mode<9;mode++) {
            var history=new UndoHistory(input);int original=0,cancel=0;
            try {
                if(mode==2)history.append(input.entries().get(1));
                for(var e:events) {
                    if(e.entry()!=null) {
                        if(mode==0 && original++==1)continue;
                        history.append(e.entry());
                        if(mode==1 && e.entry().equals(input.entries().get(0)))history.append(e.entry());
                        if(mode==6)history.close(tail);
                    } else {
                        if(mode==8 || (mode==3 && cancel++==0))continue;
                        var h=e.cancel();
                        if(mode==4 && cancel++==0)h=new UndoHeader(new HeapRowPatch.Xid(h.xid().usn(),h.xid().slot(),h.xid().sequence()+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),h.beginsTransaction());
                        history.compensate(h);
                        if(mode==5 && cancel++==0)history.compensate(h);
                    }
                }
                history.close(mode==7 ? input.entries().get(0).identity().uba() : tail);
                throw new IllegalStateException("negative accepted: "+mode);
            } catch(RedoException expected) {
                if(!expected.getMessage().equals(reasons[mode]))throw new IllegalStateException("unexpected negative reason: "+mode,expected);
                try{history.close(tail);throw new IllegalStateException("not latched: "+mode);}catch(RedoException locked) {
                    if(!locked.getMessage().equals("UNDO_HISTORY_ALREADY_REFUSED"))throw locked;
                }
                System.out.println("REFUSED|"+mode+"|"+expected.getMessage());
            }
        }
        System.err.println("real_derived_history_cases=9;failure_latches=9;cdc_events=0");
    }
}
