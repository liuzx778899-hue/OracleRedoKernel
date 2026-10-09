package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalDictionaryStateTest {
    @TempDir Path directory;
    FrozenHeapBaseline.Snapshot baseline()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;return helper.read(helper.v3());
    }
    ArchivedRedo.Header source(FrozenHeapBaseline.Snapshot s) {
        var id=s.source();return new ArchivedRedo.Header("a".repeat(64),51200,id.dbid(),id.activationId(),id.compatibility(),id.thread(),
                90,id.resetlogsId(),id.resetlogsScn(),s.scn()-10,s.scn()+100,100);
    }
    String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    ArchivedDictionaryHistory.Commit commit(FrozenHeapBaseline.Snapshot s,long scn,int offset,List<DictionaryBaseline.Row> rows) {
        return new ArchivedDictionaryHistory.Commit(new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(1,2,3),
                s.source().containerUid(),s.source().containerId(),s.scn()+1,scn,false),List.of(),0,0,0,rows,
                new ArchivedUndoInventory.Position(90,10,offset,1));
    }
    List<DictionaryBaseline.Row> renamed(FrozenHeapBaseline.Snapshot s) {
        var rows=new ArrayList<>(s.dictionaryBaseline().rows());
        for(int i=0;i<rows.size();i++)if(rows.get(i).role().equals("COL$")) {
            var row=rows.get(i);var values=new ArrayList<>(row.values());var columns=s.dictionaryBaseline().layouts().get("COL$").columns();
            for(int j=0;j<columns.size();j++)if(columns.get(j).name().equals("NAME"))
                values.set(j,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"52454e414d4544"));
            rows.set(i,new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));break;
        }
        return rows;
    }
    @Test void completeValuesAndCommitPositionSurviveRoundTripWithoutRaw()throws Exception {
        var s=baseline();var rows=renamed(s);long scn=s.scn()+10;
        var state=HistoricalDictionaryState.from(s,List.of(source(s)),List.of(commit(s,scn,32,rows)),List.of());
        byte[] bytes=state.checkpoint();var restored=HistoricalDictionaryState.restore(bytes,sha(bytes),s);
        assertEquals(s.dictionaryBaseline().rows(),restored.rowsAt(scn,new ArchivedUndoInventory.Position(90,10,16,1)));
        assertEquals(rows,restored.rowsAt(scn,new ArchivedUndoInventory.Position(90,10,32,1)));
        assertEquals(DictionaryHistoryRows.schema(s,rows),restored.schemaAt(scn,null));
        assertArrayEquals(bytes,restored.checkpoint());
        assertThrows(UnsupportedOperationException.class,()->restored.rowsAt(scn,null).clear());
    }
    @Test void pendingSysNeverBecomesCommittedSchema()throws Exception {
        var s=baseline();long begin=s.scn()+5;
        var pending=new ArchivedDictionaryHistory.Pending(new HeapRowPatch.Xid(1,2,3),begin,1,1,0,0,"identity","graph");
        var state=HistoricalDictionaryState.from(s,List.of(source(s)),List.of(),List.of(pending));byte[] bytes=state.checkpoint();
        var restored=HistoricalDictionaryState.restore(bytes,sha(bytes),s);
        assertEquals(s.columns(),restored.schemaAt(begin-1,null));
        assertEquals("DICTIONARY_SCHEMA_PENDING_SYS",assertThrows(RedoException.class,()->restored.schemaAt(begin,null)).getMessage());
        assertThrows(RedoException.class,()->restored.schemaAt(s.scn()+100,null));
    }
    @Test void wrongDigestBaselineTruncationAndTrailingBytesRefuse()throws Exception {
        var s=baseline();byte[] bytes=HistoricalDictionaryState.from(s,List.of(source(s)),List.of(),List.of()).checkpoint();
        assertThrows(RedoException.class,()->HistoricalDictionaryState.restore(bytes,"0".repeat(64),s));
        var other=new FrozenHeapBaseline.Snapshot("b".repeat(64),s.source(),s.scn(),s.heap(),s.index(),s.columns(),s.rows(),s.dictionaryAnchors(),s.dictionaryBaseline());
        assertThrows(RedoException.class,()->HistoricalDictionaryState.restore(bytes,sha(bytes),other));
        for(byte[] malformed:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1)))
            assertThrows(RedoException.class,()->HistoricalDictionaryState.restore(malformed,sha(malformed),s));
    }
    @Test void missingValuesUnorderedCommitsAndForeignSourceRefuseEvenWithMatchingDigest()throws Exception {
        var s=baseline();var rows=new ArrayList<>(s.dictionaryBaseline().rows());var row=rows.get(0);
        var values=new ArrayList<>(row.values());values.set(0,new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""));
        rows.set(0,new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));
        var badSources=new ArrayList<>(List.of(source(s),source(s)));
        var h=source(s);var foreign=new ArchivedRedo.Header(h.sha256(),h.bytes(),h.dbid()+1,h.activationId(),h.compatibility(),h.thread(),
                h.sequence(),h.resetlogsId(),h.resetlogsScn(),h.firstScn(),h.nextScn(),h.blocks());
        var wrongParent=new ArrayList<>(s.dictionaryBaseline().rows());
        wrongParent.set(0,new DictionaryBaseline.Row(row.role(),row.target()+1,row.key(),row.values()));
        for(var state:List.of(
                HistoricalDictionaryState.from(s,List.of(source(s)),List.of(commit(s,s.scn()+10,32,rows)),List.of()),
                HistoricalDictionaryState.from(s,List.of(source(s)),List.of(commit(s,s.scn()+10,32,s.dictionaryBaseline().rows()),
                        commit(s,s.scn()+10,16,s.dictionaryBaseline().rows())),List.of()),
                HistoricalDictionaryState.from(s,badSources,List.of(),List.of()),
                HistoricalDictionaryState.from(s,List.of(foreign),List.of(),List.of()),
                HistoricalDictionaryState.from(s,List.of(source(s)),List.of(commit(s,s.scn()+10,32,wrongParent)),List.of()))) {
            byte[] bytes=state.checkpoint();assertThrows(RedoException.class,()->HistoricalDictionaryState.restore(bytes,sha(bytes),s));
        }
    }
    @Test void unsupportedIntermediateSchemaPreservesValuesAndOriginalRefusal()throws Exception {
        var s=baseline();var rows=renamed(s);
        for(int i=0;i<rows.size();i++)if(rows.get(i).role().equals("COL$")) {
            var row=rows.get(i);var values=new ArrayList<>(row.values());var columns=s.dictionaryBaseline().layouts().get("COL$").columns();
            for(int j=0;j<columns.size();j++)if(columns.get(j).name().equals("NAME"))values.set(j,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"2d"));
            rows.set(i,new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));break;
        }
        byte[] bytes=HistoricalDictionaryState.from(s,List.of(source(s)),List.of(commit(s,s.scn()+10,32,rows)),List.of()).checkpoint();
        var restored=HistoricalDictionaryState.restore(bytes,sha(bytes),s);
        assertEquals(rows,restored.rowsAt(s.scn()+10,null));
        assertEquals("UNSUPPORTED_SNAPSHOT_IDENTIFIER",assertThrows(RedoException.class,()->restored.schemaAt(s.scn()+10,null)).getMessage());
    }
}
