package com.streamhouse.oracle;
import java.util.List;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoBlockAdvanceTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    RedoRecords.Vector advance(){return new RedoRecords.Vector(5,2,17,11,0x02400080,110,1,3,
            List.of(hex("010029ac0000000072214002e50001000a00dc0364683cfd0000000000000000")));}
    RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,18,11,0x02402172,110,1,3,
            List.of(hex("dc03e6000a000000010001005c020000e5000100"),hex("531d0100531d010005000000712140020b01010000005c02")));}
    RedoRecords.Record record(RedoRecords.Vector... v){return new RedoRecords.Record(2,16,2048,1,110,1,677737149,List.of(v));}
    @Test void bindsExactNewUbaWithoutExposingGuessedPredecessor() throws Exception {
        var a=UndoBlockAdvance.decode(advance());var h=a.bindUndo(record(undo(),advance()));
        assertEquals(new HeapRowPatch.Xid(1,1,604),h.xid());assertEquals(0x02402172,h.uba().dba());
        assertThrows(RedoException.class,()->UndoLink.decode(undo()));
    }
    @Test void refusesMissingDuplicateWrongSlotAndPredecessor() throws Exception {
        var a=UndoBlockAdvance.decode(advance());
        assertThrows(RedoException.class,()->a.bindUndo(record(advance())));
        assertThrows(RedoException.class,()->a.bindUndo(record(undo(),undo())));
        var u=undo();u.fields().get(0)[10]=2;
        assertThrows(RedoException.class,()->a.bindUndo(record(u)));
        var b=undo();java.util.Arrays.fill(b.fields().get(1),12,16,(byte)0);
        assertThrows(RedoException.class,()->a.bindUndo(record(b)));
    }
    @Test void refusesSequenceParentAndUnobservedLayout() {
        var a=advance();a.fields().get(0)[4]=1;
        assertThrows(RedoException.class,()->UndoBlockAdvance.decode(a));
        var b=advance();b.fields().get(0)[24]=1;
        assertThrows(RedoException.class,()->UndoBlockAdvance.decode(b));
        var c=advance();c.fields().get(0)[14]=2;
        assertThrows(RedoException.class,()->UndoBlockAdvance.decode(c));
    }
    @Test void bindsOnlyActiveFullIdentityAndLatchesFailure() throws Exception {
        var s=new TransactionLifecycle();s.archive(new ArchivedRedo.Header("synthetic",1536,1,1,0x13000000,1,10,1,1,100,200,3));
        var a=advance();
        assertEquals("UNDO_ADVANCE_WITHOUT_ACTIVE_SEGMENT_IDENTITY",assertThrows(RedoException.class,()->s.advance(record(a,undo()),a)).getMessage());
        assertThrows(RedoException.class,s::finish);
    }
    @Test void extensionEndRetainsOriginalTailReference() throws Exception {
        var v=end();assertFalse(TransactionEnd.decode(v).rolledBack());
        assertEquals(new HeapRowPatch.Uba(0x02400089,231,4),TransactionEnd.undoTail(v));
        v.fields().get(2)[8]++;
        assertThrows(RedoException.class,()->TransactionEnd.decode(v));
    }
    RedoRecords.Vector end(){return new RedoRecords.Vector(5,4,17,11,0x02400080,110,1,3,List.of(
            hex("010000005c020000000000000900000012000000"),hex("89004002e700040001005e1000000000"),
            hex("00000000010000007362c16a010000001800000018000000"),hex("7362c16a")));}
}
