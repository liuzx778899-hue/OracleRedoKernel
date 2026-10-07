package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Complete archive/capture historical row comparison. Production inputs are only the baseline and raw bytes. */
public class HistoricalRowsParityProbe {
    public static void main(String[] args)throws Exception {
        RedoBatch inputs;
        if(args[2].equals("captures")) {
            var captures=new ArrayList<CaptureJournal.Verified>();
            for(int i=4;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
            inputs=RedoBatch.captures(captures);
        } else {
            var paths=new ArrayList<Path>();for(int i=4;i<args.length;i++)paths.add(Path.of(args[i]));inputs=RedoBatch.archives(paths);
        }
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);long leaf=Long.decode(args[3]);
        var history=ArchivedDictionaryHistory.read(inputs,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistory(inputs,snapshot,history,leaf);
        var rows=ArchivedHeapRows.readHistory(inputs,snapshot,leaf,history,physical);var out=new ArrayList<String>();
        for(var change:rows.changes()) {
            var owners=physical.transactions().stream().filter(t->t.graph().live().stream().anyMatch(e->e.position().equals(change.position()))).toList();
            if(owners.size()!=1)throw new RedoException("PARITY_ROW_OWNER_AMBIGUOUS");var owner=owners.get(0).identity();
            out.add("ROW|"+change+"|"+owner+"|"+history.schemaAt(owner.endRecordScn()));
        }
        out.forEach(System.out::println);System.err.println("historical_row_parity_only;target_ack=false");
    }
}
