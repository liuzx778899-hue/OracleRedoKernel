package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableUndoStateTest {
    @TempDir Path dir;
    String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    ArchivedUndoInventory inventory()throws Exception {var helper=new UndoHistoryTest();helper.dir=dir;return helper.inventory();}
    UndoHistory restore(UndoHistory state)throws Exception {
        byte[] bytes=state.durableCheckpoint();var restored=UndoHistory.restoreDurable(bytes,sha(bytes));
        assertArrayEquals(bytes,restored.durableCheckpoint());assertArrayEquals(state.checkpoint(),restored.checkpoint());return restored;
    }
    @Test void pendingGraphResumesAfterRawFileIsRemoved()throws Exception {
        var inventory=inventory();var entries=inventory.entries();var state=new UndoHistory(inventory);
        for(int i=0;i<3;i++)state.append(entries.get(i));state.compensate(entries.get(2).identity());
        byte[] bytes=state.durableCheckpoint();Files.delete(dir.resolve("history.dbf"));
        var restored=UndoHistory.restoreDurable(bytes,sha(bytes));
        assertEquals(entries.get(1).identity().uba(),restored.append(entries.get(3)));restored.append(entries.get(4));
        restored.compensate(entries.get(4).identity());restored.compensate(entries.get(3).identity());
        var graph=restored.close(entries.get(4).identity().uba());
        assertEquals(37,graph.allocationTail().record());assertEquals(34,graph.activeHead().record());
        assertEquals(3,graph.compensated().size());assertEquals(2,graph.live().size());
        assertThrows(RedoException.class,restored::durableCheckpoint);
    }
    @Test void fullyCompensatedGraphStillRequiresCorrectRollbackIdentity()throws Exception {
        var inventory=inventory();var helper=new UndoHistoryTest();var state=helper.fullyUndone(inventory);
        assertTrue(restore(state).closeRollback(helper.rollback(9,3,1133,true)).live().isEmpty());
        assertThrows(RedoException.class,()->restore(state).closeRollback(helper.rollback(9,3,1133+65536,true)));
    }
    @Test void nonRowAllocationRootHasNoInventedPredecessor()throws Exception {
        var helper=new UndoControlRootTest();helper.dir=dir;
        var inventory=helper.read(List.of(helper.start(),helper.begin()),List.of(helper.head(),helper.root()));
        var state=new UndoHistory(inventory);state.append(inventory.entries().get(0));var restored=restore(state);
        assertNull(restored.append(inventory.entries().get(1)));
        var graph=restored.close(inventory.entries().get(1).identity().uba());assertEquals(List.of(inventory.entries().get(1)),graph.live());
    }
    @Test void wrongDigestTruncationTailAndInvalidCountsRefuse()throws Exception {
        var inventory=inventory();var state=new UndoHistory(inventory);state.append(inventory.entries().get(0));
        byte[] bytes=state.durableCheckpoint();assertThrows(RedoException.class,()->UndoHistory.restoreDurable(bytes,"0".repeat(64)));
        byte[] count=bytes.clone();java.nio.ByteBuffer.wrap(count).putInt(2+"ORK_DURABLE_UNDO_V1".length()+16,Integer.MAX_VALUE);
        byte[] source=bytes.clone();int at=new String(source,java.nio.charset.StandardCharsets.ISO_8859_1).indexOf(inventory.sources().get(0).sha256());
        assertTrue(at>=0);source[at]=(byte)(source[at]=='0'?'1':'0');
        for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),count,source))
            assertThrows(RedoException.class,()->UndoHistory.restoreDurable(bad,sha(bad)));
        var restored=restore(state);assertThrows(RedoException.class,()->restored.close(inventory.entries().get(0).identity().uba()));
    }
}
