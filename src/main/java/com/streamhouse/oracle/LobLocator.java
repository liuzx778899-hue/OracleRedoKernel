package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Observed 19c inline/external locator profiles. Page references do not authorize a transaction or value. */
public record LobLocator(Kind kind,Storage storage,String lobId,long byteLength,long version,List<Long> pages,byte[] inlinePayload) {
    public enum Kind { BLOB,CLOB }
    public enum Storage { BASICFILE,SECUREFILE }
    public LobLocator { pages=List.copyOf(pages);inlinePayload=inlinePayload==null?null:inlinePayload.clone(); }
    @Override public byte[] inlinePayload() { return inlinePayload==null?null:inlinePayload.clone(); }
    private static int be16(byte[] b,int p) { return ((b[p]&255)<<8)|(b[p+1]&255); }
    private static long be32(byte[] b,int p) { return ((long)be16(b,p)<<16)|be16(b,p+2); }
    public static LobLocator decode(byte[] b,Kind expectedKind,Storage expectedStorage)throws RedoException {
        if(b.length<30 || b[0]!=0 || b[1]!=0x70 || b[2]!=0 || b[3]!=1 || b[5]!=0x0c
                || (b[4]!=1 && b[4]!=2) || b[8]!=0 || b[9]!=b[4] || be16(b,20)!=b.length-20)
            throw new RedoException("UNSUPPORTED_LOB_LOCATOR_HEADER");
        var kind=b[4]==1?Kind.BLOB:Kind.CLOB;var storage=b[7]==0?Storage.BASICFILE:Storage.SECUREFILE;
        if((b[7]!=0 && b[7]!=(byte)0x80) || b[6]!=(kind==Kind.BLOB?0:(byte)0x80)
                || kind!=expectedKind || storage!=expectedStorage)
            throw new RedoException("LOB_LOCATOR_TYPE_OR_STORAGE_MISMATCH");
        String id=HexFormat.of().formatHex(b,10,20);long length,version;byte[] inline=null;var pages=new ArrayList<Long>();
        if(storage==Storage.BASICFILE) {
            if(b.length<36 || b[23]!=0 || be16(b,24)!=0 || be16(b,30)!=0)
                throw new RedoException("UNSUPPORTED_BASICFILE_LOB_LOCATOR");
            version=be32(b,32);
            if(b[22]==9) {
                length=be32(b,26);inline=Arrays.copyOfRange(b,36,b.length);
                if(length!=inline.length || (length==0?version!=0:version!=1))throw new RedoException("BASICFILE_LOB_INLINE_LENGTH");
            } else if(b[22]==5 && b.length>=40 && (b.length-36)%4==0) {
                int count=be16(b,26),tail=be16(b,28);length=count*8132L+tail;
                if(tail<1 || tail>8132 || (b.length-36)/4!=count+1 || version<1)
                    throw new RedoException("BASICFILE_LOB_EXTERNAL_LENGTH");
                for(int i=36;i<b.length;i+=4)pages.add(be32(b,i));
            } else throw new RedoException("UNSUPPORTED_BASICFILE_LOB_LOCATOR");
        } else {
            if(b[23]!=(byte)0x90)throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_LOCATOR");
            length=((long)(b[27]&255)<<16)|be16(b,28);version=-1;
            if(b[22]==0x48 && b.length==30 && be16(b,24)==4 && b[26]==0 && length==0)inline=new byte[0];
            else if(b[22]==0x48 && b.length>=31 && be16(b,24)==b.length-26 && b[26]==1 && b[30]==1) {
                inline=Arrays.copyOfRange(b,31,b.length);if(inline.length!=length)throw new RedoException("SECUREFILE_LOB_INLINE_LENGTH");
            } else if(b[22]==0x40 && b.length==38 && be16(b,24)==12 && b[26]==0x21 && b[31]==0 && b[32]==1) {
                version=b[30]&255;long first=be32(b,33);int count=b[37]&255;
                if(count<1 || length<1 || count!=(length+8059)/8060 || first+count-1>0xffffffffL)
                    throw new RedoException("SECUREFILE_LOB_EXTERNAL_LENGTH");
                for(int i=0;i<count;i++)pages.add(first+i);
            } else throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_LOCATOR");
        }
        if(id.equals("00000000000000000000") || pages.stream().anyMatch(p->p==0) || pages.stream().distinct().count()!=pages.size())
            throw new RedoException("LOB_LOCATOR_EMPTY_OR_DUPLICATE_IDENTITY");
        if(kind==Kind.CLOB && length%2!=0)throw new RedoException("LOB_LOCATOR_ODD_UTF16_LENGTH");
        return new LobLocator(kind,storage,id,length,version,pages,inline);
    }
}
