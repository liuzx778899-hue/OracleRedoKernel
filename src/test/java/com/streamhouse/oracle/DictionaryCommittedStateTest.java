package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryCommittedStateTest {
    @TempDir Path directory;
    record Fixture(FrozenHeapBaseline.Snapshot baseline,HistoricalDictionaryState dictionary,ArchivedDictionaryHistory.Commit commit) {}
    Fixture fixture()throws Exception {
        var helper=new HistoricalDictionaryStateTest();helper.directory=directory;var s=helper.baseline();
        var c=helper.commit(s,s.scn()+10,32,helper.renamed(s));var role=s.dictionaryBaseline().layouts().get("COL$").role();
        var entry=new ArchivedUndoInventory.Entry(new ArchivedUndoInventory.Position(90,9,16,1),
                new UndoHeader(c.identity().xid(),new HeapRowPatch.Uba(1234,1,1),role.logicalObject(),role.dataObject(),0,true),11,1,0,0);
        var commit=new ArchivedDictionaryHistory.Commit(c.identity(),List.of(entry),1,0,0,c.rows(),c.commitPosition());
        return new Fixture(s,HistoricalDictionaryState.from(s,List.of(helper.source(s)),List.of(commit),List.of()),commit);
    }
    String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void commitMetadataAndHistoricalRowsRestoreAndSeedIndexState()throws Exception {
        var f=fixture();byte[] bytes=DictionaryCommittedState.encode(List.of(f.commit()));
        var restored=DictionaryCommittedState.restore(bytes,sha(bytes),f.baseline(),f.dictionary());
        assertEquals(List.of(f.commit()),restored);
        var state=new DictionaryHistoryRows(f.baseline(),restored.get(0).rows());
        assertEquals(f.commit().rows(),state.apply(List.of(),List.of()));
        assertNotEquals(f.baseline().dictionaryBaseline().rows(),state.current());
        assertThrows(UnsupportedOperationException.class,()->restored.clear());
    }
    @Test void omittedOrMismatchedCommitMetadataCannotReuseVersions()throws Exception {
        var f=fixture();var c=f.commit();
        var wrongPosition=new ArchivedDictionaryHistory.Commit(c.identity(),c.original(),c.rowPairs(),c.indexPairs(),c.controls(),c.rows(),
                new ArchivedUndoInventory.Position(90,10,48,1));
        var missingCoverage=new ArchivedDictionaryHistory.Commit(c.identity(),c.original(),2,0,0,c.rows(),c.commitPosition());
        for(var commits:List.of(List.<ArchivedDictionaryHistory.Commit>of(),List.of(wrongPosition),List.of(missingCoverage),List.of(c,c))) {
            byte[] bytes=DictionaryCommittedState.encode(commits);
            assertThrows(RedoException.class,()->DictionaryCommittedState.restore(bytes,sha(bytes),f.baseline(),f.dictionary()));
        }
    }
    @Test void wrongDigestTruncatedAndTrailingBytesRefuse()throws Exception {
        var f=fixture();byte[] bytes=DictionaryCommittedState.encode(List.of(f.commit()));
        assertThrows(RedoException.class,()->DictionaryCommittedState.restore(bytes,"0".repeat(64),f.baseline(),f.dictionary()));
        for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1)))
            assertThrows(RedoException.class,()->DictionaryCommittedState.restore(bad,sha(bad),f.baseline(),f.dictionary()));
    }
}
