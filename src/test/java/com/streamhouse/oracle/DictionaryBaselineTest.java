package com.streamhouse.oracle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryBaselineTest {
    @TempDir Path directory;
    FrozenHeapBaseline.Snapshot read(String text)throws Exception {var helper=new FrozenHeapBaselineTest();helper.directory=directory;return helper.read(text);}
    String v3() throws Exception {
        try(var in=getClass().getResourceAsStream("/dictionary-baseline-lab-v3.txt")) {return new String(in.readAllBytes(),StandardCharsets.US_ASCII).replace("\r\n","\n");}
    }
    @Test void completeSysRowsPreservePhysicalInternalMappingWithoutHistoryAuthorization() throws Exception {
        var s=read(v3());var d=s.dictionaryBaseline();assertEquals(6,d.rows().size());assertEquals(3,d.indexes().size());
        assertEquals(230,d.rows().stream().mapToInt(r->r.values().size()).sum());
        var col=d.layouts().get("COL$");assertEquals(5,col.tableNumber());
        assertEquals("ACDRRESCOL#",col.columns().get(13).name());assertEquals(37,col.columns().get(13).internal());
        assertEquals("DEFAULT$",col.columns().get(36).name());assertEquals(14,col.columns().get(36).internal());
        assertThrows(UnsupportedOperationException.class,()->d.rows().clear());
        assertNull(read(new FrozenHeapBaselineTest().dictionarySample()).dictionaryBaseline());
    }
    @Test void completeBaselineDoesNotAuthorizeUnreplayedHistoricalSysWrite()throws Exception {
        var s=read(v3());byte[] metadata=new byte[24],kdo=new byte[29];
        ArchivedRedoTest.put(metadata,0,21,4);ArchivedRedoTest.put(metadata,4,2,4);
        ArchivedRedoTest.put(kdo,0,4229540,4);kdo[10]=5;ArchivedRedoTest.put(kdo,20,13,2);
        var vector=new RedoRecords.Vector(5,1,0,0,0,0,0,3,java.util.List.of(new byte[20],metadata,new byte[0],kdo));
        var record=new RedoRecords.Record(1,16,100,1,s.scn()+1,0,677737149,java.util.List.of(vector));
        assertEquals("TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522820:object=75185",
                assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record,s)).getMessage());
    }
    @Test void evenTrustedMalformedLayoutIndexAndMissingFullRowsRefuse() throws Exception {
        String v=v3();String row=v.lines().filter(l->l.startsWith("DICT_ROW|COL$")).findFirst().orElseThrow()+"\n";
        for(String invalid:new String[]{v.replace("DICT_LAYOUT_COLUMN|COL$|14|37|","DICT_LAYOUT_COLUMN|COL$|14|14|"),
                v.replace("DICT_LAYOUT|COL$|5|37","DICT_LAYOUT|COL$|1|37"),v.replace(row,""),v.replace(row,row+row),
                v.replace("DICT_INDEX|BEFORE|I_COL2|49|49|4194744","DICT_INDEX|BEFORE|I_COL2|48|49|4194744"),
                v.replace("DICT_INDEX|AFTER|I_COL3|50|50|4194752","DICT_INDEX|AFTER|I_COL3|50|51|4194752"),
                v.replace("DICT_HISTORY_END|6","DICT_HISTORY_END|5"),v.replace("DICT_LAYOUT_COLUMN|OBJ$|8|8|CTIME|12|1","DICT_LAYOUT_COLUMN|OBJ$|8|8|CTIME|8|1")})
            {assertNotEquals(v,invalid,"mutation must alter fixture");assertThrows(RedoException.class,()->read(invalid),invalid);}
    }
    @Test void rowValuesCannotContradictHeapOrColumnMetadataEvenWithNewTrustedDigest() throws Exception {
        String v=v3();
        for(String invalid:new String[]{v.replace("|545F56325F484953545F3230323631303036|","|585F56325F484953545F3230323631303036|"),
                v.replace("|4944|C103|","|4945|C103|"),v.replace("DICT_ROW|COL$|75185|2|1|35236|13|C3083456","DICT_ROW|COL$|75185|2|1|35236|13|C3083457")})
            assertThrows(RedoException.class,()->read(invalid));
    }
}
