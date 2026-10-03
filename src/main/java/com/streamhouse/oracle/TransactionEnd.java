package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 5.4 end marker. Segment DBA is not yet a verified undo segment number. */
public record TransactionEnd(long segmentDba, int slot, long sequence, int flags) {
    public boolean rolledBack() { return flags == 4; }

    public static HeapRowPatch.Uba undoTail(RedoRecords.Vector vector) throws RedoException {
        decode(vector);
        if (vector.fields().size()!=3 && vector.fields().size()!=4) throw new RedoException("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE");
        byte[] b=vector.fields().get(1);
        return new HeapRowPatch.Uba(u32(b,0),u16(b,4),b[6]&255);
    }

    public static TransactionEnd decode(RedoRecords.Vector vector) throws RedoException {
        var fields=vector.fields();
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=4)
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_OPCODE");
        if ((fields.size()!=2 && fields.size()!=3 && fields.size()!=4) || fields.get(0).length!=20
                || fields.get(fields.size()-1).length!=4
                || (fields.size()>=3 && fields.get(1).length!=16)
                || (fields.size()==4 && fields.get(2).length!=24))
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_LAYOUT");
        byte[] b=fields.get(0);
        long flags=u32(b,16);
        if (u16(b,2)!=0 || u32(b,8)!=0 || u32(b,12)!=9
                || (flags!=0 && flags!=2 && flags!=4 && flags!=0x12)
                || (flags==0x12)!=(fields.size()==4))
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_FLAGS");
        if (fields.size()==4) {
            byte[] extra=fields.get(2);
            // Narrow observed extension shape. No interpretation of its extra control semantics.
            if (u32(extra,0)!=0 || u32(extra,4)!=1 || u32(extra,8)!=u32(fields.get(3),0)
                    || u32(extra,12)!=1 || u32(extra,16)!=24 || u32(extra,20)!=24)
                throw new RedoException("UNSUPPORTED_TRANSACTION_END_EXTENSION");
        }
        return new TransactionEnd(vector.dba(),u16(b,0),u32(b,4),(int)flags);
    }
}
