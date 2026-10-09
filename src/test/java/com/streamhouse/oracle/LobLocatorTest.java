package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.LobLocator.*;

class LobLocatorTest {
    byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    @Test void readsExternalBasicFileAndSecureFileLengthsAndPageLists()throws Exception {
        var basic=decode(hex("00700001010c000000010000000100000034f6350018050000000001074c0000000000060300861c0300867c"),Kind.BLOB,Storage.BASICFILE);
        assertEquals(10000,basic.byteLength());assertEquals(6,basic.version());assertEquals(List.of(50365980L,50366076L),basic.pages());assertNull(basic.inlinePayload());
        var secure=decode(hex("00700001020c808000020000000100000034f6cc00124090000c210027100500010300866b02"),Kind.CLOB,Storage.SECUREFILE);
        assertEquals(10000,secure.byteLength());assertEquals(List.of(50366059L,50366060L),secure.pages());
        assertThrows(UnsupportedOperationException.class,()->secure.pages().add(1L));
    }
    @Test void distinguishesEmptyAndInlineLobAndProtectsPayload()throws Exception {
        var empty=decode(hex("00700001010c008000010000000100000034f6cb000a4890000400000000"),Kind.BLOB,Storage.SECUREFILE);
        assertEquals(0,empty.byteLength());assertArrayEquals(new byte[0],empty.inlinePayload());
        byte[] bytes=hex("00700001010c000000010000000100000034f59f0018090000000000000800000000000100ff80c328000102");
        var inline=decode(bytes,Kind.BLOB,Storage.BASICFILE);bytes[36]=9;byte[] copy=inline.inlinePayload();copy[0]=9;
        assertArrayEquals(hex("00ff80c328000102"),inline.inlinePayload());
    }
    @Test void rejectsWrongTypeUnknownStorageAndMissingPages() {
        byte[] basic=hex("00700001010c000000010000000100000034f6350018050000000001074c0000000000060300861c0300867c");
        assertThrows(RedoException.class,()->decode(basic,Kind.CLOB,Storage.BASICFILE));
        assertThrows(RedoException.class,()->decode(basic,Kind.BLOB,Storage.SECUREFILE));
        basic[27]=2;assertThrows(RedoException.class,()->decode(basic,Kind.BLOB,Storage.BASICFILE));
        byte[] secure=hex("00700001010c008000010000000100000034f6cb00124090000c210027100500010300865301");
        assertThrows(RedoException.class,()->decode(secure,Kind.BLOB,Storage.SECUREFILE));
    }
    @Test void readsFrozenSecureFileInsertedInlineValuesAndRejectsBadLength()throws Exception {
        byte[] blob=hex("00700001010c008000010000000100000034f6fd000e4890000800000401c32800ff");
        var b=decode(blob,Kind.BLOB,Storage.SECUREFILE);
        assertEquals(4,b.byteLength());assertArrayEquals(hex("c32800ff"),b.inlinePayload());
        var c=decode(hex("00700001020c808000020000000100000034f6fe00144890000e00000a01006e00650077002d6587"),Kind.CLOB,Storage.SECUREFILE);
        assertEquals(10,c.byteLength());assertArrayEquals(hex("006e00650077002d6587"),c.inlinePayload());
        blob[28]=5;assertThrows(RedoException.class,()->decode(blob,Kind.BLOB,Storage.SECUREFILE));
        blob[28]=4;blob[29]=2;assertThrows(RedoException.class,()->decode(blob,Kind.BLOB,Storage.SECUREFILE));
    }
}
