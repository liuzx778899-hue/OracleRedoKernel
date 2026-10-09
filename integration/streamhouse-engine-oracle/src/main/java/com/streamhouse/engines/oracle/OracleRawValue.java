package com.streamhouse.engines.oracle;

/** Immutable binary value; JSON hex is independent of database/session character sets. */
public record OracleRawValue(String hex) {
    public OracleRawValue {
        if(hex==null || !hex.matches("(?:[0-9a-f]{2})+"))throw new IllegalArgumentException("INVALID_RAW_VALUE");
    }
    public byte[] bytes(){return java.util.HexFormat.of().parseHex(hex);}
}
