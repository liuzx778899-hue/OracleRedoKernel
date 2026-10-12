package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen raw117-119; SQL truth is an independent assertion, never a production event input. */
@EnabledIfSystemProperty(named="ork.lob.history",matches=".+")
class LobTruncateFixtureTest {
    @Test void basicFileGenerationValuesAndDurableDictionary()throws Exception {verify(true);}
    @Test void secureFileGenerationValuesAndDurableDictionary()throws Exception {verify(false);}
    private void verify(boolean basic)throws Exception {
        Path source=Path.of(System.getProperty("ork.lob.history"));String name="T_D4_HL"+(basic?"B":"S")+"_20261011";
        Path baseline=source.resolve(name+"-baseline.snapshot");String baselineSha=sha(Files.readAllBytes(baseline));
        var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);var captures=new ArrayList<CaptureJournal.Verified>();long leaf=0;
        for(int sequence=117;sequence<=119;sequence++) {
            Path raw=source.resolve("inputs/"+sequence+".raw");var capture=new CaptureJournal().readRawClosed(raw,sha(Files.readAllBytes(raw)));captures.add(capture);
            for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors())
                if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.objectId()==snapshot.index().dataObject()) {
                    assertTrue(leaf==0 || leaf==vector.dba());leaf=vector.dba();
                }
        }
        var history=ArchivedDictionaryHistory.readCaptured(captures,baseline,baselineSha);
        var batch=CapturedHistoricalHeapBatch.read(captures,baseline,baselineSha,leaf);assertEquals(4,batch.events().size());
        var deleted=batch.events().get(2).row();assertEquals(HeapRowPatch.Kind.DELETE,deleted.operation());assertEquals(snapshot.heap().dataObject(),deleted.rowKey().dataObjectId());
        assertEquals(4,deleted.before().size());assertTrue(deleted.after().isEmpty());
        var target=new HashMap<>(snapshot.rows());
        for(var entry:batch.events()) {
            for(var key:List.copyOf(target.keySet())) {
                var current=new ArrayList<>(target.get(key));int width=entry.schema().columns().size();
                while(current.size()>width)current.remove(current.size()-1);
                while(current.size()<width)current.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""));
                target.put(key,List.copyOf(current));
            }
            var change=entry.row();assertEquals(target.getOrDefault(change.rowKey(),List.of()),change.before());
            if(change.after().isEmpty())target.remove(change.rowKey());else target.put(change.rowKey(),change.after());
        }
        assertEquals(1,target.size());
        var event=batch.events().get(3);var row=event.row();assertEquals(HeapRowPatch.Kind.INSERT,row.operation());assertTrue(row.before().isEmpty());
        assertEquals(basic?81009:81013,row.rowKey().dataObjectId());assertEquals(row.rowKey().dataObjectId(),event.schema().heap().dataObject());
        assertEquals(basic?81006:81010,event.schema().index().dataObject());assertEquals(4,row.after().size());
        var values=row.after();assertEquals(2,OracleScalars.number(HexFormat.of().parseHex(values.get(0).hex())).longValueExact());
        assertEquals("new-generation",OracleScalars.varchar2(HexFormat.of().parseHex(values.get(1).hex()),"AL32UTF8"));
        assertEquals("00aaff",values.get(2).hex());assertEquals("new-generation-text",OracleScalars.varchar2(HexFormat.of().parseHex(values.get(3).hex()),"AL32UTF8"));
        assertTrue(Files.readString(source.resolve(name+"-truncate.stdout")).contains("ROW|2|new-generation|00AAFF|new-generation-text"));
        var state=history.materializedState();byte[] bytes=state.checkpoint();
        var restored=HistoricalDictionaryState.restore(bytes,sha(bytes),baseline,baselineSha);
        long scn=row.transaction().endRecordScn();assertEquals(event.schema().columns(),restored.schemaAt(scn,null));
        byte[] metadata=DictionaryCommittedState.encode(history.commits());
        assertEquals(history.commits(),DictionaryCommittedState.restore(metadata,sha(metadata),snapshot,restored));
        var corrupt=new ArrayList<>(history.commits());var last=corrupt.remove(corrupt.size()-1);var roles=new ArrayList<>(last.generations());var heap=roles.get(0);
        roles.set(0,new FrozenHeapBaseline.ObjectRole(heap.owner(),heap.name(),heap.logicalObject()+1,heap.dataObject()));
        corrupt.add(new ArchivedDictionaryHistory.Commit(last.identity(),last.original(),last.rowPairs(),last.indexPairs(),last.controls(),last.rows(),last.commitPosition(),roles));
        byte[] poison=DictionaryCommittedState.encode(corrupt);
        assertThrows(RedoException.class,()->DictionaryCommittedState.restore(poison,sha(poison),snapshot,restored));
    }
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
