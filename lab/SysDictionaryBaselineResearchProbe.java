import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Fixed old-SCN full SYS rows are research rulers, never a new trusted acquisition for old redo. */
public class SysDictionaryBaselineResearchProbe {
    record Column(int internal,int segment,String name,int type,int required) {}
    static List<String> query(Connection c,String sql,List<String> commands)throws Exception {
        commands.add(sql);var out=new ArrayList<String>();
        try(var st=c.createStatement();var rows=st.executeQuery(sql)) {while(rows.next())out.add(rows.getString(1));}
        return out;
    }
    public static void main(String[] args)throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));long scn=Long.parseLong(args[1]);long target=Long.parseLong(args[2]),index=Long.parseLong(args[3]);
        Path output=Path.of(args[4]);var commands=new ArrayList<String>();var out=new ArrayList<String>();
        try(var c=DriverManager.getConnection(secret.get(2),secret.get(0),secret.get(1))) {
            out.addAll(query(c,"select 'SOURCE|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database",commands));
            if(!out.equals(List.of("SOURCE|1276726821|1276717861|NO|NO")))throw new IllegalStateException("SOURCE_IDENTITY_OR_LOGGING_MISMATCH");
            if(!query(c,"select con_uid||'|'||con_id||'|'||name from v$containers where con_id=to_number(sys_context('USERENV','CON_ID'))",commands).equals(List.of("677737149|3|ORKPDB")))throw new IllegalStateException("CONTAINER_MISMATCH");
            for(String name:List.of("OBJ$","TAB$","COL$")) {
                var role=query(c,"select 'ROLE|'||object_id||'|'||data_object_id||'|'||object_name from dba_objects where owner='SYS' and object_name='"+name+"' and object_type='TABLE'",commands);
                if(role.size()!=1)throw new IllegalStateException("ROLE_COVERAGE");out.addAll(role);long logical=Long.parseLong(role.get(0).split("\\|")[1]);
                var layouts=query(c,"select 'LAYOUT|'||intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$ from sys.col$ as of scn "+scn+" where obj#="+logical+" order by segcol#",commands);
                var cols=new ArrayList<Column>();
                for(String line:layouts) {
                    String[] f=line.split("\\|");int seg=Integer.parseInt(f[2]);
                    if(seg!=cols.size()+1 || !f[3].matches("[A-Z][A-Z0-9_$#]*"))throw new IllegalStateException("LAYOUT_PROFILE");
                    cols.add(new Column(Integer.parseInt(f[1]),seg,f[3],Integer.parseInt(f[4]),Integer.parseInt(f[5])));out.add(name+"|"+line);
                }
                if(cols.isEmpty())throw new IllegalStateException("EMPTY_LAYOUT");
                out.addAll(query(c,"select 'TABLE_POSITION|"+name+"|'||tab#||'|'||bobj#||'|'||cols||'|'||clucols from sys.tab$ as of scn "+scn+" where obj#="+logical,commands));
                StringBuilder sql=new StringBuilder("select obj#,dbms_rowid.rowid_object(rowid),dbms_rowid.rowid_relative_fno(rowid),dbms_rowid.rowid_block_number(rowid),dbms_rowid.rowid_row_number(rowid)");
                for(var col:cols) {
                    if(!List.of(1,2,8,12,23,180).contains(col.type()))throw new IllegalStateException("UNSUPPORTED_LAYOUT_TYPE:"+col.type());
                    sql.append(',').append(col.name());sql.append(',').append(col.type()==8?"cast(null as varchar2(10))":"dump("+col.name()+",16)");
                }
                sql.append(" from sys.").append(name).append(" as of scn ").append(scn).append(" where obj#").append(name.equals("OBJ$")?" in ("+target+","+index+")":"="+target).append(" order by 1,rowid");commands.add(sql.toString());
                try(var st=c.createStatement();var rows=st.executeQuery(sql.toString())) {
                    while(rows.next()) {
                        StringBuilder row=new StringBuilder("SYS_ROW|").append(name);
                        for(int n=1;n<=5;n++)row.append('|').append(rows.getString(n));out.add(row.toString());
                        for(int n=0;n<cols.size();n++) {
                            var col=cols.get(n);int at=6+2*n;String value=rows.getString(at),dump=rows.getString(at+1),jdbc;
                            try {byte[] b=rows.getBytes(at);jdbc=b==null?"NULL":HexFormat.of().formatHex(b);}
                            catch(SQLException ex){jdbc="UNSUPPORTED:"+ex.getErrorCode();}
                            if(col.type()==8 && value!=null)throw new IllegalStateException("NONNULL_LONG_NOT_ACQUIRED");
                            out.add("CELL|"+name+"|"+rows.getString(1)+"|"+rows.getString(5)+"|"+col.segment()+"|"+col.internal()+"|"+col.name()+"|"+col.type()+"|"+(dump==null?"NULL":dump)+"|JDBC_BYTES="+jdbc);
                        }
                    }
                }
            }
        } finally {Files.write(output.resolve("research-query.sql"),commands);Files.write(output.resolve("partial-results.txt"),out);}
        System.out.println(String.join("\n",out));
    }
}
