package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalPublicationBoundaryTest {
    final ArchivedHeapRows.Source source=new ArchivedHeapRows.Source(1,2,0x13000000,1,3,4,5,3);
    final FrozenHeapBaseline.ObjectRole heap=new FrozenHeapBaseline.ObjectRole("OWNER","T",10,11);
    final FrozenHeapBaseline.ObjectRole index=new FrozenHeapBaseline.ObjectRole("OWNER","PK",12,13);
    final List<FrozenHeapBaseline.PhysicalColumn> columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false));
    CapturedHistoricalHeapBatch.Event event(int ordinal,int xid) {
        var transaction=new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(10,xid,1),5,3,100,110+xid,false);
        var row=new CapturedHeapBatch.Event("event"+ordinal,"payload"+ordinal,new ArchivedUndoInventory.Position(7,4,16,ordinal),transaction,
                new ArchivedHeapRows.RowKey(11,100,ordinal),HeapRowPatch.Kind.UPDATE,List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c102")),
                List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c103")));
        return new CapturedHistoricalHeapBatch.Event(new CapturedHeapBatch.Schema(heap,index,columns,"schema"),row);
    }
    CapturedHistoricalHeapBatch batch(String baseline,List<CapturedHistoricalHeapBatch.Event> events)throws Exception {
        var snapshot=new FrozenHeapBaseline.Snapshot(baseline,source,100,heap,index,columns,Map.of());
        var constructor=CapturedHistoricalHeapBatch.class.getDeclaredConstructor(FrozenHeapBaseline.Snapshot.class,List.class,List.class,String.class,long.class);
        constructor.setAccessible(true);return constructor.newInstance(snapshot,List.of(),events,"full-batch",501L);
    }
    @Test void completeTransactionsAreRemovedWithoutChangingRemainingImagesOrIdentity()throws Exception {
        var full=batch("baseline",List.of(event(1,1),event(2,1),event(3,2)));
        var next=full.afterPublished(full.publicationBoundary(2));
        assertEquals(full.events().subList(2,3),next.events());assertNotEquals(full.sha256(),next.sha256());
        assertTrue(full.afterPublished(full.publicationBoundary(3)).events().isEmpty());
        assertThrows(RedoException.class,()->next.publicationBoundary(0));
    }
    @Test void transactionCannotBeSplitEvenWhenItsEventsAreNonAdjacent()throws Exception {
        var full=batch("baseline",List.of(event(1,1),event(2,1),event(3,2)));
        assertEquals("HISTORICAL_PUBLICATION_TRANSACTION_SPLIT",assertThrows(RedoException.class,()->full.publicationBoundary(1)).getMessage());
        var interleaved=batch("baseline",List.of(event(1,1),event(3,2),event(2,1)));
        assertThrows(RedoException.class,()->interleaved.publicationBoundary(2));
    }
    @Test void changedBaselinePayloadOrderOrDigestCannotHidePublishedEvents()throws Exception {
        var a=event(1,1);var b=event(2,2);var full=batch("baseline",List.of(a,b));var boundary=full.publicationBoundary(1);
        assertThrows(RedoException.class,()->batch("foreign",List.of(a,b)).afterPublished(boundary));
        assertThrows(RedoException.class,()->batch("baseline",List.of(b,a)).afterPublished(boundary));
        var row=a.row();var changed=new CapturedHistoricalHeapBatch.Event(a.schema(),new CapturedHeapBatch.Event(row.eventId(),"changed-payload",
                row.pairedUndoPosition(),row.transaction(),row.rowKey(),row.operation(),row.before(),row.after()));
        assertThrows(RedoException.class,()->batch("baseline",List.of(changed,b)).afterPublished(boundary));
        assertThrows(RedoException.class,()->full.afterPublished(new CapturedHistoricalHeapBatch.PublicationBoundary(1,"0".repeat(64))));
        assertThrows(RedoException.class,()->full.publicationBoundary(4));
    }
}
