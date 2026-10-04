package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionLifecycleTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    ArchivedRedo.Header archive(long seq,long low,long high,long dbid) {
        return new ArchivedRedo.Header("fixture",1536,dbid,11,0x13000000,1,seq,22,33,low,high,3);
    }
    RedoRecords.Vector start() {
        return new RedoRecords.Vector(5,2,35,11,0x02400110,100,1,3,List.of(
                hex("2000000070040000cb2c4002f200290052008800000000000000000000000000"),hex("bd726528")));
    }
    RedoRecords.Vector undo() {
        byte[] second=new byte[76]; second[20]=8; second[21]=12;
        return new RedoRecords.Vector(5,1,36,11,0x02402ccb,100,1,3,List.of(
                hex("88000000120000000a00200070040000f2002900"),second));
    }
    RedoRecords.Vector end(int flags) {
        byte[] b=hex("2000000070040000000000000900000002000000"); b[16]=(byte)flags;
        return new RedoRecords.Vector(5,4,35,11,0x02400110,100,1,3,List.of(b,hex("ea44c16a")));
    }
    RedoRecords.Record record(long scn,RedoRecords.Vector... v) {
        return new RedoRecords.Record(2,16,768,13,scn,1,677737149,List.of(v));
    }
    TransactionLifecycle begun() throws Exception {
        var state=new TransactionLifecycle(); state.archive(archive(8,100,200,42));
        state.begin(record(110,start(),undo()),start()); return state;
    }
    @Test void observesFullSlotSequenceAndLatchesHiddenReassignment() throws Exception {
        var same=start();var state=begun();state.observeSlotControl(record(120,same),same);
        var zero=start();java.util.Arrays.fill(zero.fields().get(0),4,8,(byte)0);
        state.observeSlotControl(record(120,zero),zero);
        var collision=start();ArchivedRedoTest.put(collision.fields().get(0),4,1136+65536,4);
        assertEquals("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE",
                assertThrows(RedoException.class,()->state.observeSlotControl(record(120,collision),collision)).getMessage());
        assertEquals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED",assertThrows(RedoException.class,state::finish).getMessage());
    }
    @Test void unreadableActiveSegmentControlRefusesAndLocks() throws Exception {
        var state=begun();var v=new RedoRecords.Vector(5,2,35,11,start().dba(),120,1,3,List.of(new byte[4]));
        assertEquals("UNREADABLE_ACTIVE_SEGMENT_SLOT_CONTROL",
                assertThrows(RedoException.class,()->state.observeSlotControl(record(120,v),v)).getMessage());
        assertThrows(RedoException.class,state::finish);
    }
    @Test void keepsIdentityAcrossContiguousArchiveSwitch() throws Exception {
        var state=begun(); state.archive(archive(9,200,300,42));
        state.undo(record(210,undo()),undo());
        var h=state.end(record(220,end(2)),end(2)); state.finish();
        assertEquals(new HeapRowPatch.Xid(10,32,1136),h.xid());
        assertEquals(110,h.beginRecordScn()); assertEquals(220,h.endRecordScn()); assertFalse(h.rolledBack());
    }
    @Test void endsEntireRollbackWithoutPublishingRows() throws Exception {
        var state=begun(); assertTrue(state.end(record(120,end(4)),end(4)).rolledBack()); state.finish();
    }
    @Test void rejectsGapAndLatchesRefusal() throws Exception {
        var state=begun();
        assertThrows(RedoException.class,()->state.archive(archive(10,200,300,42)));
        assertThrows(RedoException.class,state::finish);
        assertThrows(RedoException.class,()->state.end(record(120,end(2)),end(2)));
        var scnGap=begun(); assertThrows(RedoException.class,()->scnGap.archive(archive(9,201,300,42)));
    }
    @Test void separatesSourceIdentity() throws Exception {
        var state=begun(); assertThrows(RedoException.class,()->state.archive(archive(9,200,300,43)));
    }
    @Test void rejectsOrphanUndoAndEnd() throws Exception {
        var state=new TransactionLifecycle(); state.archive(archive(9,200,300,42));
        assertThrows(RedoException.class,()->state.undo(record(210,undo()),undo()));
        var other=new TransactionLifecycle(); other.archive(archive(9,200,300,42));
        assertThrows(RedoException.class,()->other.end(record(210,end(2)),end(2)));
    }
    @Test void rejectsIncompleteAndDuplicateIdentity() throws Exception {
        var state=begun(); assertThrows(RedoException.class,state::finish);
        var duplicate=begun(); assertThrows(RedoException.class,()->duplicate.begin(record(111,start(),undo()),start()));
        var ended=begun(); ended.end(record(120,end(2)),end(2));
        assertTrue(ended.knowsEnd(record(121,end(2)),end(2)));
        assertThrows(RedoException.class,()->ended.end(record(121,end(2)),end(2)));
    }
    @Test void selectsByFullSequenceAndStillValidatesKnownEnd() throws Exception {
        var state=begun(); var wrong=end(2); wrong.fields().get(0)[4]=0;
        assertFalse(state.knowsEnd(record(120,wrong),wrong));
        assertThrows(RedoException.class,()->state.end(record(120,wrong),wrong));
        var bad=begun(); var unknown=end(8);
        assertTrue(bad.knowsEnd(record(120,unknown),unknown));
        assertThrows(RedoException.class,()->bad.end(record(120,unknown),unknown));
    }
    @Test void rejectsRecordOutsideArchiveAndBackdatedEnd() throws Exception {
        var state=begun(); assertThrows(RedoException.class,()->state.end(record(109,end(2)),end(2)));
        var other=begun(); assertThrows(RedoException.class,()->other.undo(record(200,undo()),undo()));
    }
    @Test void sourceBindingIncludesActivationResetlogsThreadAndCompatibility() throws Exception {
        var variants=List.of(
                new ArchivedRedo.Header("fixture",1536,42,12,0x13000000,1,9,22,33,200,300,3),
                new ArchivedRedo.Header("fixture",1536,42,11,0x13000000,1,9,23,33,200,300,3),
                new ArchivedRedo.Header("fixture",1536,42,11,0x13000000,1,9,22,34,200,300,3),
                new ArchivedRedo.Header("fixture",1536,42,11,0x13000000,2,9,22,33,200,300,3),
                new ArchivedRedo.Header("fixture",1536,42,11,0x15000000,1,9,22,33,200,300,3));
        for (var h:variants) { var state=begun(); assertThrows(RedoException.class,()->state.archive(h)); }
    }
    @Test void permitsSlotReuseOnlyWithDistinctVerifiedSequence() throws Exception {
        var state=begun(); state.end(record(120,end(2)),end(2));
        var nextStart=start(); nextStart.fields().get(0)[4]=0x71;
        var nextUndo=undo(); nextUndo.fields().get(0)[12]=0x71;
        var nextEnd=end(2); nextEnd.fields().get(0)[4]=0x71;
        state.begin(record(130,nextStart,nextUndo),nextStart);
        state.undo(record(130,nextUndo),nextUndo);
        var h=state.end(record(140,nextEnd),nextEnd); state.finish();
        assertEquals(1137,h.xid().sequence());
    }
    @Test void compensationPairMustHaveActiveFullIdentity() throws Exception {
        var header=UndoHeader.decode(undo());
        var patch=new HeapRowPatch.Patch(HeapRowPatch.Kind.DELETE,0x03000083,0,1,null,null,List.of());
        var pair=new HeapUndoPair(677737149,3,patch,new UndoRowPatch(header,patch,0,0,1));
        var state=begun(); state.requireActive(record(120),pair);
        state.end(record(130,end(2)),end(2));
        assertThrows(RedoException.class,()->state.requireActive(record(140),pair));
        var mixed=begun(); var other=new HeapUndoPair(1,3,pair.forward(),pair.undo());
        assertThrows(RedoException.class,()->mixed.requireActive(record(120),other));
        var sequenceState=begun();
        var xid=new HeapRowPatch.Xid(header.xid().usn(),header.xid().slot(),header.xid().sequence()+65536);
        var collision=new UndoHeader(xid,header.uba(),header.objectId(),header.dataObjectId(),header.tablespace(),header.beginsTransaction());
        var wrongSequence=new HeapUndoPair(677737149,3,patch,new UndoRowPatch(collision,patch,0,0,1));
        assertThrows(RedoException.class,()->sequenceState.requireActive(record(120),wrongSequence));
    }
    @Test void doesNotTreatSlotReuseWithMissingEndAsConcurrentIdentity() throws Exception {
        var state=begun(); var nextStart=start(); nextStart.fields().get(0)[4]=0x71;
        var nextUndo=undo(); nextUndo.fields().get(0)[12]=0x71;
        assertThrows(RedoException.class,()->state.begin(record(130,nextStart,nextUndo),nextStart));
        assertThrows(RedoException.class,()->state.end(record(140,end(2)),end(2)));
    }
}
