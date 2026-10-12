package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.1 direct-load heap image; its ITL still requires a complete source-owned transaction. */
final class LobMigrationCopyImage {
    record Image(HeapRowPatch.Xid owner,long parent,long dba,List<ArchivedHeapRows.Cell> row) {Image {row=List.copyOf(row);}}
    static Image decode(RedoRecords.Vector vector,long object,int columns)throws RedoException {
        if(vector.opcodeMajor()!=19 || vector.opcodeMinor()!=1 || vector.blockClass()!=1 || vector.objectId()!=object
                || vector.fields().size()!=2 || vector.fields().get(0).length!=8168 || !Arrays.equals(vector.fields().get(1),new byte[]{6}))fail("PROFILE");
        byte[] b=vector.fields().get(0);long parent=u32(b,20);
        if(u32(b,0)!=1 || u32(b,4)!=object || scn(b,8)>vector.scn() || u16(b,14)!=0 || b[16]!=3 || b[17]!=0 || b[18]!=0x32 || b[19]!=0
                || parent<=0 || vector.dba()!=parent+3)fail("HEADER");
        var owner=new HeapRowPatch.Xid(u16(b,24),u16(b,26),u32(b,28));
        if(owner.usn()==0 || owner.sequence()==0)fail("OWNER");
        for(int i=32;i<104;i++)if(b[i]!=0)fail("ITL_OR_RESERVED");
        int base=104,end=u16(b,base+8),free=u16(b,base+10);
        if(b[base]!=0 || b[base+1]!=1 || u16(b,base+2)!=1 || u16(b,base+4)!=65535 || u16(b,base+6)!=20
                || end<20 || free!=end-20 || u16(b,base+12)!=free || u16(b,base+14)!=0 || u16(b,base+16)!=1 || u16(b,base+18)!=end)fail("ROW_DIRECTORY");
        int at=base+end;if(at+3>=b.length || b[at]!=0x2c || b[at+1]!=0 || (b[at+2]&255)!=columns)fail("ROW_HEADER");
        for(int i=base+20;i<at;i++)if(b[i]!=0)fail("UNACCOUNTED_BYTES");
        at+=3;var cells=new ArrayList<ArchivedHeapRows.Cell>();
        for(int i=0;i<columns;i++) {
            if(at>=b.length)fail("ROW_TRUNCATED");int size=b[at++]&255;
            if(size==255){cells.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""));continue;}
            if(size==0 || size>250 || at+size>b.length)fail("CELL_EXTENT");
            cells.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,HexFormat.of().formatHex(b,at,at+size)));at+=size;
        }
        if(at!=b.length)fail("ROW_EXTENT");return new Image(owner,parent,vector.dba(),cells);
    }
    static void requireStorage(RedoRecords.Record record,RedoRecords.Vector undo,Image image,long object)throws RedoException {
        var owner=UndoHeader.decode(undo);var f=undo.fields();var m=f.get(1);
        if(!owner.xid().equals(image.owner()) || owner.objectId()!=object || owner.dataObjectId()!=object || owner.tablespace()!=5
                || owner.beginsTransaction() || m[16]!=13 || m[18]!=(byte)owner.xid().slot() || u16(m,22)!=(owner.xid().sequence()&65535) || f.size()!=4)fail("STORAGE_OWNER");
        long root=image.parent(),hwm=image.dba()+1;
        if(m[17]==23) {
            if(!Arrays.equals(f.get(2),words(root,8,0,9,2)) || !Arrays.equals(f.get(3),words(0x7f04,1,0x7600,3)))fail("BITMAP_INVERSE");
            requireForward(record,undo,13,22,8,root,object,words(4,1,1,3));
        } else if(m[17]==29) {
            if(!Arrays.equals(f.get(2),words(root+2,0,2,4,0)) || f.get(3).length!=80)fail("HWM_METADATA");
            boolean high=u32(f.get(3),0)==2;
            var before=high?words(2,0,3,8,image.dba(),0,0,0,0,0,0,0,0,0,0,0,0,0,root,root+1)
                    :words(3,0,0,0,0,0,0,0,0,0,3,8,image.dba(),0,0,0,0,root,0,0);
            var after=high?words(2,0,4,8,hwm,0,0,0,4,0,0,0,0,0,0,0,0,0,root,root+1)
                    :words(3,0,0,0,0,0,0,0,0,0,4,8,hwm,0,0,0,4,root,0,0);
            if(!Arrays.equals(f.get(3),before))fail("HWM_INVERSE");
            requireForward(record,undo,13,28,4,root+2,object,after);
            if(high)requireForward(record,undo,13,22,8,root,object,words(0x10520,0,4,8,hwm,0,0,0,4));
            else if(record.vectors().stream().anyMatch(v->v.containerId()==undo.containerId() && v.opcodeMajor()==13 && v.opcodeMinor()==22))fail("LOW_HWM_BITMAP");
        } else fail("STORAGE_OPCODE");
    }
    private static void requireForward(RedoRecords.Record record,RedoRecords.Vector undo,int major,int minor,int blockClass,long dba,long object,byte[] expected)throws RedoException {
        var matches=record.vectors().stream().filter(v->v.containerId()==undo.containerId() && v.opcodeMajor()==major && v.opcodeMinor()==minor).toList();
        if(matches.size()!=1)fail("FORWARD_COUNT");var v=matches.get(0);
        if(v.blockClass()!=blockClass || v.dba()!=dba || v.objectId()!=object || v.fields().size()!=1 || !Arrays.equals(v.fields().get(0),expected))fail("FORWARD_BYTES");
    }
    private static byte[] words(long... values){byte[] bytes=new byte[values.length*4];for(int i=0;i<values.length;i++)for(int j=0;j<4;j++)bytes[i*4+j]=(byte)(values[i]>>>(j*8));return bytes;}
    private static void fail(String detail)throws RedoException {throw new RedoException("LOB_MIGRATION_COPY_"+detail);}
}
