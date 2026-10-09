package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalRecoveryStateTest {
    @TempDir Path dir;
    FrozenHeapBaseline.Snapshot snapshot;
    RedoBatch inputs;
    long leaf;
    PhysicalRecoveryState state()throws Exception {
        var helper=new ArchivedPhysicalTransactionsTest();helper.dir=dir;leaf=helper.leaf;
        var raw=helper.fixture();helper.f.records.record(raw,5,140,List.of());ArchivedRedoTest.seal(raw,2560);
        // The helper binds a trusted fixture baseline to the actual source header.
        assertThrows(RedoException.class,()->helper.verified(raw,105));
        Path baseline=dir.resolve("snapshot.txt");byte[] bytes=Files.readAllBytes(baseline);
        snapshot=FrozenHeapBaseline.read(baseline,DurableUndoState.sha(bytes));
        inputs=RedoBatch.archives(List.of(dir.resolve("verified-archive.dbf")));
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(73024,73024),new HeapIndexScope.Generation(73025,73025));
        var prefix=ArchivedPhysicalTransactions.readPrefix(inputs,677737149,3,scope,leaf,4);
        assertEquals(1,prefix.state().pending().size());assertEquals(1,prefix.completed().transactions().size());
        return PhysicalRecoveryState.from(inputs,snapshot,leaf,prefix);
    }
    @Test void pendingIndexPairsAndClosedGraphSurviveWithoutRawAndCannotMutateState()throws Exception {
        var state=state();byte[] bytes=state.checkpoint();Files.delete(dir.resolve("verified-archive.dbf"));
        var restored=PhysicalRecoveryState.restore(bytes,DurableUndoState.sha(bytes),snapshot,leaf);
        var prefix=restored.prefix();assertEquals(1,prefix.completed().transactions().get(0).graph().original().size());
        var p=prefix.state().pending().get(0);assertEquals(1,p.indexes().size());assertEquals(1,p.operations().size());
        byte[] key=p.indexes().get(0).forward().key();byte original=key[0];key[0]^=1;
        assertEquals(original,restored.prefix().state().pending().get(0).indexes().get(0).forward().key()[0]);
        bytes[0]^=1;assertArrayEquals(state.checkpoint(),restored.checkpoint());
    }
    @Test void digestBaselineLeafTruncationTailAndUnknownFormatRefuse()throws Exception {
        var state=state();byte[] bytes=state.checkpoint();
        assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bytes,"0".repeat(64),snapshot,leaf));
        assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bytes,DurableUndoState.sha(bytes),snapshot,leaf+1));
        var other=new FrozenHeapBaseline.Snapshot("b".repeat(64),snapshot.source(),snapshot.scn(),snapshot.heap(),snapshot.index(),snapshot.columns(),snapshot.rows(),snapshot.dictionaryAnchors(),snapshot.dictionaryBaseline());
        assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bytes,DurableUndoState.sha(bytes),other,leaf));
        var unknown=bytes.clone();unknown[2]='X';
        for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),unknown))
            assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bad,DurableUndoState.sha(bad),snapshot,leaf));
    }
    @Test void sourceHeaderCountsPendingSegmentAndPairContainerCannotBeRebound()throws Exception {
        var state=state();byte[] bytes=state.checkpoint();var source=bytes.clone();
        String digest=state.prefix().state().sources().get(0).sha256();
        int at=new String(source,java.nio.charset.StandardCharsets.ISO_8859_1).indexOf(digest);assertTrue(at>0);
        source[at]=(byte)(source[at]=='0'?'1':'0');
        var count=bytes.clone();java.nio.ByteBuffer.wrap(count).putInt(2+"ORK_PHYSICAL_STATE_V1".length()+2+64+8,Integer.MAX_VALUE);
        for(byte[] bad:List.of(source,count))assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bad,DurableUndoState.sha(bad),snapshot,leaf));
        var prefix=state.prefix();var p=prefix.state().pending().get(0);var pair=p.indexes().get(0);
        var foreign=new IndexUndoPair(pair.containerUid()+1,pair.containerId(),pair.forward(),pair.undo());
        for(var changed:List.of(
                new ArchivedPhysicalTransactions.PendingGraph(p.xid(),p.segment()+1,p.next(),p.operations(),p.heaps(),p.indexes(),p.markers(),p.graphCheckpoint()),
                new ArchivedPhysicalTransactions.PendingGraph(p.xid(),p.segment(),p.next(),p.operations(),p.heaps(),List.of(foreign),p.markers(),p.graphCheckpoint()))) {
            var replaced=new ArchivedPhysicalTransactions.Prefix(prefix.completed(),new ArchivedPhysicalTransactions.PendingState(prefix.state().sources(),List.of(changed),prefix.state().identityCheckpoint()));
            byte[] bad=PhysicalRecoveryState.from(inputs,snapshot,leaf,replaced).checkpoint();
            assertThrows(RedoException.class,()->PhysicalRecoveryState.restore(bad,DurableUndoState.sha(bad),snapshot,leaf));
        }
    }
}
