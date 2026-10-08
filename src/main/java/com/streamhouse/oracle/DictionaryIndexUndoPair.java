package com.streamhouse.oracle;

import java.util.List;

/** Scoped composite column-index pair and decoded row reference; complete dictionary lineage is not asserted. */
public record DictionaryIndexUndoPair(FrozenHeapBaseline.ObjectRole role,long indexDba,IndexUndoPair physical,DictionaryIndexKey key) {
    public static DictionaryIndexUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row,
            FrozenHeapBaseline.ObjectRole role,long indexDba,String charset) throws RedoException {
        if(!role.owner().equals("SYS") || !List.of("I_COL1","I_COL2","I_COL3","I_HH_OBJ#_COL#","I_HH_OBJ#_INTCOL#").contains(role.name()) || indexDba<=0)
            throw new RedoException("UNSUPPORTED_DICTIONARY_INDEX_SCOPE");
        var pair=IndexUndoPair.bindDictionary(record,row);var undo=pair.undo();
        if(undo.identity().objectId()!=role.logicalObject() || undo.identity().dataObjectId()!=role.dataObject()
                || undo.identity().tablespace()!=0 || undo.indexId()!=indexDba)
            throw new RedoException("DICTIONARY_INDEX_GENERATION_OR_INDEX_DBA_MISMATCH");
        byte[] data=pair.forward().kind()==IndexUndoPair.Kind.INSERT ? pair.forward().keydata() : undo.keydata();
        var key=DictionaryIndexKey.decode(role.name(),undo.key(),data,charset);
        return new DictionaryIndexUndoPair(role,indexDba,pair,key);
    }
}
