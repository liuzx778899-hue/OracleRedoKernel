package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Actual 83:38/39 DELETE pair. Metadata is a test fixture, never late historical authority. */
class DictionaryUsageChangesTest {
    @TempDir Path directory;
    byte[] hex(String value){return HexFormat.of().parseHex(value);}
    String section() {
        var s=new StringBuilder("DICT_USAGE_BEGIN|1\nDICT_USAGE_TABLE|BEFORE|SYS|COL_USAGE$|669|669\n");
        for(int i=0;i<DictionaryUsageBaseline.NAMES.size();i++)s.append("DICT_USAGE_COLUMN|").append(i+1).append('|').append(i+1).append('|')
                .append(DictionaryUsageBaseline.NAMES.get(i)).append('|').append(i==8?12:2).append("|0\n");
        return s+"DICT_USAGE_INDEX|BEFORE|I_COL_USAGE$|670|670|4214064\nDICT_USAGE_END|10\n";
    }
    DictionaryUsageBaseline baseline()throws Exception {
        return DictionaryUsageBaseline.read(new FrozenHeapBaseline.Input(section()),Map.of(),List.of(),null);
    }
    RedoRecords.Record record0(){return new RedoRecords.Record(38,16,512,1,2910694,1,677737149,List.of(new RedoRecords.Vector(5,2,35,1,37749008,2910694,1,3,new ArrayList<>(List.of(hex("1c000000e10d00001701400266011c005200e800000000000000000000000000"),hex("bd726528")))),new RedoRecords.Vector(5,1,36,1,37749015,2910694,1,3,new ArrayList<>(List.of(hex("e800e014120000000a001c00e10d000066011c00"),hex("9d0200009d02000000000000000000000b011c00080c01000000000017014002660114002a692c000000000031692c000000000000000000ffffffffffffffff680340020000000000000000"),hex("040d0000000000000a000400c40d00000603400265012f000080008094412c00"),hex("224d4000104d4000ff120201010000002c000a2ab4720000d0a6152ab4720000000000000000000023001d00000000000000"),hex("c3084d4a"),hex("c105"),hex("80"),hex("80"),hex("80"),hex("80"),hex("80"),hex("c102"),hex("787e0a080b0e3a"),hex("c121")))),new RedoRecords.Vector(11,3,1,1,4214050,2910694,1,3,new ArrayList<>(List.of(hex("110d0000000000000a001c00e10d00001701400266011c00000000000000000000000000000000000000000002010d00e6692c000080000002060080e4692c00000000000000"),hex("224d4000104d4000ff120301010000001d000000"))))));}
    RedoRecords.Record record1(){return new RedoRecords.Record(39,172,512,1,2910694,1,677737149,List.of(new RedoRecords.Vector(5,1,36,1,37749015,2910694,1,3,new ArrayList<>(List.of(hex("6800f613220000000a001c00e10d000066011dee"),hex("9e0200009e02000000000000000000000a161c1c0000e10d"),hex("040d452a010000000a001500df0d00001701400266011b0000800080e4692c00"),hex("05020100304d4000a48340000100000000000000"),hex("04c3084d4a02c105"),hex("00404d22001d")))),new RedoRecords.Vector(10,4,1,1,4228004,2910694,1,3,new ArrayList<>(List.of(hex("010d000001029d020a001c00e10d00001701400266011d00"),hex("020075011200"))))));}

    DictionaryUsageChanges.Row row(DictionaryUsageBaseline b)throws Exception {var r=record0();return DictionaryUsageChanges.bind(r,r.vectors().get(2),b);}
    DictionaryIndexUndoPair index(DictionaryUsageBaseline b)throws Exception {var r=record1();return DictionaryIndexUndoPair.bind(r,r.vectors().get(1),b.index().role(),b.index().indexDba(),"AL32UTF8");}
    DictionaryBaseline metadata() {
        var role=new FrozenHeapBaseline.ObjectRole("SYS","COL$",21,2);
        return new DictionaryBaseline(Map.of("COL$",new DictionaryBaseline.Layout(role,5,List.of(
                new DictionaryBaseline.Column(1,1,"OBJ#",2,true),new DictionaryBaseline.Column(2,2,"INTCOL#",2,true)))),List.of(),List.of());
    }
    List<DictionaryBaseline.Row> before() {return List.of(new DictionaryBaseline.Row("COL$",77673,new ArchivedHeapRows.RowKey(2,4210000,1),List.of(
            new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c3084d4a"),new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c105"))));}
    @Test void actualDeleteUsesCompleteUndoImageAndSeparateIndexRowid()throws Exception {
        var b=baseline();var row=row(b);var index=index(b);
        assertEquals(77673,row.object());assertEquals(4,row.internal());assertEquals(HeapRowPatch.Kind.DELETE,row.physical().forward().kind());
        assertEquals(new HeapRowPatch.Xid(10,28,3553),row.physical().undo().identity().xid());
        assertEquals(4214050,index.key().rowDba());assertEquals(29,index.key().rowSlot());
        DictionaryUsageChanges.requireComplete(List.of(row),List.of(index),b,77673,before(),metadata());
    }
    @Test void missingIndexWrongParentOrMissingPriorColumnRefuses()throws Exception {
        var b=baseline();var row=row(b);var index=index(b);
        assertThrows(RedoException.class,()->DictionaryUsageChanges.requireComplete(List.of(row),List.of(),b,77673,before(),metadata()));
        assertThrows(RedoException.class,()->DictionaryUsageChanges.requireComplete(List.of(row),List.of(index),b,77674,before(),metadata()));
        assertThrows(RedoException.class,()->DictionaryUsageChanges.requireComplete(List.of(row),List.of(index),b,77673,List.of(),metadata()));
        assertThrows(RedoException.class,()->DictionaryUsageChanges.requireComplete(List.of(),List.of(index),b,77673,before(),metadata()));
    }
    @Test void generationAndBeforeAfterMustAgree()throws Exception {
        var b=baseline();var r=record0();r.vectors().get(1).fields().get(1)[4]^=1;
        assertThrows(RedoException.class,()->DictionaryUsageChanges.bind(r,r.vectors().get(2),b));
        b.requireAfter(new FrozenHeapBaseline.Input("DICT_USAGE_TABLE|AFTER|SYS|COL_USAGE$|669|669\nDICT_USAGE_INDEX|AFTER|I_COL_USAGE$|670|670|4214064\n"));
        assertThrows(RedoException.class,()->b.requireAfter(new FrozenHeapBaseline.Input("DICT_USAGE_TABLE|AFTER|SYS|COL_USAGE$|669|671\n")));
        assertThrows(RedoException.class,()->DictionaryUsageBaseline.read(new FrozenHeapBaseline.Input(section().replace("|INTCOL#|2|0","|COL#|2|0")),Map.of(),List.of(),null));
    }
    @Test void optionalSectionIsBoundIntoTheSnapshot()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;
        String after="DICT_USAGE_TABLE|AFTER|SYS|COL_USAGE$|669|669\nDICT_USAGE_INDEX|AFTER|I_COL_USAGE$|670|670|4214064\n";
        var snapshot=helper.read(helper.v3().replace("DICT_HISTORY_END|6",section()+"DICT_HISTORY_END|6").replace("SOURCE_AFTER|",after+"SOURCE_AFTER|"));
        assertEquals(669,snapshot.dictionaryBaseline().usage().table().role().logicalObject());
        assertNull(helper.read(helper.v3()).dictionaryBaseline().usage());
    }
}
