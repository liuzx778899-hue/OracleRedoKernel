package com.streamhouse.oracle;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;

/** Value bytes only: caller must distinguish absent columns from SQL NULL. */
public final class OracleScalars {
    private OracleScalars() {}
    // Oracle OCI Data Types: base-100 NUMBER, seven-byte DATE, AL32UTF8.
    public static BigDecimal number(byte[] bytes) throws RedoException {
        if (bytes.length == 1 && (bytes[0]&255) == 128) return BigDecimal.ZERO;
        if (bytes.length < 2 || bytes.length > 21) throw new RedoException("INVALID_NUMBER_LENGTH");
        boolean positive=(bytes[0]&128) != 0;
        int exponent=positive ? (bytes[0]&255)-193 : 62-(bytes[0]&255);
        int end=bytes.length;
        if (!positive && (bytes[end-1]&255) == 102) end--;
        else if (!positive && end != 21) throw new RedoException("INVALID_NUMBER_TERMINATOR");
        if (end < 2) throw new RedoException("INVALID_NUMBER_MANTISSA");
        BigInteger mantissa=BigInteger.ZERO;
        for (int i=1;i<end;i++) {
            int digit=positive ? (bytes[i]&255)-1 : 101-(bytes[i]&255);
            if (digit < 0 || digit > 99 || (i == 1 && digit == 0))
                throw new RedoException("INVALID_NUMBER_DIGIT");
            mantissa=mantissa.multiply(BigInteger.valueOf(100)).add(BigInteger.valueOf(digit));
        }
        if (!positive) mantissa=mantissa.negate();
        return new BigDecimal(mantissa,2*(end-2-exponent)).stripTrailingZeros();
    }

    public static LocalDateTime date(byte[] bytes) throws RedoException {
        if (bytes.length != 7) throw new RedoException("INVALID_DATE_LENGTH");
        int century=(bytes[0]&255)-100, year=(bytes[1]&255)-100;
        if (century < 0 || year < 0 || year > 99 || century*100+year < 1 || century*100+year > 9999)
            throw new RedoException("UNSUPPORTED_DATE_ERA");
        try {
            return LocalDateTime.of(century*100+year,bytes[2]&255,bytes[3]&255,
                    (bytes[4]&255)-1,(bytes[5]&255)-1,(bytes[6]&255)-1);
        } catch (DateTimeException e) { throw new RedoException("INVALID_DATE_VALUE"); }
    }

    public static String varchar2(byte[] bytes, String databaseCharset) throws RedoException {
        if (!"AL32UTF8".equals(databaseCharset)) throw new RedoException("UNSUPPORTED_DATABASE_CHARSET");
        if (bytes.length == 0) throw new RedoException("EMPTY_VALUE_REQUIRES_EXPLICIT_NULL_STATE");
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw new RedoException("INVALID_VARCHAR2_ENCODING"); }
    }
}
