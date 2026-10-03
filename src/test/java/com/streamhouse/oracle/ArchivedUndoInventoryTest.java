package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedUndoInventoryTest {
    @TempDir Path dir;
    final HeapRowPatch.Xid xid=new HeapRowPatch.Xid(10,5,1133);
    byte[] fixture() {
        byte[] bytes=new byte[1536];
        put(bytes,0,0x2200,2);put(bytes,20,512,4);put(bytes,24,2,4);put(bytes,28,0x7a7b7c7dL,4);
        put(bytes,512,0x2201,2);put(bytes,516,1,4);put(bytes,520,7,4);
        put(bytes,532,0x13000000,4);put(bytes,536,42,4);put(bytes,668,3,4);
        put(bytes,688,1,2);put(bytes,692,100,4);put(bytes,704,200,4);
        put(bytes,1024,0x2201,2);put(bytes,1028,2,4);put(bytes,1032,7,4);
        put(bytes,1040,192,4);put(bytes,1044,1,1);put(bytes,1048,101,4);put(bytes,1056,9,4);
        var vectors=new UndoChainTest();
        for (int i=0;i<2;i++) {
            var v=vectors.link(34+i,33+i);int base=1064+84*i;
            put(bytes,base,5,1);put(bytes,base+1,1,1);put(bytes,base+8,v.dba(),4);put(bytes,base+24,3,4);
            put(bytes,base+32,6,2);put(bytes,base+34,20,2);put(bytes,base+36,24,2);
            System.arraycopy(v.fields().get(0),0,bytes,base+40,20);
            System.arraycopy(v.fields().get(1),0,bytes,base+60,24);
        }
        for (int i=0;i<bytes.length;i+=512) seal(bytes,i);
        return bytes;
    }
    Path write(byte[] bytes) throws Exception { var path=dir.resolve("archive.dbf");Files.write(path,bytes);return path; }
    ArchivedUndoInventory read(byte[] bytes) throws Exception {return ArchivedUndoInventory.read(List.of(write(bytes)),9,3,xid);}
    @Test void ownsTraversalAndRejectsOmissionDuplicateOrderAndForeignPosition() throws Exception {
        var inventory=read(fixture());var positions=inventory.entries().stream().map(ArchivedUndoInventory.Entry::position).toList();
        assertEquals(2,positions.size());assertEquals(7,positions.get(0).archiveSequence());
        inventory.requireCompleteSelection(positions);
        for (var wrong:List.of(List.of(positions.get(0)),List.of(positions.get(0),positions.get(0)),
                List.of(positions.get(1),positions.get(0)),List.of(positions.get(0),new ArchivedUndoInventory.Position(8,2,16,2))))
            assertThrows(RedoException.class,()->inventory.requireCompleteSelection(wrong));
        assertThrows(UnsupportedOperationException.class,()->inventory.entries().clear());
        assertThrows(UnsupportedOperationException.class,()->inventory.sources().clear());
    }
    @Test void fullXidSelectionDoesNotMergeLowWrapCollision() throws Exception {
        byte[] bytes=fixture();put(bytes,1200,1133+65536,4);seal(bytes,1024);
        assertEquals(1,read(bytes).entries().size());
    }
    @Test void unknownRelatedFlagsRefuseRatherThanDisappear() throws Exception {
        byte[] bytes=fixture();bytes[1144]=1;seal(bytes,1024);
        assertEquals("UNSUPPORTED_UNDO_HEADER_FLAGS",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
    @Test void unreadableContainerIdentityCannotBeExcluded() throws Exception {
        byte[] bytes=fixture();put(bytes,1182,24,2);put(bytes,1184,20,2);seal(bytes,1024);
        assertThrows(RedoException.class,()->read(bytes));
    }
    @Test void sourceContinuityAndMissingTargetRefuse() throws Exception {
        var path=write(fixture());
        assertThrows(RedoException.class,()->ArchivedUndoInventory.read(List.of(path,path),9,3,xid));
        assertThrows(RedoException.class,()->ArchivedUndoInventory.read(List.of(path),8,3,xid));
        assertThrows(RedoException.class,()->ArchivedUndoInventory.read(List.of(),9,3,xid));
    }
}
