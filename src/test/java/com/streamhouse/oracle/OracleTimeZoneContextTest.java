package com.streamhouse.oracle;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleTimeZoneContextTest {
    private byte[] hex(String value){return HexFormat.of().parseHex(value);}
    private final OracleTimeZoneContext context=new OracleTimeZoneContext("+00:00",32,Map.of(382,"Europe/Paris"));
    @Test void frozenParisWinterSummerAndAutumnKeepUtcAndOracleRegion()throws Exception {
        // raw109 / independent SQL: 01:30 CET, 03:30 CEST and 03:30 CET respectively.
        String[] bytes={"787c031f011f2e075bca0085f8","787c031f021f2e3ade677085f8","787c0a1b031f01000003e885f8"};
        LocalDateTime[] utc={LocalDateTime.of(2024,3,31,0,30,45,123456000),LocalDateTime.of(2024,3,31,1,30,45,987654000),LocalDateTime.of(2024,10,27,2,30,0,1000)};
        for(int i=0;i<bytes.length;i++) {
            var value=context.timestampWithTimeZone(hex(bytes[i]));
            assertEquals(utc[i],value.value());assertEquals("Europe/Paris",value.zone());assertTrue(value.utc());
            var local=context.timestampWithLocalTimeZone(hex(bytes[i].substring(0,22)));
            assertEquals(utc[i],local.value());assertEquals("+00:00",local.zone());assertFalse(local.utc());
        }
    }
    @Test void offsetAndBcValuesKeepTheirStoredTimeBasis()throws Exception {
        var offset=context.timestampWithTimeZone(hex("787c031e17012e075bcd15195a"));
        assertEquals("+05:30",offset.zone());assertEquals(LocalDateTime.of(2024,3,30,22,0,45,123456789),offset.value());
        var bc=context.timestampWithLocalTimeZone(hex("6438030f0d23393ade68b1"));
        assertEquals(-43,bc.value().getYear());assertEquals(987654321,bc.value().getNano());
    }
    @Test void contextCannotSilentlyInventRegionsOrTimezoneRules() {
        assertThrows(IllegalArgumentException.class,()->new OracleTimeZoneContext("Europe/Paris",0,Map.of(382,"Europe/Paris")));
        assertThrows(IllegalArgumentException.class,()->new OracleTimeZoneContext("Unknown/Zone",32,Map.of()));
        assertThrows(IllegalArgumentException.class,()->new OracleTimeZoneContext("+14:01",32,Map.of()));
        assertThrows(RedoException.class,()->new OracleTimeZoneContext("+00:00",32,Map.of()).timestampWithTimeZone(hex("787c031f011f2e075bca0085f8")));
        assertThrows(RedoException.class,()->context.timestampWithTimeZone(hex("787c031f011f2e075bca0085f9")));
        assertThrows(RedoException.class,()->context.timestampWithLocalTimeZone(hex("787c031f011f2e075bca0085f8")));
    }
}
