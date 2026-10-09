package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Window continuation from authenticated state plus a new frozen member; routing still audits the prefix. */
public class CompensationWindowStateProbe {
    public static void main(String[] args)throws Exception {
        if(args[0].equals("restore")) {
            var restored=ArchivedCompensationInventory.restoreDurable(Files.readAllBytes(Path.of(args[1])),args[2]);
            System.out.println("MARKERS="+restored.entries().size());System.out.println("COMPLETE="+restored.complete());System.out.println("RAW_MEMBERS=0");return;
        }
        var captures=new ArrayList<CaptureJournal.Verified>();for(int i=5;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var inputs=RedoBatch.captures(captures);ArchivedCompensationInventory window;
        if(args[0].equals("export"))window=ArchivedCompensationInventory.read(inputs,677737149,3,new HeapRowPatch.Xid(9,28,750),true);
        else {
            var prior=ArchivedCompensationInventory.restoreDurable(Files.readAllBytes(Path.of(args[1])),args[2]);
            if(args[0].equals("extend"))window=prior.extend(inputs,false);
            else if(args[0].equals("reference"))window=ArchivedCompensationInventory.read(inputs,prior.undo().containerUid(),prior.undo().containerId(),prior.undo().entries().get(0).identity().xid());
            else if(args[0].equals("route")) {
                var associations=ArchivedCompensationAssociations.readRestored(inputs,prior,false);
                var strict=ArchivedCompensationAssociations.read(inputs,677737149,3,new HeapRowPatch.Xid(9,28,750));
                if(!associations.entries().equals(strict.entries()))throw new AssertionError("restored window routing differs");
                for(var input:inputs){var header=input.header();input.visit(header,r->{for(int i=0;i<r.vectors().size();i++) {
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                    if(associations.entries().stream().anyMatch(e->e.position().equals(at)))associations.associate(header,r,r.vectors().get(i),at);
                }});}associations.finish();window=associations.window();System.out.println("ROUTES="+associations.entries().size());
            } else throw new IllegalArgumentException("mode");
        }
        byte[] bytes=window.durableCheckpoint();String digest=DurableUndoState.sha(bytes);
        if(!args[4].equals("-") && !digest.equals(args[4]))throw new AssertionError("window state differs from full read");
        Path output=Path.of(args[0].equals("export")?args[1]:args[3]);if(!output.toString().equals("-"))Files.write(output,bytes,StandardOpenOption.CREATE_NEW);
        System.out.println("STATE_SHA256="+digest);System.out.println("STATE_BYTES="+bytes.length);System.out.println("MARKERS="+window.entries().size());
        System.out.println("COMPLETE="+window.complete());System.out.println("RAW_MEMBERS="+captures.size());
    }
}
