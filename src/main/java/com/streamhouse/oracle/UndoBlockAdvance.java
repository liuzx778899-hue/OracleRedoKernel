package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 5.2 flags=0x000a cursor transfer. Does not by itself encode the predecessor's full UBA. */
public record UndoBlockAdvance(long segmentDba,int slot,HeapRowPatch.Uba uba,long containerId) {
    public static UndoBlockAdvance decode(RedoRecords.Vector vector) throws RedoException {
        var f=vector.fields();
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=2 || (f.size()!=1 && f.size()!=2)
                || f.get(0).length!=32 || (f.size()==2 && f.get(1).length!=36))
            throw new RedoException("UNSUPPORTED_UNDO_ADVANCE_LAYOUT");
        byte[] b=f.get(0);
        if (u32(b,4)!=0 || u16(b,16)!=0x000a || (b[14]&255)!=1 || u32(b,24)!=0 || u32(b,28)!=0)
            throw new RedoException("UNSUPPORTED_UNDO_ADVANCE_FLAGS_OR_PARENT");
        return new UndoBlockAdvance(vector.dba(),u16(b,0),new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255),vector.containerId());
    }

    /** Matches the explicit new UBA, not adjacency or the row's historical ITL. */
    public UndoHeader bindUndo(RedoRecords.Record record) throws RedoException {
        UndoHeader match=null;
        for (var v:record.vectors()) {
            if (v.containerId()!=containerId || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=uba.dba()) continue;
            var h=UndoHeader.decode(v);
            if (!h.uba().equals(uba)) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_UNDO_ADVANCE_UBA");
            byte[] b=v.fields().get(1);
            if (h.beginsTransaction() || h.xid().slot()!=slot || u32(b,12)==0 || (b[19]&255)!=0
                    || u16(b,22)!=(h.xid().sequence()&0xffff))
                throw new RedoException("UNDO_ADVANCE_IDENTITY_OR_LINK_MISMATCH");
            match=h;
        }
        if (match==null) throw new RedoException("MISSING_UNDO_ADVANCE_UBA");
        return match;
    }
}
