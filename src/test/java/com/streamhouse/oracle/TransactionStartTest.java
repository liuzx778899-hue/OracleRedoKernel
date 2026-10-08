package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionStartTest {
    List<RedoRecords.Vector> colDeleteBegin()throws Exception {
        String text;try(var in=getClass().getResourceAsStream("/col-delete-begin-profile.txt")){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII);}
        var vectors=new java.util.ArrayList<RedoRecords.Vector>();
        for(var line:text.lines().toList()) {
            var f=line.split("\\|",-1);var op=f[0].split("\\.");var fields=new java.util.ArrayList<byte[]>();
            for(int i=2;i<f.length;i++)fields.add(hex(f[i]));
            vectors.add(new RedoRecords.Vector(Integer.parseInt(op[0]),Integer.parseInt(op[1]),34,11,Long.parseLong(f[1]),2567457,1,3,fields));
        }
        return vectors;
    }
    @Test void bindsActualFirstRecordColDeleteAndRefusesSizeRoleAndBitmapPoisons()throws Exception {
        var v=colDeleteBegin();var s=TransactionStart.decode(v.get(0));
        assertEquals(new HeapRowPatch.Xid(10,31,2348),s.bindBegin(record(677737149,v.get(0),v.get(1))));
        for(int at:new int[]{0,10,12,18}) {
            var changed=colDeleteBegin();changed.get(1).fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->s.bindBegin(record(677737149,changed.get(0),changed.get(1))));
        }
        for(int at:new int[]{0,4,8,16}) {
            var changed=colDeleteBegin();changed.get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->s.bindBegin(record(677737149,changed.get(0),changed.get(1))));
        }
        var changed=colDeleteBegin();changed.get(1).fields().get(3)[46]^=1;
        assertThrows(RedoException.class,()->s.bindBegin(record(677737149,changed.get(0),changed.get(1))));
    }
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
    @Test void refusesUnobservedSplitBeginSizeFlagsAndParent() {
        for(int at:new int[]{2,14,15,16,17,18,19,24,28}) {
            var v=splitStart();v.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->TransactionStart.decode(v));
        }
    }
    @Test void actual148ByteBeginBindsSizeWhileOpaqueFbiDoesNotSupplyIdentity()throws Exception {
        var s=new RedoRecords.Vector(5,2,35,11,37749008,2910636,1,3,List.of(
                hex("09000000da0d000017014002660101004a049400c0230b790000000000000000"),hex("bd726528")));
        var u=new RedoRecords.Vector(5,1,36,11,37749015,2910636,1,3,List.of(
                hex("940000000a0000000a000900da0d000066010181"),
                hex("030000000300000000000000000000000a160900080c01000000000016014002660125000c692c00000000000e692c000000000000000000ffffffffffffffff690340020000000000000000")));
        var h=TransactionStart.decode(s);assertEquals(new HeapRowPatch.Xid(10,9,3546),h.bindBegin(record(677737149,s,u)));
        for(int at=20;at<24;at++)s.fields().get(0)[at]^=1;
        assertEquals(h,TransactionStart.decode(s));assertEquals(new HeapRowPatch.Xid(10,9,3546),h.bindBegin(record(677737149,s,u)));
        u.fields().get(0)[0]^=1;
        assertThrows(RedoException.class,()->h.bindBegin(record(677737149,s,u)));
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
    @Test void native93FirstRecordsBindExactUndoDespiteHighPadding()throws Exception {
        String[][] profiles={
                {"01000000620e0000270340026f0101004a008800000001000000000000000000","880000000a0000000a000100620e00006f010100",
                 "793201007932010005000000000000000b010100080c010000000000260340026f012c0070e42c000000000075e42c00000000001032010036e52c000000000000000000000000008d000000","37749008"},
                {"17000000d3020000fb004002190101004a00a400000000000000000000000000","a40000000a00000008001700d302000019010100",
                 "e6310100e631010005000000000000000b011700080c010000000000fa0040021901370045ed2c0000000000cfed2c000000000000000000be082d0000000000f80040020000000000000000","37748976"},
                {"0e00d1868a0e0000660340026f0101004a008800010000000000000000000000","880000000a0000000a000e008a0e00006f010100",
                 "793201007932010005000000000000000b010e00080c010000000000640340026f01040043082d000000000045082d00000000000000000045092d000000000063034002000000008d000000","37749008"}};
        for(var profile:profiles) {
            byte[] bytes=hex(profile[0]);long uba=com.streamhouse.oracle.RedoBytes.u32(bytes,8);
            var s=new RedoRecords.Vector(5,2,35,11,Long.parseLong(profile[3]),2951360,1,3,List.of(bytes,hex("bd726528")));
            var u=new RedoRecords.Vector(5,1,36,11,uba,2951360,1,3,List.of(hex(profile[1]),hex(profile[2])));
            var start=TransactionStart.decode(s);assertEquals(UndoHeader.decode(u).xid(),start.bindBegin(record(677737149,s,u)));
            u.fields().get(0)[0]^=4;
            assertThrows(RedoException.class,()->start.bindBegin(record(677737149,s,u)));
            s.fields().get(0)[24]=1;assertThrows(RedoException.class,()->TransactionStart.decode(s));
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
