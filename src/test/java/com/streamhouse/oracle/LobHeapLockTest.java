package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobHeapLockTest {
    byte[] hex(String value) {return HexFormat.of().parseHex(value);}
    RedoRecords.Vector row() {return new RedoRecords.Vector(11,4,1,12,50365967,3043508,1,3,List.of(
            hex("010d0000000000000a000700a91000002c05400290010200"),hex("0f8600030a860003fa1204010200ffff00000002")));}
    RedoRecords.Vector undo() {return new RedoRecords.Vector(5,1,36,11,37750060,3043508,1,3,List.of(
            hex("7000841f120000000a000700a910000090010200"),
            hex("e6350100e635010005000000000000000b010700080c0100000000002c0540029001010022702e000000000024702e000000000000000000b4702e00000000002a0540020000000000000000"),
            hex("030d000000000000"),hex("0f8600030a860003fa1204010200ffff00000000")));}
    RedoRecords.Record record(RedoRecords.Vector row,RedoRecords.Vector undo) {return new RedoRecords.Record(33817,156,3068,9,3043508,1,677737149,List.of(row,undo));}
    @Test void bindsActualLobLockTwoAndItsInverseWithFullOwner()throws Exception {
        var row=row();var pair=HeapUndoPair.bindLob(record(row,undo()),row,3);
        assertEquals(2,pair.forward().lockTo());assertEquals(0,pair.undo().inverse().lockTo());
        assertEquals(new HeapRowPatch.Xid(10,7,4265),pair.undo().identity().xid());
        assertTrue(pair.forward().columns().stream().allMatch(c->c.state()==HeapRowPatch.State.ABSENT));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(row,3));
    }
    @Test void decodesActualRollbackUnlockWithoutInventingXidOrValues()throws Exception {
        var row=new RedoRecords.Vector(11,4,1,12,50365967,3039854,1,3,List.of(
                hex("030d000000000000"),hex("0f8600030a860003fa1204020200400000000000")));
        var value=new HeapRowPatch().decodeLob(row,3);assertEquals(0,value.lockTo());assertNull(value.xid());assertNull(value.uba());
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(row,3));
    }
    @Test void rejectsUnobservedItlScopeAndWrongUndoUnlock() {
        var row=row();row.fields().get(1)[12]=3;assertThrows(RedoException.class,()->new HeapRowPatch().decodeLob(row,3));
        assertThrows(RedoException.class,()->HeapUndoPair.bindLob(record(row(),undo()),row(),4));
        var current=row();var inverse=undo();inverse.fields().get(3)[19]=1;
        assertThrows(RedoException.class,()->HeapUndoPair.bindLob(record(current,inverse),current,3));
    }
}
