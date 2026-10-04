package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CapturedHeapBatchTest {
    @TempDir Path dir;
    final ArchivedHeapRows.Source source=new ArchivedHeapRows.Source(1,2,0x13000000,1,3,4,5,3);
    final HeapRowPatch.Xid xid=new HeapRowPatch.Xid(10,2,1680);
    final ArchivedHeapRows.RowKey key=new ArchivedHeapRows.RowKey(73076,0x03000193,0);
    final ArchivedUndoInventory.Position at=new ArchivedUndoInventory.Position(40,14744,16,4);
    final List<ArchivedHeapRows.Cell> before=List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c10c"));
    ArchivedHeapRows.Change change(ArchivedUndoInventory.Position p,HeapRowPatch.Xid x,ArchivedHeapRows.RowKey k,
                                  List<ArchivedHeapRows.Cell> after) {
        return new ArchivedHeapRows.Change(p,x,k,HeapRowPatch.Kind.UPDATE,before,after);
    }
    @Test void fullSourcePositionXidAndGenerationSeparateEventIdentities() {
        var c=change(at,xid,key,before);String id=CapturedHeapBatch.eventId(source,"schema",c);
        long[] values={1,2,0x13000000,1,3,4,5,3};
        for (int i=0;i<values.length;i++) {
            long[] v=values.clone();v[i]++;
            var foreign=new ArchivedHeapRows.Source(v[0],v[1],v[2],(int)v[3],v[4],v[5],v[6],v[7]);
            assertNotEquals(id,CapturedHeapBatch.eventId(foreign,"schema",c));
        }
        assertNotEquals(id,CapturedHeapBatch.eventId(source,"new-schema",c));
        assertNotEquals(id,CapturedHeapBatch.eventId(source,"schema",change(new ArchivedUndoInventory.Position(40,14744,16,5),xid,key,before)));
        assertNotEquals(id,CapturedHeapBatch.eventId(source,"schema",change(at,new HeapRowPatch.Xid(10,2,1681),key,before)));
        assertNotEquals(id,CapturedHeapBatch.eventId(source,"schema",change(at,xid,new ArchivedHeapRows.RowKey(73077,key.dba(),0),before)));
    }
    @Test void conflictingPayloadKeepsEventIdentityButChangesContentProof() {
        var a=change(at,xid,key,before);var b=change(at,xid,key,List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")));
        var transaction=new TransactionLifecycle.ClosedIdentity(xid,5,3,100,110,false);
        assertEquals(CapturedHeapBatch.eventId(source,"schema",a),CapturedHeapBatch.eventId(source,"schema",b));
        assertNotEquals(CapturedHeapBatch.payloadDigest(a,transaction),CapturedHeapBatch.payloadDigest(b,transaction));
        assertNotEquals(CapturedHeapBatch.payloadDigest(a,transaction),CapturedHeapBatch.payloadDigest(a,
                new TransactionLifecycle.ClosedIdentity(xid,5,3,100,111,false)));
    }
    @Test void schemaDoesNotDependOnBaselineScnOrRowsButIncludesPhysicalMetadata() {
        var heap=new FrozenHeapBaseline.ObjectRole("OWNER","TABLE",10,11);
        var index=new FrozenHeapBaseline.ObjectRole("OWNER","PK",12,13);
        var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false));
        var a=new FrozenHeapBaseline.Snapshot("00",source,100,heap,index,columns,Map.of());
        var b=new FrozenHeapBaseline.Snapshot("11",source,101,heap,index,columns,Map.of(key,before));
        assertEquals(CapturedHeapBatch.schemaDigest(a),CapturedHeapBatch.schemaDigest(b));
        var hidden=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,true));
        assertNotEquals(CapturedHeapBatch.schemaDigest(a),CapturedHeapBatch.schemaDigest(
                new FrozenHeapBaseline.Snapshot("00",source,100,heap,index,hidden,Map.of())));
    }
    @Test void resultImagesAndSchemaColumnsCannotBeMutatedAfterReturn() {
        var mutable=new ArrayList<>(before);
        var event=new CapturedHeapBatch.Event("id","payload",at,
                new TransactionLifecycle.ClosedIdentity(xid,5,3,100,110,false),key,HeapRowPatch.Kind.UPDATE,mutable,mutable);
        mutable.clear();assertEquals(1,event.before().size());
        assertThrows(UnsupportedOperationException.class,()->event.after().clear());
        var columns=new ArrayList<FrozenHeapBaseline.PhysicalColumn>();
        columns.add(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false));
        var schema=new CapturedHeapBatch.Schema(null,null,columns,"digest");columns.clear();
        assertEquals(1,schema.columns().size());assertThrows(UnsupportedOperationException.class,()->schema.columns().clear());
    }
    @Test void badTrustedBaselineReceiptStopsBeforeAnyBatchCanBeReturned() throws Exception {
        var p=dir.resolve("baseline");String text=new FrozenHeapBaselineTest().sample();Files.writeString(p,text);
        String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
        assertNotEquals("0".repeat(64),actual);
        assertEquals("SNAPSHOT_ACQUISITION_DIGEST_MISMATCH",assertThrows(RedoException.class,
                ()->CapturedHeapBatch.read(List.of(),p,"0".repeat(64),0x0300019b)).getMessage());
    }
}
