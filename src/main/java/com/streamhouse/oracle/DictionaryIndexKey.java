package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Observed I_COL1/2/3 short key shapes. A decoded parent number is not trusted dictionary history. */
public record DictionaryIndexKey(long objectId,String columnName,Integer columnNumber,long rowDba,int rowSlot) {
    private static List<byte[]> components(byte[] key) throws RedoException {
        var result=new ArrayList<byte[]>();
        for(int at=0;at<key.length;) {
            int size=key[at++]&255;
            if(size<1 || size>250 || at+size>key.length)throw new RedoException("UNSUPPORTED_DICTIONARY_INDEX_COMPONENT");
            result.add(Arrays.copyOfRange(key,at,at+size));at+=size;
        }
        if(result.size()!=2 && result.size()!=3)throw new RedoException("UNSUPPORTED_DICTIONARY_INDEX_COMPONENT_COUNT");
        return result;
    }
    static void validatePhysical(byte[] key,byte[] data) throws RedoException {
        var parts=components(key);
        if(parts.size()==3 ? parts.get(2).length!=6 || data!=null && data.length!=0
                : data!=null && data.length!=6)
            throw new RedoException("UNSUPPORTED_DICTIONARY_INDEX_ROWID_STORAGE");
    }
    public static DictionaryIndexKey decode(String indexName,byte[] key,byte[] data,String charset) throws RedoException {
        if(!List.of("I_COL1","I_COL2","I_COL3","I_HH_OBJ#_COL#","I_HH_OBJ#_INTCOL#","I_COL_USAGE$").contains(indexName))throw new RedoException("UNSUPPORTED_DICTIONARY_INDEX_ROLE");
        validatePhysical(key,data);var parts=components(key);boolean inline=indexName.equals("I_COL2") || indexName.startsWith("I_HH_");
        if(parts.size()!=(inline?3:2) || !inline && (data==null || data.length!=6))
            throw new RedoException("DICTIONARY_INDEX_ROLE_ROWID_LAYOUT_MISMATCH");
        long object;Integer number=null;String name=null;
        try {
            object=OracleScalars.number(parts.get(0)).longValueExact();
            if(!indexName.equals("I_COL1"))number=OracleScalars.number(parts.get(1)).intValueExact();
        } catch(ArithmeticException e) {throw new RedoException("NONINTEGRAL_DICTIONARY_INDEX_KEY");}
        if(object<=0 || object>0xffffffffL || number!=null && (number<0 || indexName.equals("I_COL3") && number==0))
            throw new RedoException("INVALID_DICTIONARY_INDEX_PARENT_OR_COLUMN");
        if(indexName.equals("I_COL1"))name=OracleScalars.varchar2(parts.get(1),charset);
        byte[] rowid=inline?parts.get(2):data;long dba=0;
        for(int i=0;i<4;i++)dba=(dba<<8)|(rowid[i]&255);
        if(dba==0 || dba>>>22==0)throw new RedoException("INVALID_DICTIONARY_INDEX_ROWID_DBA");
        return new DictionaryIndexKey(object,name,number,dba,((rowid[4]&255)<<8)|(rowid[5]&255));
    }
}
