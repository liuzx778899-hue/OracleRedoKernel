package com.streamhouse.engines.oracle;

import java.sql.*;
import java.util.*;
import static com.streamhouse.engines.oracle.OracleDictionaryAcquisition.query;

/** Same-SCN statistics dependency metadata; never fills an already acquired snapshot. */
final class OracleStatisticsAcquisition {
    static OracleDictionaryAcquisition.Result acquire(Connection c,long scn)throws SQLException {
        var hist=acquire(c,scn,"HIST_HEAD$",List.of("I_HH_OBJ#_COL#","I_HH_OBJ#_INTCOL#"),"DICT_STATS",18);
        var usage=acquire(c,scn,"COL_USAGE$",List.of("I_COL_USAGE$"),"DICT_USAGE",10);
        var frames=new ArrayList<>(hist.frames());frames.addAll(usage.frames());
        var after=new ArrayList<>(hist.after());after.addAll(usage.after());
        return new OracleDictionaryAcquisition.Result(List.copyOf(frames),List.copyOf(after));
    }
    private static OracleDictionaryAcquisition.Result acquire(Connection c,long scn,String tableName,List<String> indexNames,String prefix,int minimum)throws SQLException {
        var frames=new ArrayList<String>();var after=new ArrayList<String>();frames.add(prefix+"_BEGIN|1");
        String table=identity(c,scn,tableName,"TABLE");long logical=Long.parseLong(table.split("\\|")[0]);
        String tableFrame="SYS|"+tableName+"|"+table;frames.add(prefix+"_TABLE|BEFORE|"+tableFrame);after.add(prefix+"_TABLE|AFTER|"+tableFrame);
        String columnsSql="select segcol#||'|'||intcol#||'|'||name||'|'||type#||'|'||null$ from SYS.COL$ as of scn "+scn+" where obj#="+logical+" order by segcol#";
        var columns=query(c,columnsSql);
        if(columns.size()<minimum || columns.size()>255 || tableName.equals("COL_USAGE$") && columns.size()!=10)throw new SQLException("STATISTICS_LAYOUT_COVERAGE");
        var names=new ArrayList<String>();
        for(int i=0;i<columns.size();i++) {
            var f=columns.get(i).split("\\|",-1);
            if(f.length!=5 || !f[0].equals(Integer.toString(i+1)) || !f[2].matches("[A-Z][A-Z0-9_$#]{0,127}")
                    || !List.of("2","12","23").contains(f[3]) || !List.of("0","1").contains(f[4]))throw new SQLException("STATISTICS_LAYOUT_UNSUPPORTED");
            frames.add(prefix+"_COLUMN|"+columns.get(i));names.add(f[1]+"|"+f[0]+"|"+f[2]);
        }
        String layoutCheck="select internal_column_id||'|'||segment_column_id||'|'||column_name from dba_tab_cols where owner='SYS' and table_name='"+tableName+"' order by segment_column_id";
        if(!query(c,layoutCheck).equals(names))throw new SQLException("STATISTICS_CURRENT_LAYOUT_MISMATCH");
        if(!query(c,"select nvl(to_char(tab#),'-1')||'|'||cols||'|'||nvl(to_char(clucols),'0') from SYS.TAB$ as of scn "+scn+" where obj#="+logical)
                .equals(List.of("-1|"+columns.size()+"|0")))throw new SQLException("STATISTICS_TABLE_PROFILE");
        for(String name:indexNames) {
            String index=identity(c,scn,name,"INDEX");long id=Long.parseLong(index.split("\\|")[0]);
            var address=query(c,"select file#*4194304+block# from SYS.IND$ as of scn "+scn+" where obj#="+id+" and bo#="+logical);
            if(address.size()!=1)throw new SQLException("STATISTICS_INDEX_PARENT_OR_ADDRESS");
            String second=name.equals("I_HH_OBJ#_COL#")?"COL#":"INTCOL#";
            String objectColumn=columns.stream().filter(x->x.split("\\|",-1)[2].equals("OBJ#")).findFirst().orElseThrow(()->new SQLException("STATISTICS_KEY_COLUMN_MISSING")).split("\\|",-1)[1];
            String secondColumn=columns.stream().filter(x->x.split("\\|",-1)[2].equals(second)).findFirst().orElseThrow(()->new SQLException("STATISTICS_KEY_COLUMN_MISSING")).split("\\|",-1)[1];
            if(!query(c,"select pos#||'|'||intcol# from SYS.ICOL$ as of scn "+scn+" where obj#="+id+" order by pos#")
                    .equals(List.of("1|"+objectColumn,"2|"+secondColumn)))throw new SQLException("STATISTICS_INDEX_COLUMN_MAPPING");
            String fields=name+"|"+index+"|"+address.get(0);frames.add(prefix+"_INDEX|BEFORE|"+fields);after.add(prefix+"_INDEX|AFTER|"+fields);
            if(!identity(c,scn,name,"INDEX").equals(index))throw new SQLException("STATISTICS_INDEX_CHANGED");
        }
        if(!identity(c,scn,tableName,"TABLE").equals(table) || !query(c,layoutCheck).equals(names))throw new SQLException("STATISTICS_LAYOUT_CHANGED");
        frames.add(prefix+"_END|"+columns.size());
        return new OracleDictionaryAcquisition.Result(List.copyOf(frames),List.copyOf(after));
    }
    private static String identity(Connection c,long scn,String name,String type)throws SQLException {
        var current=query(c,"select object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name='"+name+"' and object_type='"+type+"'");
        if(current.size()!=1)throw new SQLException("STATISTICS_OBJECT_IDENTITY_COVERAGE");
        var id=current.get(0).split("\\|",-1);
        if(id.length!=2 || !id[0].matches("[1-9][0-9]*") || !id[1].matches("[1-9][0-9]*")
                || !query(c,"select obj#||'|'||dataobj# from SYS.OBJ$ as of scn "+scn+" where obj#="+id[0]+" and name='"+name+"'").equals(current))
            throw new SQLException("STATISTICS_SCN_IDENTITY_MISMATCH");
        return current.get(0);
    }
}
