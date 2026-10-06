package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionStartTest {
    RedoRecords.Vector splitStart() {
        return new RedoRecords.Vector(5,2,33,11,0x02400100,2567426,1,3,List.of(
                hex("09000000940200004f2f4002e60001004a048800983f00000000000000000000"),hex("bd726528")));
    }
    RedoRecords.Vector splitUndo() {
        return new RedoRecords.Vector(5,1,34,11,0x02402f4f,2567426,1,3,List.of(
                hex("880000000a0000000900090094020000e600014d"),
                hex("320000003200000000000000000000000a150900080c0100000000004e2f4002e60015003b212700000000006d21270000000000777b0000ffffffffffffffff4a2f40020000000000000000")));
    }
    @Test void bindsObservedSplitBeginWithoutTreatingHistoricalControlAsCurrentIdentity() throws Exception {
        var h=TransactionStart.decode(splitStart());
        assertEquals(0x44a,h.flags());
        assertEquals(new HeapRowPatch.Xid(9,9,660),h.bindBegin(record(677737149,splitStart(),splitUndo())));
        // Current UBA/slot/sequence/container must still match the same-record begin undo.
        for(int at:new int[]{10,12,16,18}) {
            var u=splitUndo();u.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->h.bindBegin(record(677737149,splitStart(),u)));
        }
        assertThrows(RedoException.class,()->h.bindBegin(record(1,splitStart(),splitUndo())));
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,splitStart(),splitUndo(),splitUndo())));
    }
    @Test void refusesUnobservedSplitBeginControlWordSizeFlagsAndParent() {
        for(int at:new int[]{2,14,15,16,17,18,19,20,21,22,23,24,28}) {
            var v=splitStart();v.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->TransactionStart.decode(v));
        }
    }
    RedoRecords.Vector firstRecordStart() {
        return new RedoRecords.Vector(5,2,35,11,0x02400110,2362414,1,3,List.of(
                hex("110000008e060000142b4002080101004a00b000000000000000000000000000"),hex("bd726528")));
    }
    @Test void bindsObservedFirstRecordStartToActualBeginIdentity() throws Exception {
        var h=TransactionStart.decode(firstRecordStart());
        byte[] second=hex("711d0100741d010005000000000000000b011100080c010000000000132b4002080121004d0b2400000000004f0b24000000000000000000410c240000000000122b40020000000000000000");
        var u=new RedoRecords.Vector(5,1,36,11,0x02402b14,2362444,1,3,List.of(
                hex("b00000000a0000010a0011008e06000008010100"),second));
        assertEquals(new HeapRowPatch.Xid(10,17,1678),h.bindBegin(record(677737149,u,firstRecordStart())));
        u.fields().get(0)[18]=2;
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,u,firstRecordStart())));
    }
    @Test void refusesUnobservedFirstRecordLayoutAndParent() {
        for (int at:new int[]{2,14,15,18,20,24,28}) {
            var v=firstRecordStart();v.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->TransactionStart.decode(v));
        }
    }
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector start() {
        return new RedoRecords.Vector(5,2,35,11,0x02400110,2149086,1,3,List.of(
                hex("2000000070040000cb2c4002f200290052008800000000000000000000000000"),hex("bd726528")));
    }
    RedoRecords.Vector undo(int slot,int record,long container) {
        byte[] first=hex("88000000120000000a00200070040000f2002900");
        first[10]=(byte)slot; first[18]=(byte)record;
        byte[] second=new byte[76]; second[20]=8; second[21]=12;
        return new RedoRecords.Vector(5,1,36,11,0x02402ccb,2149086,1,container,List.of(first,second));
    }
    RedoRecords.Record record(long uid,RedoRecords.Vector... vectors) {
        return new RedoRecords.Record(3,16,768,13,2149088,1,uid,List.of(vectors));
    }
    @Test void matchesExactBeginUbaRegardlessOfVectorOrder() throws Exception {
        var h=TransactionStart.decode(start());
        assertEquals(new HeapRowPatch.Xid(10,32,1136),h.bindBegin(record(677737149,undo(32,41,3),start())));
        assertEquals(new HeapRowPatch.Xid(10,32,1136),h.bindBegin(record(677737149,start(),undo(32,41,3))));
        assertEquals(0x02400110,h.segmentDba());
    }
    @Test void refusesAdjacentWrongUbaAndMissingBegin() throws Exception {
        var h=TransactionStart.decode(start());
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,start(),undo(32,40,3))));
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,start())));
    }
    @Test void refusesSlotMismatchAndDuplicateUba() throws Exception {
        var h=TransactionStart.decode(start());
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,undo(31,41,3))));
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,undo(32,41,3),undo(32,41,3))));
    }
    @Test void separatesRecordUidAndVectorContainer() throws Exception {
        var h=TransactionStart.decode(start());
        assertThrows(RedoException.class,()->h.bindBegin(record(1,undo(32,41,3))));
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,undo(32,41,4))));
    }
    @Test void refusesParentTransactionAndUnknownFlags() {
        var v=start(); var fields=v.fields(); fields.get(0)[24]=1;
        assertThrows(RedoException.class,()->TransactionStart.decode(v));
        var other=start(); other.fields().get(0)[16]=0;
        assertThrows(RedoException.class,()->TransactionStart.decode(other));
    }
    @Test void refusesReusedSlotWithWrongSequenceAndNonBegin() throws Exception {
        var h=TransactionStart.decode(start()); var v=undo(32,41,3);
        v.fields().get(0)[12]=0;
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,v)));
        var continuation=undo(32,41,3);
        var second=new byte[24];
        var notBegin=new RedoRecords.Vector(5,1,36,11,0x02402ccb,2149086,1,3,
                List.of(continuation.fields().get(0),second));
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,notBegin)));
    }
}
