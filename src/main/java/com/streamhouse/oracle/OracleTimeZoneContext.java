package com.streamhouse.oracle;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

/** Source-owned timezone metadata. Oracle rules remain bound to their file version, never Java's TZDB. */
public record OracleTimeZoneContext(String databaseZone,int fileVersion,Map<Integer,String> regions) {
    public OracleTimeZoneContext {
        regions=Map.copyOf(regions);
        if(fileVersion<1 || databaseZone==null || databaseZone.isBlank())throw new IllegalArgumentException("INVALID_ORACLE_TIMEZONE_CONTEXT");
        for(var e:regions.entrySet())if(e.getKey()<1 || e.getKey()>8191 || !e.getValue().matches("[A-Za-z0-9_+./-]+"))
            throw new IllegalArgumentException("INVALID_ORACLE_TIMEZONE_REGION");
        if(databaseZone.startsWith("+") || databaseZone.startsWith("-")) {
            var offset=ZoneOffset.of(databaseZone);int seconds=offset.getTotalSeconds();
            if(seconds%60!=0 || seconds< -12*3600 || seconds>14*3600)throw new IllegalArgumentException("INVALID_ORACLE_DATABASE_TIMEZONE");
        } else if(!regions.containsValue(databaseZone))throw new IllegalArgumentException("UNKNOWN_ORACLE_DATABASE_TIMEZONE");
    }
    /** UTC for TSTZ; database wall clock for LTZ. The zone name/code is not resolved through Java rules. */
    public record Timestamp(LocalDateTime value,String zone,boolean utc) {}
    public Timestamp timestampWithTimeZone(byte[] bytes)throws RedoException {
        if(bytes.length!=13)throw new RedoException("INVALID_TIMESTAMP_TZ_LENGTH");
        if((bytes[11]&0x80)==0) {
            var offset=OracleScalars.timestampWithOffset(bytes).getOffset();
            return new Timestamp(OracleScalars.timestamp(Arrays.copyOf(bytes,11)),offset.getId(),true);
        }
        if((bytes[12]&3)!=0)throw new RedoException("UNSUPPORTED_TIMESTAMP_TZ_REGION_FLAGS");
        int id=((bytes[11]&0x7f)<<6)|((bytes[12]&255)>>>2);
        String region=regions.get(id);if(region==null)throw new RedoException("UNKNOWN_TIMESTAMP_TZ_REGION");
        return new Timestamp(OracleScalars.timestamp(Arrays.copyOf(bytes,11)),region,true);
    }
    public Timestamp timestampWithLocalTimeZone(byte[] bytes)throws RedoException {
        return new Timestamp(OracleScalars.timestamp(bytes),databaseZone,false);
    }
}
