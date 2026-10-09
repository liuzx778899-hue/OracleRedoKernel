package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobIndexUndoPairTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector vector(int major,int minor,int cls,long dba,long object,String... fields) {
        return new RedoRecords.Vector(major,minor,cls,12,dba,3043587,1,3,
                java.util.Arrays.stream(fields).map(this::hex).toList(),object);
    }
    RedoRecords.Record record(RedoRecords.Vector... vectors) {
        return new RedoRecords.Record(34208,16,1016,1,3043587,1,677737149,List.of(vectors));
    }
    RedoRecords.Vector row() {
        return vector(10,2,1,50365987,79336,
                "110d00000000000008002100ed020000b80940021d013600000000000000000000000000000000000000000002010d0003712e000080000002060080df702e00000000000000",
                "020006003400","0a00016ac8c4cd000000000403008679",
                "0300867900000000000000000000000000000000000000000000000000000000");
    }
    RedoRecords.Vector original() {
        return vector(5,1,32,37751224,0xffffffffL,"6800d4062200000008002100ed0200001d013682",
                "e8350100e835010005000000000000000a1621350000ed02",
                "040d4002b4702e0006001100a7020000e8014002f600260000800080df702e00",
                "030201de2286000323860003ed790000000047de","0a00016ac8c4cd000000000403008679");
    }
    RedoRecords.Vector marker() {
        return vector(5,6,32,37751224,0xffffffffL,
                "e8350100e835010005000000000000000a1621351000ed02040d4002","e806000002000000");
    }
    RedoRecords.Vector purge(long object) {
        return vector(10,3,1,50365987,object,"040d4002b4702e0006001100a7020000e8014002f600260000800080df702e00","020106003400");
    }
    @Test void bindsExactLobKeyAndMatchesSourceRoutedPurge() throws Exception {
        var row=row();var u=original();var p=IndexUndoPair.bindLob(record(row,u),row,79336);
        assertEquals(new HeapRowPatch.Xid(8,33,749),p.undo().identity().xid());
        var m=marker();var purge=purge(79336);
        assertEquals(p,IndexCompensation.matchLob(record(purge,m),m,purge,List.of(p),p.undo().identity()));
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(row,u),row));
    }
    @Test void refusesWrongScopeKeyDataAndFullUba() {
        var row=row();var u=original();
        assertThrows(RedoException.class,()->IndexUndoPair.bindLob(record(row,u),row,79338));
        for(int offset:new int[]{0,8}) {
            var changed=row();changed.fields().get(3)[offset]^=1;
            assertThrows(RedoException.class,()->IndexUndoPair.bindLob(record(changed,u),changed,79336));
        }
        u.fields().get(0)[18]++;
        assertThrows(RedoException.class,()->IndexUndoPair.bindLob(record(row,u),row,79336));
    }
    @Test void refusesWrongObjectSlotPredecessorAndDuplicatePurge() throws Exception {
        var row=row();var p=IndexUndoPair.bindLob(record(row,original()),row,79336);var m=marker();
        var other=purge(79338);
        assertThrows(RedoException.class,()->IndexCompensation.matchLob(record(other,m),m,other,List.of(p),p.undo().identity()));
        var purge=purge(79336);purge.fields().get(1)[2]++;
        assertThrows(RedoException.class,()->IndexCompensation.matchLob(record(purge,m),m,purge,List.of(p),p.undo().identity()));
        var exact=purge(79336);
        assertThrows(RedoException.class,()->IndexCompensation.matchLob(record(exact,exact,m),m,exact,List.of(p),p.undo().identity()));
        m.fields().get(0)[19]++;
        assertThrows(RedoException.class,()->IndexCompensation.matchLob(record(exact,m),m,exact,List.of(p),p.undo().identity()));
    }
}
