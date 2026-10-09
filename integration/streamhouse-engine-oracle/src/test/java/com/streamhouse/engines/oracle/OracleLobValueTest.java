package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import com.streamhouse.spi.EventEnvelope;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleLobValueTest {
    @Test void exactBinaryAndUnicodeEmptyAndNullAreDistinct()throws Exception {
        var blob=new OracleLobValue("BLOB","00ff80c328");var copy=blob.bytes();copy[0]=1;
        assertEquals(0,blob.bytes()[0]);assertEquals("中😀",new OracleLobValue("CLOB","e4b8adf09f9880").text());
        assertThrows(IllegalArgumentException.class,()->new OracleLobValue("CLOB","c328"));
        var column=new FrozenHeapBaseline.PhysicalColumn(1,1,"C","CLOB",true,false);
        assertEquals(new OracleLobValue("CLOB",""),OracleBatchProjection.image(List.of(column),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,""))).get("C"));
        assertNull(OracleBatchProjection.image(List.of(column),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""))).get("C"));
    }
    @Test void fullBeforeLobsUseLengthAndContentWithCompleteStreamBinds()throws Exception {
        var before=new LinkedHashMap<String,Object>();before.put("ID",BigDecimal.ONE);before.put("B",new OracleLobValue("BLOB","00ff80"));before.put("C",new OracleLobValue("CLOB","e4b8ad"));
        var after=new LinkedHashMap<>(before);after.put("B",new OracleLobValue("BLOB",""));after.put("C",null);
        var sql=new ArrayList<String>();var binds=new TreeMap<Integer,Object>();
        var ps=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().equals("close"))return null;
            if(m.getName().equals("executeUpdate"))return 1;
            if(m.getName().equals("setBlob")){binds.put((Integer)a[0],((java.io.InputStream)a[1]).readAllBytes());return null;}
            if(m.getName().equals("setClob")){var w=new java.io.StringWriter();((java.io.Reader)a[1]).transferTo(w);binds.put((Integer)a[0],w.toString());return null;}
            if(m.getName().equals("setObject")){binds.put((Integer)a[0],a[1]);return null;}
            throw new AssertionError(m.getName());
        });
        var conn=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{if(m.getName().equals("prepareStatement")){sql.add((String)a[0]);return ps;}throw new AssertionError(m.getName());});
        var event=new EventEnvelope("oracle1:test",null,null,EventEnvelope.Operation.UPDATE,null,before,after,List.of(),null);
        OracleTargetBatchWriter.applyRow(conn,new OracleTargetBatchWriter.Target("ORKTGT","LOB1","target"),event,List.of());
        assertTrue(sql.get(0).contains("B=empty_blob()"));assertTrue(sql.get(0).contains("dbms_lob.getlength(B)=dbms_lob.getlength(?) and dbms_lob.compare(B,?)=0"));
        assertEquals(7,binds.size());assertNull(binds.get(2));assertArrayEquals(new byte[]{0,(byte)255,(byte)128},(byte[])binds.get(4));assertArrayEquals((byte[])binds.get(4),(byte[])binds.get(5));assertEquals("中",binds.get(6));assertEquals("中",binds.get(7));
    }
}
