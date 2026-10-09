package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

/** Synthetic cross-member placement of observed OBJ$ vectors, not real Oracle acceptance. */
class PendingDictionaryRecoveryTest {
    @TempDir Path directory;
    record Fixture(RedoBatch prefix,RedoBatch closed,FrozenHeapBaseline.Snapshot snapshot) {}
    Fixture fixture()throws Exception {
        return fixture(100);
    }
    Fixture fixture(long scn)throws Exception {
        var original=new DictionaryObjectUndoPairTest().records().get(0);
        var undo=original.vectors().get(0);var row=original.vectors().get(1);
        var fields=new ArrayList<>(undo.fields());byte[] metadata=Arrays.copyOf(fields.get(1),76);
        put(metadata,12,0,4);put(metadata,19,0,1);put(metadata,20,0x0c08,2);put(metadata,22,1,2);fields.set(1,metadata);
        // Bind the observed physical pair to the baseline's OBJ$ anchor.
        put(fields.get(3),20,29,2);put(row.fields().get(1),20,29,2);
        undo=new RedoRecords.Vector(5,1,undo.blockClass(),undo.file(),undo.dba(),0,0,3,fields);
        var identity=UndoHeader.decode(undo);long segment=0x02400110L;
        var helper=new ArchivedCompensationAssociationsTest();
        var start=helper.start(identity.xid(),segment,identity.uba().dba(),identity.uba().record());
        put(start.fields().get(0),12,identity.uba().sequence(),2);
        var end=new ArchivedPhysicalTransactionsTest().end(identity.xid(),identity.uba().dba(),segment);
        put(end.fields().get(1),4,identity.uba().sequence(),2);put(end.fields().get(1),6,identity.uba().record(),1);
        var first=writeArchive("prefix.dbf",7,scn,scn+100,scn+10,List.of(start,undo,row));
        var second=writeArchive("suffix.dbf",8,scn+100,scn+200,scn+110,List.of(end));
        var baseline=new DictionaryBaselineTest();baseline.directory=directory;
        var snapshot=baseline.read(baseline.v3().replace("SNAPSHOT_SCN|2522819","SNAPSHOT_SCN|"+scn));
        return new Fixture(RedoBatch.archives(List.of(first)),RedoBatch.archives(List.of(first,second)),snapshot);
    }
    Path writeArchive(String name,int sequence,long low,long high,long scn,List<RedoRecords.Vector> vectors)throws Exception {
        byte[] bytes=new ArchivedCompensationInventoryTest().fixture(false,false);
        Arrays.fill(bytes,1024,bytes.length,(byte)0);put(bytes,692,low,4);put(bytes,704,high,4);
        put(bytes,536,1276726821L,4);put(bytes,564,1276717861L,4);put(bytes,672,1245693159L,4);put(bytes,676,1920977,4);
        for(int block=1;block<bytes.length/512;block++) {
            put(bytes,block*512,0x2201,2);put(bytes,block*512+4,block,4);put(bytes,block*512+8,sequence,4);
        }
        byte[] logical=new byte[4096];new ArchivedCompensationInventoryTest().record(logical,0,scn,vectors);
        int length=(int)RedoBytes.u32(logical,16),done=0;
        for(int block=2;done<length;block++) {
            int n=Math.min(496,length-done);System.arraycopy(logical,16+done,bytes,block*512+16,n);done+=n;
        }
        for(int block=0;block<bytes.length/512;block++)seal(bytes,block*512);
        var path=directory.resolve(name);Files.write(path,bytes);return path;
    }
    @Test void pendingSysHasReceiptButNoCommittedSchemaAndCanRestoreOnlyWithItsRawPrefix()throws Exception {
        var f=fixture();var pending=ArchivedDictionaryHistory.readPrefix(f.prefix(),f.snapshot());
        assertEquals(1,pending.pending().size());assertTrue(pending.commits().isEmpty());
        assertEquals(1,pending.pending().get(0).rowPairs());
        assertEquals("DICTIONARY_SCHEMA_PENDING_SYS",assertThrows(RedoException.class,()->pending.schemaAt(110)).getMessage());
        assertEquals(f.snapshot().columns(),pending.schemaAt(100));
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.read(f.prefix(),f.snapshot()));
        byte[] checkpoint=pending.checkpoint();
        var restored=ArchivedDictionaryHistory.restore(f.closed(),f.snapshot(),checkpoint,digest(checkpoint));
        assertTrue(restored.pending().isEmpty());assertEquals(1,restored.commits().size());
        assertEquals(210,restored.commits().get(0).identity().endRecordScn());
        assertEquals(f.snapshot().columns(),restored.schemaAt(210));
        assertArrayEquals(ArchivedDictionaryHistory.read(f.closed(),f.snapshot()).checkpoint(),restored.checkpoint());
        var omitted=RedoBatch.archives(List.of(directory.resolve("suffix.dbf")));
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.restore(omitted,f.snapshot(),checkpoint,digest(checkpoint)));
    }
    @Test void rehashedOmittedPendingStateStillFailsRawAuthority()throws Exception {
        var f=fixture();var pending=ArchivedDictionaryHistory.readPrefix(f.prefix(),f.snapshot());
        byte[] changed=new String(pending.checkpoint(),java.nio.charset.StandardCharsets.UTF_8).lines()
                .filter(line->!line.startsWith("P|")).collect(java.util.stream.Collectors.joining("\n","","\n"))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("DICTIONARY_CHECKPOINT_STATE_CHANGED",assertThrows(RedoException.class,
                ()->ArchivedDictionaryHistory.restore(f.closed(),f.snapshot(),changed,digest(changed))).getMessage());
        assertThrows(UnsupportedOperationException.class,()->pending.pending().clear());
    }
    @Test void committedDictionaryReusePreservesHistoryAndRequiresOriginalRawOwnership()throws Exception {
        var f=fixture();var closed=ArchivedDictionaryHistory.readPrefix(f.closed(),f.snapshot());var pending=closed.durablePendingState();
        var restored=ArchivedDictionaryHistory.readRestored(f.closed(),f.snapshot(),pending,closed.commits());
        assertEquals(closed.commits(),restored.commits());assertArrayEquals(closed.checkpoint(),restored.checkpoint());
        assertArrayEquals(closed.materializedState().checkpoint(),restored.materializedState().checkpoint());
        var c=closed.commits().get(0);var e=c.original().get(0);var p=e.position();
        var wrong=new ArchivedUndoInventory.Entry(new ArchivedUndoInventory.Position(p.archiveSequence(),p.block(),p.offset()+1,p.vector()),
                e.identity(),e.opcodeMajor(),e.opcodeMinor(),e.previousDba(),e.rci());
        var changed=new ArchivedDictionaryHistory.Commit(c.identity(),List.of(wrong),c.rowPairs(),c.indexPairs(),c.controls(),c.rows(),c.commitPosition());
        assertEquals("DICTIONARY_COMMITTED_ORIGINAL_CHANGED",assertThrows(RedoException.class,
                ()->ArchivedDictionaryHistory.readRestored(f.closed(),f.snapshot(),pending,List.of(changed))).getMessage());
    }
    @Test void unifiedRecoveryCountsPendingSysWithoutBusinessEvents()throws Exception {
        var f=fixture(2522819);var journal=new CaptureJournal();var first=directory.resolve("prefix.dbf");var second=directory.resolve("suffix.dbf");
        var captures=List.of(journal.readRawClosed(first,digest(Files.readAllBytes(first))),journal.readRawClosed(second,digest(Files.readAllBytes(second))));
        var baseline=directory.resolve("baseline.snapshot");
        var text=new DictionaryBaselineTest().v3();Files.writeString(baseline,text);
        var waiting=HistoricalRecovery.prefixCaptured(captures.subList(0,1),baseline,digest(Files.readAllBytes(baseline)),4190000);
        assertEquals(1,waiting.pendingTransactions());assertEquals(0,waiting.completedTransactions());
        byte[] checkpoint=waiting.checkpoint();String checkpointSha=digest(checkpoint);
        assertArrayEquals(checkpoint,HistoricalRecovery.verifyPrefixCaptured(captures.subList(0,1),baseline,
                digest(Files.readAllBytes(baseline)),4190000,checkpoint,checkpointSha).checkpoint());
        byte[] malformed={'\n'};
        assertEquals("HISTORICAL_RECOVERY_BASELINE_OR_LEAF",assertThrows(RedoException.class,()->HistoricalRecovery.verifyPrefixCaptured(
                captures,baseline,digest(Files.readAllBytes(baseline)),4190000,malformed,digest(malformed))).getMessage());
        assertTrue(new String(checkpoint,java.nio.charset.StandardCharsets.UTF_8).startsWith("ORK_HISTORICAL_RECOVERY_V4\n"));
        assertEquals("HISTORICAL_RECOVERY_DIGEST_MISMATCH",assertThrows(RedoException.class,()->HistoricalRecovery.readCaptured(
                captures,baseline,digest(Files.readAllBytes(baseline)),4190000,checkpoint,"0".repeat(64))).getMessage());
        assertEquals("HISTORICAL_RECOVERY_BASELINE_OR_LEAF",assertThrows(RedoException.class,()->HistoricalRecovery.readCaptured(
                captures,baseline,digest(Files.readAllBytes(baseline)),4190001,checkpoint,checkpointSha)).getMessage());
        byte[] noncanonical=Arrays.copyOf(checkpoint,checkpoint.length+1);noncanonical[checkpoint.length]='\n';
        assertEquals("HISTORICAL_RECOVERY_NONCANONICAL",assertThrows(RedoException.class,()->HistoricalRecovery.readCaptured(
                captures,baseline,digest(Files.readAllBytes(baseline)),4190000,noncanonical,digest(noncanonical))).getMessage());
        assertEquals("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED",assertThrows(RedoException.class,()->HistoricalRecovery.readCaptured(
                captures.subList(1,2),baseline,digest(Files.readAllBytes(baseline)),4190000,checkpoint,checkpointSha)).getMessage());
        assertEquals("NO_PHYSICAL_OBJECT_TRANSACTIONS",assertThrows(RedoException.class,()->HistoricalRecovery.readCaptured(
                captures,baseline,digest(Files.readAllBytes(baseline)),4190000,waiting.checkpoint(),digest(waiting.checkpoint()))).getMessage());
    }
    String digest(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
