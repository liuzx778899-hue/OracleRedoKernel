package com.streamhouse.engines.oracle;

import com.streamhouse.spi.OracleBatchEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Source SELECT only. A fixed-SCN initial snapshot is not historical dictionary or publication approval. */
public final class OracleSnapshotAcquirer {
    public record Scope(String owner,String table,String index) {
        public Scope {identifier(owner);identifier(table);identifier(index);}
    }
    public static final class Acquisition {
        private final byte[] snapshot;
        private final String sha256;
        private final long scn;
        private Acquisition(byte[] snapshot,long scn) throws Exception {
            this.snapshot=snapshot.clone();this.scn=scn;
            sha256=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot));
        }
        public byte[] snapshot(){return snapshot.clone();}
        public String sha256(){return sha256;}
        public long scn(){return scn;}
    }
    private static void identifier(String s) {
        if(s==null || !s.matches("[A-Z][A-Z0-9_$#]{0,127}"))throw new IllegalArgumentException("SNAPSHOT_IDENTIFIER_REQUIRED");
    }
    private static List<String> query(Connection c,String sql) throws SQLException {
        var result=new ArrayList<String>();
        try(var statement=c.createStatement();var rows=statement.executeQuery(sql)) {
            while(rows.next())result.add(rows.getString(1));
        }
        return List.copyOf(result);
    }
    private static String one(Connection c,String sql) throws SQLException {
        var rows=query(c,sql);if(rows.size()!=1 || rows.get(0)==null)throw new SQLException("SNAPSHOT_SINGLE_VALUE_REQUIRED");return rows.get(0);
    }
    private static String source(Connection c) throws SQLException {
        return one(c,"select d.dbid||'|'||d.activation#||'|'||i.resetlogs_id||'|'||d.resetlogs_change#||'|'||v.thread#||'|'||p.value from v$database d cross join v$database_incarnation i cross join v$instance v cross join v$parameter p where i.status='CURRENT' and p.name='compatible'");
    }
    private static String pdb(Connection c) throws SQLException {
        return one(c,"select con_uid||'|'||con_id||'|'||name from v$containers where con_id=to_number(sys_context('USERENV','CON_ID'))");
    }
    private static String role(Connection c,Scope s,String name,String type) throws SQLException {
        return one(c,"select owner||'|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='"+s.owner()+"' and object_name='"+name+"' and object_type='"+type+"'");
    }
    private static List<String> columns(Connection c,Scope s) throws SQLException {
        return query(c,"select segment_column_id||'|'||internal_column_id||'|'||column_name||'|'||data_type||'|'||nullable||'|'||hidden_column||'|'||virtual_column from dba_tab_cols where owner='"+s.owner()+"' and table_name='"+s.table()+"' order by segment_column_id");
    }
    private static List<String> temporalRows(Connection c,String table,long scn,List<String> columns)throws Exception {
        var expressions=new ArrayList<String>();
        expressions.add("'ROW|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)");
        for(String column:columns) {
            var f=column.split("\\|",-1);String name=f[2],type=f[3];
            expressions.add(List.of("DATE","TIMESTAMP","BLOB","CLOB").contains(type)?name:
                    "rawtohex("+(type.equals("RAW")?name:"utl_raw."+(type.equals("NUMBER")?"cast_from_number":"cast_to_raw")+"("+name+")")+")");
        }
        var result=new ArrayList<String>();
        try(var statement=c.createStatement();var rows=statement.executeQuery("select "+String.join(",",expressions)+" from "+table+" as of scn "+scn+" order by rowid")) {
            while(rows.next()) {
                var row=new StringBuilder(rows.getString(1));
                for(int i=0;i<columns.size();i++) {
                    String type=columns.get(i).split("\\|",-1)[3],raw;
                    if(type.equals("DATE") || type.equals("TIMESTAMP")) {
                        var value=rows.getObject(i+2,java.time.LocalDateTime.class);
                        raw=value==null?null:HexFormat.of().withUpperCase().formatHex(com.streamhouse.oracle.OracleScalars.temporalBytes(value,type.equals("TIMESTAMP")));
                    } else if(type.equals("BLOB")) {
                        var lob=rows.getBlob(i+2);raw=null;
                        if(lob!=null)try(var stream=lob.getBinaryStream()) {raw=HexFormat.of().withUpperCase().formatHex(stream.readAllBytes());}finally{lob.free();}
                    } else if(type.equals("CLOB")) {
                        var lob=rows.getClob(i+2);raw=null;
                        if(lob!=null)try(var reader=lob.getCharacterStream()) {
                            var text=new java.io.StringWriter();reader.transferTo(text);
                            var bytes=StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text.toString()));
                            byte[] value=new byte[bytes.remaining()];bytes.get(value);raw=HexFormat.of().withUpperCase().formatHex(value);
                        }finally{lob.free();}
                    } else raw=rows.getString(i+2);
                    row.append('|').append(raw==null?"NULL":raw);
                }
                result.add(row.toString());
            }
        }
        return List.copyOf(result);
    }
    /** Owns/closes a fresh source connection. No credentials, SQL truth, dump or mutable receipt is accepted as approval. */
    public Acquisition acquire(Connection fresh,Scope scope,OracleBatchEvidence.Source expected) throws Exception {
        return acquire(fresh,scope,expected,0);
    }
    /** Same-SCN SYS anchors from this source connection, not an externally supplied dictionary receipt. */
    public Acquisition acquireDictionaryBound(Connection fresh,Scope scope,OracleBatchEvidence.Source expected) throws Exception {
        return acquire(fresh,scope,expected,1);
    }
    /** Complete same-SCN SYS rows/layout/index identities; publication still requires raw historical replay. */
    public Acquisition acquireHistoryBound(Connection fresh,Scope scope,OracleBatchEvidence.Source expected) throws Exception {
        return acquire(fresh,scope,expected,2);
    }
    private static String dictionaryRole(Connection c,String name) throws SQLException {
        return one(c,"select owner||'|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name='"+name+"' and object_type='TABLE'");
    }
    private static List<String> lobRoles(Connection c,Scope scope,List<String> columns)throws SQLException {
        var result=new ArrayList<String>();
        for(int i=0;i<columns.size();i++) {
            var f=columns.get(i).split("\\|",-1);if(!List.of("BLOB","CLOB").contains(f[3]))continue;
            String entry=one(c,"select segment_name||'|'||index_name||'|'||securefile||'|'||in_row||'|'||logging from dba_lobs where owner='"+scope.owner()+"' and table_name='"+scope.table()+"' and column_name='"+f[2]+"'");
            var l=entry.split("\\|",-1);if(l.length!=5 || !List.of("YES","NO").contains(l[2]) || !l[3].equals("YES") || !l[4].equals("YES"))throw new SQLException("SNAPSHOT_LOB_STORAGE_UNSUPPORTED");
            identifier(l[0]);identifier(l[1]);
            result.add((i+1)+"|"+(l[2].equals("YES")?"SECUREFILE":"BASICFILE")+"|"+role(c,scope,l[0],"LOB")+"|"+role(c,scope,l[1],"INDEX"));
        }
        return List.copyOf(result);
    }
    private Acquisition acquire(Connection fresh,Scope scope,OracleBatchEvidence.Source expected,int version) throws Exception {
        boolean dictionary=version>0,history=version==2;
        try(var c=fresh) {
            if(!c.getAutoCommit())throw new SQLException("SNAPSHOT_FRESH_CONNECTION_REQUIRED");
            if(expected==null || expected.compatibility()!=0x13000000L)throw new SQLException("SNAPSHOT_EXPECTED_SOURCE_REQUIRED");
            String src=source(c),container=pdb(c);
            String wanted=expected.dbid()+"|"+expected.activationId()+"|"+expected.resetlogsId()+"|"+expected.resetlogsScn()+"|"+expected.thread()+"|19.0.0";
            String[] p=container.split("\\|",-1);
            if(!src.equals(wanted) || p.length!=3 || !p[0].equals(Long.toString(expected.containerUid())) || !p[1].equals(Long.toString(expected.containerId())))
                throw new SQLException("SNAPSHOT_SOURCE_SCOPE_MISMATCH");
            identifier(p[2]);
            if(!one(c,"select value from nls_database_parameters where parameter='NLS_CHARACTERSET'").equals("AL32UTF8"))throw new SQLException("SNAPSHOT_CHARSET_UNSUPPORTED");
            long scn=Long.parseLong(one(c,"select to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') from dual"));
            String heap=role(c,scope,scope.table(),"TABLE"),index=role(c,scope,scope.index(),"INDEX");var physicalBefore=columns(c,scope);
            var before=physicalBefore.stream().map(row->row.replaceFirst("\\|TIMESTAMP\\([0-9]\\)\\|","|TIMESTAMP|")).toList();
            if(!one(c,"select count(*) from dba_indexes where owner='"+scope.owner()+"' and index_name='"+scope.index()+"' and table_owner='"+scope.owner()+"' and table_name='"+scope.table()+"' and index_type='NORMAL' and partitioned='NO'").equals("1"))throw new SQLException("SNAPSHOT_INDEX_SCOPE_UNSUPPORTED");
            if(before.isEmpty() || before.size()>255)throw new SQLException("SNAPSHOT_COLUMNS_UNSUPPORTED");
            boolean hasLob=before.stream().anyMatch(row->List.of("BLOB","CLOB").contains(row.split("\\|",-1)[3]));
            if(hasLob && (!history || before.size()!=3 || !before.stream().map(row->row.split("\\|",-1)[3]).toList().equals(List.of("NUMBER","BLOB","CLOB"))))
                throw new SQLException("SNAPSHOT_LOB_HISTORY_LAYOUT_REQUIRED");
            var lobBefore=hasLob?lobRoles(c,scope,before):List.<String>of();
            var projection=new StringBuilder();
            for(int i=0;i<before.size();i++) {
                String[] f=before.get(i).split("\\|",-1);
                if(f.length!=7 || !f[0].equals(Integer.toString(i+1)) || !f[1].equals(f[0]) || !List.of("NUMBER","VARCHAR2","RAW","DATE","TIMESTAMP","BLOB","CLOB").contains(f[3])
                        || !List.of("Y","N").contains(f[4]) || !List.of("YES","NO").contains(f[5]) || !f[6].equals("NO"))throw new SQLException("SNAPSHOT_PHYSICAL_LAYOUT_UNSUPPORTED");
                identifier(f[2]);String raw=f[3].equals("RAW")?f[2]:"utl_raw."+(f[3].equals("NUMBER")?"cast_from_number":"cast_to_raw")+"("+f[2]+")";
                projection.append("||'|'||case when ").append(f[2]).append(" is null then 'NULL' else rawtohex(").append(raw).append(") end");
            }
            String table=scope.owner()+"."+scope.table();
            boolean temporal=before.stream().anyMatch(col->col.split("\\|",-1)[3].equals("DATE") || col.split("\\|",-1)[3].equals("TIMESTAMP"));
            var rows=temporal || hasLob?temporalRows(c,table,scn,before):query(c,"select 'ROW|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)"+projection+" from "+table+" as of scn "+scn+" order by rowid");
            String count=one(c,"select count(*) from "+table+" as of scn "+scn);
            if(!count.equals(Integer.toString(rows.size())) || !heap.equals(role(c,scope,scope.table(),"TABLE")) || !index.equals(role(c,scope,scope.index(),"INDEX"))
                    || !physicalBefore.equals(columns(c,scope)) || !src.equals(source(c)) || !container.equals(pdb(c)))throw new SQLException("SNAPSHOT_ACQUISITION_CHANGED");
            var roles=new ArrayList<String>();var dictionaryFrames=new ArrayList<String>();OracleDictionaryAcquisition.Result historical=null;
            if(dictionary) {
                long heapId=Long.parseLong(heap.split("\\|")[2]),indexId=Long.parseLong(index.split("\\|")[2]);
                for(String name:List.of("OBJ$","TAB$","COL$")) {
                    roles.add(dictionaryRole(c,name));
                    var anchors=query(c,"select 'DICT_ANCHOR|"+name+"|'||obj#||'|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from SYS."+name+" as of scn "+scn+" where obj#"+(name.equals("OBJ$")?" in ("+heapId+","+indexId+")":"="+heapId)+" order by obj#,rowid");
                    int expectedCount=name.equals("OBJ$")?2:name.equals("TAB$")?1:before.size();
                    if(anchors.size()!=expectedCount)throw new SQLException("SNAPSHOT_DICTIONARY_ANCHOR_COVERAGE");
                    dictionaryFrames.addAll(anchors);
                }
                var historicalColumns=query(c,"select 'DICT_COLUMN|'||intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$||'|'||property from SYS.COL$ as of scn "+scn+" where obj#="+heapId+" order by segcol#");
                var wantedColumns=new ArrayList<String>();
                for(String col:before) {
                    var f=col.split("\\|",-1);wantedColumns.add("DICT_COLUMN|"+f[1]+"|"+f[0]+"|"+f[2]+"|"+com.streamhouse.oracle.OracleColumnTypes.oracleId(f[3])+"|"+(f[4].equals("Y")?0:2)+"|"+(f[5].equals("YES")?17179869216L:0));
                }
                if(!historicalColumns.equals(wantedColumns))throw new SQLException("SNAPSHOT_HISTORICAL_COLUMNS_MISMATCH");
                dictionaryFrames.addAll(historicalColumns);
                if(history) {
                    historical=OracleDictionaryAcquisition.acquire(c,scn,roles,dictionaryFrames.stream().filter(f->f.startsWith("DICT_ANCHOR|")).toList());
                    dictionaryFrames.addAll(historical.frames());
                }
                for(int i=0;i<roles.size();i++)if(!roles.get(i).equals(dictionaryRole(c,List.of("OBJ$","TAB$","COL$").get(i))))throw new SQLException("SNAPSHOT_DICTIONARY_ROLE_CHANGED");
                if(!heap.equals(role(c,scope,scope.table(),"TABLE")) || !index.equals(role(c,scope,scope.index(),"INDEX"))
                        || !physicalBefore.equals(columns(c,scope)) || !src.equals(source(c)) || !container.equals(pdb(c)))throw new SQLException("SNAPSHOT_DICTIONARY_ACQUISITION_CHANGED");
            }
            String format=history?"ORK_HEAP_SNAPSHOT_V3":dictionary?"ORK_HEAP_SNAPSHOT_V2":"ORK_HEAP_SNAPSHOT_V1";
            var out=new StringBuilder("FORMAT|").append(format).append("\nSOURCE|").append(src).append("\nPDB|").append(container).append("\nCHARSET|AL32UTF8\nSNAPSHOT_SCN|").append(scn).append('\n');
            out.append("HEAP|BEFORE|").append(heap).append("\nINDEX|BEFORE|").append(index).append('\n');
            for(String col:before)out.append("COLUMN|BEFORE|").append(col).append('\n');
            for(String lob:lobBefore)out.append("LOB|BEFORE|").append(lob).append('\n');
            for(String dict:roles)out.append("DICT_ROLE|BEFORE|").append(dict).append('\n');
            for(String frame:dictionaryFrames)out.append(frame).append('\n');
            for(String row:rows)out.append(row).append('\n');
            out.append("ROW_COUNT|").append(count).append("\nHEAP|AFTER|").append(heap).append("\nINDEX|AFTER|").append(index).append('\n');
            for(String col:before)out.append("COLUMN|AFTER|").append(col).append('\n');
            if(hasLob && !lobBefore.equals(lobRoles(c,scope,before)))throw new SQLException("SNAPSHOT_LOB_GENERATION_CHANGED");
            for(String lob:lobBefore)out.append("LOB|AFTER|").append(lob).append('\n');
            for(String dict:roles)out.append("DICT_ROLE|AFTER|").append(dict).append('\n');
            if(history)for(String frame:historical.after())out.append(frame).append('\n');
            out.append("SOURCE_AFTER|").append(src).append("\nEND|").append(format).append('\n');
            return new Acquisition(out.toString().getBytes(StandardCharsets.US_ASCII),scn);
        }
    }
}
