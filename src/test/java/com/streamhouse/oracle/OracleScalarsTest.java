package com.streamhouse.oracle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleScalarsTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    void value(String bytes,String decimal) throws Exception {
        assertEquals(0,OracleScalars.number(hex(bytes)).compareTo(new BigDecimal(decimal)));
    }
    @Test void decodesExactDecimalAndSigns() throws Exception {
        value("80","0"); value("c102","1"); value("c202182e","123.45");
        value("3e594366","-12.34"); value("c002","0.01"); value("3f6466","-0.01");
        value("c202","100"); value("8002","1e-130"); value("ff6464","9.999e125");
    }
    @Test void rejectsMissingNumberAndMalformedMantissa() {
        for (String h:new String[]{"","c1","c100","3e59","3e0066","8066","c101"})
            assertThrows(RedoException.class,()->OracleScalars.number(hex(h)),h);
    }
    @Test void decodesDateWithoutTimezone() throws Exception {
        assertEquals(LocalDateTime.of(2026,10,4,8,9,10),OracleScalars.date(hex("787e0a04090a0b")));
    }
    @Test void refusesInvalidDateAndUnsupportedEra() {
        assertThrows(RedoException.class,()->OracleScalars.date(hex("787e0d04090a0b")));
        assertThrows(RedoException.class,()->OracleScalars.date(hex("63650a04010101")));
        assertThrows(RedoException.class,()->OracleScalars.date(new byte[0]));
    }
    @Test void timestampKeepsNanosecondsAndZeroFractionWithoutTimezone()throws Exception {
        var value=LocalDateTime.of(2024,2,29,23,59,58,123456789);
        assertEquals(value,OracleScalars.timestamp(hex("787c021d183c3b075bcd15")));
        assertArrayEquals(hex("787c021d183c3b075bcd15"),OracleScalars.temporalBytes(value,true));
        assertEquals(value.withNano(0),OracleScalars.timestamp(hex("787c021d183c3b")));
        assertArrayEquals(hex("787c021d183c3b"),OracleScalars.temporalBytes(value.withNano(0),true));
        for(String raw:new String[]{"787c021d183c3b3b9aca00","787c021d183c3bffffffff","787c021d183c3b00","787d021d183c3b075bcd15"})
            assertThrows(RedoException.class,()->OracleScalars.timestamp(hex(raw)),raw);
        assertThrows(RedoException.class,()->OracleScalars.temporalBytes(value,false));
        assertThrows(RedoException.class,()->OracleScalars.temporalBytes(value.withYear(-4712),true));
    }
    @Test void frozenBcBytesKeepEraAndNanosecondsWithoutInventingYearZero()throws Exception {
        // Owned raw109 / independent SQL: 0044-03-15 BC and 0001-01-01 BC.
        assertEquals(LocalDateTime.of(-43,3,15,12,34,56),OracleScalars.date(hex("6438030f0d2339")));
        assertEquals(LocalDateTime.of(0,1,1,0,0),OracleScalars.date(hex("64630101010101")));
        for(String bytes:new String[]{"6438030f0d23393ade68b1","6463010101010100000001"}) {
            var value=OracleScalars.timestamp(hex(bytes));assertArrayEquals(hex(bytes),OracleScalars.temporalBytes(value,true));
        }
        assertThrows(RedoException.class,()->OracleScalars.date(hex("64640101010101")));
        assertThrows(RedoException.class,()->OracleScalars.date(hex("65630101010101")));
    }
    @Test void frozenPositiveNegativeAndZeroOffsetsPreserveUtcInstant()throws Exception {
        assertEquals("2024-03-31T03:30:45.123456789+05:30",OracleScalars.timestampWithOffset(hex("787c031e17012e075bcd15195a")).toString());
        assertEquals("2024-03-31T03:30:45.987654321-03:30",OracleScalars.timestampWithOffset(hex("787c031f08012e3ade68b1111e")).toString());
        assertEquals("2024-03-31T03:30:45.000000001Z",OracleScalars.timestampWithOffset(hex("787c031f041f2e00000001143c")).toString());
        for(String bytes:new String[]{"787c031f011f2e075bca0085f8","787c031f041f2e00000001225a","787c031f041f2e00000001151e"})
            assertThrows(RedoException.class,()->OracleScalars.timestampWithOffset(hex(bytes)));
    }
    @Test void decodesChineseAndRejectsReplacementFallback() throws Exception {
        assertEquals("中文",OracleScalars.varchar2(hex("e4b8ade69687"),"AL32UTF8"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(hex("c328"),"AL32UTF8"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(hex("61"),"ZHS16GBK"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(new byte[0],"AL32UTF8"));
    }
}
