package com.streamhouse.oracle;

import java.util.HexFormat;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c 26.6 uncompressed page image. Fpload XID is evidence, not transaction approval. */
public record SecureFileLobPageImage(long objectId,String lobId,long pageDba,long pageScn,HeapRowPatch.Xid xid,byte[] payload) {
    public SecureFileLobPageImage { payload=payload.clone(); }
    @Override public byte[] payload() { return payload.clone(); }
    public static SecureFileLobPageImage decode(RedoRecords.Vector vector,long expectedObject)throws RedoException {
        if(vector.opcodeMajor()!=26 || vector.opcodeMinor()!=6 || vector.blockClass()!=1 || vector.fields().size()!=4)
            throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_IMAGE_PROFILE");
        byte[] common=vector.fields().get(0),load=vector.fields().get(1),identity=vector.fields().get(2),payload=vector.fields().get(3);
        if(common.length!=12 || common[0]!=6 || common[1]!=0x20 || common[2]!=2 || common[3]!=0
                || u16(common,4)!=8060 || u16(common,6)!=128 || u32(common,8)!=vector.dba() || vector.dba()==0
                || load.length!=32 || u32(load,0)!=11 || u32(load,4)!=8192 || u16(load,14)!=0 || u32(load,28)!=0
                || identity.length!=56 || u32(identity,0)!=4 || identity[10]!=0x20 || identity[11]!=0
                || u16(identity,54)!=0 || payload.length!=u16(identity,52) || payload.length>8060)
            throw new RedoException("SECUREFILE_LOB_IMAGE_HEADER_OR_LENGTH");
        for(int i=22;i<52;i++)if(identity[i]!=0)throw new RedoException("SECUREFILE_LOB_IMAGE_RESERVED");
        long object=u32(load,24),scn=scn(load,8);
        var xid=new HeapRowPatch.Xid(u16(load,16),u16(load,18),u32(load,20));
        String id=HexFormat.of().formatHex(identity,12,22);
        if(object!=expectedObject || object==0 || object==0xffffffffL || scn!=scn(identity,4) || scn==0
                || xid.usn()==0 || xid.sequence()==0 || id.equals("00000000000000000000"))
            throw new RedoException("SECUREFILE_LOB_IMAGE_IDENTITY");
        return new SecureFileLobPageImage(object,id,vector.dba(),scn,xid,payload);
    }
}
