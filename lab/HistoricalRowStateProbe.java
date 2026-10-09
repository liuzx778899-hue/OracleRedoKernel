package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Resume complete row replay from its authenticated state; physical discovery still requires the raw prefix. */
public class HistoricalRowStateProbe {
    static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static void main(String[] args)throws Exception {
        Path baseline=Path.of(args[1]),file=Path.of(args[3]);var snapshot=FrozenHeapBaseline.read(baseline,args[2]);
        if(args[0].equals("restore")) {
            byte[] bytes=Files.readAllBytes(file);var restored=HistoricalRowState.restore(bytes,args[4],baseline,args[2]);
            if(!Arrays.equals(bytes,restored.checkpoint()))throw new AssertionError("row state bytes changed");
            System.out.println("ROWS="+restored.rows().size());System.out.println("RAW_PATH_ARGUMENTS=0");return;
        }
        int first=args[0].equals("export")?4:5;var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=first;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,50359491).completed();
        var strict=ArchivedHeapRows.readHistory(inputs,snapshot,50359491,history,physical);
        var strictState=HistoricalRowState.from(snapshot,50359491,history,physical,strict);
        if(args[0].equals("export")) {
            byte[] bytes=strictState.checkpoint();Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            var repeated=ArchivedHeapRows.readHistoryRestored(inputs,snapshot,50359491,history,physical,strictState);
            if(!repeated.changes().isEmpty() || !repeated.rows().equals(strict.rows()))throw new AssertionError("old commits replayed twice");
            System.out.println("STATE_SHA256="+sha(bytes));System.out.println("STATE_BYTES="+bytes.length);
            System.out.println("EVENTS="+strict.changes().size());System.out.println("ROWS="+strict.rows().size());System.out.println("REPEATED_EVENTS=0");
        } else if(args[0].equals("resume")) {
            var restored=HistoricalRowState.restore(Files.readAllBytes(file),args[4],baseline,args[2]);
            var resumed=ArchivedHeapRows.readHistoryRestored(inputs,snapshot,50359491,history,physical,restored);
            var fullBatch=CapturedHistoricalHeapBatch.readWithRows(captures,snapshot,history,50359491,physical,strict);
            var delta=CapturedHistoricalHeapBatch.readWithRows(captures,snapshot,history,50359491,physical,resumed);
            if(fullBatch.events().size()!=46 || delta.events().size()!=2 || !resumed.rows().equals(strict.rows())
                    || !delta.events().equals(fullBatch.events().subList(44,46)))throw new AssertionError("resumed complete images/events differ");
            if(!Arrays.equals(strictState.checkpoint(),HistoricalRowState.from(snapshot,50359491,history,physical,resumed).checkpoint()))
                throw new AssertionError("resumed final state differs from strict replay");
            for(var event:delta.events())System.out.println("EVENT|"+event.row().eventId()+"|"+event.row().payloadSha256());
            System.out.println("FULL_EVENTS=46");System.out.println("RESUMED_EVENTS=2");System.out.println("STRICT_FINAL_STATE_SHA256="+sha(strictState.checkpoint()));
        } else throw new IllegalArgumentException("mode");
    }
}
