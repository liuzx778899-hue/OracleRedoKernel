package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexOriginalRestoreTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    RedoRecords.Vector row(){return new RedoRecords.Vector(10,5,1,12,50342539,2800719,1,3,List.of(
            hex("020d4002490114009a03400249011500"),hex("020000000c00"),hex("03002a840000")));}
    RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,36,11,37749658,2800719,4,3,List.of(
            hex("54001216220000000a0006002c0c000049011543"),hex("b2250100b225010005000000000000000a16061400002c0c"),
            hex("020d0000000000009a03400249011400"),hex("040201008a2a00038b2a00030000000000000000"),hex("0180"),hex("03002a840000")));}
    RedoRecords.Record record(RedoRecords.Vector...v){return new RedoRecords.Record(7,328,236,1,2800719,5,677737149,List.of(v));}
    @Test void bindsActualNormalRestoreToMarkDeletedUndo()throws Exception {
        var row=row();var undo=undo();var pair=IndexUndoPair.bind(record(undo,row),row);
        assertEquals(IndexUndoPair.Kind.RESTORE,pair.forward().kind());
        assertEquals(IndexUndoPair.Kind.DELETE,pair.undo().kind());
        assertEquals(new HeapRowPatch.Xid(10,6,3116),pair.undo().identity().xid());
        assertEquals(0,pair.forward().slot());assertEquals(12,pair.forward().rowSize());
        assertArrayEquals(hex("0180"),pair.forward().key());
        assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(row));
    }
    @Test void rejectsChangedKeydataSizeAndMissingUndo()throws Exception {
        for(int mode=0;mode<3;mode++){
            var row=row();var undo=undo();
            if(mode==0)row.fields().get(2)[0]^=1;
            if(mode==1)row.fields().get(1)[4]^=1;
            var record=mode==2?record(row):record(undo,row);
            assertThrows(RedoException.class,()->IndexUndoPair.bind(record,row));
        }
    }
}
