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
        assertThrows(RedoException.class,()->OracleScalars.date(hex("63630a04010101")));
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
        assertThrows(RedoException.class,()->OracleScalars.temporalBytes(value.withYear(0),true));
    }
    @Test void decodesChineseAndRejectsReplacementFallback() throws Exception {
        assertEquals("中文",OracleScalars.varchar2(hex("e4b8ade69687"),"AL32UTF8"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(hex("c328"),"AL32UTF8"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(hex("61"),"ZHS16GBK"));
        assertThrows(RedoException.class,()->OracleScalars.varchar2(new byte[0],"AL32UTF8"));
    }
}
