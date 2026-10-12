package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed SYS DEFERRED_STG$ insert/delete: 25 numeric cells, four-byte NULL bitmap and complete owned undo. */
final class DictionaryDeferredStoragePair {
    static IndexUndoPair bindIndex(RedoRecords.Record record,RedoRecords.Vector row)throws RedoException {
        if(row.objectId()!=82 || row.blockClass()!=1 || !List.of(2,4).contains(row.opcodeMinor()))fail("INDEX_SCOPE");
        var pair=IndexUndoPair.bindDeferredStorageIndex(record,row);var owner=pair.undo().identity();
        if(owner.objectId()!=82 || owner.dataObjectId()!=82 || owner.tablespace()!=0 || pair.undo().indexId()!=0x00400400L)fail("INDEX_OWNER");
        return pair;
    }
    static void validateKey(byte[] key,byte[] data)throws RedoException {
        if(key.length<2 || (key[0]&255)!=key.length-1 || data!=null && data.length!=6)fail("INDEX_KEY_LAYOUT");
        try {long object=OracleScalars.number(Arrays.copyOfRange(key,1,key.length)).longValueExact();if(object<=0 || object>0xffffffffL)fail("INDEX_OBJECT_RANGE");}
        catch(ArithmeticException invalid){fail("INDEX_OBJECT_INTEGER");}
    }
    static void requireRow(IndexUndoPair index,HeapUndoPair row)throws RedoException {
        var patch=row.forward().kind()==HeapRowPatch.Kind.INSERT?row.forward():row.undo().inverse();
        var operation=index.forward().kind();
        if(index.containerUid()!=row.containerUid() || index.containerId()!=row.containerId()
                || !index.undo().identity().xid().equals(row.undo().identity().xid())
                || operation!=(row.forward().kind()==HeapRowPatch.Kind.INSERT?IndexUndoPair.Kind.INSERT:IndexUndoPair.Kind.DELETE))fail("INDEX_ROW_OWNER_OR_OPERATION");
        byte[] key=operation==IndexUndoPair.Kind.INSERT?index.forward().key():index.undo().key();
        byte[] data=operation==IndexUndoPair.Kind.INSERT?index.forward().keydata():index.undo().keydata();
        validateKey(key,data);long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
        if(dba!=patch.dba() || ((data[4]&255)<<8 | data[5]&255)!=patch.slot()
                || !Arrays.equals(Arrays.copyOfRange(key,1,key.length),patch.columns().get(0).bytes()))fail("INDEX_ROW_VALUES");
    }
    static HeapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row)throws RedoException {
        if(!record.vectors().contains(row) || row.opcodeMajor()!=11 || !List.of(2,3).contains(row.opcodeMinor())
                || row.fields().size()<2)fail("FORWARD_SCOPE");
        byte[] ktb=row.fields().get(0);HeapRowPatch.Xid xid=null;int at;
        if(ktb.length<2)fail("FORWARD_KTB_TRUNCATED");
        if(ktb.length==24 && ktb[0]==1 && ktb[1]==0x0d || ktb[0]==17 && ktb[1]==0x0d
                && (ktb.length==70 || ktb.length==80 && ktb[44]==2 && ktb[45]==2 && ktb[46]==0x0d)) {
            xid=new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));at=16;
        } else if(ktb.length==16 && ktb[0]==2 && ktb[1]==0x0d)at=8;else {fail("FORWARD_KTB:"+record.block()+":"+record.offset()+":"+ktb.length);return null;}
        var uba=new HeapRowPatch.Uba(u32(ktb,at),u16(ktb,at+4),ktb[at+6]&255);
        if(uba.dba()==0 || uba.record()==0)fail("UBA");
        RedoRecords.Vector original=null;int rows=0;
        for(var v:record.vectors())if(v.containerId()==row.containerId()) {
            if(v.opcodeMajor()==11 && (v.objectId()<0 || v.objectId()==81) && v.dba()==row.dba()
                    && HeapUndoPair.referencesOriginal(v,uba))rows++;
            if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.dba()==uba.dba()) {
                var owner=UndoHeader.decode(v);
                if(!owner.uba().equals(uba))continue;
                if(original!=null)fail("AMBIGUOUS_UNDO");original=v;
            }
        }
        if(original==null || rows!=1)fail("PAIR_COVERAGE");
        var owner=UndoHeader.decode(original);var fields=original.fields();byte[] m=fields.get(1);
        if(owner.objectId()!=81 || owner.dataObjectId()!=81 || owner.tablespace()!=0
                || xid!=null && !xid.equals(owner.xid()) || fields.size()<4 || m[16]!=11 || m[17]!=1
                || (m[18]&255)!=owner.xid().slot() || !owner.beginsTransaction() && u16(m,22)!=(owner.xid().sequence()&0xffff)
                || fields.get(3).length<11)fail("OWNER");
        byte[] oldKtb=fields.get(2);
        if(!(oldKtb.length==8 && oldKtb[0]==3 && oldKtb[1]==0x0d
                || oldKtb.length==16 && oldKtb[0]==2 && oldKtb[1]==0x0d
                || oldKtb.length==32 && oldKtb[0]==4 && oldKtb[1]==0x0d))fail("INVERSE_KTB");
        var forward=decode(row.fields(),row.dba(),row.opcodeMinor());
        var inverse=decode(fields.subList(2,fields.size()),row.dba(),fields.get(3)[10]&255);
        if(forward.kind()==inverse.kind() || forward.slot()!=inverse.slot())fail("OPERATION_OR_SLOT");
        var bound=new HeapRowPatch.Patch(forward.kind(),forward.dba(),forward.slot(),1,xid,uba,forward.columns(),forward.extent());
        return new HeapUndoPair(record.containerUid(),row.containerId(),bound,
                new UndoRowPatch(owner,inverse,u32(m,12),m[19]&255,u16(m,22)));
    }
    private static HeapRowPatch.Patch decode(List<byte[]> fields,long block,int op)throws RedoException {
        if(fields.size()<2 || fields.get(1).length!=(op==2?52:op==3?20:0))fail("KDO_LENGTH");
        byte[] k=fields.get(1);
        if(u32(k,0)!=block || k[10]!=op || k[11]!=1 || (k[12]&255)<1 || k[13]!=0 || u16(k,14)!=0)fail("KDO_HEADER");
        var columns=new ArrayList<HeapRowPatch.Column>();
        for(int i=0;i<25;i++)columns.add(new HeapRowPatch.Column(HeapRowPatch.State.ABSENT,new byte[0]));
        HeapRowPatch.RowExtent extent=null;int slot;
        if(op==2) {
            if(k[16]!=0x2c || k[18]!=25 || k[44]!=0 || fields.size()!=27)fail("INSERT_LAYOUT");
            slot=u16(k,42);int size=3+25;
            for(int i=0;i<25;i++) {
                byte[] value=fields.get(i+2);
                if(((k[45+i/8]>>>(i%8))&1)!=(value.length==0?1:0))fail("NULL_BITMAP");
                if(value.length!=0)OracleScalars.number(value);
                if(i==0 && value.length==0)fail("OBJECT_REQUIRED");
                size+=value.length;columns.set(i,new HeapRowPatch.Column(value.length==0?HeapRowPatch.State.NULL:HeapRowPatch.State.VALUE,value.clone()));
            }
            if(size!=u16(k,40))fail("ROW_SIZE");extent=new HeapRowPatch.RowExtent(25,25,-1,size);
        } else {if(fields.size()!=2)fail("DELETE_LAYOUT");slot=u16(k,16);}
        return new HeapRowPatch.Patch(op==2?HeapRowPatch.Kind.INSERT:HeapRowPatch.Kind.DELETE,block,slot,1,null,null,List.copyOf(columns),extent);
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_DEFERRED_STORAGE_"+detail);}
}
