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
}
