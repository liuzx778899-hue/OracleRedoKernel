package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalUndoInventoryTest {
    @TempDir Path directory;
    final HeapRowPatch.Xid xid=new HeapRowPatch.Xid(10,5,1133);
    PendingDictionaryRecoveryTest helper(){var h=new PendingDictionaryRecoveryTest();h.directory=directory;return h;}
    UndoHistory prefix()throws Exception {
        var f=new UndoChainTest();var path=helper().writeArchive("first.dbf",7,2522800,2522900,2522810,List.of(f.begin(),f.link(34,33)));
        var inventory=ArchivedUndoInventory.read(List.of(path),677737149,3,xid);var graph=new UndoHistory(inventory);
        for(var e:inventory.entries())graph.append(e);return graph;
    }
    @Test void suffixOnlyAfterRawRemovalEqualsFullInventoryAndPreservesPrefixActions()throws Exception {
        var graph=prefix();byte[] bytes=graph.durableCheckpoint();var f=new UndoChainTest();var foreign=f.link(37,36);
        ArchivedRedoTest.put(foreign.fields().get(0),12,xid.sequence()+65536,4);
        var second=helper().writeArchive("second.dbf",8,2522900,2523000,2522910,List.of(f.link(35,34),f.link(36,35),foreign));
        var expected=ArchivedUndoInventory.read(List.of(directory.resolve("first.dbf"),second),677737149,3,xid);
        Files.delete(directory.resolve("first.dbf"));
        var restored=UndoHistory.restoreDurable(bytes,DurableUndoState.sha(bytes));var extended=restored.extend(RedoBatch.archives(List.of(second)));
        assertEquals(expected.sources(),extended.inventory().sources());assertEquals(expected.entries(),extended.inventory().entries());
        assertArrayEquals(bytes,restored.durableCheckpoint());
        for(var e:extended.inventory().entries().subList(2,4))extended.append(e);
        assertEquals(4,extended.close(extended.inventory().entries().get(3).identity().uba()).live().size());
    }
    @Test void repeatedMemberGapAndSecondBeginRefuseWithoutMutatingPrefix()throws Exception {
        var graph=prefix();byte[] before=graph.durableCheckpoint();var f=new UndoChainTest();
        var gap=helper().writeArchive("gap.dbf",9,2522900,2523000,2522910,List.of(f.link(35,34)));
        var begin=helper().writeArchive("begin.dbf",8,2522900,2523000,2522910,List.of(f.begin()));
        var scnGap=helper().writeArchive("scn-gap.dbf",8,2522901,2523001,2522911,List.of(f.link(35,34)));
        var source=helper().writeArchive("source.dbf",8,2522900,2523000,2522910,List.of(f.link(35,34)));
        byte[] changed=Files.readAllBytes(source);ArchivedRedoTest.put(changed,536,1276726822L,4);ArchivedRedoTest.seal(changed,512);Files.write(source,changed);
        for(var path:List.of(directory.resolve("first.dbf"),gap,begin,scnGap,source))assertThrows(RedoException.class,()->graph.extend(RedoBatch.archives(List.of(path))));
        assertThrows(RedoException.class,()->graph.extend(RedoBatch.archives(List.of())));assertArrayEquals(before,graph.durableCheckpoint());
    }
    @Test void memberWithoutSelectedNodesStillAdvancesSourceContinuity()throws Exception {
        var graph=prefix();var empty=helper().writeArchive("empty.dbf",8,2522900,2523000,2522910,List.of());
        var extended=graph.extend(RedoBatch.archives(List.of(empty)));assertEquals(graph.inventory().entries(),extended.inventory().entries());
        assertEquals(2,extended.inventory().sources().size());assertEquals(2,extended.close(graph.inventory().entries().get(1).identity().uba()).live().size());
    }
    @Test void nonRowAllocationRootSurvivesExtensionWithoutInventedEdge()throws Exception {
        var f=new UndoControlRootTest();f.dir=directory;var inventory=f.read(List.of(f.start(),f.begin()),List.of(f.head(),f.root()));
        var graph=new UndoHistory(inventory);for(var e:inventory.entries())graph.append(e);byte[] state=graph.durableCheckpoint();
        var last=inventory.sources().get(0);byte[] empty=f.archive(List.of(),List.of());
        ArchivedRedoTest.put(empty,692,last.nextScn(),4);ArchivedRedoTest.put(empty,704,last.nextScn()+100,4);
        for(int block=1;block<empty.length/512;block++)ArchivedRedoTest.put(empty,block*512+8,last.sequence()+1,4);
        for(int block=0;block<empty.length/512;block++)ArchivedRedoTest.seal(empty,block*512);
        var path=directory.resolve("nonrow-suffix.dbf");Files.write(path,empty);Files.delete(directory.resolve("raw.dbf"));
        var extended=UndoHistory.restoreDurable(state,DurableUndoState.sha(state)).extend(RedoBatch.archives(List.of(path)));
        assertEquals(inventory.controlRoot(),extended.inventory().controlRoot());
        assertEquals(List.of(inventory.entries().get(1)),extended.close(inventory.entries().get(1).identity().uba()).live());
    }
}
