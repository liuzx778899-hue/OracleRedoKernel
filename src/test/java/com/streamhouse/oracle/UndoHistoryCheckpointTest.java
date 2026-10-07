package com.streamhouse.oracle;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UndoHistoryCheckpointTest {
    @TempDir Path dir;
    ArchivedUndoInventory inventory()throws Exception {var f=new UndoHistoryTest();f.dir=dir;return f.inventory();}
    String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    UndoHistory restore(ArchivedUndoInventory input,UndoHistory state)throws Exception {
        byte[] bytes=state.checkpoint();var restored=UndoHistory.restore(input,bytes,sha(bytes));assertArrayEquals(bytes,restored.checkpoint());return restored;
    }
    @Test void preservesCompensationAndResumePredecessorAndAllocationTail()throws Exception {
        var input=inventory();var e=input.entries();var history=new UndoHistory(input);
        history.append(e.get(0));history.append(e.get(1));history.append(e.get(2));history.compensate(e.get(2).identity());
        var resumed=restore(input,history);assertEquals(e.get(1).identity().uba(),resumed.append(e.get(3)));
        resumed.append(e.get(4));resumed.compensate(e.get(4).identity());resumed.compensate(e.get(3).identity());
        var graph=resumed.close(e.get(4).identity().uba());assertEquals(37,graph.allocationTail().record());assertEquals(34,graph.activeHead().record());
        assertEquals(3,graph.compensated().size());assertEquals(2,graph.live().size());assertThrows(RedoException.class,resumed::checkpoint);
    }
    @Test void preservesFullyCompensatedPendingGraphForRollbackClosure()throws Exception {
        var input=inventory();var f=new UndoHistoryTest();var history=f.fullyUndone(input);var resumed=restore(input,history);
        var graph=resumed.closeRollback(f.rollback(9,3,1133,true));assertTrue(graph.live().isEmpty());assertEquals(5,graph.compensated().size());
    }
    @Test void digestTruncationMissingHeadAndDuplicatedActionsCannotAuthorizeGraph()throws Exception {
        var input=inventory();var state=new UndoHistory(input);state.append(input.entries().get(0));state.append(input.entries().get(1));
        byte[] bytes=state.checkpoint();assertThrows(RedoException.class,()->UndoHistory.restore(input,bytes,"0".repeat(64)));
        String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);String last=text.substring(text.lastIndexOf("A|"));
        for(String bad:new String[]{text.substring(0,text.length()-1),text+last,text.replace("A|0|","A|1|"),text.replace("S|","Z|")}) {
            byte[] changed=bad.getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThrows(RedoException.class,()->UndoHistory.restore(input,changed,sha(changed)));
        }
    }
    @Test void restoredGraphStillRejectsIncompleteCloseAndWrongTail()throws Exception {
        var input=inventory();var state=new UndoHistory(input);state.append(input.entries().get(0));var resumed=restore(input,state);
        assertThrows(RedoException.class,()->resumed.close(input.entries().get(0).identity().uba()));assertThrows(RedoException.class,resumed::checkpoint);
    }
    @Test void changedVerifiedSourceCannotReuseCheckpointEvenWithIdenticalUndoEntries()throws Exception {
        var input=inventory();var state=new UndoHistory(input);state.append(input.entries().get(0));byte[] checkpoint=state.checkpoint();
        Path path=dir.resolve("history.dbf");byte[] raw=java.nio.file.Files.readAllBytes(path);
        ArchivedRedoTest.put(raw,1054,2,2);ArchivedRedoTest.seal(raw,1024);java.nio.file.Files.write(path,raw);
        var changed=ArchivedUndoInventory.read(java.util.List.of(path),9,3,new HeapRowPatch.Xid(10,5,1133));assertEquals(input.entries(),changed.entries());
        assertEquals("UNDO_CHECKPOINT_SOURCE_PREFIX_CHANGED",assertThrows(RedoException.class,()->UndoHistory.restore(changed,checkpoint,sha(checkpoint))).getMessage());
    }
}
