package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed PUB split / HBB and FSB chunk queue undo. Not a page-write or publication authorization. */
public record SecureFileLobControlUndoPair(long containerUid,long containerId,UndoHeader owner,int undoMinor,
                                          long block,long segment,long chunk,long length,byte[] inverse) {
    public SecureFileLobControlUndoPair {inverse=inverse.clone();}
    @Override public byte[] inverse() {return inverse.clone();}
    private static HeapRowPatch.Xid xid(byte[] b,int p) {
        return new HeapRowPatch.Xid(u16(b,p),u16(b,p+2),u32(b,p+4));
    }
    /** Seq104 PUB cancellation restores the inverse body with a rollback SCN and a zero padded tail. */
    static SecureFileLobControlUndoPair matchPubCompensation(RedoRecords.Record record,RedoRecords.Vector marker,
            RedoRecords.Vector row,java.util.List<SecureFileLobControlUndoPair> history,UndoHeader original)throws RedoException {
        if(!record.vectors().contains(marker) || !record.vectors().contains(row) || marker.containerId()!=row.containerId())
            throw new RedoException("SECUREFILE_PUB_COMPENSATION_RECORD");
        var c=UndoCompensation.decode(marker);
        if(marker.opcodeMinor()!=6 || c.beginsTransaction() || c.crossesUndoBlock() || c.opcodeMajor()!=13 || c.opcodeMinor()!=54
                || c.objectId()!=original.objectId() || c.dataObjectId()!=original.dataObjectId() || c.tablespace()!=original.tablespace()
                || c.dba()!=original.uba().dba() || c.slot()!=original.xid().slot() || c.wrap()!=(original.xid().sequence()&0xffff)
                || row.opcodeMajor()!=13 || row.opcodeMinor()!=53 || row.blockClass()!=10 || row.objectId()!=original.dataObjectId()
                || row.fields().size()!=1 || row.fields().get(0).length!=140)
            throw new RedoException("SECUREFILE_PUB_COMPENSATION_SCOPE");
        var bytes=row.fields().get(0);SecureFileLobControlUndoPair match=null;
        for(var pair:history) {
            if(pair.undoMinor()!=54 || !pair.owner().equals(original) || pair.containerUid()!=record.containerUid()
                    || pair.containerId()!=row.containerId() || pair.block()!=row.dba())continue;
            var inverse=pair.inverse();
            if(scn(bytes,16)!=record.scn()-1 || scn(bytes,16)<scn(inverse,16) || (u16(bytes,20)&0x8000)!=(u16(inverse,20)&0x8000))
                throw new RedoException("SECUREFILE_PUB_COMPENSATION_SCN");
            for(int i=0;i<bytes.length;i++)if((i<16 || i>=22) && bytes[i]!=(i<inverse.length?inverse[i]:0))
                throw new RedoException("SECUREFILE_PUB_COMPENSATION_INVERSE");
            if(match!=null)throw new RedoException("SECUREFILE_PUB_COMPENSATION_AMBIGUOUS");match=pair;
        }
        if(match==null)throw new RedoException("SECUREFILE_PUB_COMPENSATION_MISSING_ORIGINAL");
        long rows=record.vectors().stream().filter(v->v.containerId()==row.containerId() && v.opcodeMajor()==13 && v.opcodeMinor()==53 && v.dba()==row.dba()).count();
        if(rows!=1)throw new RedoException("SECUREFILE_PUB_COMPENSATION_ROW_COUNT");return match;
    }
    public static SecureFileLobControlUndoPair bind(RedoRecords.Record record,RedoRecords.Vector original,
                                                   RedoRecords.Record forwardRecord,RedoRecords.Vector forward,long object)throws RedoException {
        if(!record.vectors().contains(original) || !forwardRecord.vectors().contains(forward)
                || record.containerUid()!=forwardRecord.containerUid() || original.containerId()!=forward.containerId())
            throw new RedoException("SECUREFILE_CONTROL_RECORD_OR_CONTAINER");
        var owner=UndoHeader.decode(original);var f=original.fields();var m=f.get(1);
        if(owner.beginsTransaction() || owner.objectId()!=object || owner.dataObjectId()!=object || f.size()!=4
                || m[16]!=13 || m[18]!=(byte)owner.xid().slot() || u16(m,22)!=(owner.xid().sequence()&0xffff))
            throw new RedoException("SECUREFILE_CONTROL_ORIGINAL_SCOPE");
        int minor=m[17]&255;byte[] metadata=f.get(2),body=f.get(3);
        if(forward.opcodeMajor()!=13 || forward.opcodeMinor()!=minor-1 || forward.objectId()!=object || forward.fields().size()!=1)
            throw new RedoException("SECUREFILE_CONTROL_FORWARD_SCOPE");
        byte[] after=forward.fields().get(0);long segment,chunk,length;
        if(minor==54) {
            if(metadata.length!=8 || u32(metadata,4)!=10 || body.length!=88 || u32(body,0)!=8 || u32(body,4)!=1
                    || u32(body,24)!=owner.tablespace() || u32(body,28)!=object || u32(body,32)==0
                    || u32(body,36)==0 || u32(body,40)==0 || !xid(body,56).equals(owner.xid()))
                throw new RedoException("UNSUPPORTED_SECUREFILE_PUB_UNDO");
            boolean split=after.length==28 && u32(after,0)==3 && xid(after,12).equals(owner.xid())
                    && u32(after,4)==u32(body,36) && u32(after,8)==u32(body,40) && scn(after,20)==scn(body,16)
                    && (forwardRecord.block()<record.block() || forwardRecord.block()==record.block() && forwardRecord.offset()<record.offset());
            // Seq104 reuses the PUB chunk after a quota rollback; forward precedes undo at the same SCN.
            boolean reuse=after.length==68 && u32(after,0)==4 && u32(after,4)==1 && u32(after,8)==0 && u32(after,12)==1
                    && u32(after,16)==u32(body,36) && u32(after,20)==u32(body,40) && u32(after,64)==2
                    && xid(after,36).equals(owner.xid()) && scn(after,44)==scn(body,16)
                    && (forwardRecord.block()<record.block() || forwardRecord.block()==record.block() && forwardRecord.offset()<record.offset()
                        || record.equals(forwardRecord) && record.vectors().indexOf(forward)<record.vectors().indexOf(original));
            if(reuse)for(int i:new int[]{24,28,32,52,56,60})if(u32(after,i)!=0)throw new RedoException("SECUREFILE_PUB_REUSE_RESERVED");
            if(forward.blockClass()!=10 || (!split && !reuse) || forwardRecord.scn()!=scn(body,16) || record.scn()!=scn(body,16))
                throw new RedoException("SECUREFILE_PUB_SPLIT_IDENTITY_OR_ORDER");
            for(int i=8;i<16;i++)if(body[i]!=0)throw new RedoException("SECUREFILE_PUB_UNDO_RESERVED");
            for(int i=44;i<56;i++)if(body[i]!=0)throw new RedoException("SECUREFILE_PUB_UNDO_RESERVED");
            for(int i=64;i<88;i++)if(body[i]!=0)throw new RedoException("SECUREFILE_PUB_UNDO_RESERVED");
            segment=u32(body,32);chunk=u32(body,36);length=u32(body,40);
        } else if(minor==50 || minor==52) {
            boolean fsb=minor==52;int cls=fsb ? 6 : 8;
            if(!record.equals(forwardRecord) || metadata.length!=(fsb ? 24 : 12) || u32(metadata,fsb ? 4 : 8)!=cls
                    || body.length!=80 || u32(body,0)!=(fsb ? 5 : 2) || u32(body,4)!=1 || u32(body,8)!=1
                    || u32(body,40)==0 || u32(body,44)==0 || u32(body,56)!=1 || u32(metadata,fsb ? 20 : 4)==0
                    || fsb && (u32(metadata,8)==0 || u32(metadata,12)==0 || u32(metadata,16)!=8))
                throw new RedoException("UNSUPPORTED_SECUREFILE_QUEUE_UNDO");
            if(forward.blockClass()!=cls || after.length!=136 || u32(after,0)!=(fsb ? 4 : 1)
                    || u32(after,4)!=1 || u32(after,8)!=1 || u32(after,12)!=(fsb ? 1 : 2)
                    || u32(after,16)!=u32(body,40) || u32(after,24)!=u32(body,44) || !xid(after,52).equals(owner.xid()))
                throw new RedoException("SECUREFILE_QUEUE_FORWARD_INVERSE");
            for(int i=12;i<40;i++)if(body[i]!=0)throw new RedoException("SECUREFILE_QUEUE_UNDO_RESERVED");
            for(int i=48;i<56;i++)if(body[i]!=0)throw new RedoException("SECUREFILE_QUEUE_UNDO_RESERVED");
            segment=u32(metadata,fsb ? 20 : 4);chunk=u32(body,40);length=u32(body,44);
        } else throw new RedoException("UNSUPPORTED_SECUREFILE_CONTROL_UNDO_OPCODE");
        if(u32(metadata,0)!=forward.dba())throw new RedoException("SECUREFILE_CONTROL_BLOCK_MISMATCH");
        int originals=0,rows=0;
        for(var candidate:record.vectors())if(candidate.containerId()==original.containerId() && candidate.opcodeMajor()==5
                && candidate.opcodeMinor()==1 && UndoHeader.decode(candidate).equals(owner))originals++;
        for(var candidate:forwardRecord.vectors())if(candidate.containerId()==forward.containerId() && candidate.opcodeMajor()==13
                && candidate.opcodeMinor()==minor-1 && candidate.dba()==forward.dba())rows++;
        if(originals!=1 || rows!=1)throw new RedoException("SECUREFILE_CONTROL_AMBIGUOUS_PAIR");
        return new SecureFileLobControlUndoPair(record.containerUid(),original.containerId(),owner,minor,forward.dba(),segment,chunk,length,body);
    }
}
