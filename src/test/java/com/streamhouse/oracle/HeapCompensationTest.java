package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapCompensationTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector marker() {
        return new RedoRecords.Vector(5,6,36,11,0x02402cbe,2148799,1,3,List.of(
                hex("401d0100401d010005000000000000000b01052210006d04020db771"),hex("d410200002000000")));
    }
    RedoRecords.Vector row() {
        return new RedoRecords.Vector(11,5,1,12,0x03000083,2148799,1,3,List.of(
                hex("020db77100000000be2c4002f2002100"),hex("8300000382000003fa120502010000002c01120000000401fdff000000"),hex("0100"),hex("6b656570")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(30718,304,1000,1,2148799,1,677737149,List.of(v)); }
    HeapUndoPair pair() throws Exception {
        var f=new RedoRecords.Vector(11,5,1,12,0x03000083,2148799,1,3,List.of(
                hex("020d000000000000be2c4002f2002300"),hex("8300000382000003fa120501010000002c01120000000401fdff000000"),hex("0100"),hex("64697363617264")));
        var u=new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,3,List.of(
                hex("5c00da10220000000a0005006d040000f2002300"),hex("401d0100401d010005000000000000000b01052200006d04"),
                hex("020db77100000000be2c4002f2002100"),hex("8300000382000003fa120501010000002c01120000000401fdff000000"),hex("0100"),hex("6b656570")));
        return HeapUndoPair.bind(new RedoRecords.Record(30715,16,1000,13,2148799,1,677737149,List.of(f,u)),f,4);
    }
    @Test void matchesOriginalUndoDespiteRestoredEarlierUba() throws Exception {
        var c=marker(); var row=row(); var pair=pair();
        assertEquals(35,HeapCompensation.match(record(row,c),c,row,List.of(pair),4).undo().identity().uba().record());
        assertEquals(33,new HeapRowPatch().decode(row,4).uba().record());
    }
    @Test void missingPhysicalExtentCannotCertifyKnownRollbackExtent() throws Exception {
        var pair=pair();var u=pair.undo();var p=u.inverse();
        var unknown=new HeapRowPatch.Patch(p.kind(),p.dba(),p.slot(),p.xtype(),p.xid(),p.uba(),p.columns());
        var history=new HeapUndoPair(pair.containerUid(),pair.containerId(),pair.forward(),new UndoRowPatch(u.identity(),unknown,u.previousDba(),u.predecessorRecord(),u.wrap()));
        var marker=marker();var row=row();
        assertEquals("MISSING_HEAP_COMPENSATION_ORIGINAL",assertThrows(RedoException.class,()->HeapCompensation.match(record(marker,row),marker,row,List.of(history),4)).getMessage());
    }
    @Test void refusesMissingAndMultipleOriginalCandidates() throws Exception {
        var c=marker(); var row=row(); var pair=pair();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(),4));
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair,pair),4));
    }
    @Test void doesNotGuessFromMatchingRowWhenPredecessorDiffers() throws Exception {
        var c=marker(); c.fields().get(0)[19]=35; var row=row(); var pair=pair();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair),4));
    }
    @Test void rejectsWrongValueOrStateAndDuplicatePhysicalRows() throws Exception {
        var c=marker(); var row=row(); row.fields().get(3)[0]=0x78; var pair=pair();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair),4));
        var duplicate=row();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,duplicate,duplicate),c,duplicate,List.of(pair),4));
        var nullRow=new RedoRecords.Vector(11,5,1,12,0x03000083,2148799,1,3,
                List.of(duplicate.fields().get(0),duplicate.fields().get(1),duplicate.fields().get(2),new byte[0]));
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,nullRow),c,nullRow,List.of(pair),4));
    }
    @Test void rejectsContainerAndSlotWrapReuse() throws Exception {
        var c=marker(); var row=row(); var pair=pair();
        var mixed=new HeapUndoPair(1,3,pair.forward(),pair.undo());
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(mixed),4));
        c.fields().get(0)[22]=(byte)0x6e;
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair),4));
    }
    @Test void rejectsBeginCompensationAndForwardRow() throws Exception {
        var row=row(); var pair=pair(); var c=marker(); row.fields().get(1)[11]=1;
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair),4));
        var begin=new RedoRecords.Vector(5,11,35,11,0x02400110,2148799,1,3,List.of(
                hex("401d0100401d010005000000000000000b010500180c010000000000"),hex("900a200002000000")));
        var rollback=row();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(begin,rollback),begin,rollback,List.of(pair),4));
    }
    @Test void rejectsSameLowWrapAcrossDifferentFullTransactionSequences() throws Exception {
        var pair=pair(); var u=pair.undo(); var h=u.identity();
        var xid=new HeapRowPatch.Xid(h.xid().usn(),h.xid().slot(),h.xid().sequence()+65536);
        var otherHeader=new UndoHeader(xid,h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),h.beginsTransaction());
        var other=new HeapUndoPair(pair.containerUid(),pair.containerId(),pair.forward(),
                new UndoRowPatch(otherHeader,u.inverse(),u.previousDba(),u.predecessorRecord(),u.wrap()));
        var c=marker(); var row=row();
        assertThrows(RedoException.class,()->HeapCompensation.match(record(c,row),c,row,List.of(pair,other),4));
    }
}
