package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryRedefinitionAuxiliaryTest {
    @Test void raw120StandaloneConstraintsRequireBothIndexesWithoutInventingAnExchange()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-constraint-drop-raw120.txt");assertEquals(6,records.size());
        for(var owner:List.of(new HeapRowPatch.Xid(7,31,726),new HeapRowPatch.Xid(6,10,715))) {
            var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
            for(var record:records) {
                var h=UndoHeader.decode(record.vectors().get(0));if(!h.xid().equals(owner))continue;
                if(h.objectId()==28)rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),28));
                else indexes.add(index(record,h.objectId()));
            }
            assertEquals(1,rows.size());assertEquals(2,indexes.size());DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,List.of(indexes.get(0))));
        }
    }
    @Test void raw120SegmentFileUpdateRetainsItsOwnedBeforeImage()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var record=helper.records("/dictionary-segment-update-raw120.txt").get(0);
        var pair=DictionaryRedefinitionAuxiliary.bindCluster(record,record.vectors().get(1),14).physical();
        assertEquals(1,pair.forward().row().extent().changedColumns());
        assertNotEquals(OracleScalars.number(pair.inverse().row().columns().get(0).bytes()),OracleScalars.number(pair.forward().row().columns().get(0).bytes()));
        var bad=helper.records("/dictionary-segment-update-raw120.txt").get(0);bad.vectors().get(1).fields().get(2)[0]=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(bad,bad.vectors().get(1),14));
    }
    @Test void raw120AuthorizationRowsRequireBothIndexesIncludingNullableColumnKey()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-authorization-indices-raw120.txt");assertEquals(6,records.size());
        for(var owner:List.of(new HeapRowPatch.Xid(2,16,715),new HeapRowPatch.Xid(10,33,5249))) {
            var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
            for(var record:records) {
                var h=UndoHeader.decode(record.vectors().get(0));if(!h.xid().equals(owner))continue;
                if(h.objectId()==61)rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),61));
                else indexes.add(index(record,h.objectId()));
            }
            assertEquals(1,rows.size());assertEquals(2,indexes.size());
            DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
            for(int i=0;i<indexes.size();i++) {
                var missing=new ArrayList<>(indexes);missing.remove(i);
                assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,missing));
            }
        }
    }
    private List<RedoRecords.Record> records()throws Exception {
        return new DictionaryObjectUndoPairTest().records("/dictionary-redefinition-aux-raw120.txt");
    }
    private DictionaryRedefinitionAuxiliary.Index index(RedoRecords.Record record,long logical)throws Exception {
        var old=record.vectors().get(1);var vector=new RedoRecords.Vector(old.opcodeMajor(),old.opcodeMinor(),1,old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),old.fields(),logical);
        var owned=new RedoRecords.Record(record.block(),record.offset(),record.length(),record.validity(),record.scn(),record.subScn(),record.containerUid(),List.of(record.vectors().get(0),vector));
        return DictionaryRedefinitionAuxiliary.bindIndex(owned,vector,logical);
    }
    @Test void raw120AuxiliaryValuesAndTheirIndexesStayWithinEachOwner()throws Exception {
        var records=records();var owners=new HashSet<HeapRowPatch.Xid>();int parsed=0;
        for(var record:records)owners.add(UndoHeader.decode(record.vectors().get(0)).xid());
        for(var owner:owners) {
            var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
            for(var record:records) {
                var h=UndoHeader.decode(record.vectors().get(0));if(!h.xid().equals(owner))continue;
                if(DictionaryRedefinitionAuxiliary.table(h.objectId()))rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),h.objectId()));
                else indexes.add(index(record,h.objectId()));parsed++;
            }
            DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes.subList(1,indexes.size())));
        }
        assertEquals(20,parsed);
    }
    @Test void corruptScalarBitmapPhysicalOwnerAndRowReferenceRefuse()throws Exception {
        var records=records();var comment=records.get(0);
        assertEquals(256,UndoHeader.decode(comment.vectors().get(0)).objectId());
        for(int at:new int[]{40,45}) {
            var bad=records().get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bind(bad,bad.vectors().get(1),256));
        }
        var row=DictionaryRedefinitionAuxiliary.bind(comment,comment.vectors().get(1),256);
        var wrongIndex=records.get(1);wrongIndex.vectors().get(1).fields().get(3)[5]^=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(row),List.of(index(wrongIndex,258))));
        var owner=records().get(0);owner.vectors().get(1).fields().get(0)[12]^=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bind(owner,owner.vectors().get(1),256));
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.validateKey(868,new byte[]{1,0},new byte[6]));
    }
    @Test void raw120MlogClusterImagesKeepThirteenAndNineteenStoredColumns()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var records=helper.records("/dictionary-redefinition-mlog-raw120.txt");assertEquals(2,records.size());
        var inserted=DictionaryRedefinitionAuxiliary.bindCluster(records.get(0),records.get(0).vectors().get(1),871).physical();
        var deleted=DictionaryRedefinitionAuxiliary.bindCluster(records.get(1),records.get(1).vectors().get(1),871).physical();
        assertEquals(13,inserted.forward().row().extent().storedColumns());assertEquals(19,deleted.inverse().row().extent().storedColumns());
        assertEquals("MLOG$_T_D4_MIG_20261011",OracleScalars.varchar2(inserted.forward().row().columns().get(6).bytes(),"AL32UTF8"));
        for(int at:new int[]{18,44,45}) {
            var bad=helper.records("/dictionary-redefinition-mlog-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(bad,bad.vectors().get(1),871));
        }
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(records.get(0),records.get(0).vectors().get(1),875));
    }
    @Test void raw120FinisherSnapshotRowsRequireAllOwnedIndexReferences()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-redefinition-snapshot-raw120.txt");
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
        for(var record:records) {
            var logical=UndoHeader.decode(record.vectors().get(0)).objectId();
            if(DictionaryRedefinitionAuxiliary.table(logical))rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),logical));
            else indexes.add(index(record,logical));
        }
        assertEquals(35,records.size());assertEquals(19,rows.size());assertEquals(16,indexes.size());
        DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
        for(int i=0;i<indexes.size();i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,missing));
        }
    }
    @Test void raw120SnapshotCreationRequiresItsCompleteTypedRowsAndIndexes()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-snapshot-creation-raw120.txt");
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
        for(var record:records) {
            var logical=UndoHeader.decode(record.vectors().get(0)).objectId();
            if(DictionaryRedefinitionAuxiliary.table(logical))rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),logical));
            else indexes.add(index(record,logical));
        }
        assertEquals(29,records.size());assertTrue(rows.stream().anyMatch(row->row.logical()==875));
        DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
        for(int i=0;i<indexes.size();i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,missing));
        }
    }
    @Test void raw120OwnedInternalIndexDropsKeepTheirExactClusterReferences()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-redefinition-index-cluster-raw120.txt");
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.ClusterRow>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
        for(var record:records) {
            var logical=UndoHeader.decode(record.vectors().get(0)).objectId();
            if(DictionaryRedefinitionAuxiliary.cluster(logical))rows.add(DictionaryRedefinitionAuxiliary.bindCluster(record,record.vectors().get(1),logical));
            else indexes.add(index(record,logical));
        }
        assertEquals(2,rows.size());assertEquals(2,indexes.size());
        DictionaryRedefinitionAuxiliary.requireComplete(List.of(),indexes,rows);
        for(int i=0;i<indexes.size();i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(),missing,rows));
        }
    }
    @Test void raw120ConstraintExchangeRequiresBothNumbersAndEveryRelocation()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-constraint-exchange-raw120.txt");
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.Row>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
        for(var record:records) {
            var logical=UndoHeader.decode(record.vectors().get(0)).objectId();
            if(logical==28)rows.add(DictionaryRedefinitionAuxiliary.bind(record,record.vectors().get(1),logical));
            else indexes.add(index(record,logical));
        }
        assertEquals(8,rows.size());assertEquals(12,indexes.size());
        DictionaryRedefinitionAuxiliary.requireComplete(rows,indexes);
        for(int i=0;i<indexes.size();i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(rows,missing));
        }
        for(int i=0;i<rows.size();i++) {
            var missing=new ArrayList<>(rows);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(missing,indexes));
        }
    }
    @Test void raw120StatisticsHistoryRequiresItsTableParentAndBothTimestampIndexes()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-statistics-history-raw120.txt");
        assertEquals(3,records.size());
        var row=DictionaryRedefinitionAuxiliary.bind(records.get(0),records.get(0).vectors().get(1),677);
        var indexes=List.of(index(records.get(1),678),index(records.get(2),679));
        DictionaryRedefinitionAuxiliary.requireComplete(List.of(row),indexes,List.of(),Set.of(81024L));
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(row),indexes,List.of(),Set.of(81014L)));
        for(int i=0;i<2;i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(row),missing,List.of(),Set.of(81024L)));
        }
        var bad=new DictionaryObjectUndoPairTest().records("/dictionary-statistics-history-raw120.txt").get(1);
        bad.vectors().get(1).fields().get(2)[16]^=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(row),List.of(index(bad,678),indexes.get(1)),List.of(),Set.of(81024L)));
    }
    @Test void raw120LobAllocationKeepsTheLogicalLobIndexKey()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var records=helper.records("/dictionary-lob-allocation-raw120.txt");
        assertEquals(2,records.size());
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.ClusterRow>();
        for(var record:records)rows.add(DictionaryRedefinitionAuxiliary.bindCluster(record,record.vectors().get(1),109));
        DictionaryRedefinitionAuxiliary.requireComplete(List.of(),List.of(),rows);
        var map=helper.records("/dictionary-lob-allocation-raw120.txt").get(0);
        map.vectors().get(1).fields().get(2)[0]=3;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(map,map.vectors().get(1),109));
        var key=helper.records("/dictionary-lob-allocation-raw120.txt").get(0);
        key.vectors().get(1).fields().get(3)[3]^=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(key,key.vectors().get(1),109));
        var owner=helper.records("/dictionary-lob-allocation-raw120.txt").get(0);
        owner.vectors().get(1).fields().get(0)[12]^=1;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(owner,owner.vectors().get(1),109));
    }
    @Test void raw120ConstraintDefinitionChangesRetainExactColumnMaps()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var records=helper.records("/dictionary-constraint-definition-raw120.txt");
        assertEquals(4,records.size());
        for(var record:records)DictionaryRedefinitionAuxiliary.bindCluster(record,record.vectors().get(1),31);
        var bad=helper.records("/dictionary-constraint-definition-raw120.txt").get(0);
        bad.vectors().get(1).fields().get(2)[0]=7;
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.bindCluster(bad,bad.vectors().get(1),31));
    }
    @Test void raw120ShadowConstraintAndLobRowsRequireEveryIndexAndAKnownTableParent()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-redefinition-parent-cluster-raw120.txt");
        var rows=new ArrayList<DictionaryRedefinitionAuxiliary.ClusterRow>();var indexes=new ArrayList<DictionaryRedefinitionAuxiliary.Index>();
        for(var record:records) {
            var logical=UndoHeader.decode(record.vectors().get(0)).objectId();
            if(DictionaryRedefinitionAuxiliary.cluster(logical))rows.add(DictionaryRedefinitionAuxiliary.bindCluster(record,record.vectors().get(1),logical));
            else indexes.add(index(record,logical));
        }
        assertEquals(4,rows.size());assertEquals(9,indexes.size());
        DictionaryRedefinitionAuxiliary.requireComplete(List.of(),indexes,rows,Set.of(81024L));
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(),indexes,rows,Set.of()));
        assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(),indexes,rows,Set.of(81014L)));
        for(int i=0;i<indexes.size();i++) {
            var missing=new ArrayList<>(indexes);missing.remove(i);
            assertThrows(RedoException.class,()->DictionaryRedefinitionAuxiliary.requireComplete(List.of(),missing,rows,Set.of(81024L)));
        }
    }
}
