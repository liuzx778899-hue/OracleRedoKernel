package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 5.2 undo segment slot assignment. Full XID requires matching its exact begin UBA. */
public record TransactionStart(long segmentDba, int slot, long sequence, HeapRowPatch.Uba uba,
                               int flags, long containerUid, long containerId) {
    public static TransactionStart decode(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=2)
            throw new RedoException("UNSUPPORTED_TRANSACTION_START_OPCODE");
        var fields=vector.fields();
        if (fields.size()!=2 || fields.get(0).length!=32 || fields.get(1).length!=4)
            throw new RedoException("UNSUPPORTED_TRANSACTION_START_LAYOUT");
        byte[] b=fields.get(0);
        int flags=u16(b,16);
        boolean observedFirstRecord=flags==0x4a && u16(b,2)==0 && (b[14]&255)==1
                && b[15]==0 && u16(b,18)==176 && u32(b,20)==0;
        if ((flags!=0x52 && flags!=0x452 && !observedFirstRecord) || u32(b,24)!=0 || u32(b,28)!=0)
            throw new RedoException("UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT");
        return new TransactionStart(vector.dba(),u16(b,0),u32(b,4),
                new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255),flags,u32(fields.get(1),0),vector.containerId());
    }

    /** Matches all vectors, not their order. Returns no identity on missing or ambiguous evidence. */
    public HeapRowPatch.Xid bindBegin(RedoRecords.Record record) throws RedoException {
        if (record.containerUid()!=containerUid)
            throw new RedoException("TRANSACTION_START_CONTAINER_MISMATCH");
        UndoHeader match=null;
        for (var v:record.vectors()) {
            if (v.containerId()!=containerId || v.opcodeMajor()!=5 || v.opcodeMinor()!=1
                    || v.dba()!=uba.dba() || v.fields().isEmpty()) continue;
            byte[] b=v.fields().get(0);
            if (b.length!=20) throw new RedoException("UNSUPPORTED_BEGIN_CANDIDATE_LAYOUT");
            if (u16(b,16)!=uba.sequence() || (b[18]&255)!=uba.record()) continue;
            var h=UndoHeader.decode(v);
            if (match!=null) throw new RedoException("AMBIGUOUS_TRANSACTION_BEGIN_UBA");
            match=h;
        }
        if (match==null) throw new RedoException("MISSING_TRANSACTION_BEGIN_UBA");
        if (!match.beginsTransaction() || match.xid().slot()!=slot || match.xid().sequence()!=sequence)
            throw new RedoException("TRANSACTION_BEGIN_IDENTITY_MISMATCH");
        return match.xid();
    }
}
