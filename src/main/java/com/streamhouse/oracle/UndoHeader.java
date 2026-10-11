package com.streamhouse.oracle;

import static com.streamhouse.oracle.RedoBytes.*;

/** 5.1 identity from the undo block record, independent of row ITL identity. */
public record UndoHeader(HeapRowPatch.Xid xid, HeapRowPatch.Uba uba, long objectId,
                         long dataObjectId, long tablespace, boolean beginsTransaction) {
    public static UndoHeader decode(RedoRecords.Vector vector) throws RedoException {
        if (vector.opcodeMajor() != 5 || vector.opcodeMinor() != 1 || vector.fields().size() < 2)
            throw new RedoException("UNSUPPORTED_UNDO_HEADER_OPCODE");
        byte[] ktudb=vector.fields().get(0), ktubu=vector.fields().get(1);
        if (ktudb.length != 20 || (ktubu.length != 24 && ktubu.length != 76))
            throw new RedoException("UNSUPPORTED_UNDO_HEADER_LAYOUT");
        int flags=u16(ktubu,20);
        boolean storageBegin=ktubu.length==76 && flags==0x0c88 && ktubu[16]==22 && ktubu[17]==3
                && u32(ktubu,0)==1 && u32(ktubu,4)==0xffffffffL && vector.fields().size()==3
                && java.util.Arrays.equals(vector.fields().get(2),new byte[20]);
        // Observed raw119 segment-space undo. This admits its identity only, not its physical operation or publication.
        boolean segmentSpace=ktubu.length==24 && flags==0x80 && ktubu[16]==14 && (ktubu[17]==5 || ktubu[17]==8)
                && u32(ktubu,0)==1 && u32(ktubu,4)>0 && u32(ktubu,8)==5;
        if ((ktubu.length == 76 && flags != 0x0c08 && !storageBegin) || (ktubu.length == 24 && flags != 0 && !segmentSpace))
            throw new RedoException("UNSUPPORTED_UNDO_HEADER_FLAGS");
        return new UndoHeader(new HeapRowPatch.Xid(u16(ktudb,8),u16(ktudb,10),u32(ktudb,12)),
                new HeapRowPatch.Uba(vector.dba(),u16(ktudb,16),ktudb[18]&255),
                u32(ktubu,0),u32(ktubu,4),u32(ktubu,8),ktubu.length == 76);
    }
}
