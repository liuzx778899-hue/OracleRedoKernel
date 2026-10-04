package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmptyHeapCleanoutTest {
    final byte[] observed=HexFormat.of().parseHex("01000d00d5c923000080000000000000");
    RedoRecords.Record record(long scn) {return new RedoRecords.Record(2,16,120,5,scn,1,677737149,List.of());}
    RedoRecords.Vector vector(byte[] bytes,int blockClass,int file,long scn) {
        return new RedoRecords.Vector(4,1,blockClass,file,0x03000193,scn,1,3,List.of(bytes));
    }
    @Test void observedEmptyEntryCleanoutBindsItsScnWithoutRowOrUndoOperations() {
        assertDoesNotThrow(()->EmptyHeapCleanout.require(record(2345429),vector(observed,1,12,2345424)));
    }
    @Test void unknownEntryFlagsSpareExtentAndScnOrHeaderRefuse() {
        for (int offset:List.of(0,1,2,3,4,8,9,10,11,12,15)) {
            byte[] bytes=observed.clone();bytes[offset]^=1;
            assertThrows(RedoException.class,()->EmptyHeapCleanout.require(record(2345429),vector(bytes,1,12,2345424)));
        }
        for (int length:List.of(15,17)) assertThrows(RedoException.class,()->EmptyHeapCleanout.require(record(2345429),vector(java.util.Arrays.copyOf(observed,length),1,12,2345424)));
        assertThrows(RedoException.class,()->EmptyHeapCleanout.require(record(2345429),vector(observed,2,12,2345424)));
        assertThrows(RedoException.class,()->EmptyHeapCleanout.require(record(2345429),vector(observed,1,11,2345424)));
        assertThrows(RedoException.class,()->EmptyHeapCleanout.require(record(2345429),vector(observed,1,12,2345430)));
    }
}
