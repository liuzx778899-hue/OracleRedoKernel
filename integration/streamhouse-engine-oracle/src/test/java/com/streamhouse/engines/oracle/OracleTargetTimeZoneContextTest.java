package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleTargetTimeZoneContextTest {
    Connection metadata(String zone,int version) {
        int[] calls={0};
        var rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()) {
            case "next"->++calls[0]==1;case "getInt"->version;case "getString"->zone;case "close"->null;default->throw new AssertionError(m.getName());
        });
        var statement=(Statement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(p,m,a)->switch(m.getName()) {
            case "executeQuery"->rows;case "close"->null;default->throw new AssertionError(m.getName());
        });
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("createStatement"))return statement;throw new AssertionError("unexpected target action: "+m.getName());
        });
    }
    CapturedHeapBatch.Schema schema(String type) {
        var role=new FrozenHeapBaseline.ObjectRole("ORKLAB","T",1,1);
        return new CapturedHeapBatch.Schema(role,role,List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"TZ",type,true,false)),"a".repeat(64),new OracleTimeZoneContext("+00:00",32,Map.of()));
    }
    @Test void mismatchRefusesBeforeTargetLocksOrWritesAndTstzAllowsDifferentDatabaseZone()throws Exception {
        assertEquals("ORACLE_TARGET_TIMEZONE_CONTEXT_MISMATCH",assertThrows(SQLException.class,()->OracleTargetBatchWriter.requireTimeZoneContext(metadata("+00:00",33),schema("TIMESTAMP WITH TIME ZONE"))).getMessage());
        assertThrows(SQLException.class,()->OracleTargetBatchWriter.requireTimeZoneContext(metadata("+08:00",32),schema("TIMESTAMP WITH LOCAL TIME ZONE")));
        OracleTargetBatchWriter.requireTimeZoneContext(metadata("+08:00",32),schema("TIMESTAMP WITH TIME ZONE"));
    }
}
