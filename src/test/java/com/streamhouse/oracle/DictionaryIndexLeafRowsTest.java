package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryIndexLeafRowsTest {
    byte[] hex(String value){return HexFormat.of().parseHex(value);}
    RedoRecords.Vector oldLeaf() {
        var f=new ArrayList<byte[]>(List.of(new byte[0],hex("2f514000"),hex("000012002400"),
                hex("0000004089a4000d04c308345602c1020101004089a4003304c308345602c105")));
        for(int i=0;i<8;i++)f.add(new byte[0]);
        return new RedoRecords.Vector(10,8,1,9,0x0040512d,2567426,1,3,f);
    }
    @Test void preservesPhysicalSlotDeletedFlagsAndBigEndianRowid()throws Exception {
        var v=DictionaryIndexLeafRows.rebuilt(oldLeaf());assertEquals(2,v.rows().size());
        assertEquals(0x0040512f,v.nextLink());assertNull(v.previousLink());
        assertEquals(1,v.rows().get(1).flags());assertEquals(1,v.rows().get(1).itl());
        assertEquals(75185,v.rows().get(1).key().objectId());assertEquals(4,v.rows().get(1).key().columnNumber());
        assertEquals(0x004089a4,v.rows().get(1).key().rowDba());assertEquals(51,v.rows().get(1).key().rowSlot());
    }
    @Test void refusesDirectoryTruncationOverlapAndWrongEnd() {
        for(int at:List.of(0,2,4)) {
            var v=oldLeaf();v.fields().get(2)[at]^=1;assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(v));
        }
        var tail=oldLeaf();tail.fields().set(4,new byte[1]);assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(tail));
    }
    @Test void refusesUnknownDeletedItlZeroRowidAndUnorderedKeys() {
        for(int at:List.of(0,1,8)) {
            var v=oldLeaf();v.fields().get(3)[at]=(byte)0xff;assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(v));
        }
        var rowid=oldLeaf();Arrays.fill(rowid.fields().get(3),2,8,(byte)0);assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(rowid));
        var order=oldLeaf();order.fields().get(3)[31]=2;assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(order));
    }
    @Test void newLeafRequiresItsOwnHeaderAndDirectoryOrigin()throws Exception {
        var old=oldLeaf();var f=new ArrayList<>(old.fields());byte[] ktb=new byte[68];ktb[0]=5;ktb[1]=0x0d;
        f.set(0,ktb);f.set(1,hex("0102062e000000002e5140002d514000"));f.set(2,hex("640076008800"));
        var v=new RedoRecords.Vector(10,8,1,9,0x0040512f,2567426,1,3,f);
        assertEquals(0x0040512d,DictionaryIndexLeafRows.rebuilt(v).previousLink());
        f.get(1)[3]=0;assertThrows(RedoException.class,()->DictionaryIndexLeafRows.rebuilt(v));
    }
}
