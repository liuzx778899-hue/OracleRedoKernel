package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 5.2 flags=2 head assignment inside an allocated block; no full predecessor inference. */
public record UndoHeadUpdate(long segmentDba,int slot,HeapRowPatch.Uba uba,long containerId) {
    public static UndoHeadUpdate decode(RedoRecords.Vector vector) throws RedoException {
        var fields=vector.fields();
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=2 || fields.size()!=1 || fields.get(0).length!=32)
            throw new RedoException("UNSUPPORTED_UNDO_HEAD_UPDATE_LAYOUT");
        byte[] b=fields.get(0);
        if (u32(b,4)!=0 || u16(b,16)!=2 || (b[14]&255)<2 || u32(b,24)!=0 || u32(b,28)!=0)
            throw new RedoException("UNSUPPORTED_UNDO_HEAD_UPDATE_FLAGS_OR_PARENT");
        return new UndoHeadUpdate(vector.dba(),u16(b,0),new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255),vector.containerId());
    }
    public UndoHeader bindUndo(RedoRecords.Record record) throws RedoException {
        return bindUndo(record,false);
    }
    UndoHeader bindArrayRoot(RedoRecords.Record record)throws RedoException {return bindUndo(record,true);}
    private UndoHeader bindUndo(RedoRecords.Record record,boolean arrayRoot)throws RedoException {
        UndoHeader match=null;
        for (var v:record.vectors()) {
            if (v.containerId()!=containerId || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=uba.dba()) continue;
            var h=UndoHeader.decode(v);
            if (!h.uba().equals(uba)) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_UNDO_HEAD_UPDATE_UBA");
            byte[] b=v.fields().get(1);
            if (h.beginsTransaction() || h.xid().slot()!=slot || (arrayRoot?u32(b,12)!=0 || !ArchivedUndoInventory.arrayRootAssigned(record,v,h):u32(b,12)==0) || (b[19]&255)!=0
                    || u16(b,22)!=(h.xid().sequence()&0xffff))
                throw new RedoException("UNDO_HEAD_UPDATE_IDENTITY_OR_LINK_MISMATCH");
            match=h;
        }
        if (match==null) throw new RedoException("MISSING_UNDO_HEAD_UPDATE_UBA");
        return match;
    }
}
