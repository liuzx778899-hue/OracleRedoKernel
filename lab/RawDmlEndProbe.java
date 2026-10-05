package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;

/** Read-only raw transaction end layout research; no events and no dump input. */
public class RawDmlEndProbe {
    public static void main(String[] args) throws Exception {
        var s=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var captures=new ArrayList<CaptureJournal.Verified>();
        for (int i=2;i<args.length;i+=2) captures.add(new CaptureJournal().read(Path.of(args[i]),args[i+1]));
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(s.heap().logicalObject(),s.heap().dataObject()),new HeapIndexScope.Generation(s.index().logicalObject(),s.index().dataObject()));
        var found=ArchivedObjectTransactions.read(RedoBatch.captures(captures),s.source().containerUid(),s.source().containerId(),scope);
        var owners=new HashMap<ArchivedUndoInventory.Position,HeapRowPatch.Xid>();
        for (var t:found.transactions()) owners.put(t.associations().window().lastRecord(),t.xid());
        var output=new ArrayList<String>();
        for (var c:captures) for (var r:new RedoRecords().readCaptured(c)) {
            var at=new ArchivedUndoInventory.Position(c.source().sequence(),r.block(),r.offset(),0);var x=owners.get(at);if (x==null) continue;
            for (var v:r.vectors()) if (v.opcodeMajor()==5 && v.opcodeMinor()==4 && v.containerId()==s.source().containerId()) {
                var end=TransactionEnd.decode(v);String tail;
                try {tail=TransactionEnd.undoTail(v).toString();} catch (RedoException e) {tail=e.getMessage();}
                output.add("END|"+at+"|"+x+"|"+r.scn()+"|"+end+"|"+v.fields().size()+"|"+tail+"|"+String.join(",",v.fields().stream().map(b->HexFormat.of().formatHex(b)).toList()));
            }
        }
        for (String line:output) System.out.println(line);
        System.err.println("snapshot_scn="+s.scn()+";object_transactions="+found.transactions().size()+";research_only=true;events=0");
    }
}
