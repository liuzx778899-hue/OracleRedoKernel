package com.streamhouse.oracle;

import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed I_COL3 lock/link/branch pairs. Pair coverage alone never authorizes dictionary events. */
public record DictionaryIndexMaintenancePair(Kind kind,UndoHeader identity,long indexDba,long block,
        int itl,int slot,long previousLink,long nextLink,long separatorParent) {
    public enum Kind { LOCK, PREVIOUS_LINK, BRANCH_INSERT }
    public static DictionaryIndexMaintenancePair bind(RedoRecords.Record record,RedoRecords.Vector forward,
            FrozenHeapBaseline.ObjectRole role,long indexDba)throws RedoException {
        if(!role.owner().equals("SYS") || !role.name().equals("I_COL3") || indexDba<=0
                || !record.vectors().contains(forward) || forward.opcodeMajor()!=10
                || !List.of(6,11,15).contains(forward.opcodeMinor()))
            throw new RedoException("UNSUPPORTED_DICTIONARY_MAINTENANCE_SCOPE_OR_OPCODE");
        var f=forward.fields();int op=forward.opcodeMinor();
        if(f.size()!=(op==15?3:2) || f.get(0).length!=24 || f.get(0)[0]!=1 || f.get(0)[1]!=0x0d
                || f.get(1).length!=(op==6?2:8))throw new RedoException("UNSUPPORTED_DICTIONARY_MAINTENANCE_FORWARD");
        byte[] ktb=f.get(0),leaf=f.get(1);int itl=leaf[0]&255;
        var xid=new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
        var uba=new HeapRowPatch.Uba(u32(ktb,16),u16(ktb,20),ktb[22]&255);
        if(itl==0 || uba.dba()==0 || uba.record()==0)throw new RedoException("INVALID_DICTIONARY_MAINTENANCE_IDENTITY");
        RedoRecords.Vector inverse=null;
        for(var v:record.vectors())if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.containerId()==forward.containerId()) {
            var h=UndoHeader.decode(v);
            if(h.uba().equals(uba)) {
                if(inverse!=null)throw new RedoException("AMBIGUOUS_DICTIONARY_MAINTENANCE_UNDO");inverse=v;
            }
        }
        if(inverse==null)throw new RedoException("MISSING_DICTIONARY_MAINTENANCE_UNDO");
        var h=UndoHeader.decode(inverse);var u=inverse.fields();
        if(!h.xid().equals(xid) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0)
            throw new RedoException("DICTIONARY_MAINTENANCE_GENERATION_OR_XID_CONFLICT");
        if(u.size()!=5 || u.get(2).length!=32 || u.get(2)[0]!=4 || u.get(2)[1]!=0x0d
                || u.get(3).length!=12 || u.get(1)[16]!=10 || u.get(1)[17]!=21
                || (u.get(1)[18]&255)!=xid.slot() || !h.beginsTransaction() && u16(u.get(1),22)!=(xid.sequence()&0xffff))
            throw new RedoException("UNSUPPORTED_DICTIONARY_MAINTENANCE_UNDO");
        byte[] descriptor=u.get(3),extra=u.get(4);
        int action=op==6?7:op==11?11:16;
        if((descriptor[0]&255)!=action || u32(descriptor,4)!=forward.dba() || u32(descriptor,8)!=indexDba)
            throw new RedoException("DICTIONARY_MAINTENANCE_BLOCK_OR_SEGMENT_CONFLICT");
        int matches=0;
        for(var v:record.vectors())if(v.opcodeMajor()==10 && v.containerId()==forward.containerId()
                && v.dba()==forward.dba())matches++;
        if(matches!=1)throw new RedoException("AMBIGUOUS_DICTIONARY_MAINTENANCE_FORWARD");
        if(op==6) {
            if(leaf[1]!=5 || extra.length!=1 || (extra[0]&255)!=itl)
                throw new RedoException("UNSUPPORTED_DICTIONARY_PRE_SPLIT_LOCK");
            return new DictionaryIndexMaintenancePair(Kind.LOCK,h,indexDba,forward.dba(),itl,0,0,0,0);
        }
        if(op==11) {
            if(leaf[1]!=0 || u16(leaf,2)!=0 || extra.length!=8 || (extra[0]&255)!=itl
                    || extra[1]!=1 || u16(extra,2)!=0 || u32(leaf,4)==0 || u32(extra,4)==0
                    || u32(leaf,4)==u32(extra,4))throw new RedoException("UNSUPPORTED_DICTIONARY_PREVIOUS_LINK");
            return new DictionaryIndexMaintenancePair(Kind.PREVIOUS_LINK,h,indexDba,forward.dba(),itl,0,u32(extra,4),u32(leaf,4),0);
        }
        if(leaf[1]!=1 || extra.length!=4 || (extra[0]&255)!=itl || extra[1]!=0
                || u16(extra,2)!=u16(leaf,2) || u32(leaf,4)==0)
            throw new RedoException("UNSUPPORTED_DICTIONARY_BRANCH_INSERT");
        byte[] key=f.get(2);long parent;
        if(key.length<3 || (key[0]&255)!=key.length-2 || key[key.length-1]!=(byte)0xfe)
            throw new RedoException("UNSUPPORTED_DICTIONARY_BRANCH_SEPARATOR");
        try {parent=OracleScalars.number(java.util.Arrays.copyOfRange(key,1,key.length-1)).longValueExact();}
        catch(ArithmeticException e){throw new RedoException("NONINTEGRAL_DICTIONARY_BRANCH_SEPARATOR");}
        if(parent<=0 || parent>0xffffffffL)throw new RedoException("INVALID_DICTIONARY_BRANCH_SEPARATOR");
        return new DictionaryIndexMaintenancePair(Kind.BRANCH_INSERT,h,indexDba,forward.dba(),itl,u16(leaf,2),0,u32(leaf,4),parent);
    }
}
