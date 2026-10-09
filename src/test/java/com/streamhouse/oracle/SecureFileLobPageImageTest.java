package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileLobPageImageTest {
    byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    RedoRecords.Vector vector(byte[] load,byte[] payload) {
        return new RedoRecords.Vector(26,6,1,12,50366035,3043623,1,3,List.of(
                hex("062002007c1f800053860003"),load,
                hex("0400000027712e00008020000000000100000034f6cb000000000000000000000000000000000000000000000000000000000000a00f0000"),payload));
    }
    byte[] load() { return hex("0b0000000020000027712e00000000000a000d00a4100000ed35010000000000"); }
    @Test void readsScopedImageAndFploadOwnerWithoutLosingBinary()throws Exception {
        byte[] payload=new byte[4000];payload[0]=(byte)0xff;
        var image=SecureFileLobPageImage.decode(vector(load(),payload),79341);
        assertEquals(new HeapRowPatch.Xid(10,13,4260),image.xid());assertEquals(3043623,image.pageScn());
        assertEquals("0000000100000034f6cb",image.lobId());assertEquals(50366035,image.pageDba());
        payload[0]=0;byte[] result=image.payload();result[0]=0;assertEquals((byte)0xff,image.payload()[0]);
    }
    @Test void refusesWrongObjectMissingPayloadAndScnDisagreement() {
        assertThrows(RedoException.class,()->SecureFileLobPageImage.decode(vector(load(),new byte[4000]),79343));
        assertThrows(RedoException.class,()->SecureFileLobPageImage.decode(vector(load(),new byte[3999]),79341));
        byte[] wrong=load();wrong[8]^=1;
        assertThrows(RedoException.class,()->SecureFileLobPageImage.decode(vector(wrong,new byte[4000]),79341));
    }
}
