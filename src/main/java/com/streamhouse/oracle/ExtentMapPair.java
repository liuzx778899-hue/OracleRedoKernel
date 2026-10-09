package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c extent ADD and its same-record 14.5 undo. Not a row or transaction receipt. */
public record ExtentMapPair(HeapRowPatch.Xid xid,HeapRowPatch.Uba uba,long dataObject,
                            long segment,long extent,long blocks,long previousExtents,long previousBlocks) {
    public static ExtentMapPair bind(RedoRecords.Record record,RedoRecords.Vector undo,RedoRecords.Vector forward) throws RedoException {
        return bind(record,undo,forward,false);
    }
    static ExtentMapPair bindLob(RedoRecords.Record record,RedoRecords.Vector undo,RedoRecords.Vector forward)throws RedoException {
        if(undo.fields().size()<2 || undo.fields().get(1).length<8 || forward.objectId()<=0
                || u32(undo.fields().get(1),4)!=forward.objectId())throw new RedoException("LOB_EXTENT_OBJECT_SCOPE");
        return bind(record,undo,forward,true);
    }
    private static ExtentMapPair bind(RedoRecords.Record record,RedoRecords.Vector undo,RedoRecords.Vector forward,boolean lob)throws RedoException {
        if (!record.vectors().contains(undo) || !record.vectors().contains(forward)
                || undo.containerId()!=forward.containerId() || undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1
                || forward.opcodeMajor()!=14 || forward.opcodeMinor()!=4)
            throw new RedoException("EXTENT_MAP_PAIR_RECORD_OR_OPCODE_MISMATCH");
        var u=undo.fields();var f=forward.fields();
        if (u.size()!=5 || u.get(0).length!=20 || u.get(1).length!=24 || u.get(2).length!=24
                || u.get(3).length!=16 || u.get(4).length!=20
                || f.size()!=3 || f.get(0).length!=16 || f.get(1).length!=16 || f.get(2).length!=20)
            throw new RedoException("UNSUPPORTED_EXTENT_MAP_PAIR_LAYOUT");
        byte[] id=u.get(0),h=u.get(1),map=u.get(2),delete=u.get(3),old=u.get(4),add=f.get(0),axis=f.get(1),now=f.get(2);
        var xid=new HeapRowPatch.Xid(u16(id,8),u16(id,10),u32(id,12));
        long segment=u32(map,0),extent=u32(map,16),blocks=u32(map,20);
        if (u32(h,0)!=1 || u32(h,4)==0xffffffffL || h[16]!=14 || h[17]!=5 || u16(h,20)!=0x80
                || (h[18]&255)!=xid.slot() || u16(h,22)!=(xid.sequence()&0xffff)
                || u32(map,4)!=4 || u32(map,8)!=segment || u32(map,12)!=2 || forward.dba()!=segment
                || delete[0]!=2 || u32(delete,4)!=u32(add,12) || u32(delete,8)!=0 || u32(delete,12)!=0
                || u32(add,0)!=1 || u32(add,4)!=extent || u32(add,8)!=blocks || blocks==0
                || axis[0]!=12 || u32(axis,4)!=u32(add,12) || u32(axis,12)!=extent+(lob?1:0)
                || old[0]!=5 || now[0]!=5 || u32(now,4)!=u32(old,4)+1 || u32(now,8)!=u32(old,8)+blocks
                || u32(old,12)!=0 || u32(old,16)!=0 || u32(now,12)!=0 || u32(now,16)!=0)
            throw new RedoException("EXTENT_MAP_PAIR_VALUES_DISAGREE");
        return new ExtentMapPair(xid,new HeapRowPatch.Uba(undo.dba(),u16(id,16),id[18]&255),
                u32(h,4),segment,extent,blocks,u32(old,4),u32(old,8));
    }
}
