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
