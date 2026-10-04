package com.streamhouse.oracle;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Arithmetic only; ownership and cancellation need independent archive/graph evidence. */
class IndexPositionReplayTest {
    ArchivedIndexLeafInventory.Entry e(IndexUndoPair.Kind kind,int slot) {
        return new ArchivedIndexLeafInventory.Entry(null,0,kind,1,1,slot,13,null,null,null,null,null,null,null);
    }
    @Test void twoInsertsAndPurgesRestoreTrackedSlot() throws Exception {
        var entries=List.of(e(IndexUndoPair.Kind.DELETE,1),e(IndexUndoPair.Kind.INSERT,1),e(IndexUndoPair.Kind.INSERT,2),
                e(IndexUndoPair.Kind.PURGE,2),e(IndexUndoPair.Kind.PURGE,1),e(IndexUndoPair.Kind.RESTORE,1));
        assertEquals(1,IndexLeafPositions.replaySlot(entries,0,5));
        assertEquals(1,IndexLeafPositions.replaySlot(entries,1,4));
    }
    @Test void deleteAndRestoreLeavePhysicalSlotInPlace() throws Exception {
        var entries=List.of(e(IndexUndoPair.Kind.DELETE,1),e(IndexUndoPair.Kind.DELETE,0),e(IndexUndoPair.Kind.RESTORE,0),e(IndexUndoPair.Kind.RESTORE,1));
        assertEquals(1,IndexLeafPositions.replaySlot(entries,0,3));
    }
    @Test void higherInsertAndPurgeDoNotMoveTrackedSlot() throws Exception {
        var entries=List.of(e(IndexUndoPair.Kind.DELETE,1),e(IndexUndoPair.Kind.INSERT,2),e(IndexUndoPair.Kind.PURGE,2),e(IndexUndoPair.Kind.RESTORE,1));
        assertEquals(1,IndexLeafPositions.replaySlot(entries,0,3));
    }
    @Test void trackedRowCannotSurviveItsOwnPurge() {
        var entries=List.of(e(IndexUndoPair.Kind.INSERT,1),e(IndexUndoPair.Kind.PURGE,1),e(IndexUndoPair.Kind.RESTORE,1));
        assertEquals("INDEX_POSITION_TRACKED_ROW_PURGED",assertThrows(RedoException.class,()->IndexLeafPositions.replaySlot(entries,0,2)).getMessage());
    }
    @Test void insertionCannotOverflowSlot() {
        var entries=List.of(e(IndexUndoPair.Kind.DELETE,65535),e(IndexUndoPair.Kind.INSERT,0),e(IndexUndoPair.Kind.RESTORE,65535));
        assertEquals("INDEX_POSITION_SLOT_OVERFLOW",assertThrows(RedoException.class,()->IndexLeafPositions.replaySlot(entries,0,2)).getMessage());
    }
}
