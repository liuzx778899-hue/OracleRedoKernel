package com.streamhouse.engines.oracle;

import com.streamhouse.spi.OracleBatchEvidence;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleSnapshotAcquirerTest {
    final OracleBatchEvidence.Source source=new OracleBatchEvidence.Source(1,2,0x13000000L,1,3,4,5,3);
    @Test void identifiersCannotInjectSourceSql() {
        assertThrows(IllegalArgumentException.class,()->new OracleSnapshotAcquirer.Scope("ORKLAB","T;DELETE FROM T","I"));
    }
    @Test void existingTransactionIsClosedAndRefusedBeforeSourceQuery() {
        boolean[] closed={false};
        var c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("getAutoCommit"))return false;
            if(m.getName().equals("close")){closed[0]=true;return null;}
            throw new AssertionError("source query must not execute");
        });
        assertThrows(SQLException.class,()->new OracleSnapshotAcquirer().acquire(c,new OracleSnapshotAcquirer.Scope("ORKLAB","T","I"),source));
        assertTrue(closed[0]);
    }
    @Test void latePhysicalSchemaChangeCannotIssueAnAcquisition() {
        var columns=new AtomicInteger();boolean[] closed={false};
        var c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("getAutoCommit"))return true;
            if(m.getName().equals("close")){closed[0]=true;return null;}
            if(m.getName().equals("createStatement"))return statement(columns);
            throw new AssertionError(m.getName());
        });
        var ex=assertThrows(SQLException.class,()->new OracleSnapshotAcquirer().acquire(c,new OracleSnapshotAcquirer.Scope("ORKLAB","T","I"),source));
        assertEquals("SNAPSHOT_ACQUISITION_CHANGED",ex.getMessage());assertTrue(closed[0]);assertEquals(2,columns.get());
    }
    Statement statement(AtomicInteger columns) {
        return (Statement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(p,m,a)->{
            if(m.getName().equals("close"))return null;
            if(!m.getName().equals("executeQuery"))throw new AssertionError(m.getName());
            String sql=(String)a[0];String value;
            if(sql.startsWith("select d.dbid"))value="1|2|3|4|1|19.0.0";
            else if(sql.contains("from v$containers"))value="5|3|ORKPDB";
            else if(sql.contains("nls_database_parameters"))value="AL32UTF8";
            else if(sql.contains("dbms_flashback"))value="20";
            else if(sql.contains("dba_objects"))value=sql.contains("'TABLE'")?"ORKLAB|T|3|3":"ORKLAB|I|4|4";
            else if(sql.contains("dba_tab_cols"))value=columns.incrementAndGet()==1?"1|1|ID|NUMBER|N|NO|NO":"1|1|ID|VARCHAR2|N|NO|NO";
            else if(sql.contains("select 'ROW|'"))value="ROW|3|12|1|0|C102";
            else if(sql.startsWith("select count(*)"))value="1";
            else throw new AssertionError(sql);
            int[] row={0};
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(q,n,b)->{
                if(n.getName().equals("close"))return null;
                if(n.getName().equals("next"))return ++row[0]==1;
                if(n.getName().equals("getString"))return value;
                throw new AssertionError(n.getName());
            });
        });
    }
    Connection dictionaryConnection(boolean[] closed,java.util.function.Function<String,List<String>> dictionaryRows) {
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("getAutoCommit"))return true;
            if(m.getName().equals("close")){closed[0]=true;return null;}
            if(!m.getName().equals("createStatement"))throw new AssertionError(m.getName());
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(q,n,b)->{
                if(n.getName().equals("close"))return null;
                if(!n.getName().equals("executeQuery"))throw new AssertionError(n.getName());
                String sql=(String)b[0];List<String> values;
                if(sql.contains("from SYS."))values=dictionaryRows.apply(sql);
                else if(sql.contains("dba_objects") && sql.contains("owner='SYS'")) {
                    String role=sql.contains("'OBJ$'")?"OBJ$|18|18":sql.contains("'TAB$'")?"TAB$|4|2":"COL$|21|2";
                    values=List.of("SYS|"+role);
                } else if(sql.contains("dba_tab_cols"))values=List.of("1|1|ID|NUMBER|N|NO|NO");
                else return statement(new AtomicInteger()).executeQuery(sql);
                int[] at={-1};
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,k,d)->{
                    if(k.getName().equals("close"))return null;
                    if(k.getName().equals("next"))return ++at[0]<values.size();
                    if(k.getName().equals("getString"))return values.get(at[0]);
                    throw new AssertionError(k.getName());
                });
            });
        });
    }
    List<String> dictionaryRows(String sql) {
        assertTrue(sql.contains("as of scn 20"));
        if(sql.startsWith("select 'DICT_COLUMN"))return List.of("DICT_COLUMN|1|1|ID|2|2|0");
        if(sql.contains("from SYS.OBJ$"))return List.of("DICT_ANCHOR|OBJ$|3|18|1|100|0","DICT_ANCHOR|OBJ$|4|18|1|100|1");
        if(sql.contains("from SYS.TAB$"))return List.of("DICT_ANCHOR|TAB$|3|2|1|101|0");
        return List.of("DICT_ANCHOR|COL$|3|2|1|101|1");
    }
    @Test void dictionaryAcquisitionUsesSameScnAndClosesItsSourceConnection() throws Exception {
        boolean[] closed={false};var result=new OracleSnapshotAcquirer().acquireDictionaryBound(
                dictionaryConnection(closed,this::dictionaryRows),new OracleSnapshotAcquirer.Scope("ORKLAB","T","I"),source);
        assertTrue(closed[0]);assertEquals(20,result.scn());
        String text=new String(result.snapshot(),java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("FORMAT|ORK_HEAP_SNAPSHOT_V2\n"));assertTrue(text.contains("DICT_ROLE|AFTER|SYS|COL$|21|2\n"));
        byte[] copy=result.snapshot();copy[0]=0;assertEquals('F',result.snapshot()[0]);
    }
    @Test void missingDictionaryRowsAndHistoricalColumnConflictCannotIssueAuthority() {
        for(boolean missing:new boolean[]{true,false}) {
            boolean[] closed={false};var ex=assertThrows(SQLException.class,()->new OracleSnapshotAcquirer().acquireDictionaryBound(
                    dictionaryConnection(closed,sql->missing && sql.contains("from SYS.TAB$")?List.of():
                            !missing && sql.startsWith("select 'DICT_COLUMN")?List.of("DICT_COLUMN|1|1|ID|1|2|0"):dictionaryRows(sql)),
                    new OracleSnapshotAcquirer.Scope("ORKLAB","T","I"),source));
            assertEquals(missing?"SNAPSHOT_DICTIONARY_ANCHOR_COVERAGE":"SNAPSHOT_HISTORICAL_COLUMNS_MISMATCH",ex.getMessage());
            assertTrue(closed[0]);
        }
    }
    @Test void historyAcquisitionCannotReplaceMissingScnRoleEvidenceWithCurrentIdentity() {
        boolean[] closed={false};
        var ex=assertThrows(SQLException.class,()->new OracleSnapshotAcquirer().acquireHistoryBound(
                dictionaryConnection(closed,sql->sql.startsWith("select obj#||")?List.of():dictionaryRows(sql)),
                new OracleSnapshotAcquirer.Scope("ORKLAB","T","I"),source));
        assertEquals("DICTIONARY_ROLE_SCN_MISMATCH",ex.getMessage());assertTrue(closed[0]);
    }
}
