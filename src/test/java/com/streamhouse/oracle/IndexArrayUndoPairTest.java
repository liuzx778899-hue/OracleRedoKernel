package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class IndexArrayUndoPairTest {
    static byte[] hex(String s){return HexFormat.of().parseHex(s);}
    // Exact owned archive90 ARRAY index packet independently checked by the native dump.
    RedoRecords.Record record() {
        String keys="018002c10202c10302c10402c10502c10602c10702c10802c10902c10a02c10b02c10c02c10d02c10e02c10f02c11002c11102c11202c11302c114";
        var u=new RedoRecords.Vector(5,1,36,11,0x02400232L,2923460,1,3,List.of(
                hex("c4004402220000000a000100270e00006d015200"),hex("e7310100e731010005000000000000000a1601510000270e"),
                hex("040d0c0004000400ffff000000000000000000000000000000800080bb9b2c00"),
                hex("03022100c26c0003c36c0003ff7f0000000089831400000002000300030003000300030003000300030003000300030003000300030003000300030003000300"),hex(keys),hex("ffffff")));
        var f=new RedoRecords.Vector(10,2,1,12,0x03006cc3L,2923460,1,3,List.of(
                hex("010d5000000000000a000100270e0000320240026d015200"),
                hex("02200000030100001400000000000100020003000400050006000700080009000a000b000c000d000e000f001000110012001300"),hex(keys),
                hex("03006cbc000003006cbc000103006cbc000203006cbd000003006cbd000103006cbd000203006cbe000003006cbe000103006cbe000203006cbf000003006cbf000103006cbf000203006cbb000003006cbb000103006cbb000203006cca000003006cca000103006cca000203006ccb000003006ccb0001"),
                hex("02000300030003000300030003000300030003000300030003000300030003000300030003000300")));
        return new RedoRecords.Record(1336,232,100,1,2923460,1,677737149,List.of(u,f));
    }
    @Test void keepsTwentyKeysAndSlotsOwnedByOneUndo()throws Exception {
        var r=record();var pairs=IndexArrayUndoPair.bind(r,r.vectors().get(1));
        assertEquals(20,pairs.size());assertEquals(1,pairs.stream().map(p->p.undo().identity()).distinct().count());
        for(int i=0;i<20;i++) {
            var p=pairs.get(i);assertEquals(i,p.forward().slot());assertEquals(6,p.forward().keydata().length);
            assertArrayEquals(p.forward().key(),p.undo().key());assertEquals(IndexUndoPair.Kind.PURGE,p.undo().kind());
        }
    }
    @Test void currentUbaWithoutInlineXidBindsOnlyItsOwnedUndo()throws Exception {
        var r=record();var original=r.vectors().get(1);var fields=new ArrayList<>(original.fields());
        byte[] ktb=new byte[16];ktb[0]=2;ktb[1]=13;System.arraycopy(fields.get(0),16,ktb,8,8);fields.set(0,ktb);
        var row=new RedoRecords.Vector(10,2,1,12,original.dba(),original.scn(),1,3,List.copyOf(fields));
        var owned=new RedoRecords.Record(r.block(),r.offset(),100,1,r.scn(),1,r.containerUid(),List.of(r.vectors().get(0),row));
        var pairs=IndexArrayUndoPair.bind(owned,row);assertEquals(20,pairs.size());assertNull(pairs.get(0).forward().xid());
        ktb[14]++;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(owned,row));
    }
    @Test void refusesMismatchedKeysPartialBitmapAndDuplicateSlots() {
        var keys=record();keys.vectors().get(0).fields().get(4)[1]++;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(keys,keys.vectors().get(1)));
        var bitmap=record();bitmap.vectors().get(0).fields().get(5)[0]=0x7f;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(bitmap,bitmap.vectors().get(1)));
        var slots=record();slots.vectors().get(1).fields().get(1)[14]=0;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(slots,slots.vectors().get(1)));
    }
    @Test void refusesWrongAggregateSizeAndForeignUba() {
        var size=record();size.vectors().get(1).fields().get(1)[4]++;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(size,size.vectors().get(1)));
        var uba=record();uba.vectors().get(0).fields().get(0)[18]++;
        assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(uba,uba.vectors().get(1)));
    }
    @Test void observedCleanoutAndLastSlotSnoDoNotReplaceCurrentOwner()throws Exception {
        var r=record();var original=r.vectors().get(1);var fields=new ArrayList<>(original.fields());
        byte[] ktb=hex("110d000020d541120a00180099130000090d4002af01030000000000209b6e8d317d0000a0243488317d000002010d005f21310000800000020600804ed33000000000000000");
        System.arraycopy(original.fields().get(0),8,ktb,8,16);fields.set(0,ktb);
        var leaf=fields.get(1);leaf[2]=20;
        for(int i=0;i<20;i++)leaf[12+i*2]=(byte)(i+1);
        var row=new RedoRecords.Vector(10,2,1,12,original.dba(),3219800,1,3,fields);
        var owned=new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),3219808,1,r.containerUid(),List.of(r.vectors().get(0),row));
        assertEquals(20,IndexArrayUndoPair.bind(owned,row).size());
        ktb[60]=(byte)0xff;ktb[61]=(byte)0xff;ktb[62]=(byte)0xff;ktb[63]=0x7f;
        assertEquals("INDEX_ARRAY_CLEANOUT_SCN",assertThrows(RedoException.class,()->IndexArrayUndoPair.bind(owned,row)).getMessage());
    }
}
