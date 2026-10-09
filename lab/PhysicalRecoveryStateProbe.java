package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Frozen physical state plus row-state continuation. Raw discovery remains explicit. */
public class PhysicalRecoveryStateProbe {
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),file=Path.of(args[3]);var snapshot=FrozenHeapBaseline.read(baseline,args[2]);
        if(args[0].equals("restore")) {
            byte[] bytes=Files.readAllBytes(file);var state=PhysicalRecoveryState.restore(bytes,args[4],baseline,args[2],50359491);var prefix=state.prefix();
            if(!Arrays.equals(bytes,state.checkpoint()))throw new AssertionError("checkpoint changed");
            System.out.println("PENDING="+prefix.state().pending().size());System.out.println("CLOSED="+prefix.completed().transactions().size());
            System.out.println("HEAP_PAIRS="+prefix.state().pending().stream().mapToInt(p->p.heaps().size()).sum());
            System.out.println("INDEX_PAIRS="+prefix.state().pending().stream().mapToInt(p->p.indexes().size()).sum());
            System.out.println("MARKERS="+prefix.state().pending().stream().mapToInt(p->p.markers().size()).sum());
            System.out.println("RAW_PATH_ARGUMENTS=0");return;
        }
        int first=args[0].equals("export")?4:7;var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=first;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        var strict=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,50359491);
        if(args[0].equals("export")) {
            var state=PhysicalRecoveryState.from(inputs,snapshot,50359491,strict);byte[] bytes=state.checkpoint();
            var restored=PhysicalRecoveryState.restore(bytes,DurableUndoState.sha(bytes),snapshot,50359491).prefix();
            if(!restored.completed().equals(strict.completed()) || restored.state().pending().size()!=strict.state().pending().size())throw new AssertionError("physical prefix changed");
            Files.write(file,bytes,StandardOpenOption.CREATE_NEW);System.out.println("STATE_SHA256="+DurableUndoState.sha(bytes));System.out.println("STATE_BYTES="+bytes.length);
        } else if(args[0].equals("resume")) {
            var restored=PhysicalRecoveryState.restore(Files.readAllBytes(file),args[4],baseline,args[2],50359491);
            var physical=ArchivedPhysicalTransactions.readHistoryRestored(inputs,snapshot,history,50359491,restored.prefix());
            if(!physical.equals(strict.completed()))throw new AssertionError("physical transactions differ from strict full replay");
            var rowState=HistoricalRowState.restore(Files.readAllBytes(Path.of(args[5])),args[6],baseline,args[2]);
            var rows=ArchivedHeapRows.readHistoryRestored(inputs,snapshot,50359491,history,physical,rowState);
            var delta=CapturedHistoricalHeapBatch.readWithRows(captures,snapshot,history,50359491,physical,rows);
            var fullRows=ArchivedHeapRows.readHistory(inputs,snapshot,50359491,history,strict.completed());
            var full=CapturedHistoricalHeapBatch.readWithRows(captures,snapshot,history,50359491,strict.completed(),fullRows);
            if(delta.events().size()!=2 || full.events().size()!=46 || !delta.events().equals(full.events().subList(44,46)) || !rows.rows().equals(fullRows.rows()))throw new AssertionError("resumed row images/events differ");
            for(var event:delta.events())System.out.println("EVENT|"+event.row().eventId()+"|"+event.row().payloadSha256());
            System.out.println("FULL_EVENTS=46");System.out.println("RESUMED_EVENTS=2");System.out.println("PHYSICAL_TRANSACTIONS="+physical.transactions().size());
        } else throw new IllegalArgumentException("mode");
    }
}
