package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileLobUndoPairTest {
    byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    RedoRecords.Vector forward(byte[] ktb) {
        return new RedoRecords.Vector(26,2,1,12,50366035,3043715,1,3,List.of(ktb,
                hex("002009007c1f800053860003"),hex("010000000100000034f6cb000000000000"),hex("0600640004000400deadbeef")));
    }
    RedoRecords.Vector original() {
        return new RedoRecords.Vector(5,1,36,11,37750062,3043715,1,3,List.of(
                hex("70003017220000000a001100aa10000090011200"),hex("ed350100ed35010005000000000000001a0111110000aa10"),
                hex("040d00000d0000000a000d00a41000002d05400290011e000080000068712e00"),
                hex("012009007c1f800053860003"),hex("010000000100000034f6cb000000000000"),hex("060064000400040000ff80c3")));
    }
    RedoRecords.Record record(List<RedoRecords.Vector> vectors) { return new RedoRecords.Record(35548,268,1624,9,3043715,2,677737149,vectors); }
    @Test void bindsCurrentXidAndUbaWithoutTreatingPriorItlAsCurrent()throws Exception {
        var forward=forward(hex("010d0000060000000a001100aa1000002e05400290011200"));var pair=SecureFileLobUndoPair.bind(record(List.of(forward,original())),forward,79341);
        assertEquals(new HeapRowPatch.Xid(10,17,4266),pair.owner().xid());assertEquals(new HeapRowPatch.Uba(37750062,400,18),pair.owner().uba());
        assertEquals(-1,pair.inverseHighWater());assertArrayEquals(hex("00ff80c3"),pair.inverse().payload());
        assertTrue(pair.inverse().undo());assertEquals("0000000100000034f6cb",pair.forward().lobId());
    }
    @Test void refusesForeignOwnerMissingOrDuplicateOriginal() {
        var forward=forward(hex("010d0000060000000a001100aa1000002e05400290011200"));var original=original();
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.bind(record(List.of(forward,original)),forward,79343));
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.bind(record(List.of(forward)),forward,79341));
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.bind(record(List.of(forward,original,original)),forward,79341));
        byte[] wrong=hex("010d0000060000000a001100ab1000002e05400290011200");var mixed=forward(wrong);
        assertThrows(RedoException.class,()->SecureFileLobUndoPair.bind(record(List.of(mixed,original)),mixed,79341));
    }
    @Test void bindsAppendUndoHighWaterAndRejectsAnUnprovenGap()throws Exception {
        byte[] previous=hex("0400000027712e00008028000000000100000034f6cb000000000000000000000000000000000000000000000000000000000000a00f0000");
        var original=new RedoRecords.Vector(5,1,36,11,37750061,3043659,1,3,List.of(
                hex("78000606220000000a000d00a410000090011500"),hex("ed350100ed35010005000000000000001a010d140000a410"),
                hex("020d00000c0000000000000000000000"),hex("012008007c1f800053860003"),previous));
        byte[] bytes=new byte[2008];System.arraycopy(hex("0600a00fd007d007"),0,bytes,0,8);
        var forward=new RedoRecords.Vector(26,2,1,12,50366035,3043659,1,3,List.of(
                hex("020d0000000000002d05400290011500"),hex("002009007c1f800053860003"),
                hex("0400000027712e00008028000000000100000034f6cb00000000000000000000000000000000000000000000000000000000000070170000"),bytes));
        var record=record(List.of(original,forward));var pair=SecureFileLobUndoPair.bind(record,forward,79341);
        assertEquals(4000,pair.inverseHighWater());assertNull(pair.inverse());assertEquals(new HeapRowPatch.Xid(10,13,4260),pair.owner().xid());
        previous[52]^=1;assertThrows(RedoException.class,()->SecureFileLobUndoPair.bind(record,forward,79341));
    }
}
