package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Owned 19.3 ARRAY leaf INSERT / multi-key PURGE. Key sizes, slots and opaque rowids stay explicit. */
public final class IndexArrayUndoPair {
    public static List<IndexUndoPair.Forward> forward(RedoRecords.Vector vector)throws RedoException {
        var fields=vector.fields();
        if(vector.opcodeMajor()!=10 || vector.opcodeMinor()!=2 || fields.size()!=5)
            throw new RedoException("UNSUPPORTED_INDEX_ARRAY_OPCODE_OR_FIELDS");
        byte[] ktb=fields.get(0),leaf=fields.get(1),keys=fields.get(2),data=fields.get(3),sizes=fields.get(4);
        boolean cleanout=ktb.length==70 && ktb[0]==17 && ktb[44]==2 && ktb[45]==1 && u16(ktb,46)==0x0d
                && u16(ktb,52)==0x8000 && u16(ktb,54)==0 && u16(ktb,56)==0x0602 && u16(ktb,58)==0x8000
                && u16(ktb,64)==0 && u32(ktb,66)==0;
        boolean current=ktb.length==16 && ktb[0]==2;
        if(!(ktb.length==24 && ktb[0]==1 || cleanout || current) || ktb[1]!=0x0d || leaf.length<12 || leaf[0]==0 || leaf[1]!=0x20
                || u16(leaf,6)!=0 || u16(leaf,10)!=0)
            throw new RedoException("UNSUPPORTED_INDEX_ARRAY_FORWARD_PROFILE");
        int count=u16(leaf,8);
        boolean paddedFifteen=count==15 && leaf.length==12+count*2+2 && u16(leaf,leaf.length-2)==0;
        if(count==0 || leaf.length!=12+count*2 && !paddedFifteen || sizes.length!=count*2 || data.length!=count*6
                || u16(leaf,4)!=count*4+keys.length+data.length
                // Native archives90/107 show sno at the first/last slot; the explicit slots own each key.
                || u16(leaf,2)!=u16(leaf,12) && u16(leaf,2)!=u16(leaf,12+(count-1)*2))
            throw new RedoException("INDEX_ARRAY_FORWARD_COUNT_OR_SIZE");
        var xid=current?null:new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
        int ubaOffset=current?8:16;
        var uba=new HeapRowPatch.Uba(u32(ktb,ubaOffset),u16(ktb,ubaOffset+4),ktb[ubaOffset+6]&255);
        if(uba.dba()==0 || uba.record()==0)throw new RedoException("INVALID_INDEX_ARRAY_UBA");
        var result=new ArrayList<IndexUndoPair.Forward>();int offset=0;var slots=new HashSet<Integer>();
        for(int i=0;i<count;i++) {
            int slot=u16(leaf,12+i*2),length=u16(sizes,i*2);
            if(!slots.add(slot) || length<2 || length>254 || offset+length>keys.length || (keys[offset]&255)!=length-1)
                throw new RedoException("INDEX_ARRAY_SLOT_OR_KEY_LENGTH");
            byte[] key=Arrays.copyOfRange(keys,offset,offset+length),rowid=Arrays.copyOfRange(data,i*6,i*6+6);offset+=length;
            result.add(new IndexUndoPair.Forward(IndexUndoPair.Kind.INSERT,vector.dba(),leaf[0]&255,slot,4+length+6,xid,uba,key,rowid));
        }
        if(offset!=keys.length)throw new RedoException("INDEX_ARRAY_KEY_REMAINDER");
        return List.copyOf(result);
    }
    static List<IndexUndoPair.Inverse> inverse(RedoRecords.Vector vector)throws RedoException {
        var identity=UndoHeader.decode(vector);var fields=vector.fields();
        if(fields.size()!=6)throw new RedoException("UNSUPPORTED_INDEX_ARRAY_INVERSE_FIELDS");
        byte[] metadata=fields.get(1),ktb=fields.get(2),leaf=fields.get(3),keys=fields.get(4),bitmap=fields.get(5);
        if(metadata[16]!=10 || metadata[17]!=22 || (metadata[18]&255)!=identity.xid().slot()
                || !identity.beginsTransaction() && u16(metadata,22)!=(identity.xid().sequence()&0xffff)
                || !(ktb.length==32 && ktb[0]==4 || ktb.length==16 && ktb[0]==2) || ktb[1]!=0x0d || leaf.length<24
                || leaf[0]!=3 || leaf[1]==0 || leaf[2]!=0x21 || (leaf[3]!=0 && !(leaf[3]==(byte)0x93 && u16(leaf,20)==10 && u16(leaf,18)==0)) || u32(leaf,4)==0 || u32(leaf,8)==0
                || u32(leaf,14)!=0 || u16(leaf,22)!=0)
            throw new RedoException("UNSUPPORTED_INDEX_ARRAY_INVERSE_PROFILE");
        int count=u16(leaf,20);
        boolean paddedFifteen=count==15 && leaf.length==24+count*2+2 && u16(leaf,leaf.length-2)==0;
        if(count==0 || leaf.length!=24+count*2 && !paddedFifteen || bitmap.length!=(count+7)/8)
            throw new RedoException("INDEX_ARRAY_INVERSE_COUNT");
        for(byte value:bitmap)if(value!=(byte)0xff)throw new RedoException("UNSUPPORTED_INDEX_ARRAY_PURGE_BITMAP");
        var result=new ArrayList<IndexUndoPair.Inverse>();int offset=0;
        for(int i=0;i<count;i++) {
            int length=u16(leaf,24+i*2);
            if(length<2 || length>254 || offset+length>keys.length || (keys[offset]&255)!=length-1)
                throw new RedoException("INDEX_ARRAY_INVERSE_KEY_LENGTH");
            var key=Arrays.copyOfRange(keys,offset,offset+length);offset+=length;
            result.add(new IndexUndoPair.Inverse(identity,IndexUndoPair.Kind.PURGE,u32(leaf,4),u32(leaf,8),leaf[1]&255,leaf[2]&255,
                    u16(leaf,12),u32(metadata,12),metadata[19]&255,u16(metadata,22),key,null));
        }
        if(offset!=keys.length)throw new RedoException("INDEX_ARRAY_INVERSE_KEY_REMAINDER");
        return List.copyOf(result);
    }
    public static List<IndexUndoPair> bind(RedoRecords.Record record,RedoRecords.Vector row)throws RedoException {
        if(!record.vectors().contains(row))throw new RedoException("INDEX_ARRAY_VECTOR_NOT_IN_RECORD");
        var forward=forward(row);var uba=forward.get(0).uba();RedoRecords.Vector undo=null;int owners=0;
        if(row.fields().get(0).length==70 && (scn(row.fields().get(0),48)>record.scn()
                || scn(row.fields().get(0),60)>row.scn()))throw new RedoException("INDEX_ARRAY_CLEANOUT_SCN");
        for(var vector:record.vectors()) {
            if(vector.containerId()!=row.containerId())continue;
            if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2) {
                var other=vector.fields().size()==5?forward(vector).get(0):IndexUndoPair.decodeForward(vector);
                if(other.uba().equals(uba))owners++;
            }
            if(vector.opcodeMajor()!=5 || vector.opcodeMinor()!=1 || vector.dba()!=uba.dba())continue;
            if(!UndoHeader.decode(vector).uba().equals(uba))continue;
            if(undo!=null)throw new RedoException("AMBIGUOUS_INDEX_ARRAY_UNDO");undo=vector;
        }
        if(owners!=1 || undo==null)throw new RedoException("INDEX_ARRAY_OWNER_COUNT");
        var inverse=inverse(undo);
        if(forward.size()!=inverse.size())throw new RedoException("INDEX_ARRAY_INVERSE_KEY_COUNT");
        var result=new ArrayList<IndexUndoPair>();
        for(int i=0;i<forward.size();i++) {
            var f=forward.get(i);var u=inverse.get(i);
            if(f.block()!=u.block() || f.itl()!=u.itl() || f.xid()!=null && !f.xid().equals(u.identity().xid()) || !Arrays.equals(f.key(),u.key()))
                throw new RedoException("INDEX_ARRAY_INVERSE_BLOCK_ITL_XID_OR_KEY");
            result.add(new IndexUndoPair(record.containerUid(),row.containerId(),f,u));
        }
        return List.copyOf(result);
    }
}
