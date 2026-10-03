package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeginCompensationTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector vector(int major,int minor,long dba,String... fields) {
        return new RedoRecords.Vector(major,minor,21,11,dba,2148795,1,3,
                java.util.Arrays.stream(fields).map(this::hex).toList());
    }
    RedoRecords.Vector start() { return vector(5,2,0x024000a0,
            "04008c009d0200002d2040028f012f0052008800000000000000000000000000","bd726528"); }
    RedoRecords.Vector undo() { return vector(5,1,0x0240202d,
            "8800aa0a12000000030004009d0200008f012f00",
            "401d0100401d010005000000000000000b010400080c0100000000002d2040028f012e009eab2000000000001cac200000000000401d0100bbc9200000000000292040020000000000000000",
            "040d4002f20017000a0003006e040000be2c4002f200180000800080b6c92000",
            "8300000382000003fa120301010000000300002e"); }
    RedoRecords.Vector forward() {
        byte[] kdo=hex(HeapRowPatchTest.IRP); kdo[42]=3;
        return vector(11,2,0x03000083,"010d000000000000030004009d0200002d2040028f012f00",
                HexFormat.of().formatHex(kdo),"c107","726f6c6c6261636b","c107","787e0a04010101");
    }
    RedoRecords.Vector marker() { return vector(5,11,0x024000a0,
            "401d0100401d010005000000000000000b010400180c010000000000","900a200002000000"); }
    RedoRecords.Vector rollback() { return vector(11,3,0x03000083,
            "040d4002f20017000a0003006e040000be2c4002f200180000800080b6c92000",
            "8300000382000003fa120302010000000300002e"); }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(30714,64,1000,1,2148795,1,677737149,List.of(v)); }
    TransactionLifecycle state(boolean begin) throws Exception {
        var state=new TransactionLifecycle();
        state.archive(new ArchivedRedo.Header("fixture",1536,42,11,0x13000000,1,8,22,33,2146496,2149085,3));
        if (begin) { var start=start(); state.begin(record(start,undo()),start); }
        return state;
    }
    HeapUndoPair pair() throws Exception { var f=forward(); return HeapUndoPair.bind(record(start(),undo(),f),f,4); }
    @Test void usesActiveSegmentSlotSequenceAndIgnoresHistoricalItl() throws Exception {
        var marker=marker(); var row=rollback(); var state=state(true);
        var pair=HeapCompensation.matchBegin(record(row,marker),marker,row,List.of(pair()),4,state);
        assertEquals(new HeapRowPatch.Xid(3,4,669),pair.undo().identity().xid());
        assertEquals(1,UndoCompensation.decode(marker).wrap());
        assertNull(new HeapRowPatch().decode(row,4).xid());
    }
    @Test void missingBeginIdentityRefusesAndLocksLifecycle() throws Exception {
        var marker=marker(); var row=rollback(); var state=state(false); var pair=pair();
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,marker),marker,row,List.of(pair),4,state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void segmentAndContainerMustMatchVerifiedAssignment() throws Exception {
        var marker=marker(); var row=rollback(); var pair=pair();
        var wrong=new RedoRecords.Vector(5,11,21,11,0x024000a1,2148795,1,3,marker.fields());
        var state=state(true);
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,wrong),wrong,row,List.of(pair),4,state));
        var other=new RedoRecords.Vector(5,11,21,11,0x024000a0,2148795,1,4,marker.fields());
        var otherState=state(true);
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,other),other,row,List.of(pair),4,otherState));
    }
    @Test void closedIdentityCannotAuthorizeLaterCompensation() throws Exception {
        var state=state(true); var end=vector(5,4,0x024000a0,"040000009d020000000000000900000004000000","ea44c16a");
        state.end(record(end),end); var marker=marker(); var row=rollback(); var pair=pair();
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,marker),marker,row,List.of(pair),4,state));
    }
    @Test void wrapOneCannotAuthorizeDifferentFullSequence() throws Exception {
        var pair=pair(); var u=pair.undo(); var h=u.identity();
        var old=new UndoHeader(new HeapRowPatch.Xid(3,4,668),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),true);
        var wrong=new HeapUndoPair(pair.containerUid(),pair.containerId(),pair.forward(),new UndoRowPatch(old,u.inverse(),0,0,1));
        var state=state(true); var marker=marker(); var row=rollback();
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,marker),marker,row,List.of(wrong),4,state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void duplicateOriginalAndWrongRowSlotRefuse() throws Exception {
        var pair=pair(); var state=state(true); var marker=marker(); var row=rollback();
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(row,marker),marker,row,List.of(pair,pair),4,state));
        assertThrows(RedoException.class,state::finish);
        var another=state(true); var wrongRow=rollback(); wrongRow.fields().get(1)[16]=2;
        assertThrows(RedoException.class,()->HeapCompensation.matchBegin(record(wrongRow,marker),marker,wrongRow,List.of(pair),4,another));
    }
}
