package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionarySpaceHwmPairTest {
    @Test void raw120ExtentExpansionHasOneOwnedSpacePairAndNoRowValues()throws Exception {
        var hex=HexFormat.of();var role=new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2);
        var undo=new RedoRecords.Vector(5,1,36,11,0x0240101bL,3272978,4,3,List.of(
                hex.parseHex("5c003c1d220000000a00050082140000b40108fd"),
                hex.parseHex("040000000200000000000000000000000e05050700008214"),
                hex.parseHex("900040000400000090004000030000000000000000000000"),
                hex.parseHex("071315001e0000008000000080000000008e4000000000001e00000000000000ff070000")));
        var row=new RedoRecords.Vector(14,4,4,9,0x00400090L,3272978,3,3,List.of(
                hex.parseHex("070000001f000000000000008000000080914000000000001f00000000000000ff070000")),2);
        var record=new RedoRecords.Record(9862,180,244,1,3272978,12,677737149,List.of(undo,row));
        DictionarySpaceHwmPair.require(record,undo,role);
        row.fields().get(0)[4]--;
        assertThrows(RedoException.class,()->DictionarySpaceHwmPair.require(record,undo,role));
        row.fields().get(0)[4]++;undo.fields().get(2)[8]++;
        assertThrows(RedoException.class,()->DictionarySpaceHwmPair.require(record,undo,role));
        assertThrows(RedoException.class,()->DictionarySpaceHwmPair.require(record,undo,new FrozenHeapBaseline.ObjectRole("SYS","COL$",21,2)));
    }
}
