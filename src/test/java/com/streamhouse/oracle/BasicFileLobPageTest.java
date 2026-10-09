package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BasicFileLobPageTest {
    byte[] page() {
        byte[] value=new byte[8168];
        byte[] header=HexFormat.of().parseHex("e73501000000000100000034f63500000500000000000000010000007886000300000000");
        System.arraycopy(header,0,value,0,header.length);value[36]=(byte)0x80;return value;
    }
    RedoRecords.Vector vector(byte[] value,byte[] tail) {
        return new RedoRecords.Vector(19,1,1,12,50366076,3043513,1,3,List.of(value,tail));
    }
    @Test void preservesBinaryPageAndDefendsAgainstMutation()throws Exception {
        byte[] value=page();var decoded=BasicFileLobPage.decode(vector(value,new byte[]{0x28}),79335);
        assertEquals("0000000100000034f635",decoded.lobId());assertEquals(5,decoded.version());
        assertEquals(1,decoded.pageNumber());assertEquals(50366072,decoded.parentDba());
        assertEquals(8132,decoded.payload().length);assertEquals((byte)0x80,decoded.payload()[0]);
        value[36]=0;byte[] copy=decoded.payload();copy[0]=0;assertEquals((byte)0x80,decoded.payload()[0]);
    }
    @Test void refusesForeignObjectTruncatedPageAndUnknownProfile() {
        assertThrows(RedoException.class,()->BasicFileLobPage.decode(vector(page(),new byte[]{0x28}),79337));
        assertThrows(RedoException.class,()->BasicFileLobPage.decode(vector(Arrays.copyOf(page(),8167),new byte[]{0x28}),79335));
        assertThrows(RedoException.class,()->BasicFileLobPage.decode(vector(page(),new byte[]{0x29}),79335));
        byte[] reserved=page();reserved[32]=1;
        assertThrows(RedoException.class,()->BasicFileLobPage.decode(vector(reserved,new byte[]{0x28}),79335));
        byte[] empty=page();Arrays.fill(empty,4,14,(byte)0);
        assertThrows(RedoException.class,()->BasicFileLobPage.decode(vector(empty,new byte[]{0x28}),79335));
    }
}
