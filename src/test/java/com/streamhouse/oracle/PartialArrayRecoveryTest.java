package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.put;

/** Controlled native-layout scenario: cancel one row in one owned array node, retain two across restore. */
class PartialArrayRecoveryTest {
    @Test void middleRowCancellationSurvivesPhysicalCheckpointAndOnlyRemainingRowsReplay()throws Exception {
        var helper=new HeapArrayUpdatePairTest();var forward=helper.forward();var undo=helper.undo();
        byte[] metadata=Arrays.copyOf(undo.fields().get(1),24);put(metadata,20,0,2);put(metadata,22,5109,2);metadata[19]=22;
        undo.fields().set(1,metadata);
        var record=new RedoRecords.Record(3,16,1000,1,110,1,677737149,List.of(forward,undo));
        var owned=HeapArrayUpdatePair.bind(record,forward,3);var original=owned.get(0).undo().identity();
        assertFalse(original.beginsTransaction());assertTrue(owned.stream().allMatch(p->p.undo().identity().equals(original)));
        var fields=new ArrayList<byte[]>();for(var value:undo.fields().subList(2,8))fields.add(value.clone());
        fields.get(1)[11]=2;put(fields.get(1),16,1,2);
        fields.set(2,Arrays.copyOfRange(fields.get(2),28,56));fields.set(3,Arrays.copyOfRange(fields.get(3),2,4));
        byte[] sizes=new byte[20];put(sizes,0,26,2);System.arraycopy(fields.get(4),8,sizes,4,16);fields.set(4,sizes);
        fields.set(5,Arrays.copyOfRange(fields.get(5),26,52));
        var row=new RedoRecords.Vector(11,19,1,12,forward.dba(),120,1,3,fields);
        byte[] markerData=Arrays.copyOf(metadata,28);put(markerData,20,16,2);
        var marker=new RedoRecords.Vector(5,6,35,11,undo.dba(),120,1,3,List.of(markerData,new byte[8]));
        var cancellation=new RedoRecords.Record(4,16,500,1,120,1,677737149,List.of(row,marker));
        var cancelled=HeapArrayUpdatePair.matchRollback(cancellation,marker,row,owned,3,original);
        assertEquals(List.of(owned.get(1)),cancelled);
        var key=new ArchivedHeapRows.RowKey(original.dataObjectId(),forward.dba(),1);
        var operation=new ArchivedPhysicalTransactions.Operation(new ArchivedUndoInventory.Position(90,4,16,2),original,true,List.of(key));
        var at=new ArchivedUndoInventory.Position(90,3,16,2);
        var entry=new ArchivedUndoInventory.Entry(at,original,11,1,0,22);
        var graph=new UndoHistory.Result(List.of(entry),List.of(entry),List.of(),original.uba(),original.uba());
        var identity=new TransactionLifecycle.ClosedIdentity(original.xid(),677737149,3,105,150,false);
        var transaction=new ArchivedPhysicalTransactions.Transaction(identity,List.of(operation),graph,new ArchivedUndoInventory.Position(90,5,16,1));
        var source=new CaptureJournal.Source("a".repeat(64),8192,16,16,1,2,0x13000000,1,90,3,4,100,200);
        var inputs=RedoBatch.origin(List.of(new LobOriginCapsule.Member(source,List.of(record,cancellation))),RedoBatch.archives(List.of()));
        var header=inputs.get(0).header();var lifecycle=new TransactionLifecycle();lifecycle.archive(header);
        var physical=new ArchivedPhysicalTransactions.Result(List.of(header),List.of(transaction));
        var rows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
        for(var pair:owned) {
            var before=new ArrayList<ArchivedHeapRows.Cell>();before.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c102"));
            for(int column=1;column<3;column++)before.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,HexFormat.of().formatHex(pair.undo().inverse().columns().get(column).bytes())));
            rows.put(new ArchivedHeapRows.RowKey(original.dataObjectId(),forward.dba(),pair.forward().slot()),List.copyOf(before));
        }
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
                new FrozenHeapBaseline.PhysicalColumn(2,2,"T1","TIMESTAMP",true,false),new FrozenHeapBaseline.PhysicalColumn(3,3,"T2","TIMESTAMP",true,false));
        var baseline=new FrozenHeapBaseline.Snapshot("b".repeat(64),ArchivedHeapRows.Source.of(header,677737149,3),100,
                new FrozenHeapBaseline.ObjectRole("OWNER","T",original.objectId(),original.dataObjectId()),
                new FrozenHeapBaseline.ObjectRole("OWNER","PK",80575,80575),columns,rows);
        var prefix=new ArchivedPhysicalTransactions.Prefix(physical,new ArchivedPhysicalTransactions.PendingState(List.of(header),List.of(),new String(lifecycle.checkpoint(),java.nio.charset.StandardCharsets.UTF_8)));
        var state=PhysicalRecoveryState.from(inputs,baseline,501,prefix);byte[] bytes=state.checkpoint();
        var restored=PhysicalRecoveryState.restore(bytes,DurableUndoState.sha(bytes),baseline,501).prefix();
        assertEquals(List.of(key),restored.completed().transactions().get(0).operations().get(0).cancelledRows());
        assertArrayEquals(bytes,PhysicalRecoveryState.from(RedoBatch.archives(List.of()),baseline,501,restored).checkpoint());
        var read=ArchivedHeapRows.class.getDeclaredMethod("read",RedoBatch.class,ArchivedHeapRows.Baseline.class,HeapIndexScope.class,long.class,ArchivedPhysicalTransactions.Result.class,List.class);
        read.setAccessible(true);
        var replay=(ArchivedHeapRows.Result)read.invoke(null,inputs,new ArchivedHeapRows.Baseline(baseline.source(),100,original.objectId(),3,rows),
                HeapIndexScope.equal(original.objectId(),80575),501L,restored.completed(),null);
        assertEquals(2,replay.changes().size());assertEquals(3,replay.rows().size());assertEquals(rows.get(key),replay.rows().get(key));
        assertEquals(Set.of(0,2),replay.changes().stream().map(c->c.key().slot()).collect(java.util.stream.Collectors.toSet()));
        fields.get(5)[2]^=1;
        assertThrows(RedoException.class,()->HeapArrayUpdatePair.matchRollback(cancellation,marker,row,owned,3,original));
    }
}
