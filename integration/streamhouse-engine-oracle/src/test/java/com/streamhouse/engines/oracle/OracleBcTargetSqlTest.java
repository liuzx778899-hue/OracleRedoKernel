package com.streamhouse.engines.oracle;

import com.streamhouse.spi.EventEnvelope;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleBcTargetSqlTest {
    @Test void bcBeforeAndAfterBindExplicitEraInsteadOfJavaCalendar()throws Exception {
        var sql=new ArrayList<String>();var binds=new TreeMap<Integer,Object>();
        var ps=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().equals("close"))return null;
            if(m.getName().equals("executeUpdate"))return 1;
            if(m.getName().equals("setObject")){binds.put((Integer)a[0],a[1]);return null;}
            throw new AssertionError(m.getName());
        });
        var conn=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("prepareStatement")){sql.add((String)a[0]);return ps;}throw new AssertionError(m.getName());
        });
        var before=new LinkedHashMap<String,Object>();
        before.put("D",new OracleTemporalValue("DATE","-0043-03-15T12:34:55"));
        before.put("TS",new OracleTemporalValue("TIMESTAMP","0000-01-01T00:00:00.000000000"));
        var after=new LinkedHashMap<String,Object>();
        after.put("D",new OracleTemporalValue("DATE","-0043-03-15T12:34:56"));
        after.put("TS",new OracleTemporalValue("TIMESTAMP","0000-01-01T00:00:00.000000001"));
        var event=new EventEnvelope("oracle1:bc-test",null,null,EventEnvelope.Operation.UPDATE,null,before,after,List.of(),null);
        OracleTargetBatchWriter.applyRow(conn,new OracleTargetBatchWriter.Target("ORKTGT","BC1","target"),event,List.of());
        assertTrue(sql.get(0).contains("where D=to_date(?, 'YYYY-MM-DD\"T\"HH24:MI:SS BC') and TS=to_timestamp"));
        assertEquals(List.of("0044-03-15T12:34:56 BC","0001-01-01T00:00:00.000000001 BC",
                "0044-03-15T12:34:55 BC","0001-01-01T00:00:00.000000000 BC"),new ArrayList<>(binds.values()));
    }
}
