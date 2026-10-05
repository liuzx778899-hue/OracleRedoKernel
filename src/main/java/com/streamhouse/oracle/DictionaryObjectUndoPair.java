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
        return new DictionaryObjectUndoPair(role,pair);
    }
}
