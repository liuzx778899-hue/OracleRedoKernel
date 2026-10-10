package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** 5.1 heap inverse patch. Identity comes from the undo header, never the historical KTB. */
public record UndoRowPatch(UndoHeader identity, HeapRowPatch.Patch inverse,
                           long previousDba,int predecessorRecord,int wrap) {
    public static UndoRowPatch decode(RedoRecords.Vector vector,int dictionaryColumns) throws RedoException {
        return decode(vector,dictionaryColumns,false);
    }
    static UndoRowPatch decode(RedoRecords.Vector vector,int dictionaryColumns,boolean dictionaryObject) throws RedoException {
        return decode(vector,dictionaryColumns,dictionaryObject,false);
    }
    public static UndoRowPatch decodeLob(RedoRecords.Vector vector,int columns)throws RedoException {
        if(columns<1 || columns>255)throw new RedoException("UNSUPPORTED_LOB_HEAP_COLUMN_PROFILE");
        return decode(vector,columns,false,true);
    }
    private static UndoRowPatch decode(RedoRecords.Vector vector,int dictionaryColumns,boolean dictionaryObject,boolean lobHeap) throws RedoException {
        var identity=UndoHeader.decode(vector);
        var fields=vector.fields();
        if (fields.size()<4 || fields.get(3).length<12)
            throw new RedoException("MISSING_UNDO_HEAP_FIELDS");
        byte[] ktubu=fields.get(1), ktb=fields.get(2), kdo=fields.get(3);
        if ((ktubu[16]&255)!=11 || (ktubu[17]&255)!=1 || (ktubu[18]&255)!=identity.xid().slot()
                || (!identity.beginsTransaction() && u16(ktubu,22)!=(identity.xid().sequence()&0xffff)))
            throw new RedoException("UNSUPPORTED_UNDO_HEAP_IDENTITY");
        if (ktb.length==0 || !((ktb[0]==3 && ktb.length==8) || (ktb[0]==2 && ktb.length==16)
                || (ktb[0]==4 && ktb.length==32)))
            throw new RedoException("UNSUPPORTED_UNDO_KTB_LAYOUT");
        var inverse=lobHeap?HeapRowPatch.decodeLobKdo(fields.subList(2,fields.size()),kdo[10]&255,u32(kdo,0),dictionaryColumns)
                :HeapRowPatch.decodeKdo(fields.subList(2,fields.size()),kdo[10]&255,u32(kdo,0),dictionaryColumns,dictionaryObject);
        return new UndoRowPatch(identity,inverse,u32(ktubu,12),ktubu[19]&255,u16(ktubu,22));
    }
}
