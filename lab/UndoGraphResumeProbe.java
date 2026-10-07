package com.streamhouse.oracle;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.*;

/** Pending source-bound graph resume only; physical pair and dictionary/event gates stay separate. */
public class UndoGraphResumeProbe {
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static void save(Path path,byte[] bytes)throws Exception {
        try(var out=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            var data=java.nio.ByteBuffer.wrap(bytes);while(data.hasRemaining())out.write(data);out.force(true);
        }
    }
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path directory=Path.of(args[1]);
        var snapshot=FrozenHeapBaseline.read(Path.of(args[2]),args[3]);var xid=new HeapRowPatch.Xid(10,14,2869);
        var first=new CaptureJournal().read(Path.of(args[4]),args[5]);
        var captures=new ArrayList<CaptureJournal.Verified>();captures.add(first);
        if(!mode.equals("prefix"))captures.add(new CaptureJournal().read(Path.of(args[6]),args[7]));
        var inputs=RedoBatch.captures(captures);
        for(var input:inputs)snapshot.requireSource(input.header());
        var inventory=ArchivedUndoInventory.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),xid);
        TransactionLifecycle identity;UndoHistory graph;
        if(mode.equals("prefix")){identity=new TransactionLifecycle();graph=new UndoHistory(inventory);}
        else {
            identity=TransactionLifecycle.restore(Files.readAllBytes(directory.resolve("identity.state")),args[8]);
            graph=UndoHistory.restore(inventory,Files.readAllBytes(directory.resolve("graph.state")),args[9]);
        }
        var input=inputs.get(mode.equals("prefix")?0:1);var header=input.header();identity.archive(header);
        UndoHistory.Result[] result={null};TransactionLifecycle.ClosedIdentity[] end={null};
        input.visit(header,r->{
            if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==2)identity.observeSlotControl(r,v);
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=1)continue;
                var h=UndoHeader.decode(v);if(!h.xid().equals(xid))continue;
                if(h.objectId()!=snapshot.heap().logicalObject() || h.dataObjectId()!=snapshot.heap().dataObject())throw new RedoException("GRAPH_PROBE_SCOPE");
                if(h.beginsTransaction()) {
                    int count=0;for(var s:r.vectors())if(s.containerId()==v.containerId() && s.opcodeMajor()==5 && s.opcodeMinor()==2){identity.begin(r,s);count++;}
                    if(count!=1)throw new RedoException("GRAPH_PROBE_BEGIN_COUNT");
                }
                identity.undo(r,v);
                var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                var e=inventory.entries().stream().filter(n->n.position().equals(at)).findFirst().orElseThrow(()->new RedoException("GRAPH_PROBE_ORIGINAL_MISSING"));
                graph.append(e);
            }
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==4 && identity.knowsEnd(r,v)) {
                if(end[0]!=null)throw new RedoException("GRAPH_PROBE_DUPLICATE_END");
                end[0]=identity.end(r,v);result[0]=graph.close(TransactionEnd.undoTail(v));
            }
        });
        if(mode.equals("prefix")) {
            if(end[0]!=null || inventory.entries().size()!=1)throw new RedoException("GRAPH_PROBE_PREFIX_NOT_PENDING");
            byte[] a=identity.checkpoint(),b=graph.checkpoint();save(directory.resolve("identity.state"),a);save(directory.resolve("graph.state"),b);
            System.out.println("PENDING_GRAPH_CHECKPOINT|"+sha(a)+"|"+sha(b));System.out.flush();Runtime.getRuntime().halt(23);
        } else {
            identity.finish();if(result[0]==null || result[0].original().size()!=2 || result[0].live().size()!=2 || !result[0].compensated().isEmpty())throw new RedoException("GRAPH_PROBE_COMPLETE_COVERAGE");
            System.out.println("CLOSED_IDENTITY|"+end[0]);
            for(var e:result[0].original())System.out.println("ORIGINAL|"+e);
            System.out.println("GRAPH|"+result[0].allocationTail()+"|"+result[0].activeHead()+"|"+result[0].compensated().size());
        }
    }
}
