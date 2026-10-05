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
}
