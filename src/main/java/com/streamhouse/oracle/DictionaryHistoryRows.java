package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

/** Complete SYS values and three-index state; used only after archive-owned transaction closure. */
final class DictionaryHistoryRows {
    private record Position(String role,ArchivedHeapRows.RowKey key) {}
    private final FrozenHeapBaseline.Snapshot snapshot;
    private final DictionaryBaseline baseline;
    private final Map<Position,DictionaryBaseline.Row> rows=new LinkedHashMap<>();
    private final Map<String,Set<DictionaryIndexKey>> indexes;
    DictionaryHistoryRows(FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        this.snapshot=snapshot;baseline=snapshot.dictionaryBaseline();
        for(var row:baseline.rows())rows.put(new Position(row.role(),row.key()),row);
        indexes=expectedIndexes();
    }
    List<DictionaryBaseline.Row> apply(List<ArchivedDictionaryHistory.RowOperation> operations,List<DictionaryIndexUndoPair> indexOperations)throws RedoException {
        var parents=new HashMap<ArchivedHeapRows.RowKey,Long>();
        for(var pair:indexOperations) {
            var key=pair.key();if(key.objectId()!=snapshot.heap().logicalObject())throw new RedoException("DICTIONARY_INDEX_FOREIGN_PARENT_UNSUPPORTED");
            var rowKey=new ArchivedHeapRows.RowKey(baseline.layouts().get("COL$").role().dataObject(),key.rowDba(),key.rowSlot());
            var prior=parents.putIfAbsent(rowKey,key.objectId());if(prior!=null && prior!=key.objectId())throw new RedoException("DICTIONARY_INDEX_PARENT_CONFLICT");
            var entries=indexes.get(pair.role().name());boolean insert=pair.physical().forward().kind()==IndexUndoPair.Kind.INSERT || pair.physical().forward().kind()==IndexUndoPair.Kind.RESTORE;
            if(!(insert?entries.add(key):entries.remove(key)))throw new RedoException("DICTIONARY_INDEX_LIFECYCLE_CONFLICT");
        }
        for(var operation:operations) {
            var layout=baseline.layouts().get(operation.role().name());var f=operation.forward();var inverse=operation.inverse();
            var key=new ArchivedHeapRows.RowKey(operation.role().dataObject(),f.dba(),f.slot());var position=new Position(operation.role().name(),key);
            var existing=rows.get(position);int offset=layout.tableNumber()<0?0:1;
            if(f.kind()==Kind.INSERT) {
                if(existing!=null)throw new RedoException("DICTIONARY_ROW_INSERT_POSITION_OCCUPIED");
                Long parent=parents.get(key);if(parent==null)throw new RedoException("DICTIONARY_NEW_ROW_WITHOUT_INDEX_PARENT");
                var values=completeInsert(layout,f,parent);
                rows.put(position,new DictionaryBaseline.Row(operation.role().name(),parent,key,values));
            } else {
                if(existing==null)throw new RedoException("DICTIONARY_ROW_BEFORE_MISSING");
                if(inverse.columns().size()!=layout.columns().size()-offset)throw new RedoException("DICTIONARY_ROW_PATCH_LAYOUT_MISMATCH");
                for(int i=0;i<inverse.columns().size();i++) {
                    var cell=inverse.columns().get(i);
                    if(cell.state()!=State.ABSENT && !existing.values().get(i+offset).equals(cell(cell)))throw new RedoException("DICTIONARY_ROW_BEFORE_VALUE_MISMATCH");
                }
                if(f.kind()==Kind.DELETE) {
                    Long parent=parents.get(key);
                    if(parent==null || parent!=existing.target() || !existing.values().equals(completeInsert(layout,inverse,parent)))throw new RedoException("DICTIONARY_DELETE_COMPLETE_BEFORE_MISMATCH");
                    rows.remove(position);
                } else if(f.kind()==Kind.UPDATE) {
                    var values=new ArrayList<>(existing.values());
                    for(int i=0;i<f.columns().size();i++)if(f.columns().get(i).state()!=State.ABSENT)values.set(i+offset,cell(f.columns().get(i)));
                    requireValues(layout,values);var updated=new DictionaryBaseline.Row(existing.role(),existing.target(),key,values);
                    if(baseline.number(updated,"OBJ#")!=existing.target())throw new RedoException("DICTIONARY_ROW_PARENT_VALUE_CONFLICT");
                    rows.put(position,updated);
                } else if(f.kind()!=Kind.LOCK)throw new RedoException("UNSUPPORTED_DICTIONARY_ROW_OPERATION");
            }
        }
        if(!indexes.equals(expectedIndexes()))throw new RedoException("DICTIONARY_COMPLETE_INDEX_ROW_STATE_MISMATCH");
        return List.copyOf(rows.values());
    }
    private List<ArchivedHeapRows.Cell> completeInsert(DictionaryBaseline.Layout layout,Patch patch,long parent)throws RedoException {
        if(!layout.role().name().equals("COL$") || layout.tableNumber()!=5 || layout.columns().size()!=37 || patch.kind()!=Kind.INSERT
                || patch.extent()==null || patch.extent().storedColumns()!=32 || patch.columns().size()!=36)
            throw new RedoException("UNSUPPORTED_DICTIONARY_INSERT_COMPLETENESS_PROFILE");
        var value=new ArrayList<ArchivedHeapRows.Cell>();
        // The parent bytes must come from a complete index association, not the cluster-key slot number.
        byte[] parentBytes=null;
        for(var row:rows.values())if(row.target()==parent) {parentBytes=HexFormat.of().parseHex(row.values().get(0).hex());break;}
        if(parentBytes==null)throw new RedoException("DICTIONARY_INSERT_PARENT_NOT_IN_BASELINE_SCOPE");
        value.add(new ArchivedHeapRows.Cell(State.VALUE,HexFormat.of().formatHex(parentBytes)));
        for(int i=0;i<patch.columns().size();i++) {
            var c=patch.columns().get(i);
            if(c.state()==State.ABSENT) {
                if(i<32 || layout.columns().get(i+1).required())throw new RedoException("DICTIONARY_INSERT_UNPROVEN_ABSENT_COLUMN");
                value.add(new ArchivedHeapRows.Cell(State.NULL,""));
            } else {
                if(i>=32)throw new RedoException("DICTIONARY_INSERT_STORED_COLUMN_BOUNDARY_MISMATCH");
                value.add(cell(c));
            }
        }
        requireValues(layout,value);return List.copyOf(value);
    }
    private static ArchivedHeapRows.Cell cell(Column c)throws RedoException {
        if(c.state()==State.ABSENT)throw new RedoException("INCOMPLETE_DICTIONARY_PATCH_CELL");
        return new ArchivedHeapRows.Cell(c.state(),HexFormat.of().formatHex(c.bytes()));
    }
    private static void requireValues(DictionaryBaseline.Layout layout,List<ArchivedHeapRows.Cell> values)throws RedoException {
        if(values.size()!=layout.columns().size())throw new RedoException("DICTIONARY_COMPLETE_VALUE_COUNT");
        for(int i=0;i<values.size();i++) {
            var c=layout.columns().get(i);var cell=values.get(i);
            if(cell.state()==State.NULL) {if(c.required())throw new RedoException("DICTIONARY_REQUIRED_VALUE_MISSING");continue;}
            byte[] raw=HexFormat.of().parseHex(cell.hex());
            if(c.type()==2)OracleScalars.number(raw);
            else if(c.type()==1)OracleScalars.varchar2(raw,"AL32UTF8");
            else if(c.type()==12)OracleScalars.date(raw);
            else if(c.type()!=23)throw new RedoException("UNSUPPORTED_NON_NULL_DICTIONARY_VALUE_TYPE");
        }
    }
    private Map<String,Set<DictionaryIndexKey>> expectedIndexes()throws RedoException {
        var result=new LinkedHashMap<String,Set<DictionaryIndexKey>>();for(var index:baseline.indexes())result.put(index.role().name(),new HashSet<>());
        for(var row:rows.values())if(row.role().equals("COL$")) {
            long parent=baseline.number(row,"OBJ#");if(parent!=row.target() || parent!=snapshot.heap().logicalObject())throw new RedoException("DICTIONARY_ROW_PARENT_VALUE_CONFLICT");
            var key=row.key();String name=baseline.text(row,"NAME");int col=ordinal(baseline.number(row,"COL#")),internal=ordinal(baseline.number(row,"INTCOL#"));
            if(col<0 || internal<1)throw new RedoException("DICTIONARY_COLUMN_KEY_ORDINAL_INVALID");
            result.get("I_COL1").add(new DictionaryIndexKey(parent,name,null,key.dba(),key.slot()));
            result.get("I_COL2").add(new DictionaryIndexKey(parent,null,col,key.dba(),key.slot()));
            result.get("I_COL3").add(new DictionaryIndexKey(parent,null,internal,key.dba(),key.slot()));
        }
        return result;
    }
    static List<FrozenHeapBaseline.PhysicalColumn> schema(FrozenHeapBaseline.Snapshot snapshot,List<DictionaryBaseline.Row> rows)throws RedoException {
        var baseline=snapshot.dictionaryBaseline();var columns=new ArrayList<FrozenHeapBaseline.PhysicalColumn>();
        var colRows=new ArrayList<>(rows.stream().filter(r->r.role().equals("COL$")).toList());
        var logical=new HashSet<Integer>();
        // Validate order without assuming internal and physical ordinals have the same meaning.
        for(var row:colRows) {
            if(row.target()!=snapshot.heap().logicalObject() || baseline.number(row,"OBJ#")!=row.target())throw new RedoException("DICTIONARY_ROW_PARENT_VALUE_CONFLICT");
            int segment=ordinal(baseline.number(row,"SEGCOL#")),internal=ordinal(baseline.number(row,"INTCOL#")),col=ordinal(baseline.number(row,"COL#"));
            long type=baseline.number(row,"TYPE#"),property=baseline.number(row,"PROPERTY"),nulls=baseline.number(row,"NULL$");
            String name=baseline.text(row,"NAME");FrozenHeapBaseline.identifier(name);
            if(segment<1 || internal!=segment || (type!=1 && type!=2) || (property!=0 && property!=17179869216L) || (nulls!=0 && nulls!=2))
                throw new RedoException("UNSTABLE_OR_UNSUPPORTED_HISTORICAL_COLUMNS");
            if(property==0 ? col<1 || !logical.add(col) : col!=0)throw new RedoException("HISTORICAL_LOGICAL_COLUMN_COVERAGE");
            columns.add(new FrozenHeapBaseline.PhysicalColumn(segment,internal,name,type==2?"NUMBER":"VARCHAR2",nulls==0,property!=0));
        }
        columns.sort(Comparator.comparingInt(FrozenHeapBaseline.PhysicalColumn::segment));
        for(int i=0;i<columns.size();i++)if(columns.get(i).segment()!=i+1)throw new RedoException("HISTORICAL_COLUMN_PHYSICAL_COVERAGE");
        for(int i=1;i<=logical.size();i++)if(!logical.contains(i))throw new RedoException("HISTORICAL_LOGICAL_COLUMN_COVERAGE");
        new DictionaryBaseline(baseline.layouts(),baseline.indexes(),rows).requireMetadata(snapshot.heap(),snapshot.index(),columns);
        return List.copyOf(columns);
    }
    private static int ordinal(long value)throws RedoException {
        if(value<0 || value>255)throw new RedoException("UNSUPPORTED_HISTORICAL_COLUMN_ORDINAL");return (int)value;
    }
}
