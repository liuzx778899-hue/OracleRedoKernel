package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalRowStateTest {
    @TempDir Path directory;
    FrozenHeapBaseline.Snapshot baseline()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;return helper.read(helper.v3());
    }
    ArchivedRedo.Header source(FrozenHeapBaseline.Snapshot s) {
        var id=s.source();return new ArchivedRedo.Header("a".repeat(64),51200,id.dbid(),id.activationId(),id.compatibility(),id.thread(),
                90,id.resetlogsId(),id.resetlogsScn(),s.scn()-10,s.scn()+100,100);
    }
    ArchivedPhysicalTransactions.Transaction transaction(FrozenHeapBaseline.Snapshot s,int offset) {
        return new ArchivedPhysicalTransactions.Transaction(new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(1,2,3),
                s.source().containerUid(),s.source().containerId(),s.scn()+1,s.scn()+20,false),List.of(),
                new UndoHistory.Result(List.of(),List.of(),List.of(),null,null),new ArchivedUndoInventory.Position(90,10,offset,1));
    }
    ArchivedPhysicalTransactions.Result physical(FrozenHeapBaseline.Snapshot s) {return new ArchivedPhysicalTransactions.Result(List.of(source(s)),List.of(transaction(s,32)));}
    HistoricalRowState state(FrozenHeapBaseline.Snapshot s,Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,
                             Map<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position> positions)throws Exception {
        var constructor=ArchivedDictionaryHistory.Timeline.class.getDeclaredConstructor(FrozenHeapBaseline.Snapshot.class,List.class,List.class);
        constructor.setAccessible(true);var history=constructor.newInstance(s,List.of(source(s)),List.of());
        return HistoricalRowState.from(s,501,history,physical(s),new ArchivedHeapRows.Result(List.of(source(s)),List.of(),rows,positions));
    }
    String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void completeRowsAndDeletedRowOrderingSurviveWithoutRaw()throws Exception {
        var s=baseline();var key=s.rows().keySet().iterator().next();
        var deleted=new ArchivedHeapRows.RowKey(s.heap().dataObject(),key.dba(),key.slot()+1);
        var positions=Map.of(key,new ArchivedUndoInventory.Position(90,10,32,4),deleted,new ArchivedUndoInventory.Position(90,9,16,1));
        byte[] bytes=state(s,s.rows(),positions).checkpoint();var restored=HistoricalRowState.restore(bytes,sha(bytes),s);
        assertEquals(s.rows(),restored.rows());assertEquals(s.columns(),restored.columns());assertEquals(positions,restored.lastPositions());
        assertArrayEquals(bytes,restored.checkpoint());assertThrows(UnsupportedOperationException.class,()->restored.rows().clear());
        assertThrows(UnsupportedOperationException.class,()->restored.rows().get(key).clear());
    }
    @Test void sameScnCommitPositionAndAuthenticatedPrefixRemainRequired()throws Exception {
        var s=baseline();var state=state(s,s.rows(),Map.of());
        assertFalse(state.after(transaction(s,32)));assertFalse(state.after(transaction(s,16)));assertTrue(state.after(transaction(s,48)));
        state.requirePrefix(s,501,physical(s));assertThrows(RedoException.class,()->state.requirePrefix(s,502,physical(s)));
        assertThrows(RedoException.class,()->state.requirePrefix(s,501,new ArchivedPhysicalTransactions.Result(List.of(source(s)),List.of(transaction(s,48)))));
        var h=source(s);var changed=new ArchivedRedo.Header("b".repeat(64),h.bytes(),h.dbid(),h.activationId(),h.compatibility(),h.thread(),h.sequence(),
                h.resetlogsId(),h.resetlogsScn(),h.firstScn(),h.nextScn(),h.blocks());
        assertThrows(RedoException.class,()->state.requirePrefix(s,501,new ArchivedPhysicalTransactions.Result(List.of(changed),physical(s).transactions())));
    }
    @Test void corruptedRequiredCellWidthAndRowScopeRefuseEvenWithMatchingDigest()throws Exception {
        var s=baseline();var key=s.rows().keySet().iterator().next();var row=s.rows().get(key);var missing=new ArrayList<>(row);
        missing.set(0,new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""));
        for(var rows:List.of(Map.of(key,List.copyOf(missing)),Map.of(key,row.subList(0,row.size()-1)),
                Map.of(new ArchivedHeapRows.RowKey(s.heap().dataObject()+1,key.dba(),key.slot()),row))) {
            byte[] bytes=state(s,rows,Map.of()).checkpoint();assertThrows(RedoException.class,()->HistoricalRowState.restore(bytes,sha(bytes),s));
        }
    }
    @Test void wrongDigestBaselineTruncationTrailingBytesAndFutureRowPositionRefuse()throws Exception {
        var s=baseline();byte[] bytes=state(s,s.rows(),Map.of()).checkpoint();
        assertThrows(RedoException.class,()->HistoricalRowState.restore(bytes,"0".repeat(64),s));
        var other=new FrozenHeapBaseline.Snapshot("b".repeat(64),s.source(),s.scn(),s.heap(),s.index(),s.columns(),s.rows(),s.dictionaryAnchors(),s.dictionaryBaseline());
        assertThrows(RedoException.class,()->HistoricalRowState.restore(bytes,sha(bytes),other));
        for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1)))
            assertThrows(RedoException.class,()->HistoricalRowState.restore(bad,sha(bad),s));
        var key=s.rows().keySet().iterator().next();byte[] future=state(s,s.rows(),Map.of(key,new ArchivedUndoInventory.Position(90,10,48,1))).checkpoint();
        assertThrows(RedoException.class,()->HistoricalRowState.restore(future,sha(future),s));
    }
}
