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
        int oracleYear=century*100+year;
        if (Math.abs(year)>99 || century<0 && year>0 || century>0 && year<0
                || oracleYear==0 || oracleYear < -4712 || oracleYear>9999)
            throw new RedoException("UNSUPPORTED_DATE_ERA");
        try {
            // Oracle has no year zero. ISO year 0 is Oracle 1 BC.
            return LocalDateTime.of(oracleYear<0?oracleYear+1:oracleYear,bytes[2]&255,bytes[3]&255,
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

    public static LocalDateTime timestamp(byte[] bytes) throws RedoException {
        if(bytes.length!=7 && bytes.length!=11)throw new RedoException("INVALID_TIMESTAMP_LENGTH");
        var value=date(java.util.Arrays.copyOf(bytes,7));
        long nanos=bytes.length==7?0:Integer.toUnsignedLong(ByteBuffer.wrap(bytes,7,4).getInt());
        if(nanos>999999999)throw new RedoException("INVALID_TIMESTAMP_NANOSECONDS");
        return value.withNano((int)nanos);
    }

    /** UTC first eleven bytes plus the explicit offset; region codes need their own source context. */
    public static java.time.OffsetDateTime timestampWithOffset(byte[] bytes)throws RedoException {
        if(bytes.length!=13)throw new RedoException("INVALID_TIMESTAMP_TZ_LENGTH");
        if((bytes[11]&128)!=0)throw new RedoException("TIMESTAMP_TZ_REGION_CONTEXT_REQUIRED");
        int hours=(bytes[11]&255)-20,minutes=(bytes[12]&255)-60;
        if(hours < -12 || hours>14 || Math.abs(minutes)>59 || hours==-12 && minutes<0 || hours==14 && minutes>0)
            throw new RedoException("INVALID_TIMESTAMP_TZ_OFFSET");
        try {
            var offset=java.time.ZoneOffset.ofHoursMinutes(hours,minutes);
            return timestamp(java.util.Arrays.copyOf(bytes,11)).atOffset(java.time.ZoneOffset.UTC).withOffsetSameInstant(offset);
        } catch(DateTimeException e){throw new RedoException("INVALID_TIMESTAMP_TZ_OFFSET");}
    }

    /** Initial snapshot encoding; preserves the Oracle BC/AD era without a machine timezone. */
    public static byte[] temporalBytes(LocalDateTime value,boolean timestamp) throws RedoException {
        if(value.getYear() < -4711 || value.getYear()>9999)throw new RedoException("UNSUPPORTED_DATE_ERA");
        if(!timestamp && value.getNano()!=0)throw new RedoException("DATE_FRACTIONAL_SECONDS");
        byte[] bytes=new byte[timestamp && value.getNano()!=0?11:7];
        int oracleYear=value.getYear()<=0?value.getYear()-1:value.getYear();
        bytes[0]=(byte)(oracleYear/100+100);bytes[1]=(byte)(oracleYear%100+100);
        bytes[2]=(byte)value.getMonthValue();bytes[3]=(byte)value.getDayOfMonth();
        bytes[4]=(byte)(value.getHour()+1);bytes[5]=(byte)(value.getMinute()+1);bytes[6]=(byte)(value.getSecond()+1);
        if(bytes.length==11)ByteBuffer.wrap(bytes,7,4).putInt(value.getNano());
        return bytes;
    }
}
