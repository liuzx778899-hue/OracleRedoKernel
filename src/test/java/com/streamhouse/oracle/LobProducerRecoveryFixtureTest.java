package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="ork.lob.migration",matches=".+")
class LobProducerRecoveryFixtureTest {
    @Test void migrationProducerRestoresWithoutRawAndCreatesACompleteReadOrigin()throws Exception {
        var source=Path.of(System.getProperty("ork.lob.migration"));var baseline=source.resolve("baseline.snapshot");
        String baselineSha="72555e21d09c917f33e2b2d7a95c34269cb11b3d16c738f7008d91979b2028d9";
        var raw=new CaptureJournal().readRawClosed(source.resolve("inputs/120.raw"),"6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36");
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);long leaf=0;
        for(var r:new RedoRecords().readCaptured(raw))for(var v:r.vectors())if(v.opcodeMajor()==10 && v.objectId()==snapshot.index().dataObject())leaf=v.dba();
        var enrolled=HistoricalRecovery.committedPrefixCaptured(List.of(raw),baseline,baselineSha,leaf);
        byte[] checkpoint=enrolled.state().checkpoint();String digest=LobOriginCapsule.sha(checkpoint);
        assertEquals(1,enrolled.completed().events().size());assertEquals(0,enrolled.state().pendingTransactions());
        var restored=HistoricalRecovery.extendPrefixCaptured(List.of(),baseline,baselineSha,leaf,checkpoint,digest);
        assertEquals(enrolled.completed().events(),restored.completed().events());
        assertArrayEquals(checkpoint,restored.state().checkpoint());
        assertEquals(enrolled.completed().sha256(),HistoricalRecovery.auditState(List.of(raw.source()),baseline,baselineSha,leaf,checkpoint,digest).sha256());
        var cut=HistoricalRecovery.readCutCaptured(List.of(),baseline,baselineSha,leaf,checkpoint,digest);
        Path file=Files.createTempFile("ork-lob-cut-",".snapshot");
        try {
            Files.write(file,cut.baseline());var values=FrozenHeapBaseline.read(file,cut.baselineSha256());
            assertEquals(81024,values.heap().logicalObject());assertEquals(1,values.rows().size());
            long nextLeaf=LobOriginCapsule.currentLeaf(RedoBatch.captures(List.of(raw)),values.index().dataObject());
            assertTrue(CapturedHistoricalHeapBatch.read(List.of(),file,cut.baselineSha256(),nextLeaf).events().isEmpty());
            var next=HistoricalRecovery.committedPrefixCaptured(List.of(),file,cut.baselineSha256(),nextLeaf);
            assertTrue(next.completed().events().isEmpty());
            assertArrayEquals(next.state().checkpoint(),HistoricalRecovery.extendPrefixCaptured(List.of(),file,cut.baselineSha256(),nextLeaf,next.state().checkpoint(),LobOriginCapsule.sha(next.state().checkpoint())).state().checkpoint());
        } finally {Files.delete(file);}
    }
}
