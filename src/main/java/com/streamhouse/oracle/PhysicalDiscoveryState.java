package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conservative windows bound to a complete authenticated physical prefix, not marker owner certificates. */
final class PhysicalDiscoveryState {
    static byte[] encode(List<String> windows) {
        return ("ORK_PHYSICAL_DISCOVERY_V1\n"+windows.stream().map(s->s+"\n").collect(java.util.stream.Collectors.joining())).getBytes(StandardCharsets.UTF_8);
    }
    static ArchivedPhysicalTransactions.Prefix restore(byte[] bytes,FrozenHeapBaseline.Snapshot baseline,ArchivedPhysicalTransactions.Prefix physical)throws IOException {
        String[] lines=new String(bytes,StandardCharsets.UTF_8).split("\n");
        if(lines.length==0 || !lines[0].equals("ORK_PHYSICAL_DISCOVERY_V1"))throw new RedoException("PHYSICAL_DISCOVERY_FORMAT");
        var values=new ArrayList<String>();var owners=new HashMap<HeapRowPatch.Xid,ArchivedCompensationInventory>();
        for(int i=1;i<lines.length;i++) {
            byte[] input;
            try{input=Base64.getDecoder().decode(lines[i]);}catch(IllegalArgumentException e){throw new RedoException("PHYSICAL_DISCOVERY_FORMAT");}
            var window=ArchivedCompensationInventory.restoreDurable(input,DurableUndoState.sha(input));var inventory=window.undo();
            var xid=inventory.entries().get(0).identity().xid();
            if(!inventory.sources().equals(physical.state().sources()) || inventory.containerUid()!=baseline.source().containerUid()
                    || inventory.containerId()!=baseline.source().containerId() || owners.putIfAbsent(xid,window)!=null)
                throw new RedoException("PHYSICAL_DISCOVERY_SOURCE_OR_OWNER");
            for(var e:inventory.entries()) {
                var h=e.identity();
                if(!(h.objectId()==baseline.heap().logicalObject() && h.dataObjectId()==baseline.heap().dataObject())
                        && !(h.objectId()==baseline.index().logicalObject() && h.dataObjectId()==baseline.index().dataObject()))
                    throw new RedoException("PHYSICAL_DISCOVERY_GENERATION");
            }
            values.add(Base64.getEncoder().encodeToString(input));
        }
        for(var p:physical.state().pending()) {
            var window=owners.get(p.xid());var graph=PhysicalRecoveryState.pendingGraph(p);
            if(window==null || window.complete() || window.segmentDba()!=p.segment() || !window.undo().entries().equals(graph.inventory().entries())
                    || !Objects.equals(window.undo().controlRoot(),graph.inventory().controlRoot())
                    || !window.entries().stream().map(ArchivedCompensationInventory.Entry::position).toList().equals(p.markers()))throw new RedoException("PHYSICAL_DISCOVERY_PENDING_WINDOW");
        }
        for(var t:physical.completed().transactions()) {
            var window=owners.get(t.identity().xid());var p=t.commitPosition();
            if(window==null || !window.complete() || !window.undo().entries().equals(t.graph().original()) || window.lastRecord().archiveSequence()!=p.archiveSequence()
                    || window.lastRecord().block()!=p.block() || window.lastRecord().offset()!=p.offset())throw new RedoException("PHYSICAL_DISCOVERY_CLOSED_WINDOW");
        }
        if(!Arrays.equals(bytes,encode(values)))throw new RedoException("PHYSICAL_DISCOVERY_NONCANONICAL");
        return new ArchivedPhysicalTransactions.Prefix(physical.completed(),new ArchivedPhysicalTransactions.PendingState(physical.state().sources(),physical.state().pending(),physical.state().identityCheckpoint(),values));
    }
}
