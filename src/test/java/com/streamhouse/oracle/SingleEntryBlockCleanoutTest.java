package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingleEntryBlockCleanoutTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Record record(long scn) {return new RedoRecords.Record(2,16,132,5,scn,1,677737149,List.of());}
    RedoRecords.Vector vector(byte[] b,long dba,long scn) {return new RedoRecords.Vector(4,1,1,12,dba,scn,1,3,List.of(b));}
    @Test void actualLeafAndHeapSingleEntryProfilesBindHeaderAndPriorScn() {
        assertDoesNotThrow(()->SingleEntryBlockCleanout.require(record(2349205),vector(hex("02010d0095d823000080000002060080eed42300000000000000"),0x0300019b,2348270)));
        assertDoesNotThrow(()->SingleEntryBlockCleanout.require(record(2349208),vector(hex("01010d0098d823000080000002060080eed42300000000000000"),0x03000193,2349202)));
    }
    @Test void unknownItlFlagsCountSpareLengthAndScnRefuse() {
        byte[] original=hex("02010d0095d823000080000002060080eed42300000000000000");
        for(int at:List.of(0,1,2,3,4,8,9,10,12,13,14,15,20,23)) {
            byte[] b=original.clone();b[at]^=4;
            assertThrows(RedoException.class,()->SingleEntryBlockCleanout.require(record(2349205),vector(b,0x0300019b,2348270)));
        }
        assertThrows(RedoException.class,()->SingleEntryBlockCleanout.require(record(2349205),vector(original,0x0300019b,2348269)));
        assertThrows(RedoException.class,()->SingleEntryBlockCleanout.require(record(2349205),vector(java.util.Arrays.copyOf(original,27),0x0300019b,2348270)));
    }
}
