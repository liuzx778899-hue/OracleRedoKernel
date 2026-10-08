package com.streamhouse.engines.oracle;

import java.sql.*;
import java.util.*;

/** Owned source connection, fixed-SCN value bytes; no redo dump or LogMiner input. */
final class OracleDictionaryAcquisition {
    record Result(List<String> frames,List<String> after) {}
    record Column(int internal,int segment,String name,int type,int required) {}
    static List<String> query(Connection c,String sql)throws SQLException {
        var out=new ArrayList<String>();
        try(var st=c.createStatement();var rs=st.executeQuery(sql)){while(rs.next())out.add(rs.getString(1));}
        return List.copyOf(out);
    }
    static Result acquire(Connection c,long scn,List<String> roles,List<String> anchors)throws SQLException {
        var frames=new ArrayList<String>();var after=new ArrayList<String>();frames.add("DICT_HISTORY_BEGIN|1");
        var layouts=new LinkedHashMap<String,List<Column>>();
        for(String r:roles) {
            var role=r.split("\\|",-1);String name=role[1];long logical=Long.parseLong(role[2]);
            if(!query(c,"select obj#||'|'||dataobj# from SYS.OBJ$ as of scn "+scn+" where obj#="+logical+" and name='"+name+"'").equals(List.of(role[2]+"|"+role[3])))throw new SQLException("DICTIONARY_ROLE_SCN_MISMATCH");
            var raw=query(c,"select intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$ from SYS.COL$ as of scn "+scn+" where obj#="+logical+" order by segcol#");
            var columns=new ArrayList<Column>();
            for(String line:raw) {
                var f=line.split("\\|",-1);
                if(f.length!=5 || !f[2].matches("[A-Z][A-Z0-9_$#]{0,127}"))throw new SQLException("DICTIONARY_LAYOUT_UNSUPPORTED");
                var col=new Column(Integer.parseInt(f[0]),Integer.parseInt(f[1]),f[2],Integer.parseInt(f[3]),Integer.parseInt(f[4]));
                if(col.segment()!=columns.size()+1 || col.internal()<1 || !List.of(1,2,8,12,23,180).contains(col.type()) || !List.of(0,1).contains(col.required()))
                    throw new SQLException("DICTIONARY_LAYOUT_UNSUPPORTED");
                columns.add(col);
            }
            if(columns.isEmpty() || columns.size()>255)throw new SQLException("DICTIONARY_LAYOUT_COVERAGE");
            var position=query(c,"select nvl(to_char(tab#),'-1')||'|'||cols||'|'||nvl(to_char(clucols),'0') from SYS.TAB$ as of scn "+scn+" where obj#="+logical);
            String expected=(name.equals("OBJ$")?-1:name.equals("TAB$")?1:5)+"|"+columns.size()+"|"+(name.equals("OBJ$")?0:1);
            if(!position.equals(List.of(expected)))throw new SQLException("DICTIONARY_TABLE_PROFILE_UNSUPPORTED");
            // Current projection names must still denote the recorded physical/internal layout.
            String check="select internal_column_id||'|'||segment_column_id||'|'||column_name from dba_tab_cols where owner='SYS' and table_name='"+name+"' order by segment_column_id";
            var wanted=columns.stream().map(x->x.internal()+"|"+x.segment()+"|"+x.name()).toList();
            if(!query(c,check).equals(wanted))throw new SQLException("DICTIONARY_CURRENT_LAYOUT_MISMATCH");
            frames.add("DICT_LAYOUT|"+name+"|"+expected.substring(0,expected.indexOf('|'))+"|"+columns.size());
            for(var col:columns)frames.add("DICT_LAYOUT_COLUMN|"+name+"|"+col.segment()+"|"+col.internal()+"|"+col.name()+"|"+col.type()+"|"+col.required());
            layouts.put(name,List.copyOf(columns));
        }
        for(String name:List.of("I_COL1","I_COL2","I_COL3")) {
            var identities=query(c,"select object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name='"+name+"' and object_type='INDEX'");
            if(identities.size()!=1)throw new SQLException("DICTIONARY_INDEX_IDENTITY_COVERAGE");
            var identity=identities.get(0).split("\\|",-1);long logical=Long.parseLong(identity[0]);
            var historical=query(c,"select obj#||'|'||dataobj# from SYS.OBJ$ as of scn "+scn+" where obj#="+logical+" and name='"+name+"'");
            if(!historical.equals(identities))throw new SQLException("DICTIONARY_INDEX_SCN_IDENTITY_MISMATCH");
            var address=query(c,"select file#*4194304+block# from SYS.IND$ as of scn "+scn+" where obj#="+logical);
            if(address.size()!=1)throw new SQLException("DICTIONARY_INDEX_ADDRESS_COVERAGE");
            String fields=name+"|"+identities.get(0)+"|"+address.get(0);frames.add("DICT_INDEX|BEFORE|"+fields);after.add("DICT_INDEX|AFTER|"+fields);
        }
        int count=0;
        for(String r:roles) {
            var role=r.split("\\|",-1);String name=role[1];var columns=layouts.get(name);
            for(String anchor:anchors) {
                var a=anchor.split("\\|",-1);if(!a[1].equals(name))continue;
                StringBuilder sql=new StringBuilder("select dbms_rowid.rowid_object(rowid),dbms_rowid.rowid_relative_fno(rowid),dbms_rowid.rowid_block_number(rowid),dbms_rowid.rowid_row_number(rowid)");
                for(var col:columns)sql.append(',').append(col.name());
                sql.append(" from SYS.").append(name).append(" as of scn ").append(scn).append(" where obj#=").append(a[2])
                        .append(" and dbms_rowid.rowid_relative_fno(rowid)=").append(a[4]).append(" and dbms_rowid.rowid_block_number(rowid)=").append(a[5]).append(" and dbms_rowid.rowid_row_number(rowid)=").append(a[6]);
                try(var st=c.createStatement();var rs=st.executeQuery(sql.toString())) {
                    if(!rs.next())throw new SQLException("DICTIONARY_FULL_ROW_MISSING");
                    StringBuilder row=new StringBuilder("DICT_ROW|").append(name).append('|').append(a[2]);
                    for(int i=1;i<=4;i++) {
                        if(!rs.getString(i).equals(a[i+2]))throw new SQLException("DICTIONARY_FULL_ROW_POSITION_CHANGED");
                        row.append('|').append(rs.getString(i));
                    }
                    for(int i=0;i<columns.size();i++) {
                        var col=columns.get(i);byte[] bytes=rs.getBytes(i+5);
                        if(bytes!=null && (bytes.length==0 || !List.of(1,2,12,23).contains(col.type())))throw new SQLException("DICTIONARY_NON_NULL_VALUE_TYPE_UNSUPPORTED");
                        if(bytes==null && col.required()==1)throw new SQLException("DICTIONARY_REQUIRED_VALUE_MISSING");
                        row.append('|').append(bytes==null?"NULL":HexFormat.of().withUpperCase().formatHex(bytes));
                    }
                    if(rs.next())throw new SQLException("DICTIONARY_FULL_ROW_DUPLICATE");
                    frames.add(row.toString());count++;
                }
            }
            String check="select internal_column_id||'|'||segment_column_id||'|'||column_name from dba_tab_cols where owner='SYS' and table_name='"+name+"' order by segment_column_id";
            if(!query(c,check).equals(columns.stream().map(x->x.internal()+"|"+x.segment()+"|"+x.name()).toList()))throw new SQLException("DICTIONARY_LAYOUT_CHANGED");
        }
        for(String frame:after) {
            var f=frame.split("\\|",-1);
            if(!query(c,"select object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name='"+f[2]+"' and object_type='INDEX'").equals(List.of(f[3]+"|"+f[4])))throw new SQLException("DICTIONARY_INDEX_CHANGED");
        }
        var statistics=OracleStatisticsAcquisition.acquire(c,scn);frames.addAll(statistics.frames());after.addAll(statistics.after());
        frames.add("DICT_HISTORY_END|"+count);return new Result(List.copyOf(frames),List.copyOf(after));
    }
}
