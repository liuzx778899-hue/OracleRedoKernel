package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryGenerationClusterPairTest {
    @Test void raw119SegmentAndPrimaryIndexKeepPairedDataObjects()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-generation-cluster-raw119.txt");
        var segment=ClusterUndoPair.bind(records.get(0),records.get(0).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","SEG$",14,8),2,16);
        assertEquals(80994,OracleScalars.number(segment.inverse().row().columns().get(14).bytes()).longValueExact());
        assertEquals(81009,OracleScalars.number(segment.forward().row().columns().get(14).bytes()).longValueExact());
        var index=ClusterUndoPair.bind(records.get(1),records.get(1).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","IND$",19,2),3,36);
        assertEquals(80999,OracleScalars.number(index.inverse().row().columns().get(1).bytes()).longValueExact());
        assertEquals(81006,OracleScalars.number(index.forward().row().columns().get(1).bytes()).longValueExact());
        assertEquals(segment.identity().xid(),index.identity().xid());
    }
    @Test void wrongTableOwnerGenerationOrLayoutCannotBecomeGenerationMetadata()throws Exception {
        var records=new DictionaryObjectUndoPairTest().records("/dictionary-generation-cluster-raw119.txt");var r=records.get(0);var row=r.vectors().get(1);
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","SEG$",14,8),3,16));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","SEG$",14,9),2,16));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("ORKLAB","SEG$",14,8),2,16));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","SEG$",14,8),2,17));
        row.fields().get(0)[12]^=1;
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","SEG$",14,8),2,16));
    }
}
