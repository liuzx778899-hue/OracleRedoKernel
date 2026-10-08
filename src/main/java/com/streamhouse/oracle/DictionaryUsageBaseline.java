package com.streamhouse.oracle;

import java.util.*;

/** Pre-window COL_USAGE$ layout and index identity; no diagnostic backfill. */
public record DictionaryUsageBaseline(DictionaryBaseline.Layout table,DictionaryBaseline.Index index) {
    static final List<String> NAMES=List.of("OBJ#","INTCOL#","EQUALITY_PREDS","EQUIJOIN_PREDS","NONEQUIJOIN_PREDS","RANGE_PREDS","LIKE_PREDS","NULL_PREDS","TIMESTAMP","FLAGS");
    static DictionaryUsageBaseline read(FrozenHeapBaseline.Input input,Map<String,DictionaryBaseline.Layout> layouts,
                                        List<DictionaryBaseline.Index> indexes,DictionaryStatisticsBaseline statistics)throws RedoException {
        input.exact("DICT_USAGE_BEGIN|1");var f=input.take("DICT_USAGE_TABLE",6);
        if(!f[1].equals("BEFORE") || !f[2].equals("SYS") || !f[3].equals("COL_USAGE$"))throw new RedoException("DICTIONARY_USAGE_TABLE_ROLE");
        var role=new FrozenHeapBaseline.ObjectRole("SYS","COL_USAGE$",positive(f[4]),positive(f[5]));
        var columns=new ArrayList<DictionaryBaseline.Column>();
        for(int i=0;i<NAMES.size();i++) {
            f=input.take("DICT_USAGE_COLUMN",6);int type=i==8?12:2;
            if(!f[1].equals(Integer.toString(i+1)) || !f[2].equals(f[1]) || !f[3].equals(NAMES.get(i))
                    || !f[4].equals(Integer.toString(type)) || !f[5].equals("0"))throw new RedoException("UNSUPPORTED_DICTIONARY_USAGE_LAYOUT");
            columns.add(new DictionaryBaseline.Column(i+1,i+1,NAMES.get(i),type,false));
        }
        f=input.take("DICT_USAGE_INDEX",6);
        if(!f[1].equals("BEFORE") || !f[2].equals("I_COL_USAGE$"))throw new RedoException("DICTIONARY_USAGE_INDEX_ROLE");
        var index=new DictionaryBaseline.Index(new FrozenHeapBaseline.ObjectRole("SYS","I_COL_USAGE$",positive(f[3]),positive(f[4])),positive(f[5]));
        var prior=new ArrayList<FrozenHeapBaseline.ObjectRole>();layouts.values().forEach(l->prior.add(l.role()));
        var allIndexes=new ArrayList<>(indexes);
        if(statistics!=null) {prior.add(statistics.table().role());allIndexes.addAll(statistics.indexes());}
        allIndexes.forEach(i->prior.add(i.role()));
        for(var p:prior) {collision(p,role);collision(p,index.role());}
        collision(role,index.role());
        if(index.indexDba()>>>22==0 || allIndexes.stream().anyMatch(i->i.indexDba()==index.indexDba()))throw new RedoException("DICTIONARY_USAGE_INDEX_ADDRESS");
        input.exact("DICT_USAGE_END|10");return new DictionaryUsageBaseline(new DictionaryBaseline.Layout(role,-1,columns),index);
    }
    void requireAfter(FrozenHeapBaseline.Input input)throws RedoException {
        input.exact("DICT_USAGE_TABLE|AFTER|SYS|COL_USAGE$|"+table.role().logicalObject()+"|"+table.role().dataObject());
        input.exact("DICT_USAGE_INDEX|AFTER|I_COL_USAGE$|"+index.role().logicalObject()+"|"+index.role().dataObject()+"|"+index.indexDba());
    }
    private static void collision(FrozenHeapBaseline.ObjectRole a,FrozenHeapBaseline.ObjectRole b)throws RedoException {
        if(a.logicalObject()==b.logicalObject() || a.dataObject()==b.dataObject())throw new RedoException("DICTIONARY_USAGE_ROLE_COLLISION");
    }
    private static long positive(String s)throws RedoException {
        long n=Long.parseLong(s);if(n<=0 || n>0xffffffffL)throw new RedoException("DICTIONARY_USAGE_IDENTITY_NUMBER");return n;
    }
}
