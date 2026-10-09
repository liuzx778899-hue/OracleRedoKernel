package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19c 26.2 uncompressed data fill. KTB is retained; ownership and commit remain unproven. */
public record SecureFileLobFill(String lobId,long pageDba,boolean undo,int offset,int fillFlags,long highWater,
                                byte[] payload,byte[] ktb) {
    public SecureFileLobFill { payload=payload.clone();ktb=ktb.clone(); }
    @Override public byte[] payload() { return payload.clone(); }
    @Override public byte[] ktb() { return ktb.clone(); }
    public static SecureFileLobFill decode(RedoRecords.Vector vector)throws RedoException {
        if(vector.opcodeMajor()!=26 || vector.opcodeMinor()!=2 || vector.blockClass()!=1 || vector.fields().size()!=4)
            throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_FILL_PROFILE");
        byte[] ktb=vector.fields().get(0),common=vector.fields().get(1),identity=vector.fields().get(2),fill=vector.fields().get(3);
        if(!((ktb.length==16 && ktb[0]==2) || (ktb.length==24 && ktb[0]==1) || (ktb.length==32 && ktb[0]==4)) || ktb[1]!=0x0d
                || common.length!=12 || (common[0]!=0 && common[0]!=1) || common[1]!=0x20
                || (common[2]!=8 && common[2]!=9) || common[3]!=0 || u16(common,4)!=8060 || u16(common,6)!=128
                || u32(common,8)!=vector.dba() || vector.dba()==0 || fill.length<8 || fill[0]!=6
                || u16(fill,4)!=u16(fill,6) || fill.length!=8+u16(fill,6) || u16(fill,2)+u16(fill,6)>8060)
            throw new RedoException("SECUREFILE_LOB_FILL_HEADER_OR_LENGTH");
        String id;long high=-1;
        if(identity.length==56 && u32(identity,0)==4 && identity[10]==0x28 && identity[11]==0) {
            for(int i=22;i<52;i++)if(identity[i]!=0)throw new RedoException("SECUREFILE_LOB_LOAD_RESERVED");
            if(u16(identity,54)!=0 || u16(identity,52)>8060)throw new RedoException("SECUREFILE_LOB_LOAD_HIGH_WATER");
            id=HexFormat.of().formatHex(identity,12,22);high=u16(identity,52);
        } else if(identity.length==17 && identity[0]==1) {
            for(int i=11;i<17;i++)if(identity[i]!=0)throw new RedoException("SECUREFILE_LOB_ID_RESERVED");
            id=HexFormat.of().formatHex(identity,1,11);
        } else throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_ID_PROFILE");
        if(id.equals("00000000000000000000"))throw new RedoException("SECUREFILE_LOB_EMPTY_ID");
        return new SecureFileLobFill(id,vector.dba(),common[0]==1,u16(fill,2),fill[1]&255,high,
                Arrays.copyOfRange(fill,8,fill.length),ktb);
    }
}
