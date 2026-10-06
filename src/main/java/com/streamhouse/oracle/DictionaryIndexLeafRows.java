package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed I_COL3 leaf bytes only; source, lifecycle and split closure must be bound separately. */
public record DictionaryIndexLeafRows(long block,Long previousLink,long nextLink,List<Row> rows) {
    public record Row(int flags,int itl,DictionaryIndexKey key,String rawHex) {}
    public DictionaryIndexLeafRows {rows=List.copyOf(rows);}
    public static DictionaryIndexLeafRows before(RedoRecords.Vector undo,FrozenHeapBaseline.ObjectRole role,long indexDba)throws RedoException {
        var h=UndoHeader.decode(undo);var f=undo.fields();
        if(!role.owner().equals("SYS") || !role.name().equals("I_COL3") || h.objectId()!=role.logicalObject()
                || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0 || indexDba<=0)
            throw new RedoException("DICTIONARY_LEAF_BEFORE_SCOPE_CONFLICT");
        if(f.size()!=5 || f.get(1)[16]!=10 || f.get(1)[17]!=21 || f.get(2).length!=68
                || f.get(2)[0]!=5 || f.get(2)[1]!=0x0d || f.get(3).length!=12
                || f.get(3)[0]!=14 || u32(f.get(3),4)==0 || u32(f.get(3),8)!=indexDba || f.get(4).length!=8032)
            throw new RedoException("UNSUPPORTED_DICTIONARY_LEAF_BEFORE_PROFILE");
        byte[] b=f.get(4);int count=u16(b,8),start=36+2*count;
        if(b[0]!=0 || b[1]!=1 || u16(b,4)!=2 || count<1 || start>=b.length
                || u16(b,10)!=start || u16(b,12)!=start)
            throw new RedoException("UNSUPPORTED_DICTIONARY_LEAF_BEFORE_HEADER");
        int[] offsets=new int[count+1];for(int i=0;i<count;i++)offsets[i]=u16(b,36+2*i);offsets[count]=b.length;
        if(offsets[0]!=start)throw new RedoException("DICTIONARY_LEAF_BEFORE_DIRECTORY_CONFLICT");
        var rows=rows(b,offsets);
        if(rows.stream().anyMatch(r->r.flags()!=0))throw new RedoException("UNSUPPORTED_DICTIONARY_LEAF_BEFORE_ROW_STATE");
        return new DictionaryIndexLeafRows(u32(f.get(3),4),u32(b,24),u32(b,20),rows);
    }
    public static DictionaryIndexLeafRows rebuilt(RedoRecords.Vector forward)throws RedoException {
        var f=forward.fields();
        if(forward.opcodeMajor()!=10 || forward.opcodeMinor()!=8 || forward.dba()==0 || f.size()!=12
                || f.subList(4,12).stream().anyMatch(b->b.length!=0))
            throw new RedoException("UNSUPPORTED_DICTIONARY_LEAF_REBUILD_FIELDS");
        boolean old=f.get(0).length==0;byte[] header=f.get(1),directory=f.get(2),data=f.get(3);
        Long previous=null;long next;
        if(old) {
            if(header.length!=4)throw new RedoException("UNSUPPORTED_DICTIONARY_OLD_LEAF_REBUILD");next=u32(header,0);
        } else {
            byte[] ktb=f.get(0);
            if(ktb.length!=68 || ktb[0]!=5 || ktb[1]!=0x0d || header.length!=16
                    || header[0]!=1 || header[1]!=2 || header[2]!=6 || header[3]!=46
                    || u32(header,4)!=0)throw new RedoException("UNSUPPORTED_DICTIONARY_NEW_LEAF_REBUILD");
            next=u32(header,8);previous=u32(header,12);
        }
        if(next==0 || !old && (previous==0 || previous==next) || directory.length<4 || directory.length%2!=0)
            throw new RedoException("INVALID_DICTIONARY_LEAF_REBUILD_LINK_OR_DIRECTORY");
        int count=directory.length/2-1,base=u16(directory,0);int[] offsets=new int[count+1];
        if(old?base!=0:base==0)throw new RedoException("DICTIONARY_LEAF_REBUILD_DIRECTORY_BASE_CONFLICT");
        for(int i=0;i<=count;i++) {
            int physical=u16(directory,2*i);
            if(physical>8032)throw new RedoException("DICTIONARY_LEAF_REBUILD_OFFSET_OUTSIDE_BLOCK");
            offsets[i]=physical-base-2*i;
        }
        if(offsets[count]!=data.length)throw new RedoException("DICTIONARY_LEAF_REBUILD_DIRECTORY_END_CONFLICT");
        return new DictionaryIndexLeafRows(forward.dba(),previous,next,rows(data,offsets));
    }
    private static List<Row> rows(byte[] data,int[] offsets)throws RedoException {
        var rows=new ArrayList<Row>();DictionaryIndexKey prior=null;
        for(int i=0;i<offsets.length-1;i++) {
            int a=offsets[i],b=offsets[i+1];
            if(a<0 || b>data.length || b-a<12)throw new RedoException("INVALID_DICTIONARY_LEAF_ROW_BOUNDARY");
            int flags=data[a]&255,itl=data[a+1]&255;
            if(!(flags==0 && itl==0 || flags==1 && itl==1))throw new RedoException("UNSUPPORTED_DICTIONARY_LEAF_ROW_FLAGS");
            var key=DictionaryIndexKey.decode("I_COL3",Arrays.copyOfRange(data,a+8,b),Arrays.copyOfRange(data,a+2,a+8),"AL32UTF8");
            if(prior!=null && (key.objectId()<prior.objectId() || key.objectId()==prior.objectId() && key.columnNumber()<=prior.columnNumber()))
                throw new RedoException("DICTIONARY_LEAF_ROW_ORDER_OR_DUPLICATE_CONFLICT");
            rows.add(new Row(flags,itl,key,HexFormat.of().formatHex(Arrays.copyOfRange(data,a,b))));prior=key;
        }
        return List.copyOf(rows);
    }
}
