package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;

/** Read-only control inventory inside discovered target windows; raw production inputs only. */
public class RawDmlControlProbe {
    public static void main(String[] args) throws Exception {
        var s=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=2;i<args.length;i+=2)captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(s.heap().logicalObject(),s.heap().dataObject()),new HeapIndexScope.Generation(s.index().logicalObject(),s.index().dataObject()));
        var found=ArchivedObjectTransactions.read(RedoBatch.captures(captures),s.source().containerUid(),s.source().containerId(),scope);
        for(var t:found.transactions())System.out.println("WINDOW|"+t.xid()+"|"+t.associations().window().firstRecord()+"|"+t.associations().window().lastRecord());
        for(var c:captures)for(var r:new RedoRecords().readCaptured(c)) {
            var at=new ArchivedUndoInventory.Position(c.source().sequence(),r.block(),r.offset(),0);
            boolean within=found.transactions().stream().anyMatch(t->{var w=t.associations().window();return order(at,w.firstRecord())>=0&&order(at,w.lastRecord())<=0;});
            if(!within||r.containerUid()!=s.source().containerUid())continue;
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=s.source().containerId()||v.opcodeMajor()!=5||v.opcodeMinor()!=2)continue;
                System.out.println("CONTROL|"+at+"|"+(i+1)+"|"+r.scn()+"|"+v.dba()+"|"+String.join(",",v.fields().stream().map(b->HexFormat.of().formatHex(b)).toList()));
                for(int j=0;j<r.vectors().size();j++) {var u=r.vectors().get(j);if(u.opcodeMajor()==5&&u.opcodeMinor()==1)System.out.println("SAME_RECORD_UNDO|"+at+"|"+(j+1)+"|"+UndoHeader.decode(u));}
            }
        }
    }
    static int order(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b){int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());return c==0?Integer.compare(a.offset(),b.offset()):c;}
}
