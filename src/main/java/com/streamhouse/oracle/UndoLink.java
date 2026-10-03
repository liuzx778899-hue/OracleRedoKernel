package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 5.1 metadata link, including index undo. Cross-block address/sequence rules remain unproven. */
public record UndoLink(UndoHeader identity,int opcodeMajor,int opcodeMinor,HeapRowPatch.Uba predecessor) {
    public static UndoLink decode(RedoRecords.Vector vector) throws RedoException {
        var header=UndoHeader.decode(vector); byte[] b=vector.fields().get(1);
        if ((b[18]&255)!=header.xid().slot()) throw new RedoException("UNDO_LINK_SLOT_MISMATCH");
        int record=b[19]&255;
        long previousDba=u32(b,12);
        if (header.beginsTransaction()) {
            if (record!=0 || previousDba!=0 || u16(b,22)!=1)
                throw new RedoException("UNSUPPORTED_UNDO_BEGIN_LINK");
            return new UndoLink(header,b[16]&255,b[17]&255,null);
        }
        if (u16(b,22)!=(header.xid().sequence()&0xffff)) throw new RedoException("UNDO_LINK_WRAP_MISMATCH");
        if (previousDba!=0) throw new RedoException("CROSS_BLOCK_UNDO_LINK_NOT_PROVEN");
        if (record==0) throw new RedoException("MISSING_UNDO_PREDECESSOR");
        return new UndoLink(header,b[16]&255,b[17]&255,
                new HeapRowPatch.Uba(header.uba().dba(),header.uba().sequence(),record));
    }
}
