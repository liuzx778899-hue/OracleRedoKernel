package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.streamhouse.oracle.RedoBytes.*;

class DictionaryCreatedTableStateTest {
    @Test void frozenShadowTableKeepsItsColumnsSeparateFromTheOriginalAndRejectsMissingReferences()throws Exception {
        String path=System.getProperty("ork.lob.migration");assumeTrue(path!=null);var root=Path.of(path);
        var snapshot=FrozenHeapBaseline.read(root.resolve("baseline.snapshot"),"72555e21d09c917f33e2b2d7a95c34269cb11b3d16c738f7008d91979b2028d9");
        var raw=new CaptureJournal().readRawClosed(root.resolve("inputs/120.raw"),"6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36");
        var owner=new HeapRowPatch.Xid(10,9,5241);var rows=new ArrayList<ArchivedDictionaryHistory.RowOperation>();
        var indexes=new ArrayList<DictionaryIndexUndoPair>();var baseline=snapshot.dictionaryBaseline();
        for(var record:new RedoRecords().readCaptured(raw))for(var undo:record.vectors()) {
            if(undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1 || undo.fields().get(0).length!=20)continue;
            byte[] b=undo.fields().get(0);if(!new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12)).equals(owner))continue;
            var h=UndoHeader.decode(undo);
            var layout=baseline.layouts().values().stream().filter(l->l.role().logicalObject()==h.objectId()).findFirst();
            var scope=baseline.indexes().stream().filter(i->i.role().logicalObject()==h.objectId()).findFirst();
            if(layout.isEmpty() && scope.isEmpty())continue;
            var forward=record.vectors().stream().filter(v->v.containerId()==undo.containerId()
                    && (v.opcodeMajor()==10 || v.opcodeMajor()==11) && HeapUndoPair.referencesOriginal(v,h.uba())).toList();
            assertEquals(1,forward.size());var row=forward.get(0);
            if(scope.isPresent())indexes.add(DictionaryIndexUndoPair.bind(record,row,scope.get().role(),scope.get().indexDba(),"AL32UTF8"));
            else {
                var l=layout.orElseThrow();
                if(l.role().name().equals("OBJ$")) {
                    var pair=DictionaryObjectUndoPair.bind(record,row,l.role(),l.columns().size()).physical();
                    rows.add(new ArchivedDictionaryHistory.RowOperation(l.role(),pair.forward(),pair.undo().inverse(),h));
                } else {
                    var pair=ClusterUndoPair.bind(record,row,l.role(),l.tableNumber(),l.columns().size()-1);
                    rows.add(new ArchivedDictionaryHistory.RowOperation(l.role(),pair.forward().row(),pair.inverse().row(),h,pair.forward().clusterKeyIndex()));
                }
            }
        }
        var state=new DictionaryHistoryRows(snapshot);var result=state.apply(rows,indexes);
        assertEquals(snapshot.columns(),DictionaryHistoryRows.schema(snapshot,result));
        assertEquals(4,result.stream().filter(r->r.role().equals("COL$") && r.target()==81024).count());
        var missing=new ArrayList<>(indexes);missing.remove(0);
        assertThrows(RedoException.class,()->new DictionaryHistoryRows(snapshot).apply(rows,missing));
        var wrongCluster=new ArrayList<ArchivedDictionaryHistory.RowOperation>();
        for(var row:rows)wrongCluster.add(row.role().name().equals("TAB$") && row.forward().kind()==HeapRowPatch.Kind.INSERT
                ?new ArchivedDictionaryHistory.RowOperation(row.role(),row.forward(),row.inverse(),row.identity(),row.clusterKeyIndex()+1):row);
        assertThrows(RedoException.class,()->new DictionaryHistoryRows(snapshot).apply(wrongCluster,indexes));
    }
}
