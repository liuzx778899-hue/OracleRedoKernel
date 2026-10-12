package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryLobClusterInsertTest {
    @Test void raw120AllocatedTableUpdatesRetainTheirCompleteInverseAndBitmap()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var records=helper.records("/dictionary-table-allocation-raw120.txt");assertEquals(3,records.size());
        var role=new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2);
        for(var record:records)ClusterUndoPair.bind(record,record.vectors().get(1),role,1,44);
        var bad=helper.records("/dictionary-table-allocation-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[26]^=1;
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(bad,bad.vectors().get(1),role,1,44));
        for(int at:new int[]{23,26,29}) {
            var wrong=helper.records("/dictionary-table-allocation-raw120.txt").get(1);wrong.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->ClusterUndoPair.bind(wrong,wrong.vectors().get(1),role,1,44));
        }
    }
    @Test void raw120SnapshotRegistrationRetainsAllFourForwardAndInverseCells()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var role=new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2);
        var record=helper.records("/dictionary-table-registration-raw120.txt").get(0);
        var pair=ClusterUndoPair.bind(record,record.vectors().get(1),role,1,44);
        assertEquals(4,pair.forward().row().extent().changedColumns());
        for(int column:new int[]{4,40,41})assertEquals(HeapRowPatch.State.NULL,pair.forward().row().columns().get(column).state());
        assertEquals(HeapRowPatch.State.VALUE,pair.inverse().row().columns().get(40).state());
        for(int at:new int[]{23,26}) {
            var bad=helper.records("/dictionary-table-registration-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->ClusterUndoPair.bind(bad,bad.vectors().get(1),role,1,44));
        }
    }
    @Test void raw120LobAndTableInsertsRetainNullableFieldsAndClusterKeyZero()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-lob-cluster-insert-raw120.txt");
        var lob=ClusterUndoPair.bind(records.get(0),records.get(0).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","LOB$",109,2),6,14);
        assertEquals(HeapRowPatch.Kind.INSERT,lob.forward().row().kind());assertEquals(HeapRowPatch.Kind.DELETE,lob.inverse().row().kind());
        assertEquals(3,OracleScalars.number(lob.forward().row().columns().get(0).bytes()).longValueExact());
        assertEquals(81025,OracleScalars.number(lob.forward().row().columns().get(2).bytes()).longValueExact());
        assertEquals(81026,OracleScalars.number(lob.forward().row().columns().get(4).bytes()).longValueExact());
        var table=ClusterUndoPair.bind(records.get(1),records.get(1).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2),1,44);
        assertEquals(0,table.forward().clusterKeyIndex());assertEquals(44,table.forward().row().columns().size());
        assertEquals(HeapRowPatch.State.NULL,table.forward().row().columns().get(4).state());
        assertEquals(81029,OracleScalars.number(table.forward().row().columns().get(0).bytes()).longValueExact());
    }
    @Test void wrongTableLayoutNullBitmapOrOwnerRefuses()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();
        var role=new FrozenHeapBaseline.ObjectRole("SYS","LOB$",109,2);
        var record=helper.records("/dictionary-lob-cluster-insert-raw120.txt").get(0);var row=record.vectors().get(1);
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record,row,role,5,14));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record,row,role,6,15));
        for(int at:new int[]{18,44,45}) {
            var bad=helper.records("/dictionary-lob-cluster-insert-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->ClusterUndoPair.bind(bad,bad.vectors().get(1),role,6,14));
        }
        row.fields().get(0)[12]^=1;assertThrows(RedoException.class,()->ClusterUndoPair.bind(record,row,role,6,14));
    }
    @Test void raw120TableCleanoutCInsertUsesItsCurrentUbaWithoutInventingXid()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var record=helper.records("/dictionary-cluster-cleanout-insert-raw120.txt").get(0);
        var role=new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2);
        var pair=ClusterUndoPair.bind(record,record.vectors().get(1),role,1,44);
        assertNull(pair.forward().row().xid());assertEquals(pair.identity().uba(),pair.forward().row().uba());
        assertEquals(new HeapRowPatch.Xid(10,18,5250),pair.identity().xid());
        for(int at:new int[]{8,44,45,46}) {
            var bad=helper.records("/dictionary-cluster-cleanout-insert-raw120.txt").get(0);bad.vectors().get(1).fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->ClusterUndoPair.bind(bad,bad.vectors().get(1),role,1,44));
        }
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record,record.vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","COL$",21,2),5,36));
    }
    @Test void raw120TableRedefinitionBitmapMatchesItsFiveChangedCells()throws Exception {
        var helper=new DictionaryObjectUndoPairTest();var record=helper.records("/dictionary-table-redefinition-raw120.txt").get(0);
        var role=new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2);
        var pair=ClusterUndoPair.bind(record,record.vectors().get(1),role,1,44);
        assertEquals(5,pair.forward().row().extent().changedColumns());
        for(int column:new int[]{4,40,41})assertEquals(HeapRowPatch.State.NULL,pair.forward().row().columns().get(column).state());
        for(int at:new int[]{23,26}) {
            var bad=helper.records("/dictionary-table-redefinition-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->ClusterUndoPair.bind(bad,bad.vectors().get(1),role,1,44));
        }
    }
}
