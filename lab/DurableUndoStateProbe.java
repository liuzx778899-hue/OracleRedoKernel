package com.streamhouse.oracle;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Frozen pending business graphs; the restore process has no raw path arguments. */
public class DurableUndoStateProbe {
    static String sha(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    public static void main(String[] args)throws Exception {
        if(args[0].equals("export")) {
            Path baseline=Path.of(args[1]),directory=Path.of(args[3]);
            var snapshot=FrozenHeapBaseline.read(baseline,args[2]);var captures=new ArrayList<CaptureJournal.Verified>();
            for(int i=4;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
            var inputs=RedoBatch.captures(captures);var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
            var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,50359491);
            var manifest=new StringBuilder();int count=0;
            for(var pending:physical.state().pending()) {
                var inventory=ArchivedUndoInventory.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),pending.xid());
                byte[] actions=pending.graphCheckpoint().getBytes(StandardCharsets.UTF_8);
                var graph=UndoHistory.restore(inventory,actions,sha(actions));byte[] state=graph.durableCheckpoint();
                String name="pending-"+(count++)+".state";Files.write(directory.resolve(name),state,StandardOpenOption.CREATE_NEW);
                manifest.append(name).append('|').append(sha(state)).append('|').append(sha(actions)).append('|')
                        .append(inventory.entries().size()).append('\n');
            }
            if(count==0)throw new AssertionError("real pending graph required");
            byte[] bytes=manifest.toString().getBytes(StandardCharsets.US_ASCII);
            Files.write(directory.resolve("manifest.txt"),bytes,StandardOpenOption.CREATE_NEW);
            System.out.println("MANIFEST_SHA256="+sha(bytes));System.out.println("PENDING_UNDO_GRAPHS="+count);
            System.out.println("COMPLETED_TRANSACTIONS="+physical.completed().transactions().size());
        } else if(args[0].equals("restore")) {
            Path directory=Path.of(args[1]);byte[] manifest=Files.readAllBytes(directory.resolve("manifest.txt"));
            if(!sha(manifest).equals(args[2]))throw new AssertionError("trusted manifest mismatch");int count=0;
            for(String line:new String(manifest,StandardCharsets.US_ASCII).lines().toList()) {
                String[] f=line.split("\\|");byte[] state=Files.readAllBytes(directory.resolve(f[0]));
                var restored=UndoHistory.restoreDurable(state,f[1]);
                if(!Arrays.equals(state,restored.durableCheckpoint()) || !sha(restored.checkpoint()).equals(f[2]))
                    throw new AssertionError("pending nodes/actions changed across JVM");count++;
            }
            System.out.println("RESTORED_UNDO_GRAPHS="+count);System.out.println("RAW_PATH_ARGUMENTS=0");
        } else throw new IllegalArgumentException("mode");
    }
}
