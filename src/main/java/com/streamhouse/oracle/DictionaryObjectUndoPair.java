package com.streamhouse.oracle;

/** Observed complete-XID OBJ$ updates, including the nine-column two-byte bitmap profile. */
public record DictionaryObjectUndoPair(FrozenHeapBaseline.ObjectRole role,HeapUndoPair physical) {
    public static DictionaryObjectUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,FrozenHeapBaseline.ObjectRole role,int columns)throws RedoException {
        if(!role.owner().equals("SYS") || !role.name().equals("OBJ$") || columns!=37)
            throw new RedoException("UNSUPPORTED_DICTIONARY_OBJECT_SCOPE");
        if(row.fields().isEmpty())throw new RedoException("MISSING_DICTIONARY_OBJECT_KTB");
        byte[] b=row.fields().get(0);
        if(b.length<2 || b[1]!=0x0d || !((b[0]==17 && b.length==70) || (b[0]==1 && b.length==24) || (b[0]==2 && b.length==16)))
            throw new RedoException("UNSUPPORTED_DICTIONARY_OBJECT_CURRENT_KTB");
        var pair=HeapUndoPair.bindDictionaryObject(record,row,columns);var h=pair.undo().identity();
        if(h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0)
            throw new RedoException("DICTIONARY_OBJECT_GENERATION_MISMATCH");
        // Actual fresh seq68 six-column OBJ$ update carries ckix239 in both images; it is not a parent identity.
        for(var undo:record.vectors())if(undo.containerId()==row.containerId() && undo.opcodeMajor()==5 && undo.opcodeMinor()==1
                && UndoHeader.decode(undo).equals(h) && row.fields().get(1)[18]!=undo.fields().get(3)[18])
            throw new RedoException("DICTIONARY_OBJECT_CKIX_PAIR_MISMATCH");
        return new DictionaryObjectUndoPair(role,pair);
    }
}
