package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalRowSchemaTest {
    @TempDir Path directory;
    record Fixture(FrozenHeapBaseline.Snapshot snapshot,RedoBatch inputs,ArchivedDictionaryHistory.Timeline history) {}
    Fixture fixture()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;var base=helper.read(helper.v3());
        var path=directory.resolve("before-baseline.dbf");Files.write(path,new ArchivedUndoInventoryTest().fixture());
        var header=new ArchivedRedo().scan(path);var source=ArchivedHeapRows.Source.of(header,base.source().containerUid(),base.source().containerId());
        var snapshot=new FrozenHeapBaseline.Snapshot(base.sha256(),source,150,base.heap(),base.index(),base.columns(),base.rows(),base.dictionaryAnchors(),base.dictionaryBaseline());
        var inputs=RedoBatch.archives(List.of(path));return new Fixture(snapshot,inputs,ArchivedDictionaryHistory.read(inputs,snapshot));
    }
    @Test void historyTokenRejectsDifferentBaselineOrInputEvenWhenSchemaMatches()throws Exception {
        var f=fixture();var s=f.snapshot();f.history().requireBoundTo(f.inputs(),s);
        var changed=new FrozenHeapBaseline.Snapshot("0".repeat(64),s.source(),s.scn(),s.heap(),s.index(),s.columns(),s.rows(),s.dictionaryAnchors(),s.dictionaryBaseline());
        assertThrows(RedoException.class,()->f.history().requireBoundTo(f.inputs(),changed));
        assertThrows(RedoException.class,()->f.history().schemaAt(s.scn()-1));
    }
    @Test void omittedColumnRequiresCommittedMetadataRatherThanNullableFlagAlone()throws Exception {
        var f=fixture();var s=f.snapshot();var next=new ArrayList<>(s.columns());next.add(new FrozenHeapBaseline.PhysicalColumn(4,4,"EXTRA", "VARCHAR2",true,false));
        assertEquals("HISTORICAL_ADDED_COLUMN_METADATA_MISSING",assertThrows(RedoException.class,()->ArchivedHeapRows.remapRows(s.rows(),s.columns(),next,f.history(),s.scn())).getMessage());
        var renamed=new ArrayList<>(s.columns());renamed.set(1,new FrozenHeapBaseline.PhysicalColumn(2,2,"RENAMED", "VARCHAR2",true,false));
        assertEquals("UNSUPPORTED_HISTORICAL_COLUMN_IDENTITY_CHANGE",assertThrows(RedoException.class,()->ArchivedHeapRows.remapRows(s.rows(),s.columns(),renamed,f.history(),s.scn())).getMessage());
        var dropped=ArchivedHeapRows.remapRows(s.rows(),s.columns(),s.columns().subList(0,2),f.history(),s.scn());
        for(var row:s.rows().entrySet())assertEquals(row.getValue().subList(0,2),dropped.get(row.getKey()));
    }
    List<DictionaryBaseline.Row> renameColumn(FrozenHeapBaseline.Snapshot snapshot,String name)throws Exception {
        var baseline=snapshot.dictionaryBaseline();var layout=baseline.layouts().get("COL$");
        int at=0;while(!layout.columns().get(at).name().equals("NAME"))at++;
        var rows=new ArrayList<DictionaryBaseline.Row>();
        for(var row:baseline.rows()) {
            var values=new ArrayList<>(row.values());
            if(row.role().equals("COL$") && baseline.number(row,"INTCOL#")==2)
                values.set(at,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,HexFormat.of().formatHex(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            rows.add(new DictionaryBaseline.Row(row.role(),row.target(),row.key(),values));
        }
        return rows;
    }
    @Test void equalScnDictionaryVersionsAreSelectedByRawCommitPosition()throws Exception {
        var f=fixture();var s=f.snapshot();long scn=160;
        var first=new ArchivedUndoInventory.Position(7,4,16,1);var second=new ArchivedUndoInventory.Position(7,5,16,1);
        var a=new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(10,1,1),s.source().containerUid(),s.source().containerId(),151,scn,false);
        var b=new TransactionLifecycle.ClosedIdentity(new HeapRowPatch.Xid(10,2,1),s.source().containerUid(),s.source().containerId(),152,scn,false);
        var commits=List.of(new ArchivedDictionaryHistory.Commit(a,List.of(),0,0,0,renameColumn(s,"FIRST"),first),
                new ArchivedDictionaryHistory.Commit(b,List.of(),0,0,0,renameColumn(s,"SECOND"),second));
        var constructor=ArchivedDictionaryHistory.Timeline.class.getDeclaredConstructor(FrozenHeapBaseline.Snapshot.class,List.class,List.class);
        constructor.setAccessible(true);var history=constructor.newInstance(s,f.history().sources(),commits);
        assertEquals(s.columns(),history.schemaAt(scn,new ArchivedUndoInventory.Position(7,3,16,1)));
        assertEquals("FIRST",history.schemaAt(scn,first).get(1).name());
        assertEquals("FIRST",history.schemaAt(scn,new ArchivedUndoInventory.Position(7,4,32,1)).get(1).name());
        assertEquals("SECOND",history.schemaAt(scn,second).get(1).name());
        assertEquals("SECOND",history.schemaAt(scn).get(1).name());
        assertNotEquals(history.schemaAt(scn,first),history.schemaAt(scn,second));
    }
}
