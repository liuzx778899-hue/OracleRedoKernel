package com.streamhouse.engines.oracle;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.HexFormat;

/** Complete immutable value; empty hex means EMPTY LOB, SQL NULL remains a separate map value. */
public record OracleLobValue(String type,String hex) {
    public OracleLobValue {
        if(!java.util.List.of("BLOB","CLOB").contains(type) || hex==null || !hex.matches("(?:[0-9a-f]{2})*"))
            throw new IllegalArgumentException("INVALID_LOB_VALUE");
        if(type.equals("CLOB"))decode(hex);
    }
    public byte[] bytes(){return HexFormat.of().parseHex(hex);}
    public String text(){if(!type.equals("CLOB"))throw new IllegalStateException("LOB_IS_NOT_CLOB");return decode(hex);}
    private static String decode(String hex) {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(HexFormat.of().parseHex(hex))).toString();}
        catch(java.nio.charset.CharacterCodingException ex){throw new IllegalArgumentException("INVALID_CLOB_ENCODING",ex);}
    }
}
