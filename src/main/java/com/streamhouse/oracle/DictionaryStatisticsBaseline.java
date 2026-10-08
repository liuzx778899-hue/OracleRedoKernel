package com.streamhouse.oracle;

import java.util.*;

/** Optional, pre-window HIST_HEAD$ identity/layout. Diagnostic SQL cannot supply this authority. */
public record DictionaryStatisticsBaseline(DictionaryBaseline.Layout table,List<DictionaryBaseline.Index> indexes) {
    public DictionaryStatisticsBaseline {indexes=List.copyOf(indexes);}
    static DictionaryStatisticsBaseline read(FrozenHeapBaseline.Input input,Map<String,DictionaryBaseline.Layout> layouts,
                                              List<DictionaryBaseline.Index> prior)throws RedoException {
        input.exact("DICT_STATS_BEGIN|1");var f=input.take("DICT_STATS_TABLE",6);
        if(!f[1].equals("BEFORE") || !f[2].equals("SYS") || !f[3].equals("HIST_HEAD$"))throw new RedoException("DICTIONARY_STATS_TABLE_ROLE");
        var role=new FrozenHeapBaseline.ObjectRole(f[2],f[3],positive(f[4]),positive(f[5]));
        var cols=new ArrayList<DictionaryBaseline.Column>();var names=new HashSet<String>();var internal=new HashSet<Integer>();
        while(input.peek("DICT_STATS_COLUMN")) {
            f=input.take("DICT_STATS_COLUMN",6);int seg=Integer.parseInt(f[1]),in=Integer.parseInt(f[2]),type=Integer.parseInt(f[4]);
            FrozenHeapBaseline.identifier(f[3]);
            if(seg!=cols.size()+1 || in<1 || !internal.add(in) || !names.add(f[3]) || !List.of(2,12,23).contains(type)
                    || !List.of("0","1").contains(f[5]))throw new RedoException("DICTIONARY_STATS_COLUMN_PROFILE");
            cols.add(new DictionaryBaseline.Column(seg,in,f[3],type,f[5].equals("1")));
        }
        if(cols.size()<18 || cols.size()>255 || !cols.get(0).name().equals("OBJ#") || !cols.get(1).name().equals("COL#")
                || !cols.get(14).name().equals("INTCOL#") || cols.get(0).type()!=2 || cols.get(1).type()!=2 || cols.get(14).type()!=2
                || !cols.get(0).required() || !cols.get(1).required() || !cols.get(14).required())
            throw new RedoException("UNSUPPORTED_DICTIONARY_STATS_LAYOUT");
        var logical=new HashSet<Long>();var data=new HashSet<Long>();var addresses=new HashSet<Long>();
        for(var l:layouts.values()) {logical.add(l.role().logicalObject());data.add(l.role().dataObject());}
        for(var index:prior) {logical.add(index.role().logicalObject());data.add(index.role().dataObject());addresses.add(index.indexDba());}
        if(!logical.add(role.logicalObject()) || !data.add(role.dataObject()))throw new RedoException("DICTIONARY_STATS_ROLE_COLLISION");
        var indexes=new ArrayList<DictionaryBaseline.Index>();
        for(String name:List.of("I_HH_OBJ#_COL#","I_HH_OBJ#_INTCOL#")) {
            f=input.take("DICT_STATS_INDEX",6);var index=new FrozenHeapBaseline.ObjectRole("SYS",name,positive(f[3]),positive(f[4]));long dba=positive(f[5]);
            if(!f[1].equals("BEFORE") || !f[2].equals(name) || !logical.add(index.logicalObject()) || !data.add(index.dataObject())
                    || (dba>>>22)==0 || !addresses.add(dba))throw new RedoException("DICTIONARY_STATS_INDEX_SCOPE");
            indexes.add(new DictionaryBaseline.Index(index,dba));
        }
        input.exact("DICT_STATS_END|"+cols.size());
        return new DictionaryStatisticsBaseline(new DictionaryBaseline.Layout(role,-1,cols),indexes);
    }
    void requireAfter(FrozenHeapBaseline.Input input)throws RedoException {
        var role=table.role();input.exact("DICT_STATS_TABLE|AFTER|SYS|HIST_HEAD$|"+role.logicalObject()+"|"+role.dataObject());
        for(var index:indexes)input.exact("DICT_STATS_INDEX|AFTER|"+index.role().name()+"|"+index.role().logicalObject()+"|"+index.role().dataObject()+"|"+index.indexDba());
    }
    private static long positive(String value)throws RedoException {
        long n=Long.parseLong(value);if(n<=0 || n>0xffffffffL)throw new RedoException("DICTIONARY_STATS_IDENTITY_NUMBER");return n;
    }
}
