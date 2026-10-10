package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HeapArrayUndoPairTest {
    static byte[] hex(String text){return HexFormat.of().parseHex(text);}
    @Test void raw112QmiUsesItsSecondItlAsTheLock()throws Exception {
        var r=record();r.vectors().get(0).fields().get(3)[12]=2;
        r.vectors().get(1).fields().get(1)[12]=2;r.vectors().get(1).fields().get(1)[17]=2;
        assertEquals(3,HeapArrayUndoPair.bind(r,r.vectors().get(1),3).size());
        r.vectors().get(1).fields().get(1)[17]=1;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(r,r.vectors().get(1),3));
    }
    @Test void raw112NativeZPaddingDoesNotBecomeUndoOwnership()throws Exception {
        var r=record();var old=r.vectors().get(0);var fields=new ArrayList<>(old.fields());
        fields.set(2,hex("030d24000b00ffff"));
        var undo=new RedoRecords.Vector(old.opcodeMajor(),old.opcodeMinor(),old.blockClass(),old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),fields);
        assertEquals(3,HeapArrayUndoPair.inverse(undo,3).size());
        var rollback=rollback(true);var rollbackFields=new ArrayList<>(rollback.fields());rollbackFields.set(0,hex("030d24000b00ffff"));
        var xr=new RedoRecords.Vector(11,12,rollback.blockClass(),rollback.file(),rollback.dba(),rollback.scn(),rollback.sequence(),rollback.containerId(),rollbackFields);
        assertEquals(20,HeapArrayUndoPair.rollback(xr,3).size());
        fields.get(2)[2]++;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.inverse(undo,3));
    }
    // Frozen raw108, RBA 15191/248 and 15200/296; native dump independently identifies XR QMD.
    RedoRecords.Vector rollback(boolean begin) {
        return new RedoRecords.Vector(11,12,1,12,0x0300b4fcL,3225993,1,3,List.of(
                hex(begin?"030d008081393100":"020d000000000000330d4002af011a00"),
                hex(begin?"fcb40003fab40003fa120c02010000000000140000000100020003000400050006000700080009000a000b000c000d000e000f0010001100120013000000"
                        :"fcb40003fab40003fa120c0201000000000014001400150016001700180019001a001b001c001d001e001f00200021002200230024002500260027000000")));
    }
    @Test void decodesFrozenFullAndSavepointRollbackSlotsWithoutInventingValues()throws Exception {
        for(boolean begin:List.of(true,false)) {
            var rows=HeapArrayUndoPair.rollback(rollback(begin),3);
            assertEquals(20,rows.size());
            for(int i=0;i<20;i++) {
                var row=rows.get(i);assertEquals(i+(begin?0:20),row.slot());
                assertEquals(HeapRowPatch.Kind.DELETE,row.kind());assertEquals(2,row.xtype());
                assertNull(row.xid());assertNull(row.uba());
                assertTrue(row.columns().stream().allMatch(c->c.state()==HeapRowPatch.State.ABSENT));
            }
        }
    }
    @Test void refusesPoisonedRollbackCountDuplicateSlotAndForwardXtype() {
        var count=rollback(true);count.fields().get(1)[18]--;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.rollback(count,3));
        var duplicate=rollback(true);duplicate.fields().get(1)[22]=0;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.rollback(duplicate,3));
        var xtype=rollback(false);xtype.fields().get(1)[11]=1;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.rollback(xtype,3));
    }
    @Test void completeOwnedArrayIsRequiredBeforeCancellingItsUndoNode()throws Exception {
        var r=record();var pairs=HeapArrayUndoPair.bind(r,r.vectors().get(1),3);var original=pairs.get(0).undo().identity();
        byte[] header=java.util.Arrays.copyOf(r.vectors().get(0).fields().get(1),28);header[20]=0x18;
        var marker=new RedoRecords.Vector(5,11,35,11,0x02400110L,2923460,1,3,List.of(header,new byte[8]));
        byte[] kdo=r.vectors().get(0).fields().get(3).clone();kdo[11]=2;
        var row=new RedoRecords.Vector(11,12,1,12,0x03006cbcL,2923460,1,3,List.of(hex("030d000000000000"),kdo));
        var cancellation=new RedoRecords.Record(1320,20,100,1,2923460,1,677737149,List.of(row,marker));
        assertEquals(pairs,HeapArrayUndoPair.matchRollback(cancellation,marker,row,pairs,3,original));
        assertThrows(RedoException.class,()->HeapArrayUndoPair.matchRollback(cancellation,marker,row,pairs.subList(0,2),3,original));
        kdo[24]=3;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.matchRollback(cancellation,marker,row,pairs,3,original));
    }
    @Test void currentUbaWithoutXidStillRequiresExactSameRecordUndoOwnership()throws Exception {
        var r=record();byte[] prior=r.vectors().get(1).fields().get(0);byte[] current=new byte[16];current[0]=2;current[1]=13;
        System.arraycopy(prior,16,current,8,8);
        var fields=new ArrayList<>(r.vectors().get(1).fields());fields.set(0,current);
        var row=new RedoRecords.Vector(11,11,1,12,0x03006cbcL,2923459,1,3,List.copyOf(fields));
        var own=new RedoRecords.Record(1319,76,100,1,2923459,1,677737149,List.of(r.vectors().get(0),row));
        var pairs=HeapArrayUndoPair.bind(own,row,3);assertEquals(3,pairs.size());assertNull(pairs.get(0).forward().xid());
        current[14]++;
        assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(own,row,3));
    }
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
