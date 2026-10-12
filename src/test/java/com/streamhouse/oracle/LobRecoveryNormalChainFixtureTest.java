package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Closed frozen history -> read origin -> new window -> published boundary -> second read origin. */
@EnabledIfSystemProperty(named="ork.lob.future",matches=".+")
class LobRecoveryNormalChainFixtureTest {
    @Test void basicFileAfterTruncate()throws Exception {verify("B");}
    @Test void secureFileAfterTruncate()throws Exception {verify("S");}
    @Test void secureFileAfterMigration()throws Exception {verify("M");}
    private void verify(String storage)throws Exception {
        boolean migration=storage.equals("M");Path root=Path.of("lab/out"),future=Path.of(System.getProperty("ork.lob.future"));
        Path source=root.resolve(migration?"lob-migration-20261011/source":"lob-history-20261011/source");
        Path baseline=source.resolve(migration?"baseline.snapshot":"T_D4_HL"+storage+"_20261011-baseline.snapshot");String baselineSha=sha(Files.readAllBytes(baseline));
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int seq=migration?120:117;seq<=(migration?120:119);seq++)captures.add(raw(source.resolve("inputs/"+seq+".raw")));
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);long leaf=LobOriginCapsule.currentLeaf(RedoBatch.captures(captures),snapshot.index().dataObject());
        var state=HistoricalRecovery.committedPrefixCaptured(captures,baseline,baselineSha,leaf);assertEquals(migration?1:4,state.completed().events().size());
        byte[] checkpoint=state.state().checkpoint();var cut=HistoricalRecovery.readCutCaptured(List.of(),baseline,baselineSha,leaf,checkpoint,sha(checkpoint));
        Path file=Files.createTempFile("ork-lob-normal-",".snapshot");
        try {
            Files.write(file,cut.baseline());
            var retained=new ArrayList<CaptureJournal.Verified>();retained.add(captures.get(captures.size()-1));
            if(!migration)retained.add(raw(root.resolve("lob-migration-20261011/source/inputs/120.raw")));
            var futurePaths=Files.list(future.resolve("inputs"));List<Path> paths;
            try(futurePaths){paths=futurePaths.sorted(Comparator.comparingLong(p->Long.parseLong(p.getFileName().toString().replace(".raw","")))).toList();}
            assertEquals(2,paths.size());retained.add(raw(paths.get(0)));
            var advanced=HistoricalRecovery.committedPrefixCaptured(retained,file,cut.baselineSha256(),cut.leaf());
            assertEquals(state.completed().baselineSchema(),advanced.completed().baselineSchema());
            assertEquals(state.completed().baselineSchema().sha256(),cut.schemaSha256());
            assertEquals(1,advanced.completed().events().size());assertEquals(0,advanced.state().pendingTransactions());
            var event=advanced.completed().events().get(0).row();assertEquals(HeapRowPatch.Kind.UPDATE,event.operation());
            assertEquals("recovery-window-1",OracleScalars.varchar2(HexFormat.of().parseHex(event.after().get(1).hex()),"AL32UTF8"));
            assertEquals("00aaff8001",event.after().get(2).hex());assertEquals("recovery-text-1",OracleScalars.varchar2(HexFormat.of().parseHex(event.after().get(3).hex()),"AL32UTF8"));
            byte[] next=advanced.state().checkpoint();var recovered=HistoricalRecovery.extendPrefixCaptured(List.of(raw(paths.get(1))),file,cut.baselineSha256(),cut.leaf(),next,sha(next));
            assertEquals(2,recovered.completed().events().size());
            var unpublished=recovered.completed().afterPublished(advanced.completed().publicationBoundary(1));assertEquals(1,unpublished.events().size());
            assertEquals(state.completed().baselineSchema(),unpublished.baselineSchema());
            assertEquals("recovery-window-2",OracleScalars.varchar2(HexFormat.of().parseHex(unpublished.events().get(0).row().after().get(1).hex()),"AL32UTF8"));
            byte[] finalState=recovered.state().checkpoint();var second=HistoricalRecovery.readCutCaptured(List.of(),file,cut.baselineSha256(),cut.leaf(),finalState,sha(finalState));
            Files.write(file,second.baseline());
            assertTrue(CapturedHistoricalHeapBatch.read(List.of(),file,second.baselineSha256(),second.leaf()).events().isEmpty());
            assertEquals(1,FrozenHeapBaseline.read(file,second.baselineSha256()).rows().size());
        } finally {Files.delete(file);}
    }
    private static CaptureJournal.Verified raw(Path path)throws Exception {return new CaptureJournal().readRawClosed(path,sha(Files.readAllBytes(path)));}
    private static String sha(byte[] bytes){return LobOriginCapsule.sha(bytes);}
}
