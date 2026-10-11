package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** Raw120 SYS cluster extent expansion changes space metadata, never dictionary row values. */
final class DictionarySpaceHwmPair {
    static void require(RedoRecords.Record record,RedoRecords.Vector undo,FrozenHeapBaseline.ObjectRole role)throws RedoException {
        var h=UndoHeader.decode(undo);var f=undo.fields();
        if(!role.owner().equals("SYS") || !role.name().equals("TAB$") || h.objectId()!=role.logicalObject()
                || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0 || f.size()!=4
                || f.get(1)[16]!=14 || f.get(1)[17]!=5 || f.get(2).length!=24 || f.get(3).length!=36)
            throw new RedoException("UNSUPPORTED_DICTIONARY_SPACE_HWM_SCOPE");
        byte[] map=f.get(2),before=f.get(3);
        if(u32(map,0)==0 || u32(map,4)!=4 || u32(map,8)!=u32(map,0) || u32(map,12)!=3 || u32(map,16)!=0 || u32(map,20)!=0
                || u32(before,0)!=0x151307 || u32(before,8)!=128 || u32(before,12)!=128 || u32(before,20)!=0
                || u32(before,24)!=u32(before,4) || u32(before,28)!=0)
            throw new RedoException("UNSUPPORTED_DICTIONARY_SPACE_HWM_UNDO");
        int pairs=0;
        for(var row:record.vectors())if(row.containerId()==undo.containerId() && row.opcodeMajor()==14 && row.opcodeMinor()==4) {
            if(row.objectId()!=role.dataObject() || row.dba()!=u32(map,0) || row.blockClass()!=4 || row.fields().size()!=1 || row.fields().get(0).length!=36)
                throw new RedoException("DICTIONARY_SPACE_HWM_FORWARD_SCOPE");
            byte[] after=row.fields().get(0);
            if(u32(after,0)!=7 || u32(after,4)!=u32(before,4)+1 || u32(after,8)!=0 || u32(after,12)!=128
                    || u32(after,16)<=u32(before,16) || u32(after,20)!=0 || u32(after,24)!=u32(after,4)
                    || u32(after,28)!=0 || u32(after,32)!=u32(before,32))
                throw new RedoException("DICTIONARY_SPACE_HWM_TRANSITION");
            pairs++;
        }
        if(record.vectors().size()!=2 || pairs!=1)throw new RedoException("DICTIONARY_SPACE_HWM_PAIR_COVERAGE");
    }
}
