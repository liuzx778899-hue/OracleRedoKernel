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
                && b[15]==0 && (u16(b,18)==176 || u16(b,18)==316) && u32(b,20)==0;
        boolean observedControlBegin=flags==0x51 && u16(b,2)==0 && (b[14]&255)>0
                && b[15]==0 && u16(b,18)==80 && b[20]==0;
        // Native seq70/82: fbi plus padding is historical control state, not a current identity.
        boolean observedSplitBegin=flags==0x44a && u16(b,2)==0 && (b[14]&255)==1
                && b[15]==0 && (u16(b,18)==136 || u16(b,18)==148);
        if ((flags!=0x52 && flags!=0x452 && !observedFirstRecord && !observedControlBegin && !observedSplitBegin) || u32(b,24)!=0 || u32(b,28)!=0)
            throw new RedoException("UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT");
        return new TransactionStart(vector.dba(),u16(b,0),u32(b,4),
                new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255),flags,u32(fields.get(1),0),vector.containerId());
    }

    /** Matches all vectors, not their order. Returns no identity on missing or ambiguous evidence. */
    public HeapRowPatch.Xid bindBegin(RedoRecords.Record record) throws RedoException {
        if (record.containerUid()!=containerUid)
            throw new RedoException("TRANSACTION_START_CONTAINER_MISMATCH");
        UndoHeader match=null;
        RedoRecords.Vector beginVector=null;
        for (var v:record.vectors()) {
            if (v.containerId()!=containerId || v.opcodeMajor()!=5 || v.opcodeMinor()!=1
                    || v.dba()!=uba.dba() || v.fields().isEmpty()) continue;
            byte[] b=v.fields().get(0);
            if (b.length!=20) throw new RedoException("UNSUPPORTED_BEGIN_CANDIDATE_LAYOUT");
            if (u16(b,16)!=uba.sequence() || (b[18]&255)!=uba.record()) continue;
            var h=UndoHeader.decode(v);
            if (match!=null) throw new RedoException("AMBIGUOUS_TRANSACTION_BEGIN_UBA");
            match=h;
            beginVector=v;
        }
        if (match==null) throw new RedoException("MISSING_TRANSACTION_BEGIN_UBA");
        if (!match.beginsTransaction() || match.xid().slot()!=slot || match.xid().sequence()!=sequence)
            throw new RedoException("TRANSACTION_BEGIN_IDENTITY_MISMATCH");
        if(flags==0x4a || flags==0x44a) {
            int size=-1;
            for(var v:record.vectors())if(v.containerId()==containerId && v.dba()==segmentDba && v.opcodeMajor()==5 && v.opcodeMinor()==2
                    && decode(v).equals(this)) {
                if(size!=-1)throw new RedoException("AMBIGUOUS_FIRST_RECORD_START");size=u16(v.fields().get(0),18);
            }
            if(size<0 || u16(beginVector.fields().get(0),0)!=size)throw new RedoException("FIRST_RECORD_BEGIN_SIZE_MISMATCH");
            // Actual seq71 first-record COL$ delete: full32-column clustered inverse, no parent transaction.
            if(size==316) {
                var f=beginVector.fields();
                if(match.objectId()!=21 || match.dataObjectId()!=2 || match.tablespace()!=0 || f.size()!=36
                        || f.get(1)[16]!=11 || f.get(1)[17]!=1 || f.get(3).length!=52 || f.get(3)[10]!=2)
                    throw new RedoException("UNSUPPORTED_COL_DELETE_BEGIN_PROFILE");
                ClusterRowPatch.decode(f.subList(2,f.size()),2,u32(f.get(3),0),5,36);
            }
        }
        if (flags==0x51) {
            var begin=match;
            var v=record.vectors().stream().filter(u->u.containerId()==containerId && u.opcodeMajor()==5
                    && u.opcodeMinor()==1 && u.dba()==uba.dba() && u.fields().size()==2
                    && u.fields().get(0).length==20 && u16(u.fields().get(0),16)==uba.sequence()
                    && (u.fields().get(0)[18]&255)==uba.record()).findFirst();
            if (begin.objectId()!=0 || begin.dataObjectId()!=0 || begin.tablespace()!=0 || v.isEmpty()
                    || v.get().fields().get(1)[16]!=5 || v.get().fields().get(1)[17]!=7)
                throw new RedoException("NON_ROW_TRANSACTION_BEGIN_PROFILE_MISMATCH");
            if (u16(v.get().fields().get(0),0)!=80)
                throw new RedoException("NON_ROW_TRANSACTION_BEGIN_SIZE_MISMATCH");
        }
        return match.xid();
    }
}
