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
}
