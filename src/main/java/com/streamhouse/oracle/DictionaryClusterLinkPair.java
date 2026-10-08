package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Observed COL$ cluster-key SKL pair. Neither dictionary scope exclusion nor transaction closure authority. */
public record DictionaryClusterLinkPair(long containerUid,long containerId,FrozenHeapBaseline.ObjectRole role,
                                       UndoHeader identity,Link forward,Link inverse) {
    public record Link(int direction,long block,int slot,long linkBlock,int linkSlot,int lock) {}
    public static DictionaryClusterLinkPair bind(RedoRecords.Record record,RedoRecords.Vector row,
                                                  FrozenHeapBaseline.ObjectRole role)throws RedoException {
        if(!role.owner().equals("SYS") || !role.name().equals("COL$") || record.vectors().size()!=2
                || record.vectors().get(1)!=row || row.opcodeMajor()!=11 || row.opcodeMinor()!=10 || row.fields().size()!=2)
            throw new RedoException("UNSUPPORTED_DICTIONARY_CLUSTER_LINK_PAIR");
        var undo=record.vectors().get(0);
        if(undo.containerId()!=row.containerId() || undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1 || undo.fields().size()!=4)
            throw new RedoException("MISSING_DICTIONARY_CLUSTER_LINK_UNDO");
        byte[] ktb=row.fields().get(0);HeapRowPatch.Xid xid=null;int ubaAt;
        if(ktb.length==24 && ktb[0]==1 && ktb[1]==0x0d) {
            xid=new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));ubaAt=16;
        } else if(ktb.length==16 && ktb[0]==2 && ktb[1]==0x0d)ubaAt=8;
        else throw new RedoException("UNSUPPORTED_DICTIONARY_CLUSTER_LINK_KTB");
        var uba=new HeapRowPatch.Uba(u32(ktb,ubaAt),u16(ktb,ubaAt+4),ktb[ubaAt+6]&255);
        var h=UndoHeader.decode(undo);byte[] metadata=undo.fields().get(1),priorKtb=undo.fields().get(2);
        if(uba.dba()==0 || uba.record()==0 || undo.dba()!=uba.dba() || !uba.equals(h.uba())
                || xid!=null && !xid.equals(h.xid()) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject()
                || h.tablespace()!=0 || h.beginsTransaction() || metadata.length!=24 || metadata[16]!=11 || metadata[17]!=1
                || (metadata[18]&255)!=h.xid().slot() || u16(metadata,22)!=(h.xid().sequence()&0xffff))
            throw new RedoException("DICTIONARY_CLUSTER_LINK_IDENTITY_CONFLICT");
        if(!((priorKtb.length==32 && priorKtb[0]==4 && priorKtb[1]==0x0d)
                || (priorKtb.length==16 && priorKtb[0]==2 && priorKtb[1]==0x0d)))
            throw new RedoException("UNSUPPORTED_DICTIONARY_CLUSTER_LINK_INVERSE_KTB");
        var forward=decode(row.fields().get(1),row.dba());var inverse=decode(undo.fields().get(3),row.dba());
        if(forward.direction()!=inverse.direction() || forward.slot()!=inverse.slot() || forward.lock()!=1
                || u32(row.fields().get(1),4)!=u32(undo.fields().get(3),4))
            throw new RedoException("DICTIONARY_CLUSTER_LINK_OPERATION_CONFLICT");
        return new DictionaryClusterLinkPair(record.containerUid(),row.containerId(),role,h,forward,inverse);
    }
    private static Link decode(byte[] k,long block)throws RedoException {
        if(k.length!=32 || u32(k,0)!=block || u32(k,4)==0 || k[10]!=10 || k[11]!=1
                || (k[12]&255)==0 || k[13]!=0 || u16(k,14)!=0 || (k[28]!=1 && k[28]!=2) || (k[29]&255)>1)
            throw new RedoException("UNSUPPORTED_DICTIONARY_CLUSTER_LINK_KDO");
        int at=k[28]==1?16:22;
        long link=(long)(k[at]&255)<<24 | (long)(k[at+1]&255)<<16 | (long)(k[at+2]&255)<<8 | k[at+3]&255;
        int slot=(k[at+4]&255)<<8 | k[at+5]&255;
        if(link==0)throw new RedoException("INVALID_DICTIONARY_CLUSTER_LINK_POINTER");
        return new Link(k[28],block,u16(k,30),link,slot,k[29]);
    }
}
