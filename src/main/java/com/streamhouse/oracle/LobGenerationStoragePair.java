package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Raw119 allocation/segment reset pairs. New DATAOBJ# must come from the owning OBJ$/SYS39 generation proof. */
final class LobGenerationStoragePair {
    static void require(RedoRecords.Record record,RedoRecords.Vector undo,long logical,long oldData,long newData,int usedBlocks)throws RedoException {
        var h=UndoHeader.decode(undo);var f=undo.fields();var m=f.get(1);int major=m[16]&255,minor=m[17]&255;
        if(h.dataObjectId()!=oldData || h.objectId()!=(major==14?1:logical) || h.tablespace()!=5 || h.beginsTransaction()
                || m[18]!=(byte)h.xid().slot() || u16(m,22)!=(h.xid().sequence()&65535))fail("OWNER");
        if(major==14 && minor==8) {
            if(f.size()!=3 || f.get(2).length!=4 || u32(f.get(2),0)!=newData
                    || record.vectors().stream().anyMatch(v->v.containerId()==undo.containerId() && v.opcodeMajor()!=5))fail("SPACE_RETIRE");
            return;
        }
        int opcode=major==13?minor-1:major==14 && minor==5?4:major==10 && minor==26?25:-1;
        RedoRecords.Vector row=null;
        for(var v:record.vectors())if(v.containerId()==undo.containerId() && v.opcodeMajor()==major && v.opcodeMinor()==opcode) {
            if(row!=null)fail("AMBIGUOUS_FORWARD");row=v;
        }
        if(row==null || row.objectId()!=oldData)fail("FORWARD_SCOPE");
        var after=row.fields();byte[] metadata=f.get(2);
        if(major==10) {
            if(f.size()!=6 || f.get(3).length!=8032 || f.get(4).length!=4 || f.get(5).length!=4 || u32(f.get(4),0)!=row.dba()
                    || u32(f.get(5),0)!=oldData || f.get(2).length!=68 || f.get(2)[0]!=5 || row.blockClass()!=1
                    || after.size()!=4 || after.get(0).length!=44 || after.get(0)[0]!=5 || after.get(0)[1]!=13
                    || after.get(1).length!=12 || u16(after.get(1),0)!=2 || u16(after.get(1),2)!=1 || u32(after.get(1),4)!=6
                    || u32(after.get(1),8)!=newData || after.get(2).length!=8 || after.get(3).length!=0
                    || !new HeapRowPatch.Xid(u16(after.get(0),20),u16(after.get(0),22),u32(after.get(0),24)).equals(h.xid())
                    || !new HeapRowPatch.Uba(u32(after.get(0),28),u16(after.get(0),32),after.get(0)[34]&255).equals(h.uba())
                    || scn(after.get(2),0)!=record.scn())fail("PRIMARY_INDEX_RESET");
            return;
        }
        if(metadata.length<8 || u32(metadata,0)!=row.dba() || u32(metadata,4)!=row.blockClass() && !(major==13 && (minor==50 || minor==29)))fail("BLOCK");
        byte[] before=f.get(3),next=after.get(0);
        if(major==14) {
            if(minor!=5 || f.size()!=4 || metadata.length!=24 || row.blockClass()!=4 || u32(metadata,8)!=row.dba()
                    || u32(metadata,12)!=2 || u32(metadata,16)!=0 || u32(metadata,20)!=0 || after.size()!=1)fail("SPACE_RESET");
            dataChange(before,next,10,oldData,newData);return;
        }
        if(minor==23 && metadata.length==8) {
            if(f.size()!=4 || row.blockClass()!=8 || after.size()!=1
                    || (before.length!=18 && before.length!=20) || next.length!=16 || u32(before,0)!=1 || u32(next,0)!=1
                    || (u32(before,4)!=2 && u32(before,4)!=4) || u32(next,4)!=u32(before,4) || u32(before,8)!=8 || u32(next,8)!=8
                    || before[12]!=1 || before[13]!=0 || before[14]!=0 || before[before.length-1]!=0
                    || next[12]!=1 || next[13]!=1 || next[14]!=(usedBlocks==4?1:0) || next[15]!=0)fail("BITMAP_ROOT");
            for(int i=15;i<before.length;i++)if((before[i]&15)>7 || ((before[i]&255)>>>4)>7)fail("BITMAP_ROOT_STATES");
        } else if(minor==23 && metadata.length==20) {
            if(f.size()!=5 || row.blockClass()!=8 || after.size()!=2 || u32(metadata,8)!=0 || u32(metadata,12)!=0 || u32(metadata,16)!=0
                    || f.get(4).length!=8 || after.get(1).length!=8 || scn(after.get(1),0)!=record.scn())fail("BITMAP_GENERATION");
            dataChange(before,next,21,oldData,newData);
        } else if(minor==25 || minor==48) {
            if(f.size()!=4 || metadata.length!=8 || after.size()!=1 || row.blockClass()!=(minor==25?9:12))fail("EXTENT_GENERATION");
            dataChange(before,next,minor==25?16:2,oldData,newData);
        } else if(minor==29) {
            long root=row.dba()-2,parent=row.dba()-1,hwm=row.dba()+usedBlocks-2;
            if(f.size()!=6 || metadata.length!=20 || u32(metadata,4)!=0 || u32(metadata,8)!=4 || u32(metadata,12)!=4 || u32(metadata,16)!=7
                    || row.blockClass()!=4 || before.length!=80 || after.size()!=3 || u32(before,0)!=4 || u32(before,4)!=0
                    || u32(before,12)!=8 || u32(before,44)!=8 || u32(before,68)!=root || u32(before,72)!=root || u32(before,76)!=parent
                    || !Arrays.equals(next,words(4,0,usedBlocks,8,hwm,0,0,0,usedBlocks==4?1:0,0,usedBlocks,8,hwm,0,0,0,usedBlocks==4?1:0,root,root,parent))
                    || !Arrays.equals(f.get(4),words(6,parent)) || !Arrays.equals(f.get(5),words(1,root,parent,0,0))
                    || !Arrays.equals(f.get(4),after.get(1)) || !Arrays.equals(f.get(5),after.get(2)))fail("BITMAP_FORMAT");
            int maps=0;
            for(var v:record.vectors())if(v.containerId()==undo.containerId() && v.opcodeMajor()==13 && v.opcodeMinor()==22) {
                if(v.objectId()!=oldData || v.dba()!=root || v.blockClass()!=8 || v.fields().size()!=1
                        || !Arrays.equals(v.fields().get(0),words(32,0,usedBlocks,8,hwm,0,0,0,usedBlocks==4?1:0)))fail("BITMAP_FORMAT_MAP");maps++;
            }
            if(maps!=1)fail("BITMAP_FORMAT_MAP_COUNT");
        } else if(minor==50) {
            if(metadata.length!=12 || row.blockClass()!=8 || u32(metadata,8)!=8 || u32(metadata,4)==0)fail("HASH_BUCKET_SCOPE");
            if(before.length==8) {
                if(f.size()!=4 || after.size()!=1 || next.length!=712 || u32(before,0)!=12 || u32(before,4)!=oldData
                        || u32(next,0)!=12 || u32(next,4)!=newData)fail("HASH_BUCKET_GENERATION");
            } else {
                if(f.size()!=5 || after.size()!=2 || before.length!=712 || next.length!=712 || f.get(4).length!=28
                        || after.get(1).length!=28 || u32(before,0)!=11 || u32(before,4)>8 || u32(before,8)!=1 || u32(before,116)!=oldData
                        || !List.of(2L,5L,6L).contains(u32(before,24)))fail("HASH_BUCKET_CLEAR");
                for(int offset=0;offset<712;offset+=4) {
                    long expected=offset==0?11:offset==4?u32(before,4):offset==8?1:offset==24?u32(before,24)==2?6:u32(before,24):offset==116?oldData:0;
                    if(u32(next,offset)!=expected)fail("HASH_BUCKET_CLEAR_HEADER");
                }
                for(int offset=0;offset<712;offset+=4) {
                    long old=u32(before,offset),value=u32(next,offset);
                    if(old==value)continue;
                    if(offset==24 && old==2 && value==6)continue;
                    if(List.of(28,32,108,124,128,132,136,152,296,312).contains(offset) && value==0)continue;
                    fail("HASH_BUCKET_HEADER_CHANGE");
                }
                byte[] cache=after.get(1);
                if(!Arrays.equals(cache,words(13,1,0,0,0,0,0)) || u32(f.get(4),0)!=13
                        || u32(f.get(4),4)>1 || (u32(f.get(4),8)!=0 && u32(f.get(4),8)!=0xffffffffL)
                        || u32(f.get(4),12)!=0 || u32(f.get(4),16)!=0 || u32(f.get(4),20)!=0 || u32(f.get(4),24)>1)
                    fail("HASH_BUCKET_FREE_CACHE_CLEAR");
            }
        } else if(minor==54) {
            if(f.size()!=4 || metadata.length!=8 || row.blockClass()!=10 || after.size()!=1 || before.length!=308 || next.length!=308
                    || u32(before,0)!=7 || u32(before,4)!=1 || u32(next,0)!=7 || u32(next,4)!=0)fail("PERSISTENT_HEADER");
            for(int i=0;i<308;i++)if(List.of(4,12,20,24,28).contains(i/4*4)) {
                if(next[i]!=0)fail("PERSISTENT_HEADER_CLEAR");
            } else if(before[i]!=next[i])fail("PERSISTENT_HEADER_UNEXPECTED_CHANGE");
        } else if(minor==56) {
            if(f.size()!=6 || metadata.length!=8 || row.blockClass()!=4 || after.size()!=3
                    || !Arrays.equals(before,HexFormat.of().parseHex("04000003ffffffff"))
                    || !Arrays.equals(next,HexFormat.of().parseHex("04000000ffffffff"))
                    || !Arrays.equals(f.get(4),HexFormat.of().parseHex("02000000050000000700000007000000070000000200000000000000010000000200000000000000"))
                    || !Arrays.equals(after.get(1),HexFormat.of().parseHex("02000000000000000700000000000000000000000200000000000000000000000200000000000000"))
                    || after.get(2).length!=44 || u32(after.get(2),0)!=3 || u32(after.get(2),4)!=0 || u32(after.get(2),8)!=11
                    || u32(after.get(2),12)!=16 || u32(after.get(2),16)!=row.dba()+10 || u32(after.get(2),20)!=0
                    || u32(after.get(2),24)!=0 || u32(after.get(2),28)!=0 || u32(after.get(2),32)!=11
                    || u32(after.get(2),36)!=1 || u32(after.get(2),40)!=13)fail("SEGMENT_HEADER_RESET");
        } else fail("OPCODE");
    }
    private static void dataChange(byte[] before,byte[] after,int opcode,long oldData,long newData)throws RedoException {
        if(before.length!=8 || after.length!=8 || u32(before,0)!=opcode || u32(after,0)!=opcode
                || u32(before,4)!=oldData || u32(after,4)!=newData)fail("DATA_OBJECT_CHANGE");
    }
    private static byte[] words(long... values) {
        var bytes=java.nio.ByteBuffer.allocate(values.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for(long value:values)bytes.putInt((int)value);return bytes.array();
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("LOB_GENERATION_STORAGE_"+detail);}
}
