package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class UndoHistoryTest {
    @TempDir Path dir;
    ArchivedUndoInventory inventory() throws Exception {
        byte[] bytes=new ArchivedUndoInventoryTest().fixture();Arrays.fill(bytes,1040,1536,(byte)0);
        var fixture=new UndoChainTest();var vectors=List.of(fixture.begin(),fixture.link(34,33),fixture.link(35,34),fixture.link(36,34),fixture.link(37,36));
        int base=1064;
        for(var v:vectors) {
            put(bytes,base,5,1);put(bytes,base+1,1,1);put(bytes,base+8,v.dba(),4);put(bytes,base+24,3,4);
            put(bytes,base+32,6,2);put(bytes,base+34,v.fields().get(0).length,2);put(bytes,base+36,v.fields().get(1).length,2);
            System.arraycopy(v.fields().get(0),0,bytes,base+40,v.fields().get(0).length);
            System.arraycopy(v.fields().get(1),0,bytes,base+60,v.fields().get(1).length);
            base+=40+v.fields().get(0).length+v.fields().get(1).length;
        }
        put(bytes,1040,base-1040,4);put(bytes,1044,1,1);put(bytes,1048,101,4);put(bytes,1056,9,4);seal(bytes,1024);
        var path=dir.resolve("history.dbf");Files.write(path,bytes);
        return ArchivedUndoInventory.read(List.of(path),9,3,new HeapRowPatch.Xid(10,5,1133));
    }
    @Test void rollbackResumeAndSecondRollbackKeepAllocationTailSeparate() throws Exception {
        var input=inventory();var e=input.entries();var history=new UndoHistory(input);
        history.append(e.get(0));history.append(e.get(1));history.append(e.get(2));history.compensate(e.get(2).identity());
        assertEquals(e.get(1).identity().uba(),history.append(e.get(3)));
        history.append(e.get(4));history.compensate(e.get(4).identity());history.compensate(e.get(3).identity());
        var result=history.close(e.get(4).identity().uba());
        assertEquals(List.of(34,33),result.live().stream().map(n->n.identity().uba().record()).toList());
        assertEquals(37,result.allocationTail().record());assertEquals(34,result.activeHead().record());
        assertEquals(List.of(35,37,36),result.compensated().stream().map(h->h.uba().record()).toList());
        assertThrows(UnsupportedOperationException.class,()->result.live().clear());
        assertThrows(RedoException.class,()->history.append(e.get(4)));
    }
    @Test void omittedReorderedOrDuplicateInputLatchesFailure() throws Exception {
        var input=inventory();var e=input.entries();
        for(int index:List.of(0,2)) {
            var history=new UndoHistory(input);history.append(e.get(0));
            assertThrows(RedoException.class,()->history.append(e.get(index)));
            assertEquals("UNDO_HISTORY_ALREADY_REFUSED",assertThrows(RedoException.class,()->history.close(null)).getMessage());
        }
    }
    @Test void missingCompensationCannotPretendResumePredecessor() throws Exception {
        var input=inventory();var e=input.entries();var history=new UndoHistory(input);
        history.append(e.get(0));history.append(e.get(1));history.append(e.get(2));
        assertThrows(RedoException.class,()->history.append(e.get(3)));assertThrows(RedoException.class,()->history.compensate(e.get(2).identity()));
    }
    @Test void wrongCompensationIdentityOrderAndDuplicateRefuse() throws Exception {
        var input=inventory();var e=input.entries();
        var wrongOrder=new UndoHistory(input);wrongOrder.append(e.get(0));wrongOrder.append(e.get(1));
        assertThrows(RedoException.class,()->wrongOrder.compensate(e.get(0).identity()));
        var duplicate=new UndoHistory(input);duplicate.append(e.get(0));duplicate.append(e.get(1));duplicate.compensate(e.get(1).identity());
        assertThrows(RedoException.class,()->duplicate.compensate(e.get(1).identity()));
        var wrong=new UndoHistory(input);wrong.append(e.get(0));var h=e.get(0).identity();
        var foreign=new UndoHeader(new HeapRowPatch.Xid(10,5,1133+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),true);
        assertThrows(RedoException.class,()->wrong.compensate(foreign));
    }
    @Test void incompleteHistoryAndWrongNativeTailNeverClose() throws Exception {
        var input=inventory();var e=input.entries();var incomplete=new UndoHistory(input);incomplete.append(e.get(0));
        assertThrows(RedoException.class,()->incomplete.close(e.get(0).identity().uba()));
        var wrong=new UndoHistory(input);wrong.append(e.get(0));wrong.append(e.get(1));wrong.append(e.get(2));wrong.compensate(e.get(2).identity());wrong.append(e.get(3));wrong.append(e.get(4));
        assertThrows(RedoException.class,()->wrong.close(e.get(3).identity().uba()));
    }
    UndoHistory fullyUndone(ArchivedUndoInventory input) throws Exception {
        var e=input.entries();var h=new UndoHistory(input);
        h.append(e.get(0));h.append(e.get(1));h.append(e.get(2));h.compensate(e.get(2).identity());
        h.append(e.get(3));h.append(e.get(4));
        for (int i:new int[]{4,3,1,0}) h.compensate(e.get(i).identity());
        return h;
    }
    TransactionLifecycle.ClosedIdentity rollback(long uid,long con,long seq,boolean rolledBack) {
        return new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(10,5,seq),uid,con,100,110,rolledBack);
    }
    @Test void fullRollbackClosesEmptyHeadWithoutInventingNativeTail() throws Exception {
        var input=inventory();var h=fullyUndone(input);var result=h.closeRollback(rollback(9,3,1133,true));
        assertTrue(result.live().isEmpty());assertNull(result.activeHead());assertEquals(5,result.compensated().size());
        assertEquals(input.entries().get(4).identity().uba(),result.allocationTail());
        assertEquals("UNDO_HISTORY_ALREADY_CLOSED",assertThrows(RedoException.class,()->h.closeRollback(rollback(9,3,1133,true))).getMessage());
    }
    @Test void unreferencedCommitCannotHideMissingNodesCompensationOrForeignIdentity() throws Exception {
        var input=inventory();var end=rollback(9,3,1133,false);var partial=new UndoHistory(input);
        partial.append(input.entries().get(0));
        assertEquals("UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT",assertThrows(RedoException.class,
                ()->partial.closeCommitWithoutNativeTail(end)).getMessage());
        assertEquals("UNDO_HISTORY_ALREADY_REFUSED",assertThrows(RedoException.class,
                ()->partial.closeCommitWithoutNativeTail(end)).getMessage());
        assertEquals("UNDO_HISTORY_UNREFERENCED_COMMIT_SCOPE_UNSUPPORTED",assertThrows(RedoException.class,
                ()->fullyUndone(input).closeCommitWithoutNativeTail(end)).getMessage());
        for (var foreign:List.of(rollback(10,3,1133,false),rollback(9,4,1133,false),
                rollback(9,3,1134,false),rollback(9,3,1133,true)))
            assertEquals("UNDO_HISTORY_COMMIT_IDENTITY_MISMATCH",assertThrows(RedoException.class,
                    ()->new UndoHistory(input).closeCommitWithoutNativeTail(foreign)).getMessage());
    }
    @Test void rollbackRequiresFullIdentityContainerAndRollbackDisposition() throws Exception {
        var input=inventory();
        for (var identity:List.of(rollback(8,3,1133,true),rollback(9,4,1133,true),rollback(9,3,1133+65536,true),rollback(9,3,1133,false))) {
            var h=fullyUndone(input);
            assertEquals("UNDO_HISTORY_ROLLBACK_IDENTITY_MISMATCH",assertThrows(RedoException.class,()->h.closeRollback(identity)).getMessage());
            assertEquals("UNDO_HISTORY_ALREADY_REFUSED",assertThrows(RedoException.class,()->h.closeRollback(rollback(9,3,1133,true))).getMessage());
        }
    }
    @Test void missingBeginCompensationOrIncompleteInputCannotCloseRollback() throws Exception {
        var input=inventory();var e=input.entries();var live=new UndoHistory(input);
        live.append(e.get(0));live.append(e.get(1));live.append(e.get(2));live.compensate(e.get(2).identity());live.append(e.get(3));live.append(e.get(4));
        for (int i:new int[]{4,3,1}) live.compensate(e.get(i).identity());
        assertEquals("UNDO_HISTORY_ROLLBACK_HAS_LIVE_HEAD",assertThrows(RedoException.class,()->live.closeRollback(rollback(9,3,1133,true))).getMessage());
        var missing=new UndoHistory(input);missing.append(e.get(0));missing.compensate(e.get(0).identity());
        assertEquals("UNDO_HISTORY_INCOMPLETE_ORIGINAL_INPUT",assertThrows(RedoException.class,()->missing.closeRollback(rollback(9,3,1133,true))).getMessage());
    }
}
