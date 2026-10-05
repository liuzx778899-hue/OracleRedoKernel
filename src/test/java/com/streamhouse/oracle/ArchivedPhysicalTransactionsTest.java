package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedPhysicalTransactionsTest {
    @TempDir Path dir;
    final ArchivedCompensationAssociationsTest f=new ArchivedCompensationAssociationsTest();
    final IndexUndoPairTest index=new IndexUndoPairTest();
    final HeapRowPatch.Xid a=new HeapRowPatch.Xid(10,12,1105),b=new HeapRowPatch.Xid(9,5,585);
    final long leaf=0x0300008bL;
    List<RedoRecords.Vector> insert(HeapRowPatch.Xid xid,long undoDba,long segment,int slot) {
        var u=index.purgeUndo();var fields=new ArrayList<>(u.fields());
        byte[] m=java.util.Arrays.copyOf(fields.get(1),76);fields.set(1,m);
        put(fields.get(0),8,xid.usn(),2);put(fields.get(0),10,xid.slot(),2);put(fields.get(0),12,xid.sequence(),4);
        put(m,18,xid.slot(),1);put(m,19,0,1);put(m,20,0x0c08,2);put(m,22,1,2);
        u=new RedoRecords.Vector(5,1,36,11,undoDba,110,1,3,fields);
        var row=index.insert();put(row.fields().get(0),8,xid.usn(),2);put(row.fields().get(0),10,xid.slot(),2);
        put(row.fields().get(0),12,xid.sequence(),4);put(row.fields().get(0),16,undoDba,4);put(row.fields().get(1),2,slot,2);
        var start=f.start(xid,segment,undoDba,8);
        return List.of(start,u,row);
    }
    RedoRecords.Vector end(HeapRowPatch.Xid xid,long undoDba,long segment) {
        var e=f.end(xid,segment);put(e.fields().get(0),16,2,4);byte[] tail=new byte[16];
        put(tail,0,undoDba,4);put(tail,4,242,2);put(tail,6,8,1);
        return new RedoRecords.Vector(5,4,35,11,segment,140,1,3,List.of(e.fields().get(0),tail,e.fields().get(1)));
    }
    byte[] fixture() {
        var raw=f.fixture();f.records.record(raw,2,110,insert(a,0x02402cbeL,0x02400110L,0));
        f.records.record(raw,3,120,insert(b,0x02402cbfL,0x02400120L,1));
        f.records.record(raw,4,130,List.of(end(b,0x02402cbfL,0x02400120L)));
        f.records.record(raw,5,140,List.of(end(a,0x02402cbeL,0x02400110L)));
        for (int i=0;i<raw.length;i+=512)seal(raw,i);return raw;
    }
    ArchivedPhysicalTransactions.Result read(byte[] raw,long selectedLeaf,long uid) throws Exception {
        var path=dir.resolve("archive.dbf");Files.write(path,raw);
        return ArchivedPhysicalTransactions.read(List.of(path),uid,3,73024,73025,selectedLeaf,4);
    }
    @Test void discoversAndClosesEveryTransactionWithoutSuppliedIdentityList() throws Exception {
        var result=read(fixture(),leaf,677737149);
        assertEquals(List.of(a,b),result.transactions().stream().map(t->t.identity().xid()).toList());
        assertTrue(result.transactions().stream().allMatch(t->t.graph().original().size()==1 && t.graph().live().size()==1 && t.operations().size()==1));
        assertThrows(UnsupportedOperationException.class,()->result.transactions().clear());
        assertThrows(UnsupportedOperationException.class,()->result.transactions().get(0).operations().clear());
    }
    @Test void missingWholeForeignEndRefusesTheBatch() {
        var raw=fixture();f.records.record(raw,4,130,List.of());seal(raw,2048);
        assertThrows(RedoException.class,()->read(raw,leaf,677737149));
    }
    @Test void wrongLeafAndContainerCannotReturnPartialSuccess() {
        assertThrows(RedoException.class,()->read(fixture(),leaf+1,677737149));
        assertEquals("NO_PHYSICAL_OBJECT_TRANSACTIONS",assertThrows(RedoException.class,()->read(fixture(),leaf,1)).getMessage());
    }
    @Test void leafOperationWithoutPhysicalPairRefuses() {
        var raw=fixture();var entries=new ArrayList<>(insert(b,0x02402cbfL,0x02400120L,1));entries.remove(2);
        f.records.record(raw,3,120,entries);seal(raw,1536);
        assertEquals("TARGET_INDEX_LEAF_NOT_FOUND_IN_WINDOW",assertThrows(RedoException.class,()->read(raw,leaf,677737149)).getMessage());
    }
    @Test void unknownPageMaintenanceInActiveWindowRefuses() {
        var raw=fixture();var entries=new ArrayList<>(insert(b,0x02402cbfL,0x02400120L,1));
        entries.add(new RedoRecords.Vector(10,31,1,12,leaf,120,1,3,List.of()));
        f.records.record(raw,3,120,entries);seal(raw,1536);
        assertEquals("UNSUPPORTED_INDEX_FORWARD_OPCODE",assertThrows(RedoException.class,()->read(raw,leaf,677737149)).getMessage());
    }
    List<RedoRecords.Vector> foreignBegin(int slot) {
        var xid=new HeapRowPatch.Xid(10,slot,1106);var entries=new ArrayList<>(insert(xid,0x02402cc0L,0x02400110L,2));
        var undo=entries.get(1);put(undo.fields().get(1),0,720,4);put(undo.fields().get(1),4,720,4);
        return List.of(entries.get(0),undo);
    }
    byte[] withForeignBegin(List<RedoRecords.Vector> controls) {
        var raw=java.util.Arrays.copyOf(fixture(),7*512);put(raw,24,6,4);put(raw,668,7,4);
        f.records.record(raw,4,125,controls);
        f.records.record(raw,5,130,List.of(end(b,0x02402cbfL,0x02400120L)));
        f.records.record(raw,6,140,List.of(end(a,0x02402cbeL,0x02400110L)));
        for(int i=0;i<raw.length;i+=512)seal(raw,i);return raw;
    }
    @Test void exactForeignBeginOnActiveSegmentDoesNotBecomeTargetHistory() throws Exception {
        var raw=withForeignBegin(foreignBegin(4));
        assertEquals(List.of(a,b),read(raw,leaf,677737149).transactions().stream().map(t->t.identity().xid()).toList());
    }
    @Test void foreignBeginCannotReassignActiveSlotOrLoseItsExactUndo() {
        for (boolean collision:List.of(true,false)) {
            var foreign=foreignBegin(collision?12:4);var raw=withForeignBegin(collision?foreign:List.of(foreign.get(0)));
            assertEquals(collision?"TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE":"MISSING_TRANSACTION_BEGIN_UBA",
                    assertThrows(RedoException.class,()->read(raw,leaf,677737149)).getMessage());
        }
    }
    @Test void duplicateForeignBeginAndTargetRoleCannotBeExcluded() {
        var foreign=foreignBegin(4);
        assertEquals("AMBIGUOUS_FOREIGN_BEGIN_CONTROL",assertThrows(RedoException.class,
                ()->read(withForeignBegin(List.of(foreign.get(0),foreign.get(0),foreign.get(1))),leaf,677737149)).getMessage());
        put(foreign.get(1).fields().get(1),4,73024,4);
        assertThrows(RedoException.class,()->read(withForeignBegin(foreign),leaf,677737149));
        var sameSlot=foreignBegin(12);put(sameSlot.get(0).fields().get(0),4,a.sequence(),4);
        put(sameSlot.get(1).fields().get(0),8,2,2);put(sameSlot.get(1).fields().get(0),12,a.sequence(),4);
        assertEquals("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE",assertThrows(RedoException.class,
                ()->read(withForeignBegin(sameSlot),leaf,677737149)).getMessage());
    }
    ArchivedPhysicalTransactions.Result verified(byte[] raw,long scn) throws Exception {
        put(raw,512+52,1,4);put(raw,512+160,1,4);put(raw,512+164,1,4);seal(raw,512);
        var path=dir.resolve("verified-archive.dbf");Files.write(path,raw);var h=new ArchivedRedo().scan(path);
        String text=new FrozenHeapBaselineTest().sample()
                .replace("SOURCE|1276726821|1276717861|1245693159|1920977|1|19.0.0",
                        "SOURCE|"+h.dbid()+"|"+h.activationId()+"|"+h.resetlogsId()+"|"+h.resetlogsScn()+"|1|19.0.0")
                .replace("SOURCE_AFTER|1276726821|1276717861|1245693159|1920977|1|19.0.0",
                        "SOURCE_AFTER|"+h.dbid()+"|"+h.activationId()+"|"+h.resetlogsId()+"|"+h.resetlogsScn()+"|1|19.0.0")
                .replace("2341293",Long.toString(scn)).replace("73073","73024").replace("73076","73024")
                .replace("73074","73025").replace("73075","73025");
        var snapshot=dir.resolve("snapshot.txt");Files.writeString(snapshot,text);
        return ArchivedPhysicalTransactions.readVerifiedBaseline(RedoBatch.archives(List.of(path)),snapshot,
                new FrozenHeapBaselineTest().digest(text),leaf);
    }
    byte[] noTailFixture() {
        var raw=fixture();var e=f.end(b,0x02400120L);put(e.fields().get(0),16,0,4);
        f.records.record(raw,4,130,List.of(e));seal(raw,2048);return raw;
    }
    @Test void verifiedBaselineCoversOldNoTailAndOrdinaryCommitClosesItsOwnedHistory() throws Exception {
        assertTrue(verified(noTailFixture(),150).transactions().isEmpty());
        assertEquals(2,read(noTailFixture(),leaf,677737149).transactions().size());
        assertEquals(2,verified(noTailFixture(),105).transactions().size());
    }
    @Test void missingTailInReferencedCommitLayoutStillRefuses() {
        var raw=noTailFixture();var e=f.end(b,0x02400120L);put(e.fields().get(0),16,2,4);
        f.records.record(raw,4,130,List.of(e));seal(raw,2048);
        assertEquals("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE",assertThrows(RedoException.class,
                ()->read(raw,leaf,677737149)).getMessage());
    }
    @Test void baselineCannotHideCrossingOrMissingOldLifecycleEnd() {
        assertEquals("BASELINE_CROSSES_TRANSACTION_BOUNDARY",assertThrows(RedoException.class,
                ()->verified(noTailFixture(),125)).getMessage());
        var raw=noTailFixture();f.records.record(raw,4,130,List.of());seal(raw,2048);
        assertThrows(RedoException.class,()->verified(raw,150));
    }
}
