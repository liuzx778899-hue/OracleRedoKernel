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
    @Test void lobPropertyIsAVisibleColumnAndMustMatchItsExactType()throws Exception {
        var snapshot=baseline();var baseline=snapshot.dictionaryBaseline();
        var layout=baseline.layouts().get("COL$");
        int typeAt=java.util.stream.IntStream.range(0,layout.columns().size()).filter(i->layout.columns().get(i).name().equals("TYPE#")).findFirst().orElseThrow();
        int propertyAt=java.util.stream.IntStream.range(0,layout.columns().size()).filter(i->layout.columns().get(i).name().equals("PROPERTY")).findFirst().orElseThrow();
        for(String type:List.of("BLOB","CLOB")) {
            var columns=new ArrayList<>(snapshot.columns());var prior=columns.get(1);
            columns.set(1,new FrozenHeapBaseline.PhysicalColumn(prior.segment(),prior.internal(),prior.name(),type,prior.nullable(),false));
            var rows=new ArrayList<DictionaryBaseline.Row>();
            for(var row:baseline.rows()) {
                if(!row.role().equals("COL$") || baseline.number(row,"INTCOL#")!=2){rows.add(row);continue;}
                var values=new ArrayList<>(row.values());
                values.set(typeAt,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,type.equals("BLOB")?"c2020e":"c2020d"));
                values.set(propertyAt,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c2021d"));
                rows.add(new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));
            }
            var lobBaseline=new DictionaryBaseline(baseline.layouts(),baseline.indexes(),rows);
            var lobSnapshot=new FrozenHeapBaseline.Snapshot(snapshot.sha256(),snapshot.source(),snapshot.scn(),snapshot.heap(),snapshot.index(),columns,
                    snapshot.rows(),snapshot.dictionaryAnchors(),lobBaseline);
            assertEquals(columns,DictionaryHistoryRows.schema(lobSnapshot,rows));
            DictionaryBaseline.Row row=null;
            for(var candidate:rows)if(candidate.role().equals("COL$") && lobBaseline.number(candidate,"INTCOL#")==2)row=candidate;
            assertNotNull(row);
            var invalid=new ArrayList<>(row.values());invalid.set(propertyAt,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"80"));
            rows.set(rows.indexOf(row),new DictionaryBaseline.Row(row.role(),row.target(),row.key(),invalid));
            assertThrows(RedoException.class,()->DictionaryHistoryRows.schema(lobSnapshot,rows));
        }
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
        assertTrue(assertThrows(RedoException.class,()->new DictionaryHistoryRows(s).apply(List.of(op),List.of())).getMessage().startsWith("DICTIONARY_ROW_BEFORE_VALUE_MISMATCH:"));
    }
    @Test void raw120ObjectInsertMaterializesStoredValuesAndNullableSuffixOnly()throws Exception {
        var snapshot=baseline();var role=snapshot.dictionaryBaseline().layouts().get("OBJ$").role();
        var record=new DictionaryObjectUndoPairTest().records("/dictionary-object-insert-raw120.txt").get(0);
        var pair=DictionaryObjectUndoPair.bind(record,record.vectors().get(1),role,37).physical();
        var operation=new ArchivedDictionaryHistory.RowOperation(role,pair.forward(),pair.undo().inverse(),pair.undo().identity());
        var state=new DictionaryHistoryRows(snapshot);var rows=state.apply(List.of(operation),List.of());
        var inserted=rows.stream().filter(r->r.role().equals("OBJ$") && r.target()==81024).findFirst().orElseThrow();
        assertEquals(37,inserted.values().size());assertEquals("T_D4_MIG_INT_20261011",snapshot.dictionaryBaseline().text(inserted,"NAME"));
        assertTrue(inserted.values().subList(26,37).stream().allMatch(c->c.state()==HeapRowPatch.State.NULL));
        assertThrows(RedoException.class,()->state.apply(List.of(operation),List.of()));
        var remove=new ArchivedDictionaryHistory.RowOperation(role,pair.undo().inverse(),pair.forward(),pair.undo().identity());
        assertEquals(snapshot.dictionaryBaseline().rows(),state.apply(List.of(remove),List.of()));
        var badColumns=new ArrayList<>(pair.forward().columns());badColumns.set(26,new HeapRowPatch.Column(HeapRowPatch.State.NULL,new byte[0]));
        var patch=pair.forward();var badPatch=new HeapRowPatch.Patch(patch.kind(),patch.dba(),patch.slot(),patch.xtype(),patch.xid(),patch.uba(),badColumns,patch.extent(),patch.lockTo());
        assertEquals("DICTIONARY_INSERT_STORED_COLUMN_BOUNDARY_MISMATCH",assertThrows(RedoException.class,()->new DictionaryHistoryRows(snapshot).apply(
                List.of(new ArchivedDictionaryHistory.RowOperation(role,badPatch,pair.undo().inverse())),List.of())).getMessage());
        var layouts=new HashMap<>(snapshot.dictionaryBaseline().layouts());var old=layouts.get("OBJ$");
        var columns=new ArrayList<>(old.columns());var suffix=columns.get(26);
        columns.set(26,new DictionaryBaseline.Column(suffix.segment(),suffix.internal(),suffix.name(),suffix.type(),true));
        layouts.put("OBJ$",new DictionaryBaseline.Layout(role,-1,columns));
        var invalidBaseline=new DictionaryBaseline(layouts,snapshot.dictionaryBaseline().indexes(),snapshot.dictionaryBaseline().rows());
        var invalid=new FrozenHeapBaseline.Snapshot(snapshot.sha256(),snapshot.source(),snapshot.scn(),snapshot.heap(),snapshot.index(),snapshot.columns(),
                snapshot.rows(),snapshot.dictionaryAnchors(),invalidBaseline);
        assertEquals("DICTIONARY_REQUIRED_VALUE_MISSING",assertThrows(RedoException.class,()->new DictionaryHistoryRows(invalid).apply(List.of(operation),List.of())).getMessage());
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
