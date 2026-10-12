package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Raw-owned shadow metadata and complete OBJ$ name exchange for the observed BasicFile to SecureFile migration. */
final class LobMigrationProof {
    private record Cluster(long dba,int key) {}
    private record Position(long logical,long dba,int slot) {}
    private record Metadata(long parent,List<Column> values) {}
    private final FrozenHeapBaseline.Snapshot snapshot;
    private final Map<Cluster,Long> parents=new HashMap<>();
    private final Map<Position,Metadata> metadata=new HashMap<>();
    LobMigrationProof(FrozenHeapBaseline.Snapshot snapshot){this.snapshot=snapshot;}
    List<FrozenHeapBaseline.ObjectRole> apply(ArchivedDictionaryHistory.Context context,List<DictionaryBaseline.Row> prior,List<DictionaryBaseline.Row> rows)throws RedoException {
        var baseline=snapshot.dictionaryBaseline();
        for(var operation:context.rows)if(operation.role().name().equals("TAB$") && operation.forward().kind()==Kind.INSERT) {
            var f=operation.forward();
            var table=rows.stream().filter(r->r.role().equals("TAB$") && r.key().dba()==f.dba() && r.key().slot()==f.slot()).toList();
            if(table.size()!=1 || operation.clusterKeyIndex()==null)fail("TABLE_CLUSTER_PARENT");
            var key=new Cluster(f.dba(),operation.clusterKeyIndex());
            var old=parents.putIfAbsent(key,table.get(0).target());if(old!=null && old!=table.get(0).target())fail("CLUSTER_PARENT_REUSED");
        }
        for(var row:context.auxiliaryClusterRows)if(Set.of(19L,20L,109L).contains(row.logical())) {
            var pair=row.physical();var f=pair.forward().row();var before=pair.inverse().row();
            var key=new Position(row.logical(),f.dba(),f.slot());var old=metadata.get(key);
            if(f.kind()==Kind.INSERT) {
                if(old!=null || pair.forward().clusterKeyIndex()==null)fail("METADATA_INSERT_POSITION");
                var parent=parents.get(new Cluster(f.dba(),pair.forward().clusterKeyIndex()));
                if(parent==null)continue; // Metadata predating the acquired scope never becomes a replacement binding.
                var values=new ArrayList<Column>();
                for(var cell:f.columns())values.add(cell.state()==State.ABSENT?new Column(State.NULL,new byte[0]):cell);
                metadata.put(key,new Metadata(parent,List.copyOf(values)));
            } else if(old!=null) {
                for(int i=0;i<before.columns().size();i++)if(before.columns().get(i).state()!=State.ABSENT && !equal(before.columns().get(i),old.values().get(i)))fail("METADATA_BEFORE_VALUE");
                if(f.kind()==Kind.DELETE)metadata.remove(key);
                else if(f.kind()==Kind.UPDATE) {
                    var values=new ArrayList<>(old.values());
                    for(int i=0;i<f.columns().size();i++)if(f.columns().get(i).state()!=State.ABSENT)values.set(i,f.columns().get(i));
                    metadata.put(key,new Metadata(old.parent(),List.copyOf(values)));
                } else fail("METADATA_OPERATION");
            }
        }
        var original=object(prior,snapshot.heap().logicalObject());
        long owner=baseline.number(original,"OWNER#");
        var nextHeap=named(rows,owner,snapshot.heap().name(),2);
        if(nextHeap.target()==named(prior,owner,snapshot.heap().name(),2).target())return List.of();
        if(!baseline.text(original,"NAME").equals(snapshot.heap().name()) || context.rows.stream().noneMatch(r->r.role().name().equals("OBJ$")
                && r.forward().kind()==Kind.INSERT && number(r.forward().columns().get(0))==nextHeap.target()))fail("NAME_EXCHANGE_OWNER");
        if(snapshot.lobs().isEmpty() || snapshot.lobs().stream().anyMatch(l->l.storage()!=LobLocator.Storage.BASICFILE))fail("STORAGE_DIRECTION");
        var nextIndex=named(rows,owner,snapshot.index().name(),1);
        var heap=role(nextHeap,snapshot.heap().owner(),snapshot.heap().name());
        var index=role(nextIndex,snapshot.index().owner(),snapshot.index().name());
        if(index.logicalObject()==snapshot.index().logicalObject())fail("PRIMARY_INDEX_NOT_EXCHANGED");
        var columns=DictionaryHistoryRows.schema(snapshot,rows,heap,index);
        if(!columns.equals(DictionaryHistoryRows.schema(snapshot,prior)))fail("COLUMN_LAYOUT_CHANGED");
        var primary=find(19,heap.logicalObject(),0,index.logicalObject());
        if(number(primary.values().get(1))!=index.dataObject() || number(primary.values().get(6))!=1
                || number(primary.values().get(11))!=1 || (number(primary.values().get(12))&2)==0)fail("PRIMARY_INDEX_METADATA");
        var key=find(20,heap.logicalObject(),0,index.logicalObject());
        if(number(key.values().get(1))!=1 || number(key.values().get(2))!=1 || number(key.values().get(6))!=1
                || !columns.get(0).type().equals("NUMBER") || columns.get(0).nullable())fail("PRIMARY_INDEX_COLUMN");
        var result=new ArrayList<FrozenHeapBaseline.ObjectRole>(List.of(heap,index));
        for(var old:snapshot.lobs()) {
            var lob=find(109,heap.logicalObject(),1,old.column());
            if(number(lob.values().get(0))!=old.column() || number(lob.values().get(3))!=0
                    || !Set.of(2050L,6146L).contains(number(lob.values().get(11))))fail("SECUREFILE_LOB_PROFILE");
            long lobId=number(lob.values().get(2)),indexId=number(lob.values().get(4));
            var lobObject=object(rows,lobId);var lobIndex=object(rows,indexId);
            if(lobId==old.lob().logicalObject() || indexId==old.index().logicalObject()
                    || baseline.number(lobObject,"OWNER#")!=owner || baseline.number(lobIndex,"OWNER#")!=owner
                    || baseline.number(lobObject,"TYPE#")!=21 || baseline.number(lobIndex,"TYPE#")!=1)fail("LOB_OBJECT_ROLE");
            var lobRole=role(lobObject,heap.owner(),baseline.text(lobObject,"NAME"));
            var lobIndexRole=role(lobIndex,heap.owner(),baseline.text(lobIndex,"NAME"));
            var lobIndexMetadata=find(19,heap.logicalObject(),0,indexId);
            if(number(lobIndexMetadata.values().get(1))!=lobIndexRole.dataObject())fail("LOB_INDEX_DATA_OBJECT");
            result.add(lobRole);result.add(lobIndexRole);
        }
        if(result.stream().map(FrozenHeapBaseline.ObjectRole::logicalObject).distinct().count()!=result.size()
                || result.stream().map(FrozenHeapBaseline.ObjectRole::dataObject).distinct().count()!=result.size())fail("OBJECT_ROLE_COLLISION");
        return List.copyOf(result);
    }
    private Metadata find(long logical,long parent,int column,long value)throws RedoException {
        var matches=metadata.entrySet().stream().filter(e->e.getKey().logical()==logical && e.getValue().parent()==parent
                && number(e.getValue().values().get(column))==value).map(Map.Entry::getValue).toList();
        if(matches.size()!=1)fail("METADATA_ROLE_COVERAGE:"+logical+":"+parent+":"+value);
        return matches.get(0);
    }
    private DictionaryBaseline.Row object(List<DictionaryBaseline.Row> rows,long id)throws RedoException {
        var matches=rows.stream().filter(r->r.role().equals("OBJ$") && r.target()==id).toList();
        if(matches.size()!=1)fail("OBJECT_ROW_COVERAGE");return matches.get(0);
    }
    private DictionaryBaseline.Row named(List<DictionaryBaseline.Row> rows,long owner,String name,long type)throws RedoException {
        var matches=new ArrayList<DictionaryBaseline.Row>();
        for(var row:rows)if(row.role().equals("OBJ$") && snapshot.dictionaryBaseline().number(row,"OWNER#")==owner
                && snapshot.dictionaryBaseline().number(row,"TYPE#")==type && snapshot.dictionaryBaseline().text(row,"NAME").equals(name))matches.add(row);
        if(matches.size()!=1)fail("NAMED_OBJECT_COVERAGE");return matches.get(0);
    }
    private FrozenHeapBaseline.ObjectRole role(DictionaryBaseline.Row row,String owner,String name)throws RedoException {
        long data=snapshot.dictionaryBaseline().number(row,"DATAOBJ#");if(data<=0 || data>0xffffffffL)fail("DATA_OBJECT_RANGE");
        return new FrozenHeapBaseline.ObjectRole(owner,name,row.target(),data);
    }
    private static boolean equal(Column a,Column b){return a.state()==b.state() && Arrays.equals(a.bytes(),b.bytes());}
    private static long number(Column cell) {
        try {if(cell.state()!=State.VALUE)return -1;return OracleScalars.number(cell.bytes()).longValueExact();}
        catch(RedoException|ArithmeticException invalid){return -1;}
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("LOB_MIGRATION_"+detail);}
}
