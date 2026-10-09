package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileLobFillTest {
    byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    RedoRecords.Vector vector(byte[] common,byte[] fill) {
        return new RedoRecords.Vector(26,2,1,12,50366035,3043715,1,3,List.of(
                hex("010d0000060000000a001100aa1000002e05400290011200"),common,
                hex("010000000100000034f6cb000000000000"),fill));
    }
    @Test void preservesByteOffsetRawPayloadAndOpaqueKtb()throws Exception {
        byte[] fill=hex("0600640004000400deadbeef");var patch=SecureFileLobFill.decode(vector(hex("002009007c1f800053860003"),fill));
        assertEquals("0000000100000034f6cb",patch.lobId());assertEquals(100,patch.offset());assertFalse(patch.undo());
        assertArrayEquals(hex("deadbeef"),patch.payload());assertEquals(-1,patch.highWater());
        fill[8]=0;byte[] payload=patch.payload();payload[0]=0;assertArrayEquals(hex("deadbeef"),patch.payload());
        byte[] ktb=patch.ktb();ktb[0]=0;assertEquals(1,patch.ktb()[0]);
        assertTrue(SecureFileLobFill.decode(vector(hex("012009007c1f800053860003"),hex("060000000400040000ff80c3"))).undo());
    }
    @Test void rejectsTruncationSizeMismatchOverflowAndWrongPage() {
        for(String value:new String[]{"0600640004000400deadbe","0600640005000400deadbeef","06007b1f04000400deadbeef","0700640004000400deadbeef"})
            assertThrows(RedoException.class,()->SecureFileLobFill.decode(vector(hex("002009007c1f800053860003"),hex(value))));
        assertThrows(RedoException.class,()->SecureFileLobFill.decode(vector(hex("002009007c1f80006b860003"),hex("0600640004000400deadbeef"))));
    }
}
