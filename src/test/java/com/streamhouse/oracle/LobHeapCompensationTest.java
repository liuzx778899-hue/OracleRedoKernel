package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobHeapCompensationTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Record record(RedoRecords.Vector... v) {return new RedoRecords.Record(25077,16,168,1,3039854,4,677737149,List.of(v));}
    HeapUndoPair pair()throws Exception {
        var row=new RedoRecords.Vector(11,4,1,12,50365967,3039848,1,3,List.of(
                hex("110d0000000000000a0013009a1000001905400290011b00000000000000000000000000000000000000000002010d006d622e000080000001060080e5542e00000000000000"),
                hex("0f8600030a860003fa1204010200400000000002")));
        var undo=new RedoRecords.Vector(5,1,36,11,37750041,3039848,1,3,List.of(
                hex("70006212120000000a0013009a10000090011b00"),
                hex("e6350100e635010005000000000000000b011300080c01000000000019054002900119000b622e00000000000d622e0000000000000000006e622e0000000000180540020000000000000000"),
                hex("030d000000000000"),hex("0f8600030a860003fa1204010200400000000000")));
        return HeapUndoPair.bindLob(new RedoRecords.Record(25069,156,3116,9,3039854,1,677737149,List.of(row,undo)),row,3);
    }
    RedoRecords.Vector row() {return new RedoRecords.Vector(11,4,1,12,50365967,3039854,1,3,List.of(
            hex("030d000000000000"),hex("0f8600030a860003fa1204020200400000000000")));}
    RedoRecords.Vector marker() {return new RedoRecords.Vector(5,11,35,11,37749008,3039854,1,3,List.of(
            hex("e6350100e635010005000000000000000b011300180c010000000000"),hex("3812f2e702000000")));}
    @Test void matchesFrozenBeginUnlockToFullRoutedOriginal()throws Exception {
        var p=pair();var m=marker();var r=row();
        assertEquals(p,HeapCompensation.matchLob(record(r,m),m,r,List.of(p),p.undo().identity()));
        assertEquals(new HeapRowPatch.Xid(10,19,4250),p.undo().identity().xid());
        assertThrows(RedoException.class,()->HeapCompensation.match(record(r,m),m,r,List.of(p),3));
    }
    @Test void rejectsWrongUnlockFullXidAndOriginalUba()throws Exception {
        var p=pair();var h=p.undo().identity();var m=marker();var r=row();r.fields().get(1)[19]=2;
        assertThrows(RedoException.class,()->HeapCompensation.matchLob(record(r,m),m,r,List.of(p),h));
        var good=row();var reused=new UndoHeader(new HeapRowPatch.Xid(10,19,4250+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),true);
        assertThrows(RedoException.class,()->HeapCompensation.matchLob(record(good,m),m,good,List.of(p),reused));
        var wrong=new UndoHeader(h.xid(),new HeapRowPatch.Uba(h.uba().dba(),h.uba().sequence(),h.uba().record()+1),h.objectId(),h.dataObjectId(),h.tablespace(),true);
        assertEquals("LOB_COMPENSATION_ROUTED_OWNER_MISMATCH",assertThrows(RedoException.class,()->HeapCompensation.matchLob(record(good,m),m,good,List.of(p),wrong)).getMessage());
    }
    @Test void rejectsDuplicatePhysicalRowsOrOriginals()throws Exception {
        var p=pair();var m=marker();var r=row();
        assertThrows(RedoException.class,()->HeapCompensation.matchLob(record(r,r,m),m,r,List.of(p),p.undo().identity()));
        assertThrows(RedoException.class,()->HeapCompensation.matchLob(record(r,m),m,r,List.of(p,p),p.undo().identity()));
    }
}
