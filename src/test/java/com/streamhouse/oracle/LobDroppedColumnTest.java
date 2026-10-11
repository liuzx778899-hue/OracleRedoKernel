package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobDroppedColumnTest {
    @Test void raw118DscRequiresExactOwnedForwardAndInverse()throws Exception {
        var hex=HexFormat.of();
        var undo=new RedoRecords.Vector(5,1,30,11,0x024002bcL,3268541,1,3,List.of(
                hex.parseHex("9c004a151200000007000600d60200000f011b00"),
                hex.parseHex("623c0100623c010005000000000000000b010600080c010000000000bc0240020f011a009eb83100000000009fb831000000000000000000ffffffffffffffffbc0240020000000000000000"),
                hex.parseHex("040d0000000000000a0013006c140000f30f4002b301150000800080bade3100"),
                hex.parseHex("13bf000312bf0003fa120e01010000000000000400000000"),hex.parseHex("65787472612d6b657074")));
        var row=new RedoRecords.Vector(11,14,1,12,0x0300bf13L,3268419,1,3,List.of(
                hex.parseHex("110d0000484c018c07000600d6020000bc0240020f011b0000000000e0a8d1fbfc7f000070720c670000000002010dfbbddf3100008000000206008043df3100000000000000"),
                undo.fields().get(3).clone()),80994);
        var record=new RedoRecords.Record(6,16,488,5,3268541,1,677737149,List.of(undo,row));
        var heap=new FrozenHeapBaseline.ObjectRole("ORKLAB","T",80994,80994);
        var index=new FrozenHeapBaseline.ObjectRole("ORKLAB","PK",80999,80999);
        var source=new ArchivedHeapRows.Source(1,1,1,1,1,1,677737149,3);
        var snapshot=new FrozenHeapBaseline.Snapshot("0".repeat(64),source,3268300,heap,index,List.of(),Map.of(),List.of(),null);
        var drop=LobDroppedColumn.bind(record,undo,snapshot);
        assertEquals(4,drop.column());assertEquals(0,drop.slot());
        assertEquals("65787472612d6b657074",hex.formatHex(drop.before()));
        row.fields().get(0)[22]++;
        assertThrows(RedoException.class,()->LobDroppedColumn.bind(record,undo,snapshot));
        row.fields().get(0)[22]--;row.fields().get(1)[19]++;
        assertThrows(RedoException.class,()->LobDroppedColumn.bind(record,undo,snapshot));
    }
}
