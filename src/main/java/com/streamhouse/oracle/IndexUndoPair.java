package com.streamhouse.oracle;

import java.util.Arrays;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed single leaf insert/delete and its same-record 10.22 undo. Not a logical DML event. */
public record IndexUndoPair(long containerUid,long containerId,Forward forward,Inverse undo) {
    public enum Kind { INSERT, DELETE, PURGE, RESTORE }
    /** DELETE carries no key/keydata. Historical ITL state is not exposed as current identity. */
    public record Forward(Kind kind,long block,int itl,int slot,int rowSize,HeapRowPatch.Xid xid,
                          HeapRowPatch.Uba uba,byte[] key,byte[] keydata) {}
    public record Inverse(UndoHeader identity,Kind kind,long indexId,long block,int itl,int flags,int sdc,
                          long previousDba,int predecessorRecord,int wrap,byte[] key,byte[] keydata) {}

    public static Forward decodeForward(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor()!=10 || (vector.opcodeMinor()!=2 && vector.opcodeMinor()!=4))
            throw new RedoException("UNSUPPORTED_INDEX_FORWARD_OPCODE");
        var fields=vector.fields();
        int count=vector.opcodeMinor()==2 ? 4 : 2;
        if (fields.size()!=count || fields.get(1).length!=6)
            throw new RedoException("UNSUPPORTED_INDEX_FORWARD_FIELDS");
        byte[] ktb=fields.get(0),leaf=fields.get(1);
        if (ktb.length<2 || (ktb[1]&255)!=0x0d || leaf[0]==0 || leaf[1]!=0)
            throw new RedoException("UNSUPPORTED_INDEX_FORWARD_MODE");
        HeapRowPatch.Xid xid=null;
        int offset;
        if (((ktb[0]&255)==1 && ktb.length==24) || ((ktb[0]&255)==17 && ktb.length==70)) {
            xid=new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12)); offset=16;
        } else if ((ktb[0]&255)==2 && ktb.length==16) offset=8;
        else throw new RedoException("UNSUPPORTED_INDEX_FORWARD_KTB");
        var uba=new HeapRowPatch.Uba(u32(ktb,offset),u16(ktb,offset+4),ktb[offset+6]&255);
        if (uba.dba()==0 || uba.record()==0) throw new RedoException("INVALID_INDEX_FORWARD_UBA");
        byte[] key=count==4 ? fields.get(2).clone() : null;
        byte[] data=count==4 ? fields.get(3).clone() : null;
        if (count==4) validateKeyData(key,data);
        int size=u16(leaf,4);
        if (size<4 || (count==4 && size!=4+key.length+data.length))
            throw new RedoException("INDEX_LEAF_ROW_SIZE_MISMATCH");
        return new Forward(count==4 ? Kind.INSERT : Kind.DELETE,vector.dba(),leaf[0]&255,u16(leaf,2),size,xid,uba,key,data);
    }

    public static Inverse decodeUndo(RedoRecords.Vector vector) throws RedoException {
        var identity=UndoHeader.decode(vector);
        var fields=vector.fields();
        if (fields.size()!=5 && fields.size()!=6) throw new RedoException("UNSUPPORTED_INDEX_UNDO_FIELDS");
        byte[] metadata=fields.get(1),ktb=fields.get(2),leaf=fields.get(3);
        if ((metadata[16]&255)!=10 || (metadata[17]&255)!=22 || (metadata[18]&255)!=identity.xid().slot()
                || (!identity.beginsTransaction() && u16(metadata,22)!=(identity.xid().sequence()&0xffff)))
            throw new RedoException("INDEX_UNDO_IDENTITY_MISMATCH");
        if (ktb.length<2 || (ktb[1]&255)!=0x0d || !((ktb[0]==3 && ktb.length==8)
                || (ktb[0]==2 && ktb.length==16) || (ktb[0]==4 && ktb.length==32)))
            throw new RedoException("UNSUPPORTED_INDEX_UNDO_KTB");
        if (leaf.length!=20 || (leaf[0]!=3 && leaf[0]!=5) || leaf[1]==0 || (leaf[2]!=1 && leaf[2]!=5)
                || u32(leaf,4)==0 || u32(leaf,8)==0)
            throw new RedoException("UNSUPPORTED_INDEX_UNDO_LEAF");
        Kind kind=leaf[0]==3 ? Kind.PURGE : Kind.RESTORE;
        if (fields.size()!=(kind==Kind.PURGE ? 5 : 6)) throw new RedoException("INDEX_UNDO_OPERATION_FIELDS_MISMATCH");
        byte[] key=fields.get(4).clone(),data=kind==Kind.RESTORE ? fields.get(5).clone() : null;
        validateKeyData(key,data);
        return new Inverse(identity,kind,u32(leaf,4),u32(leaf,8),leaf[1]&255,leaf[2]&255,u16(leaf,12),
                u32(metadata,12),metadata[19]&255,u16(metadata,22),key,data);
    }

    public static IndexUndoPair bind(RedoRecords.Record record,RedoRecords.Vector row) throws RedoException {
        if (!record.vectors().contains(row)) throw new RedoException("INDEX_VECTOR_NOT_IN_RECORD");
        var forward=decodeForward(row);
        Inverse match=null;
        int rowMatches=0;
        for (var v:record.vectors()) {
            if (v.containerId()!=row.containerId()) continue;
            if (v.opcodeMajor()==10 && v.dba()==row.dba()) {
                var other=decodeForward(v);
                if (forward.uba().equals(other.uba())) rowMatches++;
            }
            if (v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.dba()!=forward.uba().dba()) continue;
            if (v.fields().isEmpty() || v.fields().get(0).length!=20)
                throw new RedoException("UNREADABLE_INDEX_UNDO_CANDIDATE");
            byte[] b=v.fields().get(0);
            if (u16(b,16)!=forward.uba().sequence() || (b[18]&255)!=forward.uba().record()) continue;
            if (match!=null) throw new RedoException("AMBIGUOUS_INDEX_UNDO_UBA");
            match=decodeUndo(v);
        }
        if (rowMatches!=1) throw new RedoException("AMBIGUOUS_INDEX_FORWARD_UBA");
        if (match==null) throw new RedoException("MISSING_INDEX_UNDO_UBA");
        if (match.kind()!=(forward.kind()==Kind.INSERT ? Kind.PURGE : Kind.RESTORE)
                || match.block()!=forward.block() || match.itl()!=forward.itl()
                || (forward.xid()!=null && !forward.xid().equals(match.identity().xid()))
                || (forward.kind()==Kind.INSERT && !Arrays.equals(forward.key(),match.key()))
                || (forward.kind()==Kind.DELETE && forward.rowSize()!=4+match.key().length+match.keydata().length))
            throw new RedoException("INDEX_UNDO_OPERATION_BLOCK_ITL_XID_OR_KEY_MISMATCH");
        return new IndexUndoPair(record.containerUid(),row.containerId(),forward,match);
    }

    /** Single short key component, opaque bytes; six-byte keydata is retained without dictionary interpretation. */
    private static void validateKeyData(byte[] key,byte[] data) throws RedoException {
        if (key.length<2 || key.length>254 || (key[0]&255)!=key.length-1 || (data!=null && data.length!=6))
            throw new RedoException("UNSUPPORTED_INDEX_KEY_OR_KEYDATA_LAYOUT");
    }
}
