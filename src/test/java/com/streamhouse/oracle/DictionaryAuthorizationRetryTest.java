package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.streamhouse.oracle.RedoBytes.*;

class DictionaryAuthorizationRetryTest {
    @Test void raw120FailedAuthorizationInsertCancelsBeforeRestartAndRestoresItsGraph()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-authorization-retry-raw120.txt");assertEquals(10,records.size());
        for(int first:new int[]{0,5}) {
            var window=records.subList(first,first+5);var source=new ArchivedRedo.Header("0".repeat(64),8015360,42,11,0x13000000,1,120,22,33,3275000,3276000,15655);
            var entries=new ArrayList<ArchivedUndoInventory.Entry>();
            for(var r:window)for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.opcodeMajor()!=5 || v.opcodeMinor()!=1)continue;
                var h=UndoHeader.decode(v);byte[] m=v.fields().get(1);
                entries.add(new ArchivedUndoInventory.Entry(new ArchivedUndoInventory.Position(120,r.block(),r.offset(),i+1),h,m[16]&255,m[17]&255,u32(m,12),m[19]&255));
            }
            assertEquals(3,entries.size());var input=new ArchivedUndoInventory(List.of(source),entries,677737149,3,null);
            var lifecycle=new TransactionLifecycle();lifecycle.archive(source);var graph=new UndoHistory(input);var auth=new ArrayList<HeapUndoPair>();int next=0;
            for(var r:window)for(var v:r.vectors()) {
                if(v.opcodeMajor()==5 && v.opcodeMinor()==2 && next==0)lifecycle.begin(r,v);
                if(v.opcodeMajor()==5 && v.opcodeMinor()==2 && next==1) {
                    DictionaryAuthorizationRetry.headUpdate(r,v,entries.get(1).identity(),graph,lifecycle);
                    var missingCompensation=new UndoHistory(input);missingCompensation.append(entries.get(0));
                    assertThrows(RedoException.class,()->DictionaryAuthorizationRetry.headUpdate(r,v,entries.get(1).identity(),missingCompensation,lifecycle));
                    var duplicate=new ArrayList<>(r.vectors());duplicate.add(v);
                    var ambiguous=new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),List.copyOf(duplicate));
                    assertThrows(RedoException.class,()->DictionaryAuthorizationRetry.headUpdate(ambiguous,v,entries.get(1).identity(),graph,lifecycle));
                }
                if(v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                    var h=UndoHeader.decode(v);var row=r.vectors().stream().filter(x->x.opcodeMajor()==11).findFirst().orElseThrow();
                    var pair=h.objectId()==61?DictionaryRedefinitionAuxiliary.bind(r,row,61).physical():DictionaryObjectUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37).physical();
                    lifecycle.requireActive(r,pair);if(h.objectId()==61)auth.add(pair);graph.append(entries.get(next++));
                }
                if(v.opcodeMajor()==5 && v.opcodeMinor()==11) {
                    var rollback=r.vectors().stream().filter(x->x.opcodeMajor()==11).findFirst().orElseThrow();
                    var pair=HeapCompensation.matchBegin(r,v,rollback,auth,8,lifecycle);graph.compensate(pair.undo().identity());
                }
                if(v.opcodeMajor()==5 && v.opcodeMinor()==4) {
                    var checkpoint=graph.durableCheckpoint();var restored=UndoHistory.restoreDurable(checkpoint,DurableUndoState.sha(checkpoint));
                    lifecycle.end(r,v);var tail=TransactionEnd.undoTail(v);var result=graph.close(tail);var recovered=restored.close(tail);
                    assertEquals(result,recovered);assertEquals(2,result.live().size());assertEquals(List.of(entries.get(0).identity()),result.compensated());
                }
            }
            var missing=new UndoHistory(input);missing.append(entries.get(0));assertThrows(RedoException.class,()->missing.append(entries.get(1)));
        }
    }
    @Test void fullFrozenSourceRequiresBothRetryProofsBeforeAdmittingZeroPredecessors()throws Exception {
        String path=System.getProperty("ork.lob.migration");assumeTrue(path!=null);var root=Path.of(path);
        var raw=new CaptureJournal().readRawClosed(root.resolve("inputs/120.raw"),"6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36");
        var inputs=RedoBatch.captures(List.of(raw));
        for(var owner:List.of(new HeapRowPatch.Xid(10,22,5255),new HeapRowPatch.Xid(10,29,5257))) {
            var inventory=ArchivedUndoInventory.read(inputs,677737149,3,owner);
            assertEquals(3,inventory.entries().size());assertEquals(Set.of(inventory.entries().get(1).identity().uba()),DictionaryAuthorizationRetry.roots(inputs,677737149,3,owner));
        }
    }
}
