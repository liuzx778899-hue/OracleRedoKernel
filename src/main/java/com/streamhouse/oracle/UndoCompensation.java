package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Physical 5.6 / 5.11 cancellation header; never sufficient by itself to cancel a CDC event. */
public record UndoCompensation(long dba, long objectId, long dataObjectId, long tablespace, long previousDba,
                               int opcodeMajor, int opcodeMinor, int slot, int predecessorRecord,
                               int flags, int wrap, boolean beginsTransaction) {
    public boolean crossesUndoBlock() { return !beginsTransaction && predecessorRecord==0 && previousDba!=0; }
    public static UndoCompensation decode(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=5 || (vector.opcodeMinor()!=6 && vector.opcodeMinor()!=11))
            throw new RedoException("UNSUPPORTED_COMPENSATION_OPCODE");
        var fields=vector.fields();
        if (fields.size()!=2 || fields.get(0).length!=28 || fields.get(1).length!=8)
            throw new RedoException("UNSUPPORTED_COMPENSATION_LAYOUT");
        byte[] b=fields.get(0);
        int flags=u16(b,20);
        boolean begin=vector.opcodeMinor()==11 && flags==0x0c18;
        boolean cross=vector.opcodeMinor()==11 && flags==0x0010 && (b[19]&255)==0 && u32(b,12)!=0;
        if ((vector.opcodeMinor()==11 && !begin && !cross)
                || (vector.opcodeMinor()==6 && (flags!=0x0010 || ((b[19]&255)==0 && u32(b,12)!=0)))
                || (begin && ((b[19]&255)!=0 || u16(b,22)!=1)))
            throw new RedoException("UNSUPPORTED_COMPENSATION_FLAGS");
        // In a begin compensation, wrap=1 is not the original transaction sequence.
        return new UndoCompensation(vector.dba(),u32(b,0),u32(b,4),u32(b,8),u32(b,12),
                b[16]&255,b[17]&255,b[18]&255,b[19]&255,flags,u16(b,22),begin);
    }
}
