package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileLobCompensationTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Record record(RedoRecords.Vector... v) {return new RedoRecords.Record(35566,376,248,1,3043737,1,677737149,List.of(v));}
    RedoRecords.Vector rollback() {return new RedoRecords.Vector(26,2,1,12,50366035,3043737,1,3,List.of(
            hex("040d00000d0000000a001100aa1000002e054002900112000080000085712e00"),hex("012009007c1f800053860003"),
            hex("010000000100000034f6cb000000000000"),hex("063500000400040000ff80c3")));}
    RedoRecords.Vector marker() {return new RedoRecords.Vector(5,6,36,11,37750062,3043737,1,3,List.of(
            hex("ed350100ed35010005000000000000001a011a1b1000a610040d0000"),hex("0c12000002000000")));}
    SecureFileLobUndoPair pair()throws Exception {
        var f=new RedoRecords.Vector(26,2,1,12,50366035,3043734,1,3,List.of(
                hex("010d0000060000000a001a00a61000002e05400290011c00"),hex("002009007c1f800053860003"),
                hex("010000000100000034f6cb000000000000"),hex("0600000004000400ffffffff")));
        var r=rollback();var u=new RedoRecords.Vector(5,1,36,11,37750062,3043734,1,3,List.of(
                hex("70003412220000000a001a00a610000090011c00"),hex("ed350100ed35010005000000000000001a011a1b0000a610"),
                r.fields().get(0),r.fields().get(1),r.fields().get(2),r.fields().get(3)));
        return SecureFileLobUndoPair.bind(record(f,u),f,79341);
    }
    @Test void matchesFrozenPartialRollbackToCurrentOwnerDespiteHistoricalItl()throws Exception {
        var p=pair();var r=rollback();var m=marker();
        assertEquals(p,SecureFileLobUndoPair.matchCompensation(record(r,m),m,r,List.of(p),p.owner()));
        assertEquals(new HeapRowPatch.Xid(10,26,4262),p.owner().xid());
        assertArrayEquals(hex("00ff80c3"),p.inverse().payload());assertEquals(0x35,p.inverse().fillFlags());
    }
    @Test void rejectsPayloadFlagsAndCrossContainerMixing()throws Exception {
        var p=pair();var r=rollback();var m=marker();r.fields().get(3)[8]^=1;
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(record(r,m),m,r,List.of(p),p.owner()));
        var flags=rollback();flags.fields().get(3)[1]^=1;
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(record(flags,m),m,flags,List.of(p),p.owner()));
        var good=rollback();var foreign=new RedoRecords.Record(35566,376,248,1,3043737,1,1,List.of(good,m));
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(foreign,m,good,List.of(p),p.owner()));
    }
    @Test void rejectsDuplicateRowsAndOriginalsAndWrongFullUba()throws Exception {
        var p=pair();var r=rollback();var m=marker();
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(record(r,r,m),m,r,List.of(p),p.owner()));
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(record(r,m),m,r,List.of(p,p),p.owner()));
        var h=p.owner();var wrong=new UndoHeader(h.xid(),new HeapRowPatch.Uba(h.uba().dba(),h.uba().sequence()+1,h.uba().record()),h.objectId(),h.dataObjectId(),h.tablespace(),false);
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.matchCompensation(record(r,m),m,r,List.of(p),wrong));
    }
}
