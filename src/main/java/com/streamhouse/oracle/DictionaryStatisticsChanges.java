package com.streamhouse.oracle;

import java.util.*;

/** Statistics dependencies are accounted for inside the owning SYS transaction, never as schema rows. */
final class DictionaryStatisticsChanges {
    record Row(HeapUndoPair physical,long object,int column,int internal) {}
    static Row bind(RedoRecords.Record record,RedoRecords.Vector vector,DictionaryStatisticsBaseline baseline)throws RedoException {
        return fromPhysical(HeapUndoPair.bind(record,vector,baseline.table().columns().size()),baseline);
    }
    static Row fromPhysical(HeapUndoPair pair,DictionaryStatisticsBaseline baseline)throws RedoException {
        var h=pair.undo().identity();
        var role=baseline.table().role();
        if(h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0
                || pair.forward().kind()!=HeapRowPatch.Kind.INSERT)
            throw new RedoException("UNSUPPORTED_STATISTICS_ROW_OR_GENERATION");
        var values=pair.forward().columns();
        for(int i=0;i<values.size();i++) {
            var value=values.get(i);var column=baseline.table().columns().get(i);
            if(value.state()!=HeapRowPatch.State.VALUE) {
                if(column.required())throw new RedoException("STATISTICS_REQUIRED_VALUE_MISSING");
            } else if(column.type()==2)OracleScalars.number(value.bytes());
            else if(column.type()==12)OracleScalars.date(value.bytes());
            else if(column.type()!=23)throw new RedoException("UNSUPPORTED_STATISTICS_VALUE_TYPE");
        }
        try {
            long object=OracleScalars.number(values.get(0).bytes()).longValueExact();
            int column=OracleScalars.number(values.get(1).bytes()).intValueExact();
            int internal=OracleScalars.number(values.get(14).bytes()).intValueExact();
            if(object<=0 || object>0xffffffffL || column<1 || internal<1)throw new RedoException("STATISTICS_PARENT_OR_COLUMN");
            return new Row(pair,object,column,internal);
        } catch(ArithmeticException e){throw new RedoException("STATISTICS_NONINTEGRAL_IDENTITY");}
    }
    static void requireComplete(List<Row> rows,List<DictionaryIndexUndoPair> indexes,DictionaryStatisticsBaseline baseline,
                                long target,List<DictionaryBaseline.Row> dictionary,DictionaryBaseline metadata)throws RedoException {
        var remaining=new ArrayList<>(indexes);var positions=new HashSet<String>();
        for(var row:rows) {
            var p=row.physical().forward();
            if(row.object()!=target || !positions.add(p.dba()+":"+p.slot()) || dictionary.stream().noneMatch(r->{
                if(!r.role().equals("COL$"))return false;
                try{return metadata.number(r,"OBJ#")==row.object() && metadata.number(r,"COL#")==row.column()
                        && metadata.number(r,"INTCOL#")==row.internal();}catch(RedoException e){return false;}
            }))throw new RedoException("STATISTICS_COLUMN_ROW_ASSOCIATION");
            for(var scope:baseline.indexes()) {
                var matches=remaining.stream().filter(i->i.role().equals(scope.role()) && i.indexDba()==scope.indexDba()
                        && i.key().objectId()==row.object() && i.key().rowDba()==p.dba() && i.key().rowSlot()==p.slot()
                        && i.key().columnNumber()==(scope.role().name().equals("I_HH_OBJ#_COL#")?row.column():row.internal())).toList();
                if(matches.size()!=1 || matches.get(0).physical().forward().kind()!=IndexUndoPair.Kind.INSERT)
                    throw new RedoException("STATISTICS_INDEX_ROW_COVERAGE");
                remaining.remove(matches.get(0));
            }
        }
        if(!remaining.isEmpty())throw new RedoException("STATISTICS_ORPHAN_INDEX_CHANGE");
    }
}
