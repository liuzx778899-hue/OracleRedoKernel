package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedCompensationInventoryTest {
    @TempDir Path dir;
    final TransactionLifecycleTest vectors=new TransactionLifecycleTest();
    final HeapRowPatch.Xid xid=new HeapRowPatch.Xid(10,32,1136);
    RedoRecords.Vector marker(long object,long container) {
        byte[] b=new byte[28];put(b,0,object,4);put(b,4,object,4);put(b,18,32,1);
        put(b,19,2,1);put(b,20,16,2);put(b,22,1136,2);
        return new RedoRecords.Vector(5,6,36,11,0x02402ccb,120,1,container,List.of(b,new byte[8]));
    }
    byte[] fixture(boolean includeEnd,boolean unknownMarker) {
        byte[] bytes=Arrays.copyOf(new ArchivedUndoInventoryTest().fixture(),3072);
        Arrays.fill(bytes,1024,bytes.length,(byte)0);put(bytes,24,5,4);put(bytes,668,6,4);
        var undo=vectors.undo();put(undo.fields().get(1),18,32,1);put(undo.fields().get(1),22,1,2);
        var first=marker(42,3);if (unknownMarker) put(first.fields().get(0),20,17,2);
        record(bytes,2,110,List.of(vectors.start(),undo));
        record(bytes,3,120,List.of(first,marker(999,3),marker(42,4)));
        record(bytes,4,130,includeEnd ? List.of(vectors.end(2)) : List.of());
        record(bytes,5,140,List.of(marker(42,3)));
        for (int i=0;i<bytes.length;i+=512) seal(bytes,i);
        return bytes;
    }
    void record(byte[] bytes,int block,long scn,List<RedoRecords.Vector> vs) {
        int base=block*512;put(bytes,base,0x2201,2);put(bytes,base+4,block,4);put(bytes,base+8,7,4);
        int start=base+16,pos=start+24;put(bytes,start+4,1,1);put(bytes,start+8,scn,4);put(bytes,start+16,677737149,4);
        for (var v:vs) {
            put(bytes,pos,v.opcodeMajor(),1);put(bytes,pos+1,v.opcodeMinor(),1);put(bytes,pos+8,v.dba(),4);put(bytes,pos+24,v.containerId(),4);
            int table=2+2*v.fields().size(),data=pos+32+((table+3)&~3);put(bytes,pos+32,table,2);
            for (int i=0;i<v.fields().size();i++) {
                byte[] b=v.fields().get(i);put(bytes,pos+34+2*i,b.length,2);System.arraycopy(b,0,bytes,data,b.length);data+=(b.length+3)&~3;
            }
            pos=data;
        }
        put(bytes,start,pos-start,4);
    }
    ArchivedCompensationInventory read(byte[] bytes) throws Exception {
        var p=dir.resolve("archive.dbf");Files.write(p,bytes);
        return ArchivedCompensationInventory.read(List.of(p),677737149,3,xid);
    }
    @Test void ownsWindowAndIncludesOtherObjectsWithoutClaimingOwnership() throws Exception {
        var inventory=read(fixture(true,false));assertEquals(2,inventory.entries().size());
        assertEquals(999,inventory.entries().get(1).marker().objectId());
        assertEquals(1,inventory.undo().entries().size());
        assertThrows(UnsupportedOperationException.class,()->inventory.entries().clear());
        var positions=inventory.entries().stream().map(ArchivedCompensationInventory.Entry::position).toList();
        inventory.requireCompleteSelection(positions);
        for (var wrong:List.of(List.<ArchivedUndoInventory.Position>of(),List.of(positions.get(0)),
                List.of(positions.get(1),positions.get(0)),List.of(positions.get(0),positions.get(0)),
                List.of(positions.get(0),new ArchivedUndoInventory.Position(8,3,16,2))))
            assertEquals("COMPENSATION_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED",
                    assertThrows(RedoException.class,()->inventory.requireCompleteSelection(wrong)).getMessage());
    }
    @Test void missingEndRefusesBeforeReturningCoverage() {
        assertEquals("INCOMPLETE_TRANSACTION_IDENTITIES:1",assertThrows(RedoException.class,()->read(fixture(false,false))).getMessage());
    }
    @Test void unknownWindowMarkerRefusesRatherThanBeingFilteredByObject() {
        assertEquals("UNSUPPORTED_COMPENSATION_FLAGS",assertThrows(RedoException.class,()->read(fixture(true,true))).getMessage());
    }
    @Test void missingBeginControlRefuses() {
        byte[] b=fixture(true,false);put(b,1112,2,4);seal(b,1024);
        assertEquals("COMPENSATION_INVENTORY_BEGIN_CONTROL_COUNT",assertThrows(RedoException.class,()->read(b)).getMessage());
    }
}
