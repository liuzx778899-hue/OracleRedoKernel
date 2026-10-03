package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoHeadUpdateTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    RedoRecords.Vector control(){return new RedoRecords.Vector(5,2,35,11,0x02400110,2165447,24,3,List.of(
            hex("0200000000000000162d4002f20003000200dc0353f57e040000000000000000")));}
    RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,36,11,0x02402d16,2165447,4,3,List.of(
            hex("dc031a18220000000a0002008b040000f2000300"),hex("591d0100591d0100050000000e2d40020b01020000008b04")));}
    RedoRecords.Record record(RedoRecords.Vector... vectors){return new RedoRecords.Record(11153,352,2132,1,2165447,1,677737149,List.of(vectors));}
    TransactionLifecycle state() throws Exception {
        var s=new TransactionLifecycle();s.archive(new ArchivedRedo.Header("synthetic",1536,1,1,0x13000000,1,13,1,1,2165400,2165500,3));
        var begin=new RedoRecords.Vector(5,2,35,11,0x02400110,2165446,1,3,List.of(
                hex("0200d1758b040000072d4002f2001a0052000804000000000000000000000000"),hex("bd726528")));
        var u=new RedoRecords.Vector(5,1,36,11,0x02402d07,2165446,1,3,List.of(
                hex("0804e817120000000a0002008b040000f2001a00"),hex("591d0100591d010005000000000000000b010200080c010000000000072d4002f2001800db08210000000000dd0821000000000000000000c30a210000000000052d40020000000000000000")));
        s.begin(new RedoRecords.Record(10519,16,2048,1,2165446,1,677737149,List.of(begin,u)),begin);return s;
    }
    @Test void bindsAllocatedRecordThreeToFullActiveIdentityWithoutInferringPredecessor() throws Exception {
        var v=control();var h=state().headUpdate(record(undo(),v),v);
        assertEquals(new HeapRowPatch.Xid(10,2,1163),h.xid());assertEquals(new HeapRowPatch.Uba(0x02402d16,242,3),h.uba());
        assertThrows(RedoException.class,()->UndoBlockAdvance.decode(v));assertThrows(RedoException.class,()->UndoLink.decode(undo()));
    }
    @Test void missingOrDuplicateOriginalLocksLifecycle() throws Exception {
        for(boolean duplicate:List.of(false,true)) {
            var s=state();var v=control();var u=undo();
            assertThrows(RedoException.class,()->s.headUpdate(duplicate ? record(v,u,u) : record(v),v));
            assertEquals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED",assertThrows(RedoException.class,s::finish).getMessage());
        }
    }
    @Test void lowWrapCollisionIsNotFullTransactionIdentity() throws Exception {
        var s=state();var u=undo();u.fields().get(0)[14]=1;var v=control();
        assertThrows(RedoException.class,()->s.headUpdate(record(v,u),v));assertThrows(RedoException.class,s::finish);
    }
    @Test void wrongSegmentAndUnverifiedBeginLockLifecycle() throws Exception {
        var s=state();var v=control();var wrong=new RedoRecords.Vector(5,2,35,11,v.dba()+1,v.scn(),24,3,v.fields());
        assertThrows(RedoException.class,()->s.headUpdate(record(wrong,undo()),wrong));assertThrows(RedoException.class,s::finish);
        var empty=new TransactionLifecycle();empty.archive(new ArchivedRedo.Header("synthetic",1536,1,1,0x13000000,1,13,1,1,2165400,2165500,3));
        assertThrows(RedoException.class,()->empty.headUpdate(record(v,undo()),v));assertThrows(RedoException.class,empty::finish);
    }
    @Test void refusesControlAbsentFromRecordAndDuplicateControl() throws Exception {
        var s=state();var v=control();assertThrows(RedoException.class,()->s.headUpdate(record(undo()),v));assertThrows(RedoException.class,s::finish);
        var duplicate=state();assertThrows(RedoException.class,()->duplicate.headUpdate(record(v,v,undo()),v));assertThrows(RedoException.class,duplicate::finish);
    }
    @Test void rejectsUnknownFlagsParentsAndMissingCrossReference() throws Exception {
        for(int offset:List.of(4,14,16,24)) {
            var v=control();v.fields().get(0)[offset]=1;assertThrows(RedoException.class,()->UndoHeadUpdate.decode(v));
        }
        var u=undo();java.util.Arrays.fill(u.fields().get(1),12,16,(byte)0);
        assertThrows(RedoException.class,()->UndoHeadUpdate.decode(control()).bindUndo(record(u)));
        var r=undo();r.fields().get(1)[19]=1;assertThrows(RedoException.class,()->UndoHeadUpdate.decode(control()).bindUndo(record(r)));
    }
}
