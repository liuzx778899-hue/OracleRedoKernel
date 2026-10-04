package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c OP 4.1 empty-entry heap cleanout only; no row values or transaction ownership. */
final class EmptyHeapCleanout {
    static void require(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=4 || vector.opcodeMinor()!=1 || vector.blockClass()!=1
                || vector.file()<=0 || (vector.dba()>>>22)!=vector.file() || vector.scn()>record.scn()
                || vector.fields().size()!=1) throw new RedoException("UNSUPPORTED_EMPTY_HEAP_CLEANOUT");
        byte[] bytes=vector.fields().get(0);
        if (bytes.length!=16 || u32(bytes,0)!=0x000d0001L || u16(bytes,8)!=0x8000
                || scn(bytes,4)!=record.scn() || u16(bytes,10)!=0 || u32(bytes,12)!=0)
            throw new RedoException("UNSUPPORTED_EMPTY_HEAP_CLEANOUT");
    }
}
