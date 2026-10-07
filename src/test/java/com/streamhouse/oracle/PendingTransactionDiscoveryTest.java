package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class PendingTransactionDiscoveryTest {
    @TempDir Path directory;
    final ArchivedCompensationInventoryTest fixture=new ArchivedCompensationInventoryTest();
    RedoBatch input(byte[] raw)throws Exception {var path=directory.resolve("archive.dbf");Files.write(path,raw);return RedoBatch.archives(List.of(path));}
    @Test void pendingCoverageExtendsToVerifiedSourceEndButDefaultStillRefuses()throws Exception {
        var inputs=input(fixture.fixture(false,false));
        assertEquals("INCOMPLETE_TRANSACTION_IDENTITIES:1",assertThrows(RedoException.class,()->ArchivedCompensationInventory.read(inputs,677737149,3,fixture.xid)).getMessage());
        var pending=ArchivedCompensationInventory.read(inputs,677737149,3,fixture.xid,true);
        assertFalse(pending.complete());assertEquals(3,pending.entries().size());
        assertEquals(inputs.get(0).header().blocks()-1,pending.lastRecord().block());
        pending.requireCompleteSelection(pending.entries().stream().map(ArchivedCompensationInventory.Entry::position).toList());
        assertThrows(RedoException.class,()->pending.requireCompleteSelection(List.of()));
        assertTrue(ArchivedCompensationInventory.read(input(fixture.fixture(true,false)),677737149,3,fixture.xid,true).complete());
    }
    @Test void pendingModeRetainsBeginAndActiveSlotChecks()throws Exception {
        byte[] missing=fixture.fixture(false,false);put(missing,1112,2,4);seal(missing,1024);
        assertEquals("COMPENSATION_INVENTORY_BEGIN_CONTROL_COUNT",assertThrows(RedoException.class,()->ArchivedCompensationInventory.read(input(missing),677737149,3,fixture.xid,true)).getMessage());
        byte[] conflict=fixture.fixture(false,false);var control=fixture.vectors.start();put(control.fields().get(0),4,fixture.xid.sequence()+65536,4);
        fixture.record(conflict,3,120,List.of(control));seal(conflict,1536);
        assertEquals("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE",assertThrows(RedoException.class,()->ArchivedCompensationInventory.read(input(conflict),677737149,3,fixture.xid,true)).getMessage());
    }
    @Test void pendingDiscoveryStillRoutesForeignCompensationToFullIdentity()throws Exception {
        var f=new ArchivedCompensationAssociationsTest();byte[] raw=f.fixture();f.records.record(raw,5,140,List.of(f.marker(f.a.slot(),0x02400110)));seal(raw,2560);
        var found=ArchivedObjectTransactions.readPending(input(raw),677737149,3,HeapIndexScope.equal(42,43));
        assertEquals(List.of(f.a,f.b),found.transactions().stream().map(ArchivedObjectTransactions.Transaction::xid).toList());
        assertFalse(found.transactions().get(0).associations().window().complete());assertTrue(found.transactions().get(1).associations().window().complete());
        assertEquals(List.of(f.b,f.a),found.transactions().get(0).associations().entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
    }
    @Test void pendingModeCannotHideUnknownMarkerOrUnownedRelevantMarker()throws Exception {
        assertEquals("UNSUPPORTED_COMPENSATION_FLAGS",assertThrows(RedoException.class,()->ArchivedCompensationInventory.read(input(fixture.fixture(false,true)),677737149,3,fixture.xid,true)).getMessage());
        var f=new ArchivedCompensationAssociationsTest();byte[] raw=f.fixture();f.records.record(raw,2,110,List.of());f.records.record(raw,3,120,List.of());seal(raw,1024);seal(raw,1536);
        assertEquals("UNDISCOVERED_OBJECT_TRANSACTION_COMPENSATION",assertThrows(RedoException.class,()->ArchivedObjectTransactions.readPending(input(raw),677737149,3,HeapIndexScope.equal(42,43))).getMessage());
    }
    @Test void physicalPrefixKeepsPendingIndexPairsAndSeparatesClosedOwner()throws Exception {
        var f=new ArchivedPhysicalTransactionsTest();byte[] raw=f.fixture();f.f.records.record(raw,5,140,List.of());seal(raw,2560);
        var result=ArchivedPhysicalTransactions.readPrefix(input(raw),677737149,3,HeapIndexScope.equal(73024,73025),f.leaf,4);
        assertEquals(1,result.completed().transactions().size());assertEquals(f.b,result.completed().transactions().get(0).identity().xid());
        assertEquals(1,result.state().pending().size());var pending=result.state().pending().get(0);
        assertEquals(f.a,pending.xid());assertEquals(1,pending.next());assertEquals(1,pending.indexes().size());assertTrue(pending.heaps().isEmpty());
        assertTrue(pending.graphCheckpoint().contains("A|0|"));
        assertThrows(UnsupportedOperationException.class,()->result.state().pending().clear());
    }
    @Test void pendingIndexWindowRetainsUnboundPageOperationRejection()throws Exception {
        var f=new ArchivedPhysicalTransactionsTest();byte[] raw=f.fixture();var entries=new java.util.ArrayList<>(f.insert(f.b,0x02402cbfL,0x02400120L,1));
        entries.add(new RedoRecords.Vector(10,31,1,12,f.leaf,120,1,3,List.of()));f.f.records.record(raw,3,120,entries);f.f.records.record(raw,5,140,List.of());seal(raw,1536);seal(raw,2560);
        assertEquals("UNSUPPORTED_INDEX_FORWARD_OPCODE",assertThrows(RedoException.class,()->ArchivedPhysicalTransactions.readPrefix(input(raw),677737149,3,HeapIndexScope.equal(73024,73025),f.leaf,4)).getMessage());
    }
}
