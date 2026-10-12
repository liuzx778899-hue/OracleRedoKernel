package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryObjectRenameTest {
    private List<RedoRecords.Record> records()throws Exception {
        return new DictionaryObjectUndoPairTest().records("/dictionary-object-rename-raw120.txt");
    }
    private RedoRecords.Record indexed(RedoRecords.Record record,long object) {
        var v=record.vectors().get(1);
        var row=new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),1,v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),v.fields(),object);
        return new RedoRecords.Record(record.block(),record.offset(),record.length(),record.validity(),record.scn(),record.subScn(),record.containerUid(),List.of(record.vectors().get(0),row));
    }
    @Test void sixRawNameChangesMoveEveryObjectIndexToItsNewRow()throws Exception {
        var records=records();assertEquals(60,records.size());
        var role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
        var operations=new ArrayList<ArchivedDictionaryHistory.RowOperation>();var allIndexes=new ArrayList<DictionaryObjectIndexPair.Pair>();var allDataIndexes=new ArrayList<DictionaryDataObjectIndexPair>();
        for(int group=0;group<6;group++) {
            var deleted=records.get(group*10);var inserted=records.get(group*10+5);
            var before=DictionaryObjectUndoPair.bind(deleted,deleted.vectors().get(1),role,37).physical();
            var after=DictionaryObjectUndoPair.bind(inserted,inserted.vectors().get(1),role,37).physical();
            assertEquals(HeapRowPatch.Kind.DELETE,before.forward().kind());assertEquals(HeapRowPatch.Kind.INSERT,after.forward().kind());
            assertEquals(before.undo().identity().xid(),after.undo().identity().xid());
            operations.add(new ArchivedDictionaryHistory.RowOperation(role,before.forward(),before.undo().inverse(),before.undo().identity()));
            operations.add(new ArchivedDictionaryHistory.RowOperation(role,after.forward(),after.undo().inverse(),after.undo().identity()));
            var oldIndexes=new ArrayList<DictionaryObjectIndexPair.Pair>();var newIndexes=new ArrayList<DictionaryObjectIndexPair.Pair>();
            for(int i:new int[]{1,2,4}) {
                long logical=i==1?36:i==2?37:40;
                var old=indexed(records.get(group*10+i),logical);var next=indexed(records.get(group*10+5+i),logical);
                var oldPair=DictionaryObjectIndexPair.bind(old,old.vectors().get(1),logical);
                var nextPair=DictionaryObjectIndexPair.bind(next,next.vectors().get(1),logical);
                oldIndexes.add(oldPair);newIndexes.add(nextPair);
                DictionaryObjectIndexPair.requireObject(oldPair,before.undo().inverse(),before.undo().identity());
                DictionaryObjectIndexPair.requireObject(nextPair,after.forward(),after.undo().identity());
                if(logical==36)DictionaryObjectIndexPair.requireRelocation(nextPair,before.undo().inverse(),after.forward(),after.undo().identity());
            }
            var dataIndexes=new ArrayList<DictionaryDataObjectIndexPair>();
            for(int at:new int[]{group*10+3,group*10+8}) {
                var record=indexed(records.get(at),39);var index=DictionaryDataObjectIndexPair.bind(record,record.vectors().get(1));
                dataIndexes.add(index);
                var row=at==group*10+3?before.undo().inverse():after.forward();
                assertEquals(row.dba(),index.rowDba());assertEquals(row.slot(),index.rowSlot());
                assertEquals(OracleScalars.number(row.columns().get(1).bytes()).longValueExact(),index.dataObject());
            }
            DictionaryObjectRebind.requireRows(before.undo().inverse(),after.forward());
            allIndexes.addAll(oldIndexes);allIndexes.addAll(newIndexes);allDataIndexes.addAll(dataIndexes);
            if(group==1) {
                assertEquals("T_D4_MIG_20261011",OracleScalars.varchar2(after.forward().columns().get(3).bytes(),"AL32UTF8"));
                assertEquals(81024,OracleScalars.number(after.forward().columns().get(0).bytes()).longValueExact());
            }
        }
        DictionaryObjectDependencies.require(operations,allIndexes,allDataIndexes,List.of(),List.of());
        assertThrows(RedoException.class,()->DictionaryObjectDependencies.require(operations,allIndexes.subList(1,allIndexes.size()),allDataIndexes,List.of(),List.of()));
        assertThrows(RedoException.class,()->DictionaryObjectDependencies.require(operations,List.of(),List.of(),List.of(),List.of()));
    }
    @Test void relocatedReferenceNeedsBothRowsAndKeepsOrdinaryIndexGate()throws Exception {
        var records=records();var role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
        var deleted=records.get(0);var inserted=records.get(5);var record=indexed(records.get(6),36);
        var before=DictionaryObjectUndoPair.bind(deleted,deleted.vectors().get(1),role,37).physical();
        var after=DictionaryObjectUndoPair.bind(inserted,inserted.vectors().get(1),role,37).physical();
        var pair=DictionaryObjectIndexPair.bind(record,record.vectors().get(1),36);
        assertThrows(RedoException.class,()->IndexUndoPair.bindDictionary(record,record.vectors().get(1)));
        record.vectors().get(0).fields().get(5)[5]^=1;
        var changed=DictionaryObjectIndexPair.bind(record,record.vectors().get(1),36);
        assertThrows(RedoException.class,()->DictionaryObjectIndexPair.requireRelocation(changed,before.undo().inverse(),after.forward(),after.undo().identity()));
        assertThrows(RedoException.class,()->DictionaryObjectIndexPair.requireRelocation(pair,after.forward(),after.forward(),after.undo().identity()));
    }
}
