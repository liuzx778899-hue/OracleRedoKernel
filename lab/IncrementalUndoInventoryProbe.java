package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Compare full inventory with an independent process that receives only the new member. */
public class IncrementalUndoInventoryProbe {
    public static void main(String[] args)throws Exception {
        var pending=DictionaryPendingState.restore(Files.readAllBytes(Path.of(args[3])),args[4],Path.of(args[1]),args[2]);
        var contexts=pending.decoded().contexts();if(contexts.size()!=1)throw new AssertionError("one frozen pending SYS required");var prior=contexts.get(0).graph;
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=7;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);UndoHistory graph;
        if(args[0].equals("reference")) {
            var inventory=prior.inventory();var full=ArchivedUndoInventory.read(inputs,inventory.containerUid(),inventory.containerId(),inventory.entries().get(0).identity().xid());
            byte[] actions=prior.checkpoint();graph=UndoHistory.restore(full,actions,DurableUndoState.sha(actions));
        } else if(args[0].equals("extend"))graph=prior.extend(inputs);
        else throw new IllegalArgumentException("mode");
        byte[] bytes=graph.durableCheckpoint();String digest=DurableUndoState.sha(bytes);
        if(!args[6].equals("-") && !digest.equals(args[6]))throw new AssertionError("incremental inventory/actions differ from full raw discovery");
        Files.write(Path.of(args[5]),bytes,StandardOpenOption.CREATE_NEW);
        System.out.println("STATE_SHA256="+digest);System.out.println("PREVIOUS_ORIGINALS="+prior.inventory().entries().size());
        System.out.println("ORIGINALS="+graph.inventory().entries().size());System.out.println("NEW_ORIGINALS="+(graph.inventory().entries().size()-prior.inventory().entries().size()));
        System.out.println("RAW_MEMBERS="+captures.size());System.out.println("CONTROL_ROOT_PRESERVED="+Objects.equals(prior.inventory().controlRoot(),graph.inventory().controlRoot()));
    }
}
