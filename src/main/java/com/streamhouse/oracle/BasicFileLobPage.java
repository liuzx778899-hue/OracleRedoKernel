package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c 8K BasicFile 19.1 page image. No length, owner XID or commit authority. */
public record BasicFileLobPage(long objectId,String lobId,long version,long pageNumber,long parentDba,byte[] payload) {
    public BasicFileLobPage { payload=payload.clone(); }
    @Override public byte[] payload() { return payload.clone(); }
    public static BasicFileLobPage decode(RedoRecords.Vector vector,long expectedObject)throws RedoException {
        if(vector.opcodeMajor()!=19 || vector.opcodeMinor()!=1 || vector.blockClass()!=1
                || vector.fields().size()!=2 || vector.fields().get(0).length!=8168
                || !Arrays.equals(vector.fields().get(1),new byte[]{0x28}))
            throw new RedoException("UNSUPPORTED_BASICFILE_LOB_PAGE_PROFILE");
        byte[] data=vector.fields().get(0);
        long object=u32(data,0),parent=u32(data,28);
        if(object!=expectedObject || object==0 || object==0xffffffffL || parent==0 || vector.dba()==0
                || u32(data,20)!=0 || u32(data,32)!=0)
            throw new RedoException("BASICFILE_LOB_PAGE_IDENTITY_OR_RESERVED");
        String id=HexFormat.of().formatHex(data,4,14);
        if(id.equals("00000000000000000000"))throw new RedoException("BASICFILE_LOB_PAGE_EMPTY_ID");
        return new BasicFileLobPage(object,id,u32(data,16)|((long)u16(data,14)<<32),u32(data,24),parent,
                Arrays.copyOfRange(data,36,data.length));
    }
}
