package com.streamhouse.oracle;

import java.util.*;

/** Raw120 redefinition OBJ$ replacement preserves storage identity while changing its name and registration metadata. */
final class DictionaryObjectRebind {
    static void requireRows(HeapRowPatch.Patch before,HeapRowPatch.Patch after)throws RedoException {
        if(after.kind()!=HeapRowPatch.Kind.INSERT || before.kind()!=HeapRowPatch.Kind.INSERT
                || before.extent()==null || after.extent()==null || before.columns().size()!=37 || after.columns().size()!=37
                || !List.of(25,26).contains(before.extent().storedColumns()) || after.extent().storedColumns()!=26)
            fail("ROW_PAIR");
        boolean collationAppend=before.extent().storedColumns()==25;
        if(collationAppend && (before.columns().get(6).state()!=HeapRowPatch.State.VALUE
                || OracleScalars.number(before.columns().get(6).bytes()).compareTo(java.math.BigDecimal.ONE)!=0
                || after.columns().get(25).state()!=HeapRowPatch.State.VALUE))fail("COLLATION_APPEND_SCOPE");
        if(collationAppend)OracleScalars.number(after.columns().get(25).bytes());
        // Physical replacement preserves object identity and storage. Names, times, version and registration GUID may change.
        for(int i=0;i<37;i++)if(!Set.of(3,8,9,15,16,21).contains(i) && !(collationAppend && i==25)) {
            var a=before.columns().get(i);var b=after.columns().get(i);
            if(a.state()!=b.state() || !Arrays.equals(a.bytes(),b.bytes()))fail("IDENTITY_CHANGED");
        }
        for(int at:new int[]{7,8,9}) {OracleScalars.date(before.columns().get(at).bytes());OracleScalars.date(after.columns().get(at).bytes());}
        for(var row:List.of(before,after)) {
            if(row.columns().get(3).state()!=HeapRowPatch.State.VALUE || OracleScalars.varchar2(row.columns().get(3).bytes(),"AL32UTF8").isEmpty())fail("NAME");
            for(int at:new int[]{15,16})if(row.columns().get(at).state()!=HeapRowPatch.State.VALUE)fail("VERSION");else OracleScalars.number(row.columns().get(at).bytes());
            var guid=row.columns().get(21);if(guid.state()!=HeapRowPatch.State.NULL && (guid.state()!=HeapRowPatch.State.VALUE || guid.bytes().length!=16))fail("GUID");
        }
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_OBJECT_REBIND_"+detail);}
}
