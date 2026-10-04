package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedIndexLeafInventoryTest {
    @TempDir Path dir;
    final ArchivedCompensationInventoryTest records=new ArchivedCompensationInventoryTest();
    final IndexUndoPairTest index=new IndexUndoPairTest();
    final HeapRowPatch.Xid xid=records.xid;
    final long object=73025,leaf=0x0300008bL;

    byte[] fixture() {
        byte[] bytes=Arrays.copyOf(records.fixture(true,false),3584);put(bytes,24,6,4);put(bytes,668,7,4);
        var row=index.delete();put(row.fields().get(0),10,xid.slot(),2);put(row.fields().get(0),12,xid.sequence(),4);
        var undo=index.restoreUndo();put(undo.fields().get(0),10,xid.slot(),2);put(undo.fields().get(0),12,xid.sequence(),4);
        put(undo.fields().get(1),18,xid.slot(),1);put(undo.fields().get(1),22,xid.sequence(),2);
        records.record(bytes,3,120,List.of(row,undo));
        records.record(bytes,4,125,List.of(index.purgeUndo(),index.insert()));
        var p=new IndexCompensationTest().purge();put(p.fields().get(1),2,2,2);
        var restore=new RedoRecords.Vector(10,5,1,12,leaf,130,1,3,p.fields());
        var marker=records.marker(object,3);put(marker.fields().get(0),16,10,1);put(marker.fields().get(0),17,22,1);
        records.record(bytes,5,130,List.of(restore,marker,records.vectors.end(2)));
        records.record(bytes,6,140,List.of(index.purgeUndo(),index.insert()));
        for (int i=0;i<bytes.length;i+=512) seal(bytes,i);
        return bytes;
    }
    ArchivedIndexLeafInventory read(byte[] bytes) throws Exception {
        var path=dir.resolve("archive.dbf");Files.write(path,bytes);
        return ArchivedIndexLeafInventory.read(List.of(path),677737149,3,xid,object,leaf);
    }
    @Test void includesForeignTransactionAndUsesVerifiedInclusiveWindow() throws Exception {
        var inventory=read(fixture());var entries=inventory.entries();
        assertEquals(3,entries.size());
        assertEquals(List.of(IndexUndoPair.Kind.DELETE,IndexUndoPair.Kind.INSERT,IndexUndoPair.Kind.RESTORE),entries.stream().map(ArchivedIndexLeafInventory.Entry::kind).toList());
        assertEquals(xid,entries.get(0).original().xid());
        assertNotEquals(xid,entries.get(1).original().xid());
        assertEquals("030000830000",entries.get(1).keydata());
        assertEquals(2,entries.get(2).slot());assertNotNull(entries.get(2).marker());
        assertEquals(2,inventory.window().firstRecord().block());assertEquals(5,inventory.window().lastRecord().block());
        assertEquals(0,inventory.window().firstRecord().vector());
        assertThrows(UnsupportedOperationException.class,()->entries.clear());
    }
    @Test void rejectsOmissionDuplicationReorderingAndForeignSourcePosition() throws Exception {
        var inventory=read(fixture());var positions=inventory.entries().stream().map(ArchivedIndexLeafInventory.Entry::position).toList();
        inventory.requireCompleteSelection(positions);
        for (var wrong:List.of(List.of(positions.get(0),positions.get(2)),List.of(positions.get(0),positions.get(0),positions.get(2)),
                List.of(positions.get(1),positions.get(0),positions.get(2)),List.of(positions.get(0),positions.get(1),new ArchivedUndoInventory.Position(8,5,16,1))))
            assertEquals("INDEX_LEAF_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED",assertThrows(RedoException.class,()->inventory.requireCompleteSelection(wrong)).getMessage());
    }
    @Test void unknownLeafMaintenanceRefusesBeforeReturningCoverage() {
        byte[] bytes=fixture();var unknown=new RedoRecords.Vector(10,31,1,12,leaf,125,1,3,List.of());
        records.record(bytes,4,125,List.of(unknown));seal(bytes,2048);
        assertEquals("UNSUPPORTED_INDEX_LEAF_WINDOW_OPCODE:10.31",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
    @Test void foreignInsertWithoutItsFullUndoCannotBeCountedAsSafeCoverage() {
        byte[] bytes=fixture();records.record(bytes,4,125,List.of(index.insert()));seal(bytes,2048);
        assertEquals("MISSING_INDEX_UNDO_UBA",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
    @Test void wrongObjectAndMissingRollbackMarkerRefuse() {
        byte[] bytes=fixture();var undo=index.purgeUndo();put(undo.fields().get(1),0,999,4);put(undo.fields().get(1),4,999,4);
        records.record(bytes,4,125,List.of(index.insert(),undo));seal(bytes,2048);
        assertEquals("INDEX_LEAF_INVENTORY_OBJECT_MISMATCH",assertThrows(RedoException.class,()->read(bytes)).getMessage());
        byte[] missing=fixture();var p=new IndexCompensationTest().purge();
        records.record(missing,5,130,List.of(p,records.vectors.end(2)));seal(missing,2560);
        assertEquals("MISSING_INDEX_LEAF_INVENTORY_MARKER",assertThrows(RedoException.class,()->read(missing)).getMessage());
    }
    @Test void otherContainerAndLeafDoNotEnterSelection() throws Exception {
        byte[] bytes=fixture();var row=index.insert();var undo=index.purgeUndo();
        var other=new RedoRecords.Vector(10,2,1,12,leaf,125,1,4,row.fields());
        var otherUndo=new RedoRecords.Vector(5,1,36,11,undo.dba(),125,1,4,undo.fields());
        var maintenance=new RedoRecords.Vector(10,31,1,12,leaf+1,125,1,3,List.of());
        records.record(bytes,4,125,List.of(other,otherUndo,maintenance));seal(bytes,2048);
        assertEquals(2,read(bytes).entries().size());
    }
}
