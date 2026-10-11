package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProducerStateTest {
    @TempDir Path directory;
    record Fixture(FrozenHeapBaseline.Snapshot baseline,HistoricalRowState rows,ArchivedPhysicalTransactions.Result physical,
                   ArchivedHeapRows.Change change) {}
    Fixture fixture()throws Exception {
        var helper=new HistoricalRowStateTest();helper.directory=directory;var s=helper.baseline();
        var at=new ArchivedUndoInventory.Position(90,9,16,1);var key=s.rows().keySet().iterator().next();
        var xid=new HeapRowPatch.Xid(1,2,3);var uba=new HeapRowPatch.Uba(1234,1,1);
        var undo=new ArchivedUndoInventory.Entry(at,new UndoHeader(xid,uba,s.heap().logicalObject(),s.heap().dataObject(),0,true),11,1,0,0);
        var t=helper.transaction(s,32);
        var owner=new ArchivedPhysicalTransactions.Transaction(t.identity(),List.of(),new UndoHistory.Result(List.of(undo),List.of(undo),List.of(),uba,uba),t.commitPosition());
        var physical=new ArchivedPhysicalTransactions.Result(List.of(helper.source(s)),List.of(owner));
        var rows=helper.state(s,s.rows(),Map.of(key,at));
        var change=new ArchivedHeapRows.Change(at,xid,key,HeapRowPatch.Kind.UPDATE,s.rows().get(key),s.rows().get(key));
        return new Fixture(s,rows,physical,change);
    }
    String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void fullImagesAndEventHistoryRoundTripWithoutRaw()throws Exception {
        var f=fixture();byte[] bytes=HistoricalProducerState.encode(f.rows(),List.of(f.change()));
        var restored=HistoricalProducerState.restore(bytes,sha(bytes),f.baseline(),501,f.physical());
        assertEquals(List.of(f.change()),restored.changes());assertEquals(f.rows().rows(),restored.rows().rows());
        assertArrayEquals(bytes,HistoricalProducerState.encode(restored.rows(),restored.changes()));
        assertThrows(UnsupportedOperationException.class,()->restored.changes().clear());
        assertThrows(RedoException.class,()->HistoricalProducerState.restore(bytes,"0".repeat(64),f.baseline(),501,f.physical()));
    }
    @Test void duplicateUnknownOwnerAndIncompleteImagesRefuse()throws Exception {
        var f=fixture();var c=f.change();
        for(var changes:List.of(List.of(c,c),List.of(new ArchivedHeapRows.Change(c.position(),new HeapRowPatch.Xid(1,2,4),c.key(),c.kind(),c.before(),c.after())),
                List.of(new ArchivedHeapRows.Change(c.position(),c.xid(),c.key(),c.kind(),List.of(),c.after())),
                List.of(new ArchivedHeapRows.Change(c.position(),c.xid(),c.key(),c.kind(),c.before(),c.after().subList(0,c.after().size()-1))))) {
            byte[] bytes=HistoricalProducerState.encode(f.rows(),changes);
            assertThrows(RedoException.class,()->HistoricalProducerState.restore(bytes,sha(bytes),f.baseline(),501,f.physical()));
        }
    }
    @Test void missingRowFenceAndTrailingBytesRefuse()throws Exception {
        var f=fixture();var helper=new HistoricalRowStateTest();helper.directory=directory;
        byte[] missing=HistoricalProducerState.encode(helper.state(f.baseline(),f.rows().rows(),Map.of()),List.of(f.change()));
        assertEquals("PRODUCER_STATE_ROW_FENCE",assertThrows(RedoException.class,
                ()->HistoricalProducerState.restore(missing,sha(missing),f.baseline(),501,f.physical())).getMessage());
        byte[] bytes=HistoricalProducerState.encode(f.rows(),List.of(f.change()));var extra=Arrays.copyOf(bytes,bytes.length+1);
        assertThrows(RedoException.class,()->HistoricalProducerState.restore(extra,sha(extra),f.baseline(),501,f.physical()));
    }
    @Test void distinctArrayRowsShareAnOwnedPositionButDuplicateRowRefuses()throws Exception {
        var f=fixture();var c=f.change();var key=new ArchivedHeapRows.RowKey(c.key().dataObjectId(),c.key().dba(),c.key().slot()+1);
        var helper=new HistoricalRowStateTest();helper.directory=directory;
        var values=new HashMap<>(f.rows().rows());values.put(key,c.after());
        var positions=new HashMap<>(f.rows().lastPositions());positions.put(key,c.position());
        var rows=helper.state(f.baseline(),values,positions);
        var second=new ArchivedHeapRows.Change(c.position(),c.xid(),key,HeapRowPatch.Kind.INSERT,List.of(),c.after());
        byte[] bytes=HistoricalProducerState.encode(rows,List.of(c,second));
        assertEquals(List.of(c,second),HistoricalProducerState.restore(bytes,sha(bytes),f.baseline(),501,f.physical()).changes());
        byte[] duplicate=HistoricalProducerState.encode(rows,List.of(c,second,second));
        assertThrows(RedoException.class,()->HistoricalProducerState.restore(duplicate,sha(duplicate),f.baseline(),501,f.physical()));
    }
    @Test void equalScnDistinctOwnersKeepCommitOrderThroughRestoreAndPublication()throws Exception {
        var f=fixture();var s=f.baseline();var first=f.physical().transactions().get(0);var c=f.change();
        var xid=new HeapRowPatch.Xid(1,3,4);var uba=new HeapRowPatch.Uba(1235,1,1);
        var at=new ArchivedUndoInventory.Position(90,9,32,1);
        var key=new ArchivedHeapRows.RowKey(c.key().dataObjectId(),c.key().dba(),c.key().slot()+1);
        var undo=new ArchivedUndoInventory.Entry(at,new UndoHeader(xid,uba,s.heap().logicalObject(),s.heap().dataObject(),0,true),11,1,0,0);
        var identity=new TransactionLifecycle.ClosedIdentity(xid,s.source().containerUid(),s.source().containerId(),
                first.identity().beginRecordScn()+1,first.identity().endRecordScn(),false);
        var second=new ArchivedPhysicalTransactions.Transaction(identity,List.of(),
                new UndoHistory.Result(List.of(undo),List.of(undo),List.of(),uba,uba),new ArchivedUndoInventory.Position(90,10,16,1));
        var physical=new ArchivedPhysicalTransactions.Result(f.physical().sources(),List.of(first,second));
        assertEquals(List.of(second,first),ArchivedHeapRows.commitOrdered(physical.transactions()));
        var inserted=new ArrayList<>(c.after());inserted.set(0,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c103"));
        var values=new HashMap<>(f.rows().rows());values.put(key,List.copyOf(inserted));
        var positions=new HashMap<>(f.rows().lastPositions());positions.put(key,at);
        var helper=new HistoricalRowStateTest();helper.directory=directory;var rows=helper.state(s,values,positions);
        var insertion=new ArchivedHeapRows.Change(at,xid,key,HeapRowPatch.Kind.INSERT,List.of(),inserted);
        byte[] checkpoint=HistoricalProducerState.encode(rows,List.of(insertion,c));
        var restored=HistoricalProducerState.restore(checkpoint,sha(checkpoint),s,501,physical);
        assertEquals(List.of(insertion,c),restored.changes());
        assertArrayEquals(checkpoint,HistoricalProducerState.encode(restored.rows(),restored.changes()));
        assertFalse(restored.rows().after(first));assertFalse(restored.rows().after(second));
        byte[] reversed=HistoricalProducerState.encode(rows,List.of(c,insertion));
        assertEquals("PRODUCER_STATE_COMMIT_ORDER",assertThrows(RedoException.class,
                ()->HistoricalProducerState.restore(reversed,sha(reversed),s,501,physical)).getMessage());
        var history=ArchivedDictionaryHistory.committedState(s,physical.sources(),List.of());
        var h=physical.sources().get(0);
        var source=new CaptureJournal.Source(h.sha256(),h.bytes(),h.blocks(),h.blocks(),h.dbid(),h.activationId(),h.compatibility(),h.thread(),
                h.sequence(),h.resetlogsId(),h.resetlogsScn(),h.firstScn(),h.nextScn());
        var changes=new ArchivedHeapRows.Result(physical.sources(),restored.changes(),restored.rows().rows(),restored.rows().lastPositions());
        var batch=CapturedHistoricalHeapBatch.readWithSourceRows(List.of(source),s,history,501,physical,changes);
        assertEquals(2,batch.events().stream().map(e->e.row().eventId()).distinct().count());
        var suffix=batch.afterPublished(batch.publicationBoundary(1));
        assertEquals(List.of(batch.events().get(1)),suffix.events());
        assertEquals(first.identity(),suffix.events().get(0).row().transaction());
        assertTrue(batch.afterPublished(batch.publicationBoundary(2)).events().isEmpty());
    }
}
