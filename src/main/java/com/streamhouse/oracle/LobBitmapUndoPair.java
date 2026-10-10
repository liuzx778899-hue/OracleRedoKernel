package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed BasicFile 13.23 / 13.22 bitmap pair; parent scope must come from owned metadata. */
public record LobBitmapUndoPair(long containerUid,long containerId,UndoHeader owner,long bitmapDba,long parentDba,
                                boolean parentCompensation,byte[] inverse,long previousDba,int predecessorRecord,int wrap) {
    public LobBitmapUndoPair {inverse=inverse.clone();}
    @Override public byte[] inverse() {return inverse.clone();}
    private static void primary(RedoRecords.Vector row,long object,long dba)throws RedoException {
        if(row.opcodeMajor()!=13 || row.opcodeMinor()!=22 || row.blockClass()!=8 || row.objectId()!=object
                || row.dba()!=dba || row.fields().size()!=1)throw new RedoException("LOB_BITMAP_PRIMARY_SCOPE");
        byte[] b=row.fields().get(0);
        if(b.length==8 && (b[0]==0x13 || b[0]==0x14) && (u32(b,4)==1 || u32(b,4)==2))return;
        if(b.length==16 && b[0]==4 && u32(b,4)==1 && (b[8]==0 || b[8]==1) && u32(b,12)>0)return;
        throw new RedoException("UNSUPPORTED_LOB_BITMAP_PRIMARY_PROFILE");
    }
    public static LobBitmapUndoPair bind(RedoRecords.Record record,RedoRecords.Vector original,long object,long parentDba)throws RedoException {
        if(!record.vectors().contains(original))throw new RedoException("LOB_BITMAP_ORIGINAL_NOT_IN_RECORD");
        var owner=UndoHeader.decode(original);var f=original.fields();var m=f.get(1);
        if(owner.objectId()!=object || owner.dataObjectId()!=object || owner.beginsTransaction() || parentDba<0
                || f.size()!=4 || m[16]!=13 || m[17]!=23 || m[18]!=(byte)owner.xid().slot() || u16(m,22)!=(owner.xid().sequence()&0xffff)
                || f.get(2).length!=20)throw new RedoException("LOB_BITMAP_ORIGINAL_SCOPE_OR_PROFILE");
        byte[] b=f.get(2);long dba=u32(b,0);
        if(dba==0 || u32(b,4)!=8 || u32(b,8)>1 || (u32(b,8)==1 && parentDba==0) || u32(b,12)!=9 || u32(b,16)!=2)
            throw new RedoException("LOB_BITMAP_INVERSE_METADATA");
        var inverse=new RedoRecords.Vector(13,22,8,original.file(),dba,original.scn(),original.sequence(),original.containerId(),List.of(f.get(3)),object);
        primary(inverse,object,dba);int rows=0,undo=0;
        for(var row:record.vectors()) {
            if(row.containerId()!=original.containerId())continue;
            if(row.opcodeMajor()==13 && row.opcodeMinor()==22 && row.dba()==dba) {
                primary(row,object,dba);byte[] after=row.fields().get(0),before=f.get(3);
                if(after.length!=before.length || (after.length==8 && (before[0]!=0x13 || after[0]!=0x14 || u32(after,4)!=u32(before,4)))
                        || (after.length==16 && (before[8]!=0 || after[8]!=1 || u32(after,12)!=u32(before,12))))
                    throw new RedoException("LOB_BITMAP_FORWARD_INVERSE_EXTENT");rows++;
            }
            if(row.opcodeMajor()==5 && row.opcodeMinor()==1 && row.fields().size()==4
                    && row.fields().get(1).length>=24 && row.fields().get(1)[16]==13 && row.fields().get(1)[17]==23
                    && row.fields().get(2).length==20 && u32(row.fields().get(2),0)==dba)undo++;
        }
        if(rows!=1 || undo!=1)throw new RedoException("LOB_BITMAP_SAME_RECORD_PAIR_COUNT");
        return new LobBitmapUndoPair(record.containerUid(),original.containerId(),owner,dba,parentDba,u32(b,8)==1,f.get(3),u32(m,12),m[19]&255,u16(m,22));
    }
    public static LobBitmapUndoPair matchCompensation(RedoRecords.Record record,RedoRecords.Vector marker,
                                                     List<LobBitmapUndoPair> history,UndoHeader original)throws RedoException {
        if(!record.vectors().contains(marker))throw new RedoException("LOB_BITMAP_MARKER_NOT_IN_RECORD");
        var c=UndoCompensation.decode(marker);
        if(marker.opcodeMinor()!=6 || c.beginsTransaction() || c.crossesUndoBlock() || c.opcodeMajor()!=13 || c.opcodeMinor()!=23)
            throw new RedoException("UNSUPPORTED_LOB_BITMAP_COMPENSATION");
        LobBitmapUndoPair match=null;
        for(var pair:history) {
            if(pair.containerUid()!=record.containerUid() || pair.containerId()!=marker.containerId() || !pair.owner().equals(original)
                    || c.dba()!=original.uba().dba() || c.slot()!=original.xid().slot() || c.objectId()!=original.objectId()
                    || c.dataObjectId()!=original.dataObjectId() || c.tablespace()!=original.tablespace()
                    || c.previousDba()!=pair.previousDba() || c.predecessorRecord()!=pair.predecessorRecord() || c.wrap()!=pair.wrap())continue;
            if(match!=null)throw new RedoException("LOB_BITMAP_COMPENSATION_AMBIGUOUS_ORIGINAL");match=pair;
        }
        if(match==null)throw new RedoException("LOB_BITMAP_COMPENSATION_MISSING_ORIGINAL");
        int rows=0,parents=0;
        for(var row:record.vectors()) {
            if(row.containerId()!=marker.containerId() || row.opcodeMajor()!=13)continue;
            if(row.opcodeMinor()==22) {
                primary(row,original.dataObjectId(),match.bitmapDba());
                if(!Arrays.equals(row.fields().get(0),match.inverse()))throw new RedoException("LOB_BITMAP_COMPENSATION_INVERSE_BYTES");rows++;
            } else if(row.opcodeMinor()==24) {
                if(!match.parentCompensation() || row.objectId()!=original.dataObjectId() || row.blockClass()!=9 || row.dba()!=match.parentDba()
                        || row.fields().size()!=1 || !Arrays.equals(row.fields().get(0),new byte[]{1,0,0,0,1,0,0,0,5,0,0,0,1,0,0,0}))
                    throw new RedoException("LOB_BITMAP_COMPENSATION_PARENT_SCOPE_OR_PROFILE");parents++;
            } else throw new RedoException("UNBOUND_LOB_BITMAP_COMPENSATION_VECTOR");
        }
        if(rows!=1 || parents!=(match.parentCompensation()?1:0))throw new RedoException("LOB_BITMAP_COMPENSATION_VECTOR_COUNT");
        return match;
    }
}
