package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Bounded original undo graph. Includes compensated nodes; does not select surviving row effects. */
public final class UndoChain {
    private final long containerId;
    private final HeapRowPatch.Xid xid;
    private final HeapRowPatch.Uba begin;
    private final Map<HeapRowPatch.Uba,UndoLink> nodes=new HashMap<>();
    private boolean failed;
    public UndoChain(long containerId,HeapRowPatch.Xid xid,HeapRowPatch.Uba begin) { this.containerId=containerId; this.xid=xid; this.begin=begin; }

    public void add(RedoRecords.Vector vector) throws RedoException {
        healthy();
        if (vector.containerId()!=containerId) refuse("UNDO_CHAIN_CONTAINER_MISMATCH");
        try {
            var link=UndoLink.decode(vector); var h=link.identity();
            if (!h.xid().equals(xid)) refuse("UNDO_CHAIN_XID_MISMATCH");
            if (h.beginsTransaction()!=h.uba().equals(begin)) refuse("UNDO_CHAIN_BEGIN_MISMATCH");
            if (nodes.putIfAbsent(h.uba(),link)!=null) refuse("DUPLICATE_UNDO_CHAIN_ADDRESS");
        } catch (RedoException e) { failed=true; throw e; }
    }

    /** Optional tail is a native 5.4 reference. Null means unique graph tail only, not commit-tail proof. */
    public List<UndoLink> close(HeapRowPatch.Uba expectedTail) throws RedoException {
        healthy();
        if (!nodes.containsKey(begin)) refuse("MISSING_UNDO_CHAIN_BEGIN");
        var referenced=new HashSet<HeapRowPatch.Uba>();
        for (var n:nodes.values()) {
            var previous=n.predecessor();
            if (previous==null) continue;
            if (!nodes.containsKey(previous)) refuse("UNDO_CHAIN_HOLE");
            if (!referenced.add(previous)) refuse("UNDO_CHAIN_BRANCH");
        }
        var tails=new ArrayList<HeapRowPatch.Uba>();
        for (var address:nodes.keySet()) if (!referenced.contains(address)) tails.add(address);
        if (tails.size()!=1) refuse("UNDO_CHAIN_TAIL_NOT_UNIQUE");
        var tail=tails.get(0);
        if (expectedTail!=null && !tail.equals(expectedTail)) refuse("UNDO_CHAIN_END_REFERENCE_MISMATCH");
        var seen=new HashSet<HeapRowPatch.Uba>(); var chain=new ArrayList<UndoLink>();
        for (var address=tail;address!=null;address=nodes.get(address).predecessor()) {
            if (!seen.add(address)) refuse("UNDO_CHAIN_CYCLE");
            chain.add(nodes.get(address));
        }
        if (chain.size()!=nodes.size() || !chain.get(chain.size()-1).identity().uba().equals(begin))
            refuse("UNDO_CHAIN_DISCONNECTED");
        return List.copyOf(chain);
    }
    private void healthy() throws RedoException { if (failed) throw new RedoException("UNDO_CHAIN_ALREADY_REFUSED"); }
    private void refuse(String reason) throws RedoException { failed=true; throw new RedoException(reason); }
}
