package com.streamhouse.oracle;

import java.util.Arrays;
import static com.streamhouse.oracle.RedoBytes.*;

/** Raw118 DSC removes one ordinary trailing column within its owned dictionary transaction. */
record LobDroppedColumn(UndoHeader owner,long dba,int slot,int column,byte[] before) {
    static LobDroppedColumn bind(RedoRecords.Record record,RedoRecords.Vector undo,FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        var owner=UndoHeader.decode(undo);var fields=undo.fields();
        if(owner.objectId()!=snapshot.heap().logicalObject() || owner.dataObjectId()!=snapshot.heap().dataObject()
                || fields.size()!=5 || fields.get(2).length!=32 || fields.get(2)[0]!=4 || fields.get(2)[1]!=13
                || fields.get(4).length<1 || fields.get(4).length>250)
            throw new RedoException("UNSUPPORTED_LOB_DROPPED_COLUMN_UNDO");
        byte[] kdo=fields.get(3);
        if(kdo.length!=24 || kdo[10]!=14 || kdo[11]!=1 || kdo[12]!=1 || kdo[13]!=0
                || u16(kdo,14)!=0 || kdo[16]!=0 || u32(kdo,20)!=0)
            throw new RedoException("UNSUPPORTED_LOB_DROPPED_COLUMN_PROFILE");
        int count=0;
        for(var row:record.vectors())if(row.containerId()==undo.containerId() && row.opcodeMajor()==11 && row.opcodeMinor()==14) {
            var f=row.fields();
            if(f.size()!=2 || row.objectId()!=owner.dataObjectId() || row.dba()!=u32(kdo,0) || !Arrays.equals(f.get(1),kdo))
                throw new RedoException("LOB_DROPPED_COLUMN_FORWARD_CONFLICT");
            byte[] ktb=f.get(0);
            if(ktb.length!=70 || ktb[0]!=17 || ktb[1]!=13 || u16(ktb,8)!=owner.xid().usn()
                    || u16(ktb,10)!=owner.xid().slot() || u32(ktb,12)!=owner.xid().sequence()
                    || u32(ktb,16)!=owner.uba().dba() || u16(ktb,20)!=owner.uba().sequence() || (ktb[22]&255)!=owner.uba().record())
                throw new RedoException("LOB_DROPPED_COLUMN_OWNER_CONFLICT");
            count++;
        }
        if(count!=1)throw new RedoException("LOB_DROPPED_COLUMN_FORWARD_COVERAGE");
        return new LobDroppedColumn(owner,u32(kdo,0),u16(kdo,17),kdo[19]&255,fields.get(4).clone());
    }
}
