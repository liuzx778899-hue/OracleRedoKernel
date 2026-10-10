package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 5.4 end marker. Segment DBA is not yet a verified undo segment number. */
public record TransactionEnd(long segmentDba, int slot, long sequence, int flags) {
    public boolean rolledBack() { return flags == 4 || flags == 0x14; }

    public static HeapRowPatch.Uba undoTail(RedoRecords.Vector vector) throws RedoException {
        decode(vector);
        if (vector.fields().size()==2 || u32(vector.fields().get(0),16)==0x14 || u32(vector.fields().get(0),16)==0x10)
            throw new RedoException("TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE");
        byte[] b=vector.fields().get(1);
        return new HeapRowPatch.Uba(u32(b,0),u16(b,4),b[6]&255);
    }

    public static TransactionEnd decode(RedoRecords.Vector vector) throws RedoException {
        var fields=vector.fields();
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=4)
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_OPCODE");
        if ((fields.size()!=2 && fields.size()!=3 && fields.size()!=4) || fields.get(0).length!=20
                || fields.get(fields.size()-1).length!=4
                || (fields.size()>=3 && fields.get(1).length!=16 && !(fields.size()==3 && fields.get(1).length==24))
                || (fields.size()==4 && fields.get(2).length!=24))
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_LAYOUT");
        byte[] b=fields.get(0);
        long flags=u32(b,16);
        if (u16(b,2)!=0 || u32(b,8)!=0 || u32(b,12)!=9
                || (flags!=0 && flags!=2 && flags!=4 && flags!=0x10 && flags!=0x12 && flags!=0x14)
                || (flags==0x12)!=(fields.size()==4)
                || (flags==0x14 || flags==0x10)!=(fields.size()==3 && fields.get(1).length==24))
            throw new RedoException("UNSUPPORTED_TRANSACTION_END_FLAGS");
        if (flags==0x12 || flags==0x14 || flags==0x10) {
            byte[] extra=fields.get(flags==0x14 || flags==0x10 ? 1 : 2);
            int observedSize=flags==0x14 ? 16 : 24;
            // Narrow observed extension shape. No interpretation of its extra control semantics.
            boolean sized=flags!=0x10 && u32(extra,0)==0 && u32(extra,4)==1 && u32(extra,12)==1
                    && u32(extra,16)==observedSize && u32(extra,20)==observedSize;
            // Seq59/93 native-matched commit tails with ext 0/1/2 and an otherwise empty extension.
            // The extra control payload is not interpreted as a transaction identity or undo tail.
            // Native seq82 ktucf ext=0, spc=4194, fbi=1: fbi/padding is not extension or identity authority.
            boolean empty=flags==0x12 && u32(extra,0)<=2 && u32(extra,4)==u32(extra,0)
                    && u16(fields.get(1),8)==u32(extra,0) && fields.get(1)[7]==0
                    && u32(extra,12)==0 && u32(extra,16)==0 && u32(extra,20)==0;
            // Native seq92 SCN2939701 identifies this exact empty extension as full rollback.
            boolean emptyRollback=flags==0x14 && u32(extra,0)==3 && u32(extra,4)==3
                    && u32(extra,12)==0 && u32(extra,16)==0 && u32(extra,20)==0;
            // Seq104 native-matched SecureFile maintenance tails: ktucf ext=12 and ext=2.
            // These opaque extensions do not supply XID or replace the explicit UBA tail.
            boolean maintenance=(flags==0x12 || flags==0x10) && (flags==0x10 || fields.get(1)[7]==0) &&
                    ((u32(extra,0)==11 && u32(extra,4)==12 && (flags==0x10 || u16(fields.get(1),8)==12)
                        && u32(extra,12)==0 && u32(extra,16)==0 && u32(extra,20)==0)
                     || (u32(extra,0)==0 && u32(extra,4)==2 && (flags==0x10 || u16(fields.get(1),8)==2)
                        && u32(extra,12)==1 && u32(extra,16)==5 && u32(extra,20)==5));
            if ((!sized && !empty && !emptyRollback && !maintenance) || u32(extra,8)!=u32(fields.get(fields.size()-1),0))
                throw new RedoException("UNSUPPORTED_TRANSACTION_END_EXTENSION");
        }
        return new TransactionEnd(vector.dba(),u16(b,0),u32(b,4),(int)flags);
    }
}
