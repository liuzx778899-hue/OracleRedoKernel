package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.HeapRowPatch.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 QMI / QMD pair. One owned undo node retains every physical row, in source order. */
public final class HeapArrayUndoPair {
    public static List<Patch> forward(RedoRecords.Vector vector,int columns)throws RedoException {
        if(vector.opcodeMajor()!=11 || vector.opcodeMinor()!=11 || vector.fields().size()!=4)
            throw new RedoException("UNSUPPORTED_HEAP_ARRAY_OPCODE_OR_FIELDS");
        byte[] ktb=vector.fields().get(0);
        if(ktb.length!=24 || ktb[0]!=1 || ktb[1]!=0x0d)
            throw new RedoException("UNSUPPORTED_HEAP_ARRAY_KTB");
        var xid=new Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
        var uba=new Uba(u32(ktb,16),u16(ktb,20),ktb[22]&255);
        if(uba.dba()==0 || uba.record()==0)throw new RedoException("INVALID_HEAP_ARRAY_UBA");
        var slots=slots(vector.fields().get(1),11,1,vector.dba());
        byte[] sizes=vector.fields().get(2),data=vector.fields().get(3);
        if(columns<1 || columns>255 || sizes.length!=slots.size()*2)
            throw new RedoException("HEAP_ARRAY_COLUMNS_OR_LENGTH_COUNT");
        var rows=new ArrayList<Patch>();int offset=0;
        for(int ordinal=0;ordinal<slots.size();ordinal++) {
            int length=u16(sizes,ordinal*2),end=offset+length;
            if(length<3 || end>data.length || data[offset]!=0x2c || data[offset+1]!=0)
                throw new RedoException("UNSUPPORTED_HEAP_ARRAY_ROW_PROFILE");
            int stored=data[offset+2]&255;offset+=3;
            if(stored==0 || stored>columns)throw new RedoException("HEAP_ARRAY_ROW_WIDTH");
            var values=new ArrayList<Column>();
            for(int col=0;col<stored;col++) {
                if(offset>=end)throw new RedoException("TRUNCATED_HEAP_ARRAY_VALUE");
                int size=data[offset++]&255;
                if(size==255)values.add(new Column(State.NULL,new byte[0]));
                else {
                    if(size==0 || size>250 || offset+size>end)throw new RedoException("UNSUPPORTED_HEAP_ARRAY_VALUE_LENGTH");
                    values.add(new Column(State.VALUE,Arrays.copyOfRange(data,offset,offset+size)));offset+=size;
                }
            }
            if(offset!=end)throw new RedoException("HEAP_ARRAY_ROW_LENGTH_MISMATCH");
            while(values.size()<columns)values.add(new Column(State.ABSENT,new byte[0]));
            rows.add(new Patch(Kind.INSERT,vector.dba(),slots.get(ordinal),1,xid,uba,List.copyOf(values),new RowExtent(stored,stored,-1,null)));
        }
        if(offset!=data.length)throw new RedoException("HEAP_ARRAY_PAYLOAD_REMAINDER");
        return List.copyOf(rows);
    }
    static List<UndoRowPatch> inverse(RedoRecords.Vector vector,int columns)throws RedoException {
        var identity=UndoHeader.decode(vector);var fields=vector.fields();
        if(columns<1 || columns>255 || fields.size()!=4)
            throw new RedoException("UNSUPPORTED_HEAP_ARRAY_INVERSE_FIELDS");
        byte[] ktb=fields.get(2);
        // Native archives90/107 both identify Z with no owner; trailing padding is not an XID/UBA.
        if(ktb.length!=8 || ktb[0]!=3 || ktb[1]!=0x0d || ktb[2]!=0 || ktb[3]!=0 && ktb[3]!=(byte)0x80)
            throw new RedoException("UNSUPPORTED_HEAP_ARRAY_INVERSE_KTB");
        byte[] metadata=fields.get(1),kdo=fields.get(3);
        if(metadata[16]!=11 || metadata[17]!=1 || (metadata[18]&255)!=identity.xid().slot()
                || !identity.beginsTransaction() && u16(metadata,22)!=(identity.xid().sequence()&0xffff))
            throw new RedoException("HEAP_ARRAY_INVERSE_IDENTITY");
        if(kdo.length<20)throw new RedoException("TRUNCATED_HEAP_ARRAY_INVERSE");
        var slots=slots(kdo,12,0,u32(kdo,0));var rows=new ArrayList<UndoRowPatch>();
        for(int slot:slots) {
            var absent=new ArrayList<Column>();for(int i=0;i<columns;i++)absent.add(new Column(State.ABSENT,new byte[0]));
            var patch=new Patch(Kind.DELETE,u32(kdo,0),slot,1,null,null,List.copyOf(absent));
            rows.add(new UndoRowPatch(identity,patch,u32(metadata,12),metadata[19]&255,u16(metadata,22)));
        }
        return List.copyOf(rows);
    }
    public static List<HeapUndoPair> bind(RedoRecords.Record record,RedoRecords.Vector row,int columns)throws RedoException {
        if(!record.vectors().contains(row))throw new RedoException("HEAP_ARRAY_VECTOR_NOT_IN_RECORD");
        var forward=forward(row,columns);var uba=forward.get(0).uba();RedoRecords.Vector undo=null;int owners=0;
        for(var vector:record.vectors()) {
            if(vector.containerId()!=row.containerId())continue;
            if(vector.opcodeMajor()==11 && vector.opcodeMinor()==11 && forward(vector,columns).get(0).uba().equals(uba))owners++;
            if(vector.opcodeMajor()!=5 || vector.opcodeMinor()!=1 || vector.dba()!=uba.dba())continue;
            var header=UndoHeader.decode(vector);if(!header.uba().equals(uba))continue;
            if(undo!=null)throw new RedoException("AMBIGUOUS_HEAP_ARRAY_UNDO");undo=vector;
        }
        if(owners!=1 || undo==null)throw new RedoException("HEAP_ARRAY_OWNER_COUNT");
        var inverse=inverse(undo,columns);
        if(forward.size()!=inverse.size())throw new RedoException("HEAP_ARRAY_INVERSE_ROW_COUNT");
        byte[] f=row.fields().get(1),u=undo.fields().get(3);
        if(f.length!=u.length)throw new RedoException("HEAP_ARRAY_INVERSE_BLOCK_PROFILE");
        // Preserve the observed final two opaque bytes; they are not another row slot.
        for(int i=0;i<f.length;i++)if(i!=10 && i!=17 && f[i]!=u[i])throw new RedoException("HEAP_ARRAY_INVERSE_BLOCK_PROFILE");
        var result=new ArrayList<HeapUndoPair>();
        for(int i=0;i<forward.size();i++) {
            var p=forward.get(i);var q=inverse.get(i);
            if(p.dba()!=q.inverse().dba() || p.slot()!=q.inverse().slot() || !p.xid().equals(q.identity().xid()))
                throw new RedoException("HEAP_ARRAY_INVERSE_ROW_OR_XID");
            result.add(new HeapUndoPair(record.containerUid(),row.containerId(),p,q));
        }
        return List.copyOf(result);
    }
    private static List<Integer> slots(byte[] kdo,int opcode,int lock,long dba)throws RedoException {
        if(kdo.length<20 || u32(kdo,0)!=dba || dba==0 || u32(kdo,4)==0 || (kdo[10]&255)!=opcode || kdo[11]!=1
                // Native QMI/QMD does not expose bytes14/15; bind compares the opaque bytes exactly.
                || kdo[12]==0 || kdo[13]!=0 || kdo[16]!=0 || (kdo[17]&255)!=lock)
            throw new RedoException("UNSUPPORTED_HEAP_ARRAY_KDO");
        int count=u16(kdo,18);
        if(count==0 || kdo.length!=22+count*2)throw new RedoException("HEAP_ARRAY_SLOT_COUNT");
        var slots=new ArrayList<Integer>();var seen=new HashSet<Integer>();
        for(int i=0;i<count;i++) {int slot=u16(kdo,20+i*2);if(!seen.add(slot))throw new RedoException("HEAP_ARRAY_DUPLICATE_SLOT");slots.add(slot);}
        return List.copyOf(slots);
    }
}
