package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobTransactionStartTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector start() {return new RedoRecords.Vector(5,2,35,11,37749008,3043522,1,3,List.of(
            hex("0100d186a41000002d054002900101004a007000000000000000000000000000"),hex("bd726528")));}
    RedoRecords.Vector undo() {return new RedoRecords.Vector(5,1,36,11,37750061,3043522,1,3,List.of(
            hex("700000000a0000000a000100a410000090010100"),
            hex("e6350100e635010005000000000000000b010100080c0100000000002c0540029001020024702e000000000026702e00000000000000000000000000000000002a0540020000000000000000")));}
    RedoRecords.Record record(RedoRecords.Vector... v) {return new RedoRecords.Record(34072,16,884,13,3043522,1,677737149,List.of(v));}
    @Test void bindsFrozen112ByteBeginToFullSameRecordOwner()throws Exception {
        var s=start();var identity=TransactionStart.decode(s);
        assertEquals(new HeapRowPatch.Xid(10,1,4260),identity.bindBegin(record(s,undo())));
        s.fields().get(0)[2]^=1;assertEquals(identity,TransactionStart.decode(s));
        var u=undo();u.fields().get(0)[0]^=1;
        assertThrows(RedoException.class,()->identity.bindBegin(record(start(),u)));
    }
    @Test void rejectsUnknownSizeFlagsParentAndMismatchedUba() {
        for(int at:new int[]{14,16,18,24,28}) {
            var s=start();s.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->TransactionStart.decode(s));
        }
        var s=start();s.fields().get(0)[8]^=1;
        assertThrows(RedoException.class,()->TransactionStart.decode(s).bindBegin(record(s,undo())));
    }
}
