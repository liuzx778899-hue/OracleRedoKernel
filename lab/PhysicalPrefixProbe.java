package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Complete physical prefix gates, with explicit pending state and no event authorization. */
public class PhysicalPrefixProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=3;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var history=ArchivedDictionaryHistory.read(inputs,snapshot);
        var prefix=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,Long.decode(args[2]));
        var rows=new ArrayList<String>();rows.add("PREFIX|"+prefix.completed().transactions().size()+"|"+prefix.state().pending().size());
        rows.add("IDENTITY|"+prefix.state().identityCheckpoint());
        for(var p:prefix.state().pending()) {
            rows.add("PENDING|"+p.xid()+"|"+p.segment()+"|"+p.next()+"|"+p.heaps().size()+"|"+p.indexes().size()+"|"+p.markers().size());
            rows.add("GRAPH|"+p.graphCheckpoint());
            for(var op:p.operations())rows.add("OPERATION|"+op);
        }
        for(var t:prefix.completed().transactions())rows.add("COMPLETED|"+t.identity());
        System.out.println(String.join("\n",rows));
    }
}
