package com.streamhouse.oracle;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.*;

/** Identity checkpoint evidence only; does not authorize complete rows, dictionary state or ACK. */
public class TransactionIdentityResumeProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path statePath=Path.of(args[1]);
        var snapshot=FrozenHeapBaseline.read(Path.of(args[2]),args[3]);
        var capture=new CaptureJournal().read(Path.of(args[4]),args[5]);
        var header=RedoBatch.captures(List.of(capture)).get(0).header();snapshot.requireSource(header);
        TransactionLifecycle state;
        if(mode.equals("prefix"))state=new TransactionLifecycle();
        else {state=TransactionLifecycle.restore(Files.readAllBytes(statePath),args[6]);}
        state.archive(header);
        var ends=new ArrayList<TransactionLifecycle.ClosedIdentity>();
        for(var r:new RedoRecords().readCaptured(capture)) {
            if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())continue;
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==2)state.observeSlotControl(r,v);
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                var h=UndoHeader.decode(v);if(h.objectId()!=snapshot.heap().logicalObject())continue;
                if(h.dataObjectId()!=snapshot.heap().dataObject())throw new RedoException("RESUME_PROBE_TARGET_GENERATION");
                if(h.beginsTransaction()) {
                    int count=0;
                    for(var start:r.vectors())if(start.containerId()==v.containerId() && start.opcodeMajor()==5 && start.opcodeMinor()==2) {
                        state.begin(r,start);count++;
                    }
                    if(count!=1)throw new RedoException("RESUME_PROBE_BEGIN_COUNT");
                }
                state.undo(r,v);
            }
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v))ends.add(state.end(r,v));
        }
        if(mode.equals("prefix")) {
            byte[] bytes=state.checkpoint();
            try(var out=FileChannel.open(statePath,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
            }
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            System.out.println("DURABLE_IDENTITY_CHECKPOINT|"+sha+"|"+ends.size());System.out.flush();
            Runtime.getRuntime().halt(23);
        } else {
            state.finish();
            if(ends.size()!=1)throw new RedoException("RESUME_PROBE_CLOSED_COUNT");
            System.out.println("RESTORED_CLOSED_IDENTITY|"+ends.get(0));
            Files.write(statePath.resolveSibling("closed-identities.state"),state.checkpoint(),StandardOpenOption.CREATE_NEW);
        }
    }
}
