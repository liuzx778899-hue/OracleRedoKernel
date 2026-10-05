package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed non-row 5.7 allocation start and separately assigned row root. No edge between them. */
public record UndoControlRoot(TransactionStart start,UndoHeader allocationBegin,UndoHeader rowRoot) {
    static TransactionStart begin(RedoRecords.Record record,RedoRecords.Vector undo) throws RedoException {
        var h=UndoHeader.decode(undo);TransactionStart found=null;
        for(var v:record.vectors()) {
            if(v.containerId()!=undo.containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=2
                    || v.fields().isEmpty() || v.fields().get(0).length!=32) continue;
            byte[] b=v.fields().get(0);
            if(u32(b,8)!=h.uba().dba() || u16(b,12)!=h.uba().sequence() || (b[14]&255)!=h.uba().record()) continue;
            var s=TransactionStart.decode(v);
            if(found!=null) throw new RedoException("AMBIGUOUS_NON_ROW_BEGIN_CONTROL");
            if(s.flags()!=0x51 || !s.bindBegin(record).equals(h.xid())) throw new RedoException("NON_ROW_BEGIN_CONTROL_MISMATCH");
            found=s;
        }
        if(found==null) throw new RedoException("MISSING_NON_ROW_BEGIN_CONTROL");
        return found;
    }
    static UndoControlRoot bind(TransactionStart start,UndoHeader begin,RedoRecords.Record record,RedoRecords.Vector undo) throws RedoException {
        var h=UndoHeader.decode(undo);byte[] m=undo.fields().get(1);
        if(record.containerUid()!=start.containerUid() || undo.containerId()!=start.containerId()
                || !h.xid().equals(begin.xid()) || h.beginsTransaction() || h.objectId()==0 || h.dataObjectId()==0
                || h.tablespace()!=0 || m[16]!=11 || m[17]!=1 || u32(m,12)!=0 || m[19]!=0
                || u16(m,22)!=(h.xid().sequence()&0xffff) || h.uba().dba()!=begin.uba().dba()
                || h.uba().sequence()!=begin.uba().sequence() || h.uba().record()!=begin.uba().record()+1)
            throw new RedoException("NON_ROW_FIRST_ROOT_IDENTITY_OR_LINK_MISMATCH");
        int controls=0;
        for(var v:record.vectors()) {
            if(v.containerId()!=start.containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=2
                    || v.dba()!=start.segmentDba()) continue;
            if(v.fields().isEmpty() || v.fields().get(0).length!=32)
                throw new RedoException("UNREADABLE_NON_ROW_FIRST_ROOT_CONTROL");
            byte[] b=v.fields().get(0);
            if(u16(b,0)!=start.slot()) continue;
            var c=UndoHeadUpdate.decode(v);
            if(!c.uba().equals(h.uba())) throw new RedoException("NON_ROW_FIRST_ROOT_CONTROL_UBA_MISMATCH");
            if(u16(b,18)!=u16(undo.fields().get(0),0)) throw new RedoException("NON_ROW_FIRST_ROOT_CONTROL_SIZE_MISMATCH");
            controls++;
        }
        if(controls!=1) throw new RedoException("NON_ROW_FIRST_ROOT_CONTROL_MISSING_OR_AMBIGUOUS");
        int originals=0;
        for(var v:record.vectors()) {
            if(v.containerId()!=start.containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=h.uba().dba()) continue;
            var other=UndoHeader.decode(v);if(other.uba().equals(h.uba())) originals++;
        }
        if(originals!=1) throw new RedoException("NON_ROW_FIRST_ROOT_UNDO_AMBIGUOUS");
        return new UndoControlRoot(start,begin,h);
    }
}
