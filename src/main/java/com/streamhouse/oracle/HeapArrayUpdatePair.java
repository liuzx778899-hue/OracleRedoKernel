package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Observed raw111 11.19 / KDO21 packed array UPDATE. Every row remains attached to one owned undo node. */
final class HeapArrayUpdatePair {
    static List<Patch> forward(RedoRecords.Vector vector,int columns)throws RedoException {
        if(vector.opcodeMajor()!=11 || vector.opcodeMinor()!=19 || vector.fields().size()!=6)throw new RedoException("UNSUPPORTED_ARRAY_UPDATE_FIELDS");
        var fields=vector.fields();var rows=rows(fields.subList(1,6),vector.dba(),false);var result=new ArrayList<Patch>();
        for(var row:rows) {
            var patch=new HeapRowPatch().decode(new RedoRecords.Vector(11,5,vector.blockClass(),vector.file(),vector.dba(),vector.scn(),vector.sequence(),vector.containerId(),List.of(fields.get(0),row.get(0),row.get(1),row.get(2))),columns);
            if(patch.uba()==null || patch.uba().dba()==0 || patch.uba().record()==0)throw new RedoException("ARRAY_UPDATE_CURRENT_UBA_REQUIRED");
            result.add(patch);
        }
        return List.copyOf(result);
    }
    static List<UndoRowPatch> inverse(RedoRecords.Vector vector,int columns)throws RedoException {
        var identity=UndoHeader.decode(vector);var fields=vector.fields();
        if(fields.size()!=8)throw new RedoException("UNSUPPORTED_ARRAY_UPDATE_UNDO_FIELDS");
        byte[] metadata=fields.get(1),ktb=fields.get(2);
        if(metadata[16]!=11 || metadata[17]!=1 || (metadata[18]&255)!=identity.xid().slot()
                || !identity.beginsTransaction() && u16(metadata,22)!=(identity.xid().sequence()&0xffff)
                || ktb.length!=8 || ktb[0]!=3 || ktb[1]!=0x0d)throw new RedoException("ARRAY_UPDATE_UNDO_IDENTITY");
        long dba=u32(fields.get(3),0);var result=new ArrayList<UndoRowPatch>();
        for(var row:rows(fields.subList(3,8),dba,true)) {
            var patch=HeapRowPatch.decodeKdo(List.of(ktb,row.get(0),row.get(1),row.get(2)),5,dba,columns);
            result.add(new UndoRowPatch(identity,patch,u32(metadata,12),metadata[19]&255,u16(metadata,22)));
        }
        return List.copyOf(result);
    }
    private static List<List<byte[]>> rows(List<byte[]> fields,long dba,boolean inverse)throws RedoException {
        byte[] header=fields.get(0),kdos=fields.get(1),maps=fields.get(2),sizes=fields.get(3),data=fields.get(4);
        if(header.length!=20 || u32(header,0)!=dba || u32(header,4)==0 || header[10]!=19 || header[11]!=1 || header[12]==0 || header[13]!=0)
            throw new RedoException("UNSUPPORTED_ARRAY_UPDATE_HEADER");
        int count=u16(header,16),aligned=(count*2+3)&~3;
        if(count==0 || kdos.length!=count*28 || maps.length!=count*2 || sizes.length!=aligned+16)
            throw new RedoException("ARRAY_UPDATE_ROW_COUNT");
        for(int i=count*2;i<aligned;i++)if(sizes[i]!=0)throw new RedoException("ARRAY_UPDATE_LENGTH_ALIGNMENT");
        var result=new ArrayList<List<byte[]>>();var slots=new HashSet<Integer>();int offset=0;
        for(int i=0;i<count;i++) {
            byte[] kdo=Arrays.copyOfRange(kdos,i*28,(i+1)*28);int length=u16(sizes,i*2);
            if(u32(kdo,0)!=dba || u32(kdo,4)!=u32(header,4) || kdo[10]!=21 || (kdo[11]&255)!=0x81
                    || kdo[12]!=header[12] || kdo[13]!=0 || kdo[17]!=(inverse?0:header[12])
                    || !slots.add(u16(kdo,20)) || length==0 || offset+length>data.length)
                throw new RedoException("ARRAY_UPDATE_ROW_PROFILE_OR_LENGTH");
            kdo[10]=5; // KDO21 row payload is the observed ordinary packed URP suffix representation.
            result.add(List.of(kdo,Arrays.copyOfRange(maps,i*2,i*2+2),Arrays.copyOfRange(data,offset,offset+length)));offset+=length;
        }
        if(offset!=data.length)throw new RedoException("ARRAY_UPDATE_PAYLOAD_REMAINDER");return List.copyOf(result);
    }
    static List<HeapUndoPair> bind(RedoRecords.Record record,RedoRecords.Vector row,int columns)throws RedoException {
        if(!record.vectors().contains(row))throw new RedoException("ARRAY_UPDATE_NOT_IN_RECORD");
        var forward=forward(row,columns);var uba=forward.get(0).uba();RedoRecords.Vector undo=null;int owners=0;
        for(var v:record.vectors()) {
            if(v.containerId()!=row.containerId())continue;
            if(v.opcodeMajor()==11 && v.opcodeMinor()==19 && forward(v,columns).get(0).uba().equals(uba))owners++;
            if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && UndoHeader.decode(v).uba().equals(uba)) {
                if(undo!=null)throw new RedoException("ARRAY_UPDATE_UNDO_AMBIGUOUS");undo=v;
            }
        }
        if(owners!=1 || undo==null)throw new RedoException("ARRAY_UPDATE_OWNER_COUNT");
        var inverse=inverse(undo,columns);var identity=inverse.get(0).identity();
        if(identity.dataObjectId()!=row.objectId() || forward.size()!=inverse.size())throw new RedoException("ARRAY_UPDATE_OBJECT_OR_ROW_COVERAGE");
        byte[] f=row.fields().get(1),u=undo.fields().get(3);
        if(!Arrays.equals(Arrays.copyOf(f,18),Arrays.copyOf(u,18)) || !Arrays.equals(row.fields().get(3),undo.fields().get(5)))
            throw new RedoException("ARRAY_UPDATE_HEADER_OR_MAP_MISMATCH");
        var result=new ArrayList<HeapUndoPair>();
        for(int i=0;i<forward.size();i++) {
            var p=forward.get(i);var q=inverse.get(i);
            byte[] a=Arrays.copyOfRange(row.fields().get(2),i*28,i*28+28),b=Arrays.copyOfRange(undo.fields().get(4),i*28,i*28+28);
            a[17]=0;if(!Arrays.equals(a,b) || p.xid()!=null && !p.xid().equals(identity.xid()) || !p.uba().equals(identity.uba())
                    || p.dba()!=q.inverse().dba() || p.slot()!=q.inverse().slot())throw new RedoException("ARRAY_UPDATE_ROW_OR_OWNER_MISMATCH");
            result.add(new HeapUndoPair(record.containerUid(),row.containerId(),p,q));
        }
        return List.copyOf(result);
    }
}
