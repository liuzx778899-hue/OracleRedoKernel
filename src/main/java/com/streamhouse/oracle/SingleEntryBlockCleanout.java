package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c heap/leaf OP 4.1: empty profile or one ITL1/2 entry with flags6; no row/undo operation. */
final class SingleEntryBlockCleanout {
    static void require(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        if (vector.fields().size()==1 && vector.fields().get(0).length==16) {
            EmptyHeapCleanout.require(record,vector);return;
        }
        if (vector.opcodeMajor()!=4 || vector.opcodeMinor()!=1 || vector.blockClass()!=1 || vector.file()<=0
                || (vector.dba()>>>22)!=vector.file() || vector.scn()>record.scn() || vector.fields().size()!=1)
            throw new RedoException("UNSUPPORTED_SINGLE_ENTRY_BLOCK_CLEANOUT");
        byte[] b=vector.fields().get(0);
        if (b.length!=26 || ((b[0]&255)!=1 && (b[0]&255)!=2) || (b[1]&255)!=1 || u16(b,2)!=0x000d
                || u16(b,8)!=0x8000 || u16(b,10)!=0 || scn(b,4)!=record.scn()
                || (u16(b,12)!=0x0601 && u16(b,12)!=0x0602) || u16(b,14)!=0x8000 || u32(b,16)>vector.scn() || u16(b,20)!=0 || u32(b,22)!=0)
            throw new RedoException("UNSUPPORTED_SINGLE_ENTRY_BLOCK_CLEANOUT");
    }
}
