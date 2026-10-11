package com.streamhouse.oracle;

import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryDataObjectIndexPairTest {
    @TempDir Path directory;
    List<RedoRecords.Record> records()throws Exception {
        return records("/dictionary-data-object-index-raw119.txt");
    }
    List<RedoRecords.Record> records(String resource)throws Exception {
        var result=new ArrayList<RedoRecords.Record>();
        for(var record:new DictionaryObjectUndoPairTest().records(resource)) {
            var old=record.vectors().get(1);
            var row=new RedoRecords.Vector(old.opcodeMajor(),old.opcodeMinor(),old.blockClass(),old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),old.fields(),39);
            result.add(new RedoRecords.Record(record.block(),record.offset(),record.length(),record.validity(),record.scn(),record.subScn(),record.containerUid(),List.of(record.vectors().get(0),row)));
        }
        return result;
    }
    @Test void raw120NullDataObjectDoesNotBecomeAZeroDataObject()throws Exception {
        var r=records("/dictionary-data-object-index-raw120-null.txt").get(0);
        var pair=DictionaryDataObjectIndexPair.bind(r,r.vectors().get(1));
        assertNull(pair.dataObject());assertEquals(2,pair.type());assertEquals(106,pair.spare3());assertEquals(40,pair.rowSlot());
        for(String key:List.of("ff", "ff03c202070600408e200028", "ff02c103ff0600408e200028", "ff02c10303c20207ff"))
            assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.validateKey(HexFormat.of().parseHex(key),new byte[0]));
    }
    @Test void raw119TruncateRemovesOldAndAddsNewDataObjectAtTheSameObjectRow()throws Exception {
        var records=records();var old=DictionaryDataObjectIndexPair.bind(records.get(0),records.get(0).vectors().get(1));
        var next=DictionaryDataObjectIndexPair.bind(records.get(1),records.get(1).vectors().get(1));
        assertEquals(IndexUndoPair.Kind.DELETE,old.physical().forward().kind());assertEquals(IndexUndoPair.Kind.INSERT,next.physical().forward().kind());
        assertEquals(80997,old.dataObject());assertEquals(81008,next.dataObject());assertEquals(21,old.type());assertEquals(106,old.spare3());
        assertEquals(old.rowDba(),next.rowDba());assertEquals(0x00408e20L,old.rowDba());assertEquals(19,old.rowSlot());assertEquals(old.rowSlot(),next.rowSlot());
        assertEquals(old.physical().undo().identity().xid(),next.physical().undo().identity().xid());
        for(var r:records)assertThrows(RedoException.class,()->IndexUndoPair.bind(r,r.vectors().get(1)));
    }
    @Test void generationNeedsTheOwnedObjectUpdateAndBothOldAndNewIndexKeys()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var r=helper.records("/dictionary-object-generation-raw119.txt").get(0);
        var object=DictionaryObjectUndoPair.bind(r,r.vectors().get(1),helper.role,37);
        var indexes=records();var before=DictionaryDataObjectIndexPair.bind(indexes.get(0),indexes.get(0).vectors().get(1));
        var after=DictionaryDataObjectIndexPair.bind(indexes.get(1),indexes.get(1).vectors().get(1));
        var target=new FrozenHeapBaseline.ObjectRole("ORKLAB","SYS_LOB0000080994C00004$$",80997,80997);
        var next=DictionaryDataObjectIndexPair.requireGeneration(object,target,21,106,before,after);
        assertEquals(80997,next.logicalObject());assertEquals(81008,next.dataObject());assertEquals(target.name(),next.name());
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(object,target,21,106,after,before));
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(object,target,1,106,before,after));
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(object,target,21,107,before,after));
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(object,new FrozenHeapBaseline.ObjectRole(target.owner(),target.name(),80995,80997),21,106,before,after));
        var wrong=new DictionaryDataObjectIndexPair(after.physical(),after.dataObject(),after.type(),after.spare3(),after.rowDba(),after.rowSlot()+1);
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(object,target,21,106,before,wrong));
        r.vectors().get(1).fields().get(3)[3]++;
        var changed=DictionaryObjectUndoPair.bind(r,r.vectors().get(1),helper.role,37);
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.requireGeneration(changed,target,21,106,before,after));
    }
    @Test void wrongOwnerGenerationRootKeyAndRowSizeRefuse()throws Exception {
        for(int field:new int[]{0,1,3,4}) {
            var r=records().get(1);var u=r.vectors().get(0);
            int at=field==0?12:field==1?4:field==3?4:4;u.fields().get(field)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.bind(r,r.vectors().get(1)));
        }
        var r=records().get(1);r.vectors().get(1).fields().get(1)[4]++;
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.bind(r,r.vectors().get(1)));
        for(String key:List.of("04c3090b0902c11603c20207", "04c3090b0902c11603c2020706000000000013", "04c3090b0902c11603c202070600408e200013ff"))
            assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.validateKey(HexFormat.of().parseHex(key),new byte[0]));
        assertThrows(RedoException.class,()->DictionaryDataObjectIndexPair.validateKey(HexFormat.of().parseHex("04c3090b0902c11603c202070600408e200013"),new byte[6]));
    }
    @Test void indexNeedsTheSameCompleteObjectRowAndEveryIndexedMetadataValue()throws Exception {
        var helper=new DictionaryBaselineTest();helper.directory=directory;var baseline=helper.read(helper.v3()).dictionaryBaseline();
        var r=records().get(0);var pair=DictionaryDataObjectIndexPair.bind(r,r.vectors().get(1));
        var original=baseline.rows().stream().filter(v->v.role().equals("OBJ$")).findFirst().orElseThrow();
        var values=new ArrayList<>(original.values());
        values.set(1,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c3090a62"));
        values.set(6,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c116"));
        values.set(17,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c20207"));
        var key=new ArchivedHeapRows.RowKey(18,0x00408e20L,19);var row=new DictionaryBaseline.Row("OBJ$",original.target(),key,values);
        pair.requireRow(baseline,row);
        for(int at:new int[]{1,6,17}) {
            var invalid=new ArrayList<>(values);invalid.set(at,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"80"));
            assertThrows(RedoException.class,()->pair.requireRow(baseline,new DictionaryBaseline.Row("OBJ$",original.target(),key,invalid)));
        }
        assertThrows(RedoException.class,()->pair.requireRow(baseline,new DictionaryBaseline.Row("OBJ$",original.target(),new ArchivedHeapRows.RowKey(19,key.dba(),key.slot()),values)));
        assertThrows(RedoException.class,()->pair.requireRow(baseline,new DictionaryBaseline.Row("OBJ$",original.target()+1,key,values)));
    }
}
