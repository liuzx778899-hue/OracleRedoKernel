package com.streamhouse.engines.oracle;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleStatisticsAcquisitionTest {
    Connection connection(boolean wrongMapping,List<String> seen) {
        var columns=new ArrayList<String>();var projection=new ArrayList<String>();
        for(int i=1;i<=18;i++) {
            String name=i==1?"OBJ#":i==2?"COL#":i==15?"INTCOL#":"SPARE"+i;
            columns.add(i+"|"+i+"|"+name+"|2|"+(i==1 || i==2 || i==15?1:0));projection.add(i+"|"+i+"|"+name);
        }
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(!m.getName().equals("createStatement"))throw new AssertionError(m.getName());
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(s,method,args)->{
                if(method.getName().equals("close"))return null;
                if(!method.getName().equals("executeQuery"))throw new AssertionError(method.getName());
                String sql=(String)args[0];seen.add(sql);List<String> values;
                if(sql.contains("from dba_objects") || sql.contains("from SYS.OBJ$")) {
                    String id=sql.contains("'HIST_HEAD$'")?"68":sql.contains("'I_HH_OBJ#_COL#'")?"69":"70";values=List.of(id+"|"+id);
                } else if(sql.contains("from SYS.COL$"))values=columns;
                else if(sql.contains("from dba_tab_cols"))values=projection;
                else if(sql.contains("from SYS.TAB$"))values=List.of("-1|18|0");
                else if(sql.contains("from SYS.IND$"))values=List.of(sql.contains("obj#=69")?"4195224":"4195232");
                else if(sql.contains("from SYS.ICOL$"))values=List.of("1|1","2|"+(wrongMapping?"3":sql.contains("obj#=69")?"2":"15"));
                else throw new AssertionError(sql);
                int[] at={-1};return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,rm,ra)->switch(rm.getName()) {
                    case "next" -> ++at[0]<values.size();case "getString" -> values.get(at[0]);case "close" -> null;default -> throw new AssertionError(rm.getName());
                });
            });
        });
    }
    @Test void metadataUsesFixedScnAndBindsBothIndexColumns()throws Exception {
        var seen=new ArrayList<String>();var result=OracleStatisticsAcquisition.acquire(connection(false,seen),12345);
        assertTrue(result.frames().contains("DICT_STATS_INDEX|BEFORE|I_HH_OBJ#_INTCOL#|70|70|4195232"));
        assertEquals(3,result.after().size());
        assertTrue(seen.stream().filter(s->s.contains("from SYS.")).allMatch(s->s.contains("as of scn 12345")));
    }
    @Test void indexNameAloneDoesNotAuthorizeItsKeyMapping() {
        assertEquals("STATISTICS_INDEX_COLUMN_MAPPING",assertThrows(SQLException.class,
                ()->OracleStatisticsAcquisition.acquire(connection(true,new ArrayList<>()),12345)).getMessage());
    }
}
