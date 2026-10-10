package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HeapArrayUndoPairTest {
    static byte[] hex(String text){return HexFormat.of().parseHex(text);}
    // Owned 19.3 archive90 QMI/QMD headers; small row values exercise the same bounded layout.
    RedoRecords.Record record() {
        var undo=new RedoRecords.Vector(5,1,36,11,0x02400232L,2923459,1,3,List.of(
                hex("780062041200a2fd0a000100270e00006d014b00"),
                hex("e6310100e631010005000000000000000b010100080c010000000000320240026d014a00c6982c0000000000c9982c0000000000ff7f0000ffffffffffffffff300240020000000000000000"),
                hex("030d000000000000"),hex("bc6c0003ba6c0003fa120c0101000000000003000000010002000000")));
        var row=new RedoRecords.Vector(11,11,1,12,0x03006cbcL,2923459,1,3,List.of(
                hex("010d0000000000000a000100270e0000320240026d014b00"),
                hex("bc6c0003ba6c0003fa120b0101000000000103000000010002000000"),
                hex("080009000800"),hex("2c000301800161ff2c000302c1020162ff2c000301800163ff")));
        return new RedoRecords.Record(1319,76,100,1,2923459,1,677737149,List.of(undo,row));
    }
    @Test void oneUndoOwnsAllRowsWithoutLosingSourceOrder()throws Exception {
        var r=record();var pairs=HeapArrayUndoPair.bind(r,r.vectors().get(1),3);
        assertEquals(3,pairs.size());assertEquals(List.of(0,1,2),pairs.stream().map(p->p.forward().slot()).toList());
        assertEquals(1,pairs.stream().map(p->p.undo().identity()).distinct().count());
        for(int i=0;i<3;i++) {
            var p=pairs.get(i);assertEquals(HeapRowPatch.Kind.INSERT,p.forward().kind());
            assertEquals(HeapRowPatch.Kind.DELETE,p.undo().inverse().kind());
            assertEquals(HeapRowPatch.State.NULL,p.forward().columns().get(2).state());
            assertEquals(String.valueOf((char)('a'+i)),OracleScalars.varchar2(p.forward().columns().get(1).bytes(),"AL32UTF8"));
        }
    }
    @Test void refusesMissingRowBytesRepeatedSlotsAndWrongUndoOwnership() {
        var r=record();var fields=new ArrayList<>(r.vectors().get(1).fields());
        fields.get(3)[3]=2;assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(r,r.vectors().get(1),3));
        var repeated=record();repeated.vectors().get(1).fields().get(1)[24]=1;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(repeated,repeated.vectors().get(1),3));
        var foreign=record();foreign.vectors().get(0).fields().get(0)[18]++;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(foreign,foreign.vectors().get(1),3));
    }
    @Test void refusesWrongWidthsRowLengthsAndUnmatchedInverseSlots() {
        var r=record();assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(r,r.vectors().get(1),2));
        r.vectors().get(1).fields().get(2)[0]++;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(r,r.vectors().get(1),3));
        var other=record();other.vectors().get(0).fields().get(3)[24]=3;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(other,other.vectors().get(1),3));
    }
    @Test void opaquePaddingMustMatchTheOwnedInverse()throws Exception {
        var r=record();
        r.vectors().get(0).fields().get(2)[3]=(byte)0x80;
        r.vectors().get(0).fields().get(2)[4]=0x5a;
        for(var bytes:List.of(r.vectors().get(0).fields().get(3),r.vectors().get(1).fields().get(1))) {
            bytes[14]=0x3d;bytes[15]=0x3d;
        }
        assertEquals(3,HeapArrayUndoPair.bind(r,r.vectors().get(1),3).size());
        r.vectors().get(0).fields().get(3)[14]++;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(r,r.vectors().get(1),3));
    }
}
