package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="ork.lob.migration",matches=".+")
class LobMigrationFixtureTest {
    @Test void frozenMigrationBindsTheNewTablePrimaryKeyAndBothSecureFileLobsAndRestoresThem()throws Exception {
        var source=Path.of(System.getProperty("ork.lob.migration"));var baseline=source.resolve("baseline.snapshot");
        String baselineSha="72555e21d09c917f33e2b2d7a95c34269cb11b3d16c738f7008d91979b2028d9";
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);
        var raw=new CaptureJournal().readRawClosed(source.resolve("inputs/120.raw"),"6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36");
        var history=ArchivedDictionaryHistory.readCaptured(List.of(raw),baseline,baselineSha);
        assertEquals(16,history.commits().size());assertEquals(477,history.commits().stream().mapToInt(c->c.original().size()).sum());
        var changes=history.commits().stream().filter(c->!c.generations().isEmpty()).toList();assertEquals(1,changes.size());
        var change=changes.get(0);long scn=change.identity().endRecordScn();var position=change.commitPosition();
        assertEquals(81024,history.heapAt(scn,position).logicalObject());assertEquals(81035,history.indexAt(scn,position).logicalObject());
        var lobs=history.lobsAt(scn,position);assertEquals(2,lobs.size());
        assertEquals(List.of(81025L,81027L),lobs.stream().map(l->l.lob().logicalObject()).toList());
        assertEquals(List.of(81026L,81028L),lobs.stream().map(l->l.index().logicalObject()).toList());
        assertTrue(lobs.stream().allMatch(l->l.storage()==LobLocator.Storage.SECUREFILE));
        assertEquals(snapshot.columns(),history.schemaAt(scn,position));
        long leaf=0;
        for(var record:new RedoRecords().readCaptured(raw))for(var vector:record.vectors())if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.objectId()==snapshot.index().dataObject()) {
            assertTrue(leaf==0 || leaf==vector.dba());leaf=vector.dba();
        }
        var batch=CapturedHistoricalHeapBatch.read(List.of(raw),baseline,baselineSha,leaf);assertEquals(1,batch.events().size());
        var event=batch.events().get(0);assertEquals(81024,event.schema().heap().logicalObject());assertEquals(HeapRowPatch.Kind.UPDATE,event.row().operation());
        var before=event.row().before();var after=event.row().after();assertEquals(4,before.size());assertEquals(4,after.size());
        assertEquals("migration-seed",OracleScalars.varchar2(HexFormat.of().parseHex(before.get(1).hex()),"AL32UTF8"));
        assertEquals("0080ff",before.get(2).hex());assertEquals("migration-text",OracleScalars.varchar2(HexFormat.of().parseHex(before.get(3).hex()),"AL32UTF8"));
        assertEquals("after-migration",OracleScalars.varchar2(HexFormat.of().parseHex(after.get(1).hex()),"AL32UTF8"));
        assertEquals("00aaff80",after.get(2).hex());assertEquals("after-migration-text",OracleScalars.varchar2(HexFormat.of().parseHex(after.get(3).hex()),"AL32UTF8"));
        assertTrue(Files.readString(source.resolve("future-r4.stdout")).contains("ROW|1|after-migration|00AAFF80|after-migration-text"));
        var state=history.materializedState();byte[] values=state.checkpoint();
        var restored=HistoricalDictionaryState.restore(values,sha(values),baseline,baselineSha);
        assertEquals(history.schemaAt(scn,position),restored.schemaAt(scn,position));
        byte[] metadata=DictionaryCommittedState.encode(history.commits());
        assertEquals(history.commits(),DictionaryCommittedState.restore(metadata,sha(metadata),snapshot,restored));
        var corrupt=new ArrayList<>(history.commits());int at=corrupt.indexOf(change);var roles=new ArrayList<>(change.generations());roles.set(3,roles.get(2));
        corrupt.set(at,new ArchivedDictionaryHistory.Commit(change.identity(),change.original(),change.rowPairs(),change.indexPairs(),change.controls(),change.rows(),position,roles));
        byte[] poison=DictionaryCommittedState.encode(corrupt);
        assertThrows(RedoException.class,()->DictionaryCommittedState.restore(poison,sha(poison),snapshot,restored));
    }
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
