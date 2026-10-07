package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ordered 5.1 history and supplied verified compensations; not capture or event authorization. */
public final class UndoHistory {
    private record Node(ArchivedUndoInventory.Entry entry,HeapRowPatch.Uba predecessor) {}
    private record Action(boolean append,int index) {}
    public record Result(List<ArchivedUndoInventory.Entry> original,List<ArchivedUndoInventory.Entry> live,
                         List<UndoHeader> compensated,HeapRowPatch.Uba allocationTail,HeapRowPatch.Uba activeHead) {}
    private final ArchivedUndoInventory inventory;
    private final Map<HeapRowPatch.Uba,Node> nodes=new HashMap<>();
    private final List<UndoHeader> compensated=new ArrayList<>();
    private final List<Action> actions=new ArrayList<>();
    private HeapRowPatch.Uba head,allocated;
    private int next;
    private boolean failed,closed;

    public UndoHistory(ArchivedUndoInventory inventory) { this.inventory=inventory; }

    /** Inventory order closes selector holes; cross-block full predecessor comes from the active head. */
    public HeapRowPatch.Uba append(ArchivedUndoInventory.Entry entry) throws RedoException {
        healthy();
        if (next>=inventory.entries().size() || !inventory.entries().get(next).equals(entry)) refuse("UNDO_HISTORY_INPUT_ORDER_OR_COVERAGE_MISMATCH");
        var h=entry.identity();var address=h.uba();
        var control=inventory.controlRoot();
        boolean allocationOnly=control!=null && h.equals(control.allocationBegin());
        boolean rowRoot=control!=null && h.equals(control.rowRoot());
        if (nodes.containsKey(address)) refuse("UNDO_HISTORY_ADDRESS_REUSE_UNSUPPORTED");
        if (h.beginsTransaction()) {
            if (next!=0 || head!=null) refuse("UNDO_HISTORY_BEGIN_MISMATCH");
        } else if(rowRoot) {
            if(next!=1 || head!=null) refuse("UNDO_HISTORY_NON_ROW_ROOT_ORDER_MISMATCH");
        } else {
            if (head==null) refuse("UNDO_HISTORY_MISSING_ACTIVE_PREDECESSOR");
            if (entry.previousDba()==0) {
                var explicit=new HeapRowPatch.Uba(address.dba(),address.sequence(),entry.rci());
                if (!explicit.equals(head)) refuse("UNDO_HISTORY_SAME_BLOCK_HEAD_MISMATCH");
            } else if (entry.rci()!=0 || entry.previousDba()!=head.dba()) refuse("UNDO_HISTORY_CROSS_BLOCK_HEAD_MISMATCH");
        }
        var previous=head;nodes.put(address,new Node(entry,previous));if(!allocationOnly) head=address;allocated=address;
        actions.add(new Action(true,next));next++;
        return previous;
    }

    /** Caller must first bind this original to a physical compensation and verified active identity. */
    public void compensate(UndoHeader original) throws RedoException {
        healthy();
        if (head==null || !head.equals(original.uba()) || !nodes.get(head).entry().identity().equals(original))
            refuse("UNDO_HISTORY_COMPENSATION_NOT_ACTIVE_HEAD");
        actions.add(new Action(false,inventory.entries().indexOf(nodes.get(head).entry())));
        compensated.add(original);head=nodes.get(head).predecessor();
    }

    /** Pending graph state only; source receipts and a trusted external digest remain required. */
    public byte[] checkpoint()throws RedoException {
        healthy();
        var out=new StringBuilder("ORK_UNDO_HISTORY_V1\nR|").append(inventory.containerUid()).append('|').append(inventory.containerId())
                .append('|').append(digest(String.valueOf(inventory.controlRoot()))).append('\n');
        for(var source:inventory.sources())out.append("S|").append(digest(source.toString())).append('\n');
        for(var action:actions)out.append(action.append()?"A|":"C|").append(action.index()).append('|')
                .append(digest(inventory.entries().get(action.index()).toString())).append('\n');
        return out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    /** Extended inventories must retain every original source and selected entry exactly. */
    public static UndoHistory restore(ArchivedUndoInventory input,byte[] bytes,String trustedSha256)throws RedoException {
        if(!digest(bytes).equals(trustedSha256))throw new RedoException("UNDO_CHECKPOINT_DIGEST_MISMATCH");
        String[] lines=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).split("\n");
        String role="R|"+input.containerUid()+"|"+input.containerId()+"|"+digest(String.valueOf(input.controlRoot()));
        if(lines.length<3 || !lines[0].equals("ORK_UNDO_HISTORY_V1") || !lines[1].equals(role))throw new RedoException("UNDO_CHECKPOINT_SCOPE");
        var state=new UndoHistory(input);int sourceCount=0;
        try {
            int at=2;
            while(at<lines.length && lines[at].startsWith("S|")) {
                if(sourceCount>=input.sources().size() || !lines[at].equals("S|"+digest(input.sources().get(sourceCount).toString())))
                    throw new RedoException("UNDO_CHECKPOINT_SOURCE_PREFIX_CHANGED");
                sourceCount++;at++;
            }
            if(sourceCount==0)throw new RedoException("UNDO_CHECKPOINT_SOURCE_PREFIX_CHANGED");
            for(;at<lines.length;at++) {
                String[] f=lines[at].split("\\|",-1);
                if(f.length!=3 || (!f[0].equals("A") && !f[0].equals("C")))throw new RedoException("UNDO_CHECKPOINT_ACTION");
                int index=Integer.parseInt(f[1]);
                if(index<0 || index>=input.entries().size())throw new RedoException("UNDO_CHECKPOINT_ACTION");
                var entry=input.entries().get(index);
                if(!f[2].equals(digest(entry.toString())) || input.sources().subList(0,sourceCount).stream()
                        .noneMatch(h->h.sequence()==entry.position().archiveSequence()))throw new RedoException("UNDO_CHECKPOINT_ENTRY_CHANGED");
                if(f[0].equals("A"))state.append(entry);else state.compensate(entry.identity());
            }
            // Re-encode only the source prefix: a new verified suffix does not alter the old checkpoint.
            String rebuilt=new String(state.checkpoint(),java.nio.charset.StandardCharsets.UTF_8);
            for(int i=sourceCount;i<input.sources().size();i++)rebuilt=rebuilt.replace("S|"+digest(input.sources().get(i).toString())+"\n","");
            if(!java.util.Arrays.equals(bytes,rebuilt.getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new RedoException("UNDO_CHECKPOINT_NONCANONICAL");
            return state;
        } catch(NumberFormatException e){throw new RedoException("UNDO_CHECKPOINT_ACTION");}
    }
    private static String digest(String text)throws RedoException {return digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String digest(byte[] bytes)throws RedoException {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new RedoException("SHA256_UNAVAILABLE");}
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
    /** Caller must verify the end through its source-bound lifecycle and complete compensation input. No native tail is asserted. */
    Result closeCommitWithoutNativeTail(TransactionLifecycle.ClosedIdentity identity) throws RedoException {
        healthy();
        if (inventory.entries().isEmpty() || identity.rolledBack()
                || !identity.xid().equals(inventory.entries().get(0).identity().xid())
                || identity.containerUid()!=inventory.containerUid() || identity.containerId()!=inventory.containerId())
            refuse("UNDO_HISTORY_COMMIT_IDENTITY_MISMATCH");
        if (next!=inventory.entries().size()) refuse("UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT");
        var first=inventory.entries().get(0).identity().uba();
        if (!compensated.isEmpty() || head==null || !head.equals(allocated)
                || inventory.entries().stream().anyMatch(e->e.identity().uba().dba()!=first.dba()
                    || e.identity().uba().sequence()!=first.sequence()))
            refuse("UNDO_HISTORY_UNREFERENCED_COMMIT_SCOPE_UNSUPPORTED");
        var live=new ArrayList<ArchivedUndoInventory.Entry>();
        for (var at=head;at!=null;at=nodes.get(at).predecessor()) live.add(nodes.get(at).entry());
        closed=true;
        return new Result(inventory.entries(),List.copyOf(live),List.of(),allocated,head);
    }
    /** Caller must verify the rollback through its source-bound lifecycle and complete compensation input. */
    public Result closeRollback(TransactionLifecycle.ClosedIdentity identity) throws RedoException {
        healthy();
        if (!identity.rolledBack() || !identity.xid().equals(inventory.entries().get(0).identity().xid())
                || identity.containerUid()!=inventory.containerUid() || identity.containerId()!=inventory.containerId())
            refuse("UNDO_HISTORY_ROLLBACK_IDENTITY_MISMATCH");
        if (next!=inventory.entries().size()) refuse("UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT");
        if (head!=null) refuse("UNDO_HISTORY_ROLLBACK_HAS_LIVE_HEAD");
        closed=true;
        return new Result(inventory.entries(),List.of(),List.copyOf(compensated),allocated,null);
    }
    private void healthy() throws RedoException {
        if (failed) throw new RedoException("UNDO_HISTORY_ALREADY_REFUSED");
        if (closed) throw new RedoException("UNDO_HISTORY_ALREADY_CLOSED");
    }
    private void refuse(String reason) throws RedoException { failed=true;throw new RedoException(reason); }
}
