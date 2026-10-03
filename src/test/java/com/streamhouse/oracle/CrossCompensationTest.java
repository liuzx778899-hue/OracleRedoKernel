package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrossCompensationTest {
    // Real seq12 regular 5.11 header; slot adapted to the existing synthetic active-state fixture.
    RedoRecords.Vector marker() {
        var hex=HexFormat.of();byte[] b=hex.parseHex("571d0100571d0100050000003c2040020b011a0010009d02020d4b4b");b[18]=4;
        return new RedoRecords.Vector(5,11,21,11,0x024000a0,2148799,1,3,List.of(b,hex.parseHex("0c1c895b02000000")));
    }
    HeapUndoPair pair() throws Exception {
        var p=new HeapCompensationTest().pair();var row=new HeapRowPatch().decode(new HeapCompensationTest().row(),4);
        var inverse=new HeapRowPatch.Patch(row.kind(),row.dba(),row.slot(),1,null,null,row.columns());
        var h=new UndoHeader(new HeapRowPatch.Xid(3,4,669),new HeapRowPatch.Uba(0x0240203d,399,1),73047,73047,5,false);
        return new HeapUndoPair(677737149,3,p.forward(),new UndoRowPatch(h,inverse,0x0240203c,0,669));
    }
    @Test void regularFiveElevenIsCrossBlockNotBegin() throws Exception {
        var h=UndoCompensation.decode(marker());assertFalse(h.beginsTransaction());assertTrue(h.crossesUndoBlock());assertEquals(669,h.wrap());
        assertTrue(UndoCompensation.decode(new BeginCompensationTest().marker()).beginsTransaction());
    }
    @Test void matchesByActiveFullIdentityWithoutUsingRestoredRowUba() throws Exception {
        var helper=new HeapCompensationTest();var m=marker();var row=helper.row();var p=pair();var state=new BeginCompensationTest().state(true);
        assertSame(p,HeapCompensation.matchCross(helper.record(m,row),m,row,List.of(p),4,state));
        assertNotEquals(p.undo().identity().uba(),new HeapRowPatch().decode(row,4).uba());
        assertThrows(RedoException.class,()->HeapCompensation.match(helper.record(m,row),m,row,List.of(p),4));
    }
    @Test void refusesMissingIdentityAndLocksState() throws Exception {
        var helper=new HeapCompensationTest();var m=marker();var row=helper.row();var p=pair();var state=new BeginCompensationTest().state(false);
        assertThrows(RedoException.class,()->HeapCompensation.matchCross(helper.record(m,row),m,row,List.of(p),4,state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void sameLowWrapCannotMatchAnotherFullSequence() throws Exception {
        var p=pair();var u=p.undo();var h=u.identity();
        var wrong=new UndoHeader(new HeapRowPatch.Xid(3,4,669+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),false);
        var candidate=new HeapUndoPair(p.containerUid(),p.containerId(),p.forward(),new UndoRowPatch(wrong,u.inverse(),u.previousDba(),0,669));
        var helper=new HeapCompensationTest();var m=marker();var row=helper.row();var state=new BeginCompensationTest().state(true);
        assertThrows(RedoException.class,()->HeapCompensation.matchCross(helper.record(m,row),m,row,List.of(candidate),4,state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void refusesMissingPreviousDbaNonzeroRciAndWrongMarkerOpcode() {
        var m=marker();java.util.Arrays.fill(m.fields().get(0),12,16,(byte)0);
        assertThrows(RedoException.class,()->UndoCompensation.decode(m));
        var other=marker();other.fields().get(0)[19]=1;
        assertThrows(RedoException.class,()->UndoCompensation.decode(other));
        var normal=marker();var fake=new RedoRecords.Vector(5,6,21,11,normal.dba(),normal.scn(),1,3,normal.fields());
        assertThrows(RedoException.class,()->UndoCompensation.decode(fake));
    }
    @Test void duplicateCandidatesAndWrongRestoredValueRefuse() throws Exception {
        var helper=new HeapCompensationTest();var m=marker();var row=helper.row();var p=pair();var state=new BeginCompensationTest().state(true);
        assertThrows(RedoException.class,()->HeapCompensation.matchCross(helper.record(m,row),m,row,List.of(p,p),4,state));
        var changed=helper.row();changed.fields().get(3)[0]++;
        var other=new BeginCompensationTest().state(true);
        assertThrows(RedoException.class,()->HeapCompensation.matchCross(helper.record(m,changed),m,changed,List.of(p),4,other));
    }
}
