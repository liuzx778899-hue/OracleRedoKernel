package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Frozen SYS dependency state; its independent restore opens no redo member. */
public class DictionaryPendingStateProbe {
    static void counts(DictionaryPendingState state)throws Exception {
        var contexts=state.decoded().contexts();
        System.out.println("PENDING_SYS="+contexts.size());System.out.println("ORIGINALS="+contexts.stream().mapToInt(c->c.next).sum());
        System.out.println("ROW_PAIRS="+contexts.stream().mapToInt(c->c.rows.size()).sum());System.out.println("INDEX_PAIRS="+contexts.stream().mapToInt(c->c.indexes.size()).sum());
        System.out.println("STATISTICS_ROWS="+contexts.stream().mapToInt(c->c.statisticsRows.size()).sum());System.out.println("STATISTICS_INDEXES="+contexts.stream().mapToInt(c->c.statisticsIndexes.size()).sum());
        System.out.println("USAGE_ROWS="+contexts.stream().mapToInt(c->c.usageRows.size()).sum());System.out.println("USAGE_INDEXES="+contexts.stream().mapToInt(c->c.usageIndexes.size()).sum());
    }
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),file=Path.of(args[3]);var snapshot=FrozenHeapBaseline.read(baseline,args[2]);
        if(args[0].equals("restore")) {
            var state=DictionaryPendingState.restore(Files.readAllBytes(file),args[4],baseline,args[2]);counts(state);System.out.println("RAW_PATH_ARGUMENTS=0");return;
        }
        int first=args[0].equals("export")?4:5;var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=first;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);
        if(args[0].equals("export")) {
            var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);byte[] bytes=history.durablePendingState().checkpoint();
            var state=DictionaryPendingState.restore(bytes,DurableUndoState.sha(bytes),snapshot);if(state.decoded().contexts().isEmpty())throw new AssertionError("real pending SYS required");
            Files.write(file,bytes,StandardOpenOption.CREATE_NEW);counts(state);System.out.println("STATE_SHA256="+DurableUndoState.sha(bytes));System.out.println("STATE_BYTES="+bytes.length);
        } else if(args[0].equals("resume")) {
            var state=DictionaryPendingState.restore(Files.readAllBytes(file),args[4],baseline,args[2]);
            var restored=ArchivedDictionaryHistory.readRestored(inputs,snapshot,state);var strict=ArchivedDictionaryHistory.read(inputs,snapshot);
            if(!restored.pending().isEmpty() || !Arrays.equals(strict.checkpoint(),restored.checkpoint())
                    || !Arrays.equals(strict.materializedState().checkpoint(),restored.materializedState().checkpoint()))throw new AssertionError("dictionary closure differs from strict replay");
            var physical=ArchivedPhysicalTransactions.readHistory(inputs,snapshot,strict,50357659);
            var resumed=CapturedHistoricalHeapBatch.readWithPhysical(captures,inputs,snapshot,restored,50357659,physical);
            var full=CapturedHistoricalHeapBatch.readWithPhysical(captures,inputs,snapshot,strict,50357659,physical);
            if(resumed.events().size()!=2 || !resumed.events().equals(full.events()) || !resumed.sha256().equals(full.sha256()))throw new AssertionError("complete events/schema differ");
            for(var event:resumed.events())System.out.println("EVENT|"+event.row().eventId()+"|"+event.row().payloadSha256());
            System.out.println("COMMITTED_VERSIONS="+restored.commits().size());System.out.println("PENDING_SYS=0");System.out.println("EVENTS=2");System.out.println("DICT_STATE_SHA256="+DurableUndoState.sha(restored.materializedState().checkpoint()));
        } else throw new IllegalArgumentException("mode");
    }
}
