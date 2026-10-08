package com.streamhouse.oracle;

import java.util.*;

/** Same-SCN complete SYS row acquisition. Its existence alone never authorizes historical event publication. */
public record DictionaryBaseline(Map<String,Layout> layouts,List<Index> indexes,List<Row> rows,DictionaryStatisticsBaseline statistics) {
    public DictionaryBaseline(Map<String,Layout> layouts,List<Index> indexes,List<Row> rows){this(layouts,indexes,rows,null);}
    public record Column(int segment,int internal,String name,int type,boolean required) {}
    public record Layout(FrozenHeapBaseline.ObjectRole role,int tableNumber,List<Column> columns) {
        public Layout {columns=List.copyOf(columns);}
    }
    public record Index(FrozenHeapBaseline.ObjectRole role,long indexDba) {}
    public record Row(String role,long target,ArchivedHeapRows.RowKey key,List<ArchivedHeapRows.Cell> values) {
        public Row {values=List.copyOf(values);}
    }
    public DictionaryBaseline {layouts=Map.copyOf(layouts);indexes=List.copyOf(indexes);rows=List.copyOf(rows);}
    void requireMetadata(FrozenHeapBaseline.ObjectRole heap,FrozenHeapBaseline.ObjectRole index,List<FrozenHeapBaseline.PhysicalColumn> columns) throws RedoException {
        for(var role:List.of(heap,index)) {
            var row=rows.stream().filter(r->r.role().equals("OBJ$") && r.target()==role.logicalObject()).findFirst().orElseThrow();
            if(number(row,"DATAOBJ#")!=role.dataObject() || !text(row,"NAME").equals(role.name()) || number(row,"TYPE#")!=(role.equals(heap)?2:1))
                throw new RedoException("DICTIONARY_OBJECT_METADATA_MISMATCH");
        }
        var tab=rows.stream().filter(r->r.role().equals("TAB$")).findFirst().orElseThrow();
        if(number(tab,"DATAOBJ#")!=heap.dataObject() || number(tab,"COLS")!=columns.stream().filter(c->!c.hidden()).count()
                || number(tab,"INTCOLS")!=columns.size() || number(tab,"KERNELCOLS")!=columns.size())throw new RedoException("DICTIONARY_TABLE_METADATA_MISMATCH");
        var seen=new HashSet<Long>();
        for(var row:rows)if(row.role().equals("COL$")) {
            long internal=number(row,"INTCOL#");var col=columns.stream().filter(c->c.internal()==internal).findFirst().orElseThrow(()->new RedoException("DICTIONARY_COLUMN_METADATA_MISMATCH"));
            if(!seen.add(internal) || number(row,"SEGCOL#")!=col.segment() || !text(row,"NAME").equals(col.name())
                    || number(row,"TYPE#")!=(col.type().equals("NUMBER")?2:1) || number(row,"NULL$")!=(col.nullable()?0:2)
                    || number(row,"PROPERTY")!=(col.hidden()?17179869216L:0))throw new RedoException("DICTIONARY_COLUMN_METADATA_MISMATCH");
        }
        if(seen.size()!=columns.size())throw new RedoException("DICTIONARY_COLUMN_METADATA_MISMATCH");
    }
    private byte[] value(Row row,String name) throws RedoException {
        var columns=layouts.get(row.role()).columns();
        for(int i=0;i<columns.size();i++)if(columns.get(i).name().equals(name)) {
            var cell=row.values().get(i);if(cell.state()!=HeapRowPatch.State.VALUE)throw new RedoException("DICTIONARY_METADATA_VALUE_MISSING");
            return HexFormat.of().parseHex(cell.hex());
        }
        throw new RedoException("DICTIONARY_METADATA_COLUMN_MISSING");
    }
    long number(Row row,String name)throws RedoException {
        try{return OracleScalars.number(value(row,name)).longValueExact();}
        catch(ArithmeticException e){throw new RedoException("DICTIONARY_METADATA_INTEGER_REQUIRED");}
    }
    String text(Row row,String name)throws RedoException{return OracleScalars.varchar2(value(row,name),"AL32UTF8");}
    static DictionaryBaseline read(List<String> lines,List<FrozenHeapBaseline.DictionaryAnchor> anchors) throws RedoException {
        var input=new FrozenHeapBaseline.Input(String.join("\n",lines));var layouts=new LinkedHashMap<String,Layout>();
        input.exact("DICT_HISTORY_BEGIN|1");
        for(String name:List.of("OBJ$","TAB$","COL$")) {
            var role=anchors.stream().filter(a->a.dictionary().name().equals(name)).findFirst().orElseThrow().dictionary();
            var header=input.take("DICT_LAYOUT",4);int table=Integer.parseInt(header[2]),count=Integer.parseInt(header[3]);
            if(!header[1].equals(name) || table!=(name.equals("OBJ$")?-1:name.equals("TAB$")?1:5) || count<2 || count>255)
                throw new RedoException("UNSUPPORTED_DICTIONARY_LAYOUT_PROFILE");
            var columns=new ArrayList<Column>();var names=new HashSet<String>();var internal=new HashSet<Integer>();
            for(int i=1;i<=count;i++) {
                var f=input.take("DICT_LAYOUT_COLUMN",7);int seg=Integer.parseInt(f[2]),in=Integer.parseInt(f[3]),type=Integer.parseInt(f[5]);
                FrozenHeapBaseline.identifier(f[4]);
                if(!f[1].equals(name) || seg!=i || in<1 || !internal.add(in) || !names.add(f[4])
                        || !List.of(1,2,8,12,23,180).contains(type) || !List.of("0","1").contains(f[6]))
                    throw new RedoException("DICTIONARY_LAYOUT_COVERAGE_MISMATCH");
                columns.add(new Column(seg,in,f[4],type,f[6].equals("1")));
            }
            if(!columns.get(0).name().equals("OBJ#") || columns.get(0).type()!=2 || !columns.get(0).required())
                throw new RedoException("DICTIONARY_PARENT_COLUMN_MISMATCH");
            layouts.put(name,new Layout(role,table,columns));
        }
        var indexes=new ArrayList<Index>();var logical=new HashSet<Long>();var data=new HashSet<Long>();var addresses=new HashSet<Long>();
        for(String name:List.of("I_COL1","I_COL2","I_COL3")) {
            var f=input.take("DICT_INDEX",6);var role=new FrozenHeapBaseline.ObjectRole("SYS",name,positive(f[3]),positive(f[4]));long dba=positive(f[5]);
            if(!f[1].equals("BEFORE") || !f[2].equals(name) || dba>0xffffffffL || (dba>>>22)==0
                    || !logical.add(role.logicalObject()) || !data.add(role.dataObject()) || !addresses.add(dba)
                    || layouts.values().stream().anyMatch(l->l.role().logicalObject()==role.logicalObject() || l.role().dataObject()==role.dataObject()))
                throw new RedoException("DICTIONARY_INDEX_SCOPE_COLLISION");
            indexes.add(new Index(role,dba));
        }
        var rows=new ArrayList<Row>();var seen=new HashSet<FrozenHeapBaseline.DictionaryAnchor>();
        while(input.peek("DICT_ROW")) {
            String name=input.lines.get(input.at).split("\\|",-1)[1];var layout=layouts.get(name);
            if(layout==null)throw new RedoException("DICTIONARY_ROW_ROLE_MISMATCH");
            var f=input.take("DICT_ROW",7+layout.columns().size());long target=positive(f[2]),object=positive(f[3]),file=positive(f[4]),block=positive(f[5]);int slot=Integer.parseInt(f[6]);
            if(object!=layout.role().dataObject() || file>1023 || block>=0x400000L || slot<0 || slot>65535)
                throw new RedoException("DICTIONARY_ROW_POSITION_MISMATCH");
            var key=new ArchivedHeapRows.RowKey(object,(file<<22)|block,slot);
            var anchor=new FrozenHeapBaseline.DictionaryAnchor(layout.role(),target,key);
            if(!anchors.contains(anchor) || !seen.add(anchor))throw new RedoException("DICTIONARY_FULL_ROW_ANCHOR_MISMATCH");
            var values=new ArrayList<ArchivedHeapRows.Cell>();
            for(int i=0;i<layout.columns().size();i++) {
                var c=layout.columns().get(i);String value=f[i+7];boolean nil=value.equals("NULL");
                if(nil && c.required())throw new RedoException("DICTIONARY_REQUIRED_VALUE_MISSING");
                if(!nil) {
                    if(!value.matches("(?:[0-9A-F]{2})+"))throw new RedoException("DICTIONARY_VALUE_ENCODING");
                    byte[] raw=HexFormat.of().parseHex(value);
                    if(c.type()==2)OracleScalars.number(raw);
                    else if(c.type()==1)OracleScalars.varchar2(raw,"AL32UTF8");
                    else if(c.type()==12)OracleScalars.date(raw);
                    else if(c.type()!=23)throw new RedoException("UNSUPPORTED_NON_NULL_DICTIONARY_VALUE_TYPE");
                }
                values.add(new ArchivedHeapRows.Cell(nil?HeapRowPatch.State.NULL:HeapRowPatch.State.VALUE,nil?"":value));
            }
            if(OracleScalars.number(HexFormat.of().parseHex(values.get(0).hex())).compareTo(java.math.BigDecimal.valueOf(target))!=0)
                throw new RedoException("DICTIONARY_FULL_ROW_PARENT_MISMATCH");
            rows.add(new Row(name,target,key,values));
        }
        var statistics=input.peek("DICT_STATS_BEGIN")?DictionaryStatisticsBaseline.read(input,layouts,indexes):null;
        input.exact("DICT_HISTORY_END|"+rows.size());
        if(seen.size()!=anchors.size() || input.at!=input.lines.size())throw new RedoException("DICTIONARY_FULL_ROW_COVERAGE_MISMATCH");
        return new DictionaryBaseline(layouts,indexes,rows,statistics);
    }
    private static long positive(String s) throws RedoException {
        long n=Long.parseLong(s);if(n<=0 || n>0xffffffffL)throw new RedoException("DICTIONARY_SCOPE_NUMBER");return n;
    }
}
