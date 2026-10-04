package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ordered 5.1 history and supplied verified compensations; not capture or event authorization. */
public final class UndoHistory {
    private record Node(ArchivedUndoInventory.Entry entry,HeapRowPatch.Uba predecessor) {}
    public record Result(List<ArchivedUndoInventory.Entry> original,List<ArchivedUndoInventory.Entry> live,
                         List<UndoHeader> compensated,HeapRowPatch.Uba allocationTail,HeapRowPatch.Uba activeHead) {}
    private final ArchivedUndoInventory inventory;
    private final Map<HeapRowPatch.Uba,Node> nodes=new HashMap<>();
    private final List<UndoHeader> compensated=new ArrayList<>();
    private HeapRowPatch.Uba head,allocated;
    private int next;
    private boolean failed,closed;

    public UndoHistory(ArchivedUndoInventory inventory) { this.inventory=inventory; }

    /** Inventory order closes selector holes; cross-block full predecessor comes from the active head. */
    public HeapRowPatch.Uba append(ArchivedUndoInventory.Entry entry) throws RedoException {
        healthy();
        if (next>=inventory.entries().size() || !inventory.entries().get(next).equals(entry)) refuse("UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH");
        var h=entry.identity();var address=h.uba();
        if (nodes.containsKey(address)) refuse("UNDO_HISTORY_ADDRESS_REUSE_UNSUPPORTED");
        if (h.beginsTransaction()) {
            if (next!=0 || head!=null) refuse("UNDO_HISTORY_BEGIN_MISMATCH");
        } else {
            if (head==null) refuse("UNDO_HISTORY_MISSING_ACTIVE_PREDECESSOR");
            if (entry.previousDba()==0) {
                var explicit=new HeapRowPatch.Uba(address.dba(),address.sequence(),entry.rci());
                if (!explicit.equals(head)) refuse("UNDO_HISTORY_SAME_BLOCK_HEAD_MISMATCH");
            } else if (entry.rci()!=0 || entry.previousDba()!=head.dba()) refuse("UNDO_HISTORY_CROSS_BLOCK_HEAD_MISMATCH");
        }
        var previous=head;nodes.put(address,new Node(entry,previous));head=address;allocated=address;next++;
        return previous;
    }

    /** Caller must first bind this original to a physical compensation and verified active identity. */
    public void compensate(UndoHeader original) throws RedoException {
        healthy();
        if (head==null || !head.equals(original.uba()) || !nodes.get(head).entry().identity().equals(original))
            refuse("UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD");
        compensated.add(original);head=nodes.get(head).predecessor();
    }

    /** Native 5.4 reference is checked against allocation history, not the post-rollback active head. */
    public Result close(HeapRowPatch.Uba nativeAllocationTail) throws RedoException {
        healthy();
        if (next!=inventory.entries().size()) refuse("UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT");
        if (nativeAllocationTail==null || !nativeAllocationTail.equals(allocated)) refuse("UNDO_HISTORY_ALLOCATION_TAIL_MISMATCH");
        var live=new ArrayList<ArchivedUndoInventory.Entry>();
        for (var at=head;at!=null;at=nodes.get(at).predecessor()) live.add(nodes.get(at).entry());
        closed=true;
        return new Result(inventory.entries(),List.copyOf(live),List.copyOf(compensated),allocated,head);
    }
    private void healthy() throws RedoException {
        if (failed) throw new RedoException("UNDO_HISTORY_ALREADY_REFUSED");
        if (closed) throw new RedoException("UNDO_HISTORY_ALREADY_CLOSED");
    }
    private void refuse(String reason) throws RedoException { failed=true;throw new RedoException(reason); }
}
