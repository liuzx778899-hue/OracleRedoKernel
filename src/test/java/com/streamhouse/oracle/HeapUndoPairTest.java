package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapUndoPairTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector row() {
        return new RedoRecords.Vector(11,5,1,12,0x03000083,2148799,1,3,List.of(
                hex("020d000000000000be2c4002f2002300"),hex("8300000382000003fa120501010000002c01120000000401fdff000000"),hex("0100"),hex("64697363617264")));
    }
    RedoRecords.Vector undo() {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,3,List.of(
                hex("5c00da10220000000a0005006d040000f2002300"),hex("401d0100401d010005000000000000000b01052200006d04"),
                hex("020db77100000000be2c4002f2002100"),hex("8300000382000003fa120501010000002c01120000000401fdff000000"),hex("0100"),hex("6b656570")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(30715,16,1000,13,2148799,1,677737149,List.of(v)); }
    @Test void bindsExactUbaWithoutXidOrAdjacencyAndKeepsPartialBefore() throws Exception {
        var row=row(); var undo=undo();
        var p=HeapUndoPair.bind(record(undo,row),row,4);
        assertEquals(new HeapRowPatch.Xid(10,5,1133),p.undo().identity().xid());
        assertEquals("keep",OracleScalars.varchar2(p.undo().inverse().columns().get(1).bytes(),"AL32UTF8"));
        assertEquals("discard",OracleScalars.varchar2(p.forward().columns().get(1).bytes(),"AL32UTF8"));
        assertEquals(HeapRowPatch.State.ABSENT,p.undo().inverse().columns().get(0).state());
        assertEquals(p.undo().identity().uba(),HeapUndoPair.bind(record(row,undo),row,4).forward().uba());
    }
    @Test void refusesMissingAndAmbiguousUndoAndDuplicateForward() {
        var row=row(); var undo=undo();
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row),row,4));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,undo,undo),row,4));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,row,undo),row,4));
    }
    @Test void refusesWrongSlotAndColumnSet() {
        var row=row(); var slot=undo(); slot.fields().get(3)[20]=1;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,slot),row,4));
        var cols=undo(); cols.fields().get(4)[0]=2;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,cols),row,4));
    }
    @Test void refusesRestoredRollbackPointerAndWrongSequence() {
        var rollback=row(); rollback.fields().get(1)[11]=2;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(rollback,undo()),rollback,4));
        var row=row(); var undo=undo(); undo.fields().get(0)[16]=(byte)243;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,undo),row,4));
    }
    @Test void refusesContainerMixAndForeignRecord() {
        var row=row(); var undo=undo();
        var other=new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,4,undo.fields());
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,other),row,4));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(undo),row,4));
    }
    @Test void refusesExplicitXidWrongBlockAndNonInverseOperation() {
        var row=row(); var undo=undo();
        var f=new RedoRecords.Vector(11,5,1,12,0x03000083,2148799,1,3,List.of(
                hex("010d0000000000000a0005006e040000be2c4002f2002300"),row.fields().get(1),row.fields().get(2),row.fields().get(3)));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(f,undo),f,4));
        var wrongBlock=undo(); wrongBlock.fields().get(3)[0]=(byte)0x84;
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(row,wrongBlock),row,4));
        var delete=new RedoRecords.Vector(11,3,1,12,0x03000083,2148799,1,3,List.of(row.fields().get(0),
                hex("8300000382000003fa1203010100000000000000")));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record(delete,undo),delete,4));
    }
}
