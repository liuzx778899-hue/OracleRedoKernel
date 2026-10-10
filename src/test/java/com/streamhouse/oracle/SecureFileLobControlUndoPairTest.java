package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileLobControlUndoPairTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector vector(int major,int minor,int cls,long dba,long object,String... fields) {
        return new RedoRecords.Vector(major,minor,cls,12,dba,3043623,1,3,
                java.util.Arrays.stream(fields).map(this::hex).toList(),object);
    }
    RedoRecords.Record record(long block,RedoRecords.Vector... vectors) {
        return new RedoRecords.Record(block,16,1016,1,3043623,1,677737149,List.of(vectors));
    }
    RedoRecords.Vector pubUndo() {
        return vector(5,1,36,37750061,0xffffffffL,"80009e17220000000a000d00a410000090011100",
                "ed350100ed35010005000000000000000d360d100000a410","878700030a000000",
                "0800000001000000000000000000000027712e000080000005000000ed3501004986000353860003010000000000000000000000000000000a000d00a4100000000000000000000000000000000000000000000000000000");
    }
    RedoRecords.Vector split(long object) {
        return vector(13,53,10,50366343,object,"0300000053860003010000000a000d00a410000027712e0000800000");
    }
    RedoRecords.Vector queueUndo(boolean fsb) {
        return vector(5,1,36,37750062,0xffffffffL,
                fsb ? "8800da1e220000000a000d00a41000009001038b" : "7c003c13220000000a001e00a910000090011a00",
                fsb ? "ed350100ed35010005000000000000000d340d020000a410" : "ed350100ed35010005000000000000000d321e190000a910",
                fsb ? "0c87000306000000098700034c8600030800000049860003" : "4b8600034986000308000000",
                fsb ? "0500000001000000010000000000000000000000000000000000000000000000000000000000000055860003010000000000000000000000010000000a000000020000004e2d65870014065d617b0000"
                        : "02000000010000000100000000000000000000000000000000000000000000000000000000000000548600030100000000000000000000000100000000000000000000000000000048a14df338710000");
    }
    RedoRecords.Vector queue(boolean fsb) {
        return vector(13,fsb ? 51 : 49,fsb ? 6 : 8,fsb ? 50366220 : 50366027,79341,
                fsb ? "040000000100000001000000010000005586000300000000010000000000000000000000000000000000000000000000000000000a000d00a410000000000000000000000000000000000000617b0000050421000b00ffff0001400253712e00000000000200ffff030000000000000008001400100004004e2d65874e2d65871c00000019030000"
                        : "010000000100000001000000020000005486000300000000010000000000000000000000000000000000000000000000000000000a001e00a9100000ffc4c86a89712e0000800000000000000a001100aa10000072702e000000000074702e000000000013240000ffffffffffffffff2b0540020000000000000000040d000116030a000a000d00");
    }
    @Test void bindsPubAcrossRecordsByExplicitOwnerScnBlockAndExtent() throws Exception {
        var u=pubUndo();var f=split(79341);
        var p=SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,f),f,79341);
        assertEquals(54,p.undoMinor());assertEquals(50366035,p.chunk());assertEquals(1,p.length());
        assertEquals(new HeapRowPatch.Xid(10,13,4260),p.owner().xid());
        byte[] b=p.inverse();b[0]++;
        assertEquals(8,p.inverse()[0]);
    }
    @Test void bindsBothSameRecordChunkQueueProfiles() throws Exception {
        for(boolean fsb:new boolean[]{false,true}) {
            var u=queueUndo(fsb);var f=queue(fsb);var r=record(11,u,f);
            var p=SecureFileLobControlUndoPair.bind(r,u,r,f,79341);
            assertEquals(fsb ? 52 : 50,p.undoMinor());assertEquals(50366025,p.segment());
            assertEquals(fsb ? 50366037 : 50366036,p.chunk());
        }
    }
    @Test void observedPubReuseRequiresPhysicalOrderOwnerAndExtent()throws Exception {
        var u=pubUndo();u.fields().get(3)[40]=2;
        byte[] bytes=new byte[68];var b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,4).putInt(4,1).putInt(12,1).putInt(16,50366035).putInt(20,2).putInt(64,2);
        System.arraycopy(u.fields().get(3),56,bytes,36,8);System.arraycopy(u.fields().get(3),16,bytes,44,8);
        var f=new RedoRecords.Vector(13,53,10,12,50366343,3043623,1,3,List.of(bytes),79341);
        var r=record(11,f,u);var p=SecureFileLobControlUndoPair.bind(r,u,r,f,79341);
        assertEquals(2,p.length());
        assertEquals(2,SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,f),f,79341).length());
        var reversed=record(11,u,f);
        assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(reversed,u,reversed,f,79341));
        for(int offset:new int[]{4,12,16,20,24,36,40,44,52,64}) {
            bytes[offset]^=1;
            assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(r,u,r,f,79341));
            bytes[offset]^=1;
        }
    }
    @Test void pubRollbackMatchesOwnedInverseAndRejectsChanges()throws Exception {
        var u=pubUndo();var f=split(79341);var p=SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,f),f,79341);
        var body=java.util.Arrays.copyOf(p.inverse(),140);
        java.nio.ByteBuffer.wrap(body).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(16,3043624);
        var row=new RedoRecords.Vector(13,53,10,12,p.block(),3043625,1,3,List.of(body),79341);
        var header=java.util.Arrays.copyOf(u.fields().get(1),28);header[20]=0x10;
        java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(24,(int)p.block());
        var marker=new RedoRecords.Vector(5,6,24,12,p.owner().uba().dba(),3043625,1,3,List.of(header,new byte[8]),0xffffffffL);
        var r=new RedoRecords.Record(12,16,1016,1,3043625,1,677737149,List.of(row,marker));
        assertEquals(p,SecureFileLobControlUndoPair.matchPubCompensation(r,marker,row,List.of(p),p.owner()));
        assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.matchPubCompensation(r,marker,row,List.of(p,p),p.owner()));
        for(int offset:new int[]{0,24,36,56,87,88,139,21,22}) {
            body[offset]^=1;
            assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.matchPubCompensation(r,marker,row,List.of(p),p.owner()));
            body[offset]^=1;
        }
        assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.matchPubCompensation(r,marker,row,List.of(),p.owner()));
    }
    @Test void refusesPubObjectOrderIdentityExtentAndScnMismatch() {
        var u=pubUndo();var other=split(79343);
        assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,other),other,79341));
        var exact=split(79341);
        assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(record(10,u),u,record(11,exact),exact,79341));
        for(int offset:new int[]{4,8,12,16,20}) {
            var f=split(79341);f.fields().get(0)[offset]^=1;
            assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,f),f,79341));
        }
    }
    @Test void refusesQueueForeignRecordXidExtentAndDuplicates() {
        for(boolean fsb:new boolean[]{false,true}) {
            var u=queueUndo(fsb);var f=queue(fsb);
            assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(record(11,u),u,record(10,f),f,79341));
            var repeated=record(11,u,f,f);
            assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(repeated,u,repeated,f,79341));
            for(int offset:new int[]{0,12,16,24,52,56}) {
                var changed=queue(fsb);changed.fields().get(0)[offset]^=1;var r=record(11,u,changed);
                assertThrows(RedoException.class,()->SecureFileLobControlUndoPair.bind(r,u,r,changed,79341));
            }
        }
    }
}
