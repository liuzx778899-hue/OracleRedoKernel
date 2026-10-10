package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.OracleScalars;
import com.streamhouse.oracle.OracleTimeZoneContext;
import com.streamhouse.oracle.RedoException;
import java.util.HexFormat;

/** Lossless Oracle native bytes plus their source timezone basis; never converted through Java TZDB. */
public record OracleZonedTemporalValue(String type,String hex,String databaseZone,int timeZoneVersion,String zone) {
    public OracleZonedTemporalValue {
        if(!hex.matches("(?:[0-9a-f]{2})+") || timeZoneVersion<1 || !databaseZone.matches("[A-Za-z0-9_+.:/-]+") || !zone.matches("[A-Za-z0-9_+.:/-]+"))
            throw new IllegalArgumentException("ORACLE_ZONED_TEMPORAL_VALUE");
        try {
            byte[] bytes=HexFormat.of().parseHex(hex);
            if(type.equals("TIMESTAMP WITH LOCAL TIME ZONE")) {
                OracleScalars.timestamp(bytes);if(!databaseZone.equals(zone))throw new IllegalArgumentException("ORACLE_LOCAL_TIMEZONE_BASIS");
            } else if(type.equals("TIMESTAMP WITH TIME ZONE")) {
                if(bytes.length!=13)throw new IllegalArgumentException("ORACLE_ZONED_TEMPORAL_LENGTH");
                if((bytes[11]&128)==0) {
                    if(!OracleScalars.timestampWithOffset(bytes).getOffset().getId().equals(zone))throw new IllegalArgumentException("ORACLE_OFFSET_TIMEZONE_BASIS");
                } else {
                    if((bytes[12]&3)!=0)throw new IllegalArgumentException("ORACLE_ZONED_TEMPORAL_FLAGS");
                    OracleScalars.timestamp(java.util.Arrays.copyOf(bytes,11));
                }
            } else throw new IllegalArgumentException("ORACLE_ZONED_TEMPORAL_TYPE");
        } catch(RedoException e){throw new IllegalArgumentException(e.getMessage(),e);}
    }
    static OracleZonedTemporalValue of(String type,byte[] bytes,OracleTimeZoneContext context)throws RedoException {
        if(context==null)throw new RedoException("ORACLE_TIMEZONE_CONTEXT_REQUIRED");
        var value=type.equals("TIMESTAMP WITH TIME ZONE")?context.timestampWithTimeZone(bytes):context.timestampWithLocalTimeZone(bytes);
        return new OracleZonedTemporalValue(type,HexFormat.of().formatHex(bytes),context.databaseZone(),context.fileVersion(),value.zone());
    }
    public byte[] bytes(){return HexFormat.of().parseHex(hex);}
}
