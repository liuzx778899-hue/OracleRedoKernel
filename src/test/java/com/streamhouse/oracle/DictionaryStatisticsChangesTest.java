package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual 81:25/26/27 vectors; role metadata here is a test fixture, not historical acquisition authority. */
class DictionaryStatisticsChangesTest {
    @TempDir Path directory;
    byte[] hex(String v){return HexFormat.of().parseHex(v);}
    final String section="DICT_STATS_BEGIN|1\nDICT_STATS_TABLE|BEFORE|SYS|HIST_HEAD$|68|68\nDICT_STATS_COLUMN|1|1|OBJ#|2|1\nDICT_STATS_COLUMN|2|2|COL#|2|1\nDICT_STATS_COLUMN|3|3|BUCKET_CNT|2|1\nDICT_STATS_COLUMN|4|4|ROW_CNT|2|1\nDICT_STATS_COLUMN|5|5|CACHE_CNT|2|0\nDICT_STATS_COLUMN|6|6|NULL_CNT|2|0\nDICT_STATS_COLUMN|7|7|TIMESTAMP#|12|0\nDICT_STATS_COLUMN|8|8|SAMPLE_SIZE|2|0\nDICT_STATS_COLUMN|9|9|MINIMUM|2|0\nDICT_STATS_COLUMN|10|10|MAXIMUM|2|0\nDICT_STATS_COLUMN|11|11|DISTCNT|2|0\nDICT_STATS_COLUMN|12|12|LOWVAL|23|0\nDICT_STATS_COLUMN|13|13|HIVAL|23|0\nDICT_STATS_COLUMN|14|14|DENSITY|2|0\nDICT_STATS_COLUMN|15|15|INTCOL#|2|1\nDICT_STATS_COLUMN|16|16|SPARE1|2|0\nDICT_STATS_COLUMN|17|17|SPARE2|2|0\nDICT_STATS_COLUMN|18|18|AVGCLN|2|0\nDICT_STATS_COLUMN|19|19|SPARE3|2|0\nDICT_STATS_COLUMN|20|20|SPARE4|2|0\nDICT_STATS_COLUMN|21|21|MINIMUM_ENC|23|0\nDICT_STATS_COLUMN|22|22|MAXIMUM_ENC|23|0\nDICT_STATS_INDEX|BEFORE|I_HH_OBJ#_COL#|69|69|4195224\nDICT_STATS_INDEX|BEFORE|I_HH_OBJ#_INTCOL#|70|70|4195232\nDICT_STATS_END|22\n";
    final String after="DICT_STATS_TABLE|AFTER|SYS|HIST_HEAD$|68|68\nDICT_STATS_INDEX|AFTER|I_HH_OBJ#_COL#|69|69|4195224\nDICT_STATS_INDEX|AFTER|I_HH_OBJ#_INTCOL#|70|70|4195232\n";
    FrozenHeapBaseline.Snapshot snapshot()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;
        return helper.read(helper.v3().replace("DICT_HISTORY_END|6",section+"DICT_HISTORY_END|6").replace("SOURCE_AFTER|",after+"SOURCE_AFTER|"));
    }
    RedoRecords.Record row(){return new RedoRecords.Record(25,340,512,1,2900179,1,677737149,List.of(new RedoRecords.Vector(5,1,36,1,37749505,2900179,1,3,new ArrayList<>(List.of(hex("3c005811220000000a001a00c20d000065012494"),hex("440000004400000000000000000000000b011a230000c20d"),hex("030d000000000000"),hex("358b400068024000ff120301020000000a000094")))),new RedoRecords.Vector(11,2,1,1,4229941,2900179,1,3,new ArrayList<>(List.of(hex("010d0000000000000a001a00c20d00000103400265012400"),hex("358b400068024000ff120201020000002c021200ff7f00000070d55100000000000000000000000030000a0000801b00040000"),hex("c3083456"),hex("c104"),hex("80"),hex("80"),hex("80"),hex("c102"),hex("787e0a08083412"),hex(""),hex(""),hex(""),hex("80"),hex(""),hex(""),hex("80"),hex("c105"),hex("80"),hex("c22963"),hex("80"))))));}
    RedoRecords.Record index(int at){return at==0?new RedoRecords.Record(26,192,512,1,2900179,1,677737149,List.of(new RedoRecords.Vector(5,1,36,1,37749505,2900179,1,3,new ArrayList<>(List.of(hex("68001a11220000000a001a00c20d000065012500"),hex("450000004500000000000000000000000a161a240000c20d"),hex("040d0000000000000a000300e3090000120140021d015000008000801cb72700"),hex("030201009803400029874000c473000000005f94"),hex("04c308345602c1040600408b35000a")))),new RedoRecords.Vector(10,2,1,1,4228905,2900179,1,3,new ArrayList<>(List.of(hex("110d2415000000000a001a00c20d000001034002650125000000000088710f92000000006f0000000000000002010d94d3402c0000800000020600801cb72700000000000000"),hex("020074001300"),hex("04c308345602c1040600408b35000a")))))):new RedoRecords.Record(27,16,512,1,2900179,1,677737149,List.of(new RedoRecords.Vector(5,1,36,1,37749505,2900179,1,3,new ArrayList<>(List.of(hex("6800b010220000000a001a00c20d000065012600"),hex("460000004600000000000000000000000a161a250000c20d"),hex("040d0000000000000a000300e3090000120140021d015100008000801cb72700"),hex("03020100a0034000aa864000c473000000005f94"),hex("04c308345602c1050600408b35000a")))),new RedoRecords.Vector(10,2,1,1,4228778,2900179,1,3,new ArrayList<>(List.of(hex("110d2415000000000a001a00c20d000001034002650126000000000088710f92000000006f0000000000000002010d94d3402c0000800000020600801cb72700000000000000"),hex("020074001300"),hex("04c308345602c1050600408b35000a"))))));}
    DictionaryStatisticsChanges.Row bind(FrozenHeapBaseline.Snapshot s,RedoRecords.Record r)throws Exception {
        return DictionaryStatisticsChanges.bind(r,r.vectors().get(1),s.dictionaryBaseline().statistics());
    }
    List<DictionaryIndexUndoPair> indexes(FrozenHeapBaseline.Snapshot s)throws Exception {
        var result=new ArrayList<DictionaryIndexUndoPair>();
        for(int i=0;i<2;i++) {var scope=s.dictionaryBaseline().statistics().indexes().get(i);var r=index(i);
            result.add(DictionaryIndexUndoPair.bind(r,r.vectors().get(1),scope.role(),scope.indexDba(),"AL32UTF8"));}
        return result;
    }
    List<DictionaryBaseline.Row> columns(FrozenHeapBaseline.Snapshot s)throws Exception {
        var metadata=s.dictionaryBaseline();var original=metadata.rows().stream().filter(r->r.role().equals("COL$") && r.key().slot()==15).findFirst().orElseThrow();
        var values=new ArrayList<>(original.values());values.set(1,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c104"));
        values.set(14,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c105"));
        return List.of(new DictionaryBaseline.Row(original.role(),original.target(),original.key(),values));
    }
    @Test void optionalMetadataAndBeforeAfterGenerationAreBound()throws Exception {
        var s=snapshot();assertEquals(22,s.dictionaryBaseline().statistics().table().columns().size());
        var helper=new DictionaryBaselineTest();helper.directory=directory;
        assertNull(helper.read(helper.v3()).dictionaryBaseline().statistics());
        String text=helper.v3().replace("DICT_HISTORY_END|6",section+"DICT_HISTORY_END|6").replace("SOURCE_AFTER|",after.replace("|70|70|","|70|71|")+"SOURCE_AFTER|");
        assertThrows(RedoException.class,()->helper.read(text));
    }
    @Test void actualStatisticsInsertAndBothInlineIndexesBelongToOneColumn()throws Exception {
        var s=snapshot();var r=bind(s,row());assertEquals(75185,r.object());assertEquals(3,r.column());assertEquals(4,r.internal());
        assertEquals(new HeapRowPatch.Xid(10,26,3522),r.physical().undo().identity().xid());
        assertEquals(HeapRowPatch.State.ABSENT,r.physical().forward().columns().get(21).state());
        DictionaryStatisticsChanges.requireComplete(List.of(r),indexes(s),s.dictionaryBaseline().statistics(),75185,columns(s),s.dictionaryBaseline());
    }
    @Test void missingIndexAndWrongColumnNeverDisappearFromCoverage()throws Exception {
        var s=snapshot();var r=bind(s,row());var indexes=indexes(s);
        assertThrows(RedoException.class,()->DictionaryStatisticsChanges.requireComplete(List.of(r),indexes.subList(0,1),s.dictionaryBaseline().statistics(),75185,columns(s),s.dictionaryBaseline()));
        assertThrows(RedoException.class,()->DictionaryStatisticsChanges.requireComplete(List.of(r),indexes,s.dictionaryBaseline().statistics(),75185,s.dictionaryBaseline().rows(),s.dictionaryBaseline()));
    }
    @Test void generationAndRequiredTrailingValueRefuse()throws Exception {
        var s=snapshot();var r=row();r.vectors().get(0).fields().get(1)[4]^=1;
        assertThrows(RedoException.class,()->bind(s,r));
        var baseline=s.dictionaryBaseline().statistics();var cols=new ArrayList<>(baseline.table().columns());var c=cols.get(21);
        cols.set(21,new DictionaryBaseline.Column(c.segment(),c.internal(),c.name(),c.type(),true));
        var changed=new DictionaryStatisticsBaseline(new DictionaryBaseline.Layout(baseline.table().role(),-1,cols),baseline.indexes());
        var original=row();assertThrows(RedoException.class,()->DictionaryStatisticsChanges.bind(original,original.vectors().get(1),changed));
    }
}
