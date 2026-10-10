package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryHistoryRowsTest {
    @TempDir Path directory;
    FrozenHeapBaseline.Snapshot baseline()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;return helper.read(helper.v3());
    }
    @Test void observedTabClusterScopeIncludesBothLinkEndpointsAcrossSharedDictionaryRoles()throws Exception {
        var snapshot=baseline();var role=snapshot.dictionaryBaseline().layouts().get("TAB$").role();
        var fixture=new DictionaryClusterLinkPairTest();var record=fixture.record(false);
        java.nio.ByteBuffer.wrap(record.vectors().get(0).fields().get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0,(int)role.logicalObject());
        assertFalse(ArchivedDictionaryHistory.affectsObservedTabCluster(record,record.vectors().get(0),role,snapshot));
        var anchor=snapshot.dictionaryAnchors().stream().filter(a->a.dictionary().name().equals("COL$")).findFirst().orElseThrow();
        var pointer=java.nio.ByteBuffer.wrap(record.vectors().get(1).fields().get(1));
        pointer.putInt(22,(int)anchor.key().dba());pointer.putShort(26,(short)anchor.key().slot());
        assertTrue(ArchivedDictionaryHistory.affectsObservedTabCluster(record,record.vectors().get(0),role,snapshot));
        record.vectors().get(1).fields().get(0)[16]^=1;
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.affectsObservedTabCluster(record,record.vectors().get(0),role,snapshot));
    }
    @Test void observedTabFullInsertDoesNotExcludeAnAnchoredRowOrUnknownProfile()throws Exception {
        var snapshot=baseline();var role=snapshot.dictionaryBaseline().layouts().get("TAB$").role();
        byte[] k=HexFormat.of().parseHex("dc8d400090004000ff120201020000006c002c04be760000010000000000000000000000010000007e00030001b1c0ff83f7d43a0100");
        var fields=new ArrayList<byte[]>();for(int i=0;i<48;i++)fields.add(new byte[0]);fields.set(3,k);
        var undo=new RedoRecords.Vector(5,1,0,0,0,0,0,3,fields);
        var record=new RedoRecords.Record(1,1,1,1,1,1,677737149,List.of(undo));
        assertFalse(ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
        var anchor=snapshot.dictionaryAnchors().stream().filter(a->a.dictionary().name().equals("TAB$")).findFirst().orElseThrow();
        var bytes=java.nio.ByteBuffer.wrap(k).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0,(int)anchor.key().dba());bytes.putShort(42,(short)anchor.key().slot());
        assertTrue(ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
        k[44]=5;
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
    }
    @Test void observedTabOverwriteIncludesRelocationDestinationAndRequiresCurrentUndoOwner()throws Exception {
        var snapshot=baseline();var role=snapshot.dictionaryBaseline().layouts().get("TAB$").role();
        var fixture=new DictionaryClusterLinkPairTest().record(true);var old=fixture.vectors().get(0);
        var fields=new ArrayList<byte[]>(old.fields());
        java.nio.ByteBuffer.wrap(fields.get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0,(int)role.logicalObject());
        fields.set(3,HexFormat.of().parseHex("c78d400090004000ff120601020000006c022c07ff7f0000a009fb35be760000c0130036be7600008900070001b0c0ff83f707000000"));
        while(fields.size()<48)fields.add(new byte[0]);
        var undo=new RedoRecords.Vector(5,1,old.blockClass(),old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),fields);
        byte[] k=HexFormat.of().parseHex("c78d400090004000ff1206010200000020020007ff7f0000a009fb35dc8d400002000000be7600000900070001b0c0ff");
        var row=new RedoRecords.Vector(11,6,1,9,0x00408dc7L,1,1,3,List.of(fixture.vectors().get(1).fields().get(0),k));
        var record=new RedoRecords.Record(1,1,1,1,1,1,677737149,List.of(undo,row));
        assertFalse(ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
        var anchor=snapshot.dictionaryAnchors().stream().filter(a->a.dictionary().name().equals("TAB$")).findFirst().orElseThrow();
        var bytes=java.nio.ByteBuffer.wrap(k).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(28,(int)anchor.key().dba());bytes.putShort(32,(short)anchor.key().slot());
        assertTrue(ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
        row.fields().get(0)[8]^=1;
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.affectsObservedTabCluster(record,undo,role,snapshot));
    }
    @Test void schemaUsesCompleteMetadataAndRefusesMissingRowsAndWrongParent()throws Exception {
        var s=baseline();var rows=s.dictionaryBaseline().rows();
        assertEquals(s.columns(),DictionaryHistoryRows.schema(s,rows));
        var missing=new ArrayList<>(rows);missing.removeIf(r->r.role().equals("COL$")&&r.key().slot()==15);
        assertThrows(RedoException.class,()->DictionaryHistoryRows.schema(s,missing));
        var bad=new ArrayList<>(rows);var row=rows.stream().filter(r->r.role().equals("COL$")).findFirst().orElseThrow();
        var values=new ArrayList<>(row.values());values.set(0,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"80"));
        bad.set(rows.indexOf(row),new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));
        assertThrows(RedoException.class,()->DictionaryHistoryRows.schema(s,bad));
    }
    @Test void rowReplayRequiresMatchingBeforeValues()throws Exception {
        var s=baseline();var role=s.dictionaryBaseline().layouts().get("OBJ$").role();
        var row=s.dictionaryBaseline().rows().stream().filter(r->r.role().equals("OBJ$")).findFirst().orElseThrow();
        var columns=new ArrayList<HeapRowPatch.Column>();for(var v:row.values())columns.add(new HeapRowPatch.Column(HeapRowPatch.State.ABSENT,new byte[0]));
        columns.set(3,new HeapRowPatch.Column(HeapRowPatch.State.VALUE,new byte[]{'X'}));
        var patch=new HeapRowPatch.Patch(HeapRowPatch.Kind.UPDATE,row.key().dba(),row.key().slot(),1,null,null,columns,new HeapRowPatch.RowExtent(26,1,-1,0),null);
        var op=new ArchivedDictionaryHistory.RowOperation(role,patch,patch);
        assertEquals("DICTIONARY_ROW_BEFORE_VALUE_MISMATCH",assertThrows(RedoException.class,()->new DictionaryHistoryRows(s).apply(List.of(op),List.of())).getMessage());
    }
    @Test void temporalBusinessTypesSurviveTheHistoricalSchemaGate()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;
        String fixture=helper.v3().replace("TXT|VARCHAR2","TXT|DATE").replace("NOTE|VARCHAR2","NOTE|TIMESTAMP")
                .replace("DICT_COLUMN|2|2|TXT|1|","DICT_COLUMN|2|2|TXT|12|")
                .replace("DICT_COLUMN|3|3|NOTE|1|","DICT_COLUMN|3|3|NOTE|180|")
                .replace("|545854|C102|","|545854|C10D|").replace("|4E4F5445|C102|","|4E4F5445|C20251|");
        var snapshot=helper.read(fixture);
        assertEquals(snapshot.columns(),DictionaryHistoryRows.schema(snapshot,snapshot.dictionaryBaseline().rows()));
    }
}
