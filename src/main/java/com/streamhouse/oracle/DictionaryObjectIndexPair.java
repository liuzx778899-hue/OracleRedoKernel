package com.streamhouse.oracle;

import java.util.*;

/** Observed raw120 SYS36/37/40 keys and OBJ$ row references. Nullable name qualifiers are supported only when all NULL. */
final class DictionaryObjectIndexPair {
    record Pair(long logical,IndexUndoPair physical,List<byte[]> key,long rowDba,int rowSlot) {}
    static Pair bind(RedoRecords.Record record,RedoRecords.Vector row,long logical)throws RedoException {
        if(!List.of(36L,37L,40L).contains(logical) || row.objectId()!=logical || row.blockClass()!=1)fail("SCOPE");
        var pair=IndexUndoPair.bindObjectIndex(record,row,logical);var owner=pair.undo().identity();
        if(owner.objectId()!=logical || owner.dataObjectId()!=logical || owner.tablespace()!=0
                || pair.undo().indexId()!=(logical==36?0x00400150L:logical==37?0x00400158L:0x00400170L)
                || pair.forward().kind()!=IndexUndoPair.Kind.INSERT && pair.forward().kind()!=IndexUndoPair.Kind.DELETE
                    && !(logical==36 && pair.forward().kind()==IndexUndoPair.Kind.RESTORE))fail("OWNER_OR_OPERATION");
        boolean writes=pair.forward().kind()!=IndexUndoPair.Kind.DELETE;
        byte[] bytes=writes?pair.forward().key():pair.undo().key();
        byte[] data=writes?pair.forward().keydata():pair.undo().keydata();
        validateKey(logical,bytes,data);long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
        if(dba==0 || dba>>>22==0)fail("ROW_DBA");
        return new Pair(logical,pair,components(bytes),dba,((data[4]&255)<<8)|(data[5]&255));
    }
    /** SYS36 restore keeps its compound key while moving the OBJ$ row reference. Both rows need independent owned proof. */
    static void requireRelocation(Pair index,HeapRowPatch.Patch before,HeapRowPatch.Patch after,UndoHeader owner)throws RedoException {
        if(index.logical()!=36 || index.physical().forward().kind()!=IndexUndoPair.Kind.RESTORE)fail("RELOCATION_OPERATION");
        requireObject(index,after,owner);
        byte[] data=index.physical().undo().keydata();long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
        var prior=new Pair(36,index.physical(),index.key(),dba,((data[4]&255)<<8)|(data[5]&255));
        requireObject(prior,before,owner);
        if(before.dba()==after.dba() && before.slot()==after.slot())fail("RELOCATION_UNCHANGED_ROW");
    }
    static void requireObject(Pair index,HeapRowPatch.Patch row,UndoHeader owner)throws RedoException {
        if(index.rowDba()!=row.dba() || index.rowSlot()!=row.slot() || !index.physical().undo().identity().xid().equals(owner.xid()))fail("OBJECT_ROW_OR_OWNER");
        int[] columns=index.logical()==36?new int[]{0,2,6}:index.logical()==37?new int[]{2,3,4,11,12,5,6,17,0}:new int[]{2,3,4,6,17,11,12,5,0};
        for(int i=0;i<columns.length;i++) {
            var cell=row.columns().get(columns[i]);var key=index.key().get(i);
            if(cell.state()==HeapRowPatch.State.ABSENT || cell.state()==HeapRowPatch.State.NULL && key!=null
                    || cell.state()==HeapRowPatch.State.VALUE && (key==null || !Arrays.equals(cell.bytes(),key)))fail("OBJECT_KEY_VALUES");
        }
    }
    static void validateKey(long logical,byte[] key,byte[] data)throws RedoException {
        if(!List.of(36L,37L,40L).contains(logical) || data!=null && data.length!=6)fail("KEY_SCOPE_OR_ROWID");
        var parts=components(key);if(parts.size()!=(logical==36?3:9))fail("KEY_COLUMN_COUNT");
        int[] numbers=logical==36?new int[]{0,1,2}:logical==37?new int[]{0,2,6,7,8}:new int[]{0,2,3,4,8};
        for(int at:numbers) {
            if(parts.get(at)==null)fail("NUMBER_NULL");
            try {long number=OracleScalars.number(parts.get(at)).longValueExact();if(number<0 || number>0xffffffffL)fail("NUMBER_RANGE");}
            catch(ArithmeticException invalid){fail("NUMBER_INTEGER");}
        }
        if(logical!=36) {
            if(parts.get(1)==null || OracleScalars.varchar2(parts.get(1),"AL32UTF8").isEmpty())fail("NAME");
            for(int at:logical==37?new int[]{3,4,5}:new int[]{5,6,7})if(parts.get(at)!=null)fail("NAME_QUALIFIER_NOT_PROVEN");
        }
    }
    private static List<byte[]> components(byte[] bytes)throws RedoException {
        var parts=new ArrayList<byte[]>();
        for(int at=0;at<bytes.length;) {
            int size=bytes[at++]&255;if(size==255){parts.add(null);continue;}
            if(size==0 || size>250 || at+size>bytes.length)fail("KEY_COMPONENT");
            parts.add(Arrays.copyOfRange(bytes,at,at+size));at+=size;
        }
        return Collections.unmodifiableList(parts);
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_OBJECT_INDEX_"+detail);}
}
