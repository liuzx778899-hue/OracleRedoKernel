package com.streamhouse.oracle;

import java.util.*;

/** Observed column-usage deletion, owned by the complete schema transaction. */
final class DictionaryUsageChanges {
    record Row(HeapUndoPair physical,long object,int internal) {}
    static Row bind(RedoRecords.Record record,RedoRecords.Vector vector,DictionaryUsageBaseline baseline)throws RedoException {
        return fromPhysical(HeapUndoPair.bind(record,vector,baseline.table().columns().size()),baseline);
    }
    static Row fromPhysical(HeapUndoPair pair,DictionaryUsageBaseline baseline)throws RedoException {
        var identity=pair.undo().identity();
        if(identity.objectId()!=baseline.table().role().logicalObject() || identity.dataObjectId()!=baseline.table().role().dataObject()
                || identity.tablespace()!=0 || pair.forward().kind()!=HeapRowPatch.Kind.DELETE)throw new RedoException("UNSUPPORTED_USAGE_ROW_OR_GENERATION");
        var values=pair.undo().inverse().columns();
        for(int i=0;i<values.size();i++) {
            var value=values.get(i);
            if(value.state()==HeapRowPatch.State.ABSENT)throw new RedoException("USAGE_DELETE_INCOMPLETE_BEFORE");
            if(value.state()==HeapRowPatch.State.VALUE) {
                if(i==8)OracleScalars.date(value.bytes());else OracleScalars.number(value.bytes());
            }
        }
        if(values.get(0).state()!=HeapRowPatch.State.VALUE || values.get(1).state()!=HeapRowPatch.State.VALUE)throw new RedoException("USAGE_IDENTITY_VALUE_MISSING");
        try {
            long object=OracleScalars.number(values.get(0).bytes()).longValueExact();int internal=OracleScalars.number(values.get(1).bytes()).intValueExact();
            if(object<=0 || object>0xffffffffL || internal<1)throw new RedoException("USAGE_PARENT_OR_COLUMN");return new Row(pair,object,internal);
        } catch(ArithmeticException e){throw new RedoException("USAGE_NONINTEGRAL_IDENTITY");}
    }
    static void requireComplete(List<Row> rows,List<DictionaryIndexUndoPair> indexes,DictionaryUsageBaseline baseline,
                                long target,List<DictionaryBaseline.Row> before,DictionaryBaseline metadata)throws RedoException {
        var remaining=new ArrayList<>(indexes);var positions=new HashSet<String>();
        for(var row:rows) {
            var p=row.physical().forward();
            if(row.object()!=target || !positions.add(p.dba()+":"+p.slot()) || before.stream().noneMatch(r->{
                if(!r.role().equals("COL$"))return false;
                try{return metadata.number(r,"OBJ#")==row.object() && metadata.number(r,"INTCOL#")==row.internal();}catch(RedoException e){return false;}
            }))throw new RedoException("USAGE_COLUMN_BEFORE_ASSOCIATION");
            var matches=remaining.stream().filter(i->i.role().equals(baseline.index().role()) && i.indexDba()==baseline.index().indexDba()
                    && i.key().objectId()==row.object() && i.key().columnNumber()==row.internal()
                    && i.key().rowDba()==p.dba() && i.key().rowSlot()==p.slot()).toList();
            if(matches.size()!=1 || matches.get(0).physical().forward().kind()!=IndexUndoPair.Kind.DELETE)throw new RedoException("USAGE_INDEX_ROW_COVERAGE");
            remaining.remove(matches.get(0));
        }
        if(!remaining.isEmpty())throw new RedoException("USAGE_ORPHAN_INDEX_CHANGE");
    }
}
