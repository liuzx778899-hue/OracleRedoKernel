package com.streamhouse.engines.oracle;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Oracle wall-clock value, never an Instant or an implicit machine timezone. */
public record OracleTemporalValue(String type,String iso) {
    private static DateTimeFormatter format(String type) {
        if(!type.equals("DATE") && !type.equals("TIMESTAMP"))throw new IllegalArgumentException("ORACLE_TEMPORAL_TYPE");
        return DateTimeFormatter.ofPattern(type.equals("DATE")?"uuuu-MM-dd'T'HH:mm:ss":"uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
                .withResolverStyle(ResolverStyle.STRICT);
    }
    public OracleTemporalValue {
        var value=LocalDateTime.parse(iso,format(type));
        if(value.getYear() < -4711 || value.getYear()>9999 || !iso.equals(value.format(format(type))))
            throw new IllegalArgumentException("ORACLE_TEMPORAL_VALUE");
    }
    public static OracleTemporalValue of(String type,LocalDateTime value) {
        if(type.equals("DATE") && value.getNano()!=0)throw new IllegalArgumentException("DATE_FRACTIONAL_SECONDS");
        return new OracleTemporalValue(type,value.format(format(type)));
    }
    public LocalDateTime localDateTime() {return LocalDateTime.parse(iso,format(type));}
}
