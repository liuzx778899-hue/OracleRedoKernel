package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryIndexUndoPairTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    String nameKey="04c30834561a5359535f4330303030345f32363130303531373a32373a303824";
    RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,36,11,37749351,2522903,2,3,List.of(
            hex("78002c04220000000a001200e00800001b013e00"),hex("300000003000000000000000000000000a16123d0000e008"),
            hex("040d140018001000080006009d0200007f0a400212014d0000800080cc7d2600"),hex("03020100b00140006c4540000000000000000000"),hex(nameKey)));}
    RedoRecords.Vector row(){return new RedoRecords.Vector(10,2,1,9,4212076,2522903,3,3,List.of(
            hex("010d66a0000000000a001200e0080000670240021b013e00"),hex("02002f002a00"),hex(nameKey),hex("004089a40033")));}
    RedoRecords.Record record(RedoRecords.Vector...v){return new RedoRecords.Record(6708,104,312,1,2522903,3,677737149,List.of(v));}
    DictionaryIndexUndoPair bind(RedoRecords.Vector u,RedoRecords.Vector r)throws Exception {
        return DictionaryIndexUndoPair.bind(record(u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","I_COL1",48,48),0x004001b0,"AL32UTF8");
    }
    @Test void scopedNameKeyDecodesParentAndBigEndianRowidAndGenericEntryStillRefuses()throws Exception {
        var u=undo();var r=row();var p=bind(u,r);
        assertEquals(75185,p.key().objectId());assertEquals(4229540,p.key().rowDba());assertEquals(51,p.key().rowSlot());
        assertTrue(p.key().columnName().startsWith("SYS_C00004_"));assertNull(p.key().columnNumber());
        assertEquals(new HeapRowPatch.Xid(10,18,2272),p.physical().undo().identity().xid());
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(u,r),r));
    }
    @Test void inlineNumberKeySupportsSparseInsertAndRestoreFieldsOnlyInScopedPath()throws Exception {
        var u=new RedoRecords.Vector(5,1,22,11,37749054,2522910,1,3,List.of(
                hex("680016122200000003001e00c1020000b4012700"),hex("310000003100000000000000000000000a161e260000c102"),
                hex("040d0000000000000a001200e0080000670240021b013f0000800080187f2600"),hex("05020100b8014000848a40000100000000000000"),hex("04c3083456018006004089a40033")));
        var r=new RedoRecords.Vector(10,4,1,9,4229764,2522910,1,3,List.of(hex("010d1a03ba77000003001e00c10200003e014002b4012700"),hex("020028001200")));
        var p=DictionaryIndexUndoPair.bind(record(u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","I_COL2",49,49),0x004001b8,"AL32UTF8");
        assertEquals(IndexUndoPair.Kind.DELETE,p.physical().forward().kind());assertEquals(0,p.key().columnNumber());
        assertEquals(0,p.physical().undo().keydata().length);assertThrows(RedoException.class,()->IndexUndoPair.bind(record(u,r),r));
        u.fields().get(3)[0]=3;
        var insert=new RedoRecords.Vector(10,2,1,9,r.dba(),r.scn(),1,3,List.of(r.fields().get(0),r.fields().get(1),u.fields().get(4)));
        var sparse=DictionaryIndexUndoPair.bind(record(u,insert),insert,new FrozenHeapBaseline.ObjectRole("SYS","I_COL2",49,49),0x004001b8,"AL32UTF8");
        assertEquals(IndexUndoPair.Kind.INSERT,sparse.physical().forward().kind());assertEquals(0,sparse.physical().forward().keydata().length);
        assertThrows(RedoException.class,()->IndexUndoPair.bind(record(u,insert),insert));
    }
    @Test void roleGenerationRootDuplicateAndAlteredKeyRefuse()throws Exception {
        var u=undo();var r=row();
        assertThrows(RedoException.class,()->DictionaryIndexUndoPair.bind(record(u,u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","I_COL1",48,48),0x004001b0,"AL32UTF8"));
        assertThrows(RedoException.class,()->DictionaryIndexUndoPair.bind(record(u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","I_COL1",48,49),0x004001b0,"AL32UTF8"));
        assertThrows(RedoException.class,()->DictionaryIndexUndoPair.bind(record(u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","I_COL1",48,48),0x004001b8,"AL32UTF8"));
        u.fields().get(4)[4]++;assertThrows(RedoException.class,()->bind(u,r));
    }
    @Test void truncatedUnknownComponentRowidAndNonintegralKeysRefuse()throws Exception {
        var data=hex("004089a40033");
        for(String key:List.of("04c3083456","00","04c3083456ff","04c30834560545585452","04808080800180","02c0020158","01800158"))
            assertThrows(RedoException.class,()->DictionaryIndexKey.decode("I_COL1",hex(key),data,"AL32UTF8"));
        assertThrows(RedoException.class,()->DictionaryIndexKey.decode("I_COL2",hex("04c3083456018006004089a40033"),data,"AL32UTF8"));
        assertThrows(RedoException.class,()->DictionaryIndexKey.decode("I_COL3",hex("04c30834560180"),data,"AL32UTF8"));
        assertThrows(RedoException.class,()->DictionaryIndexKey.decode("I_COL1",hex(nameKey),new byte[6],"AL32UTF8"));
    }
}
