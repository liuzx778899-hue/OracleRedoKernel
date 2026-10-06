package com.streamhouse.engines.oracle;

import com.streamhouse.spi.OracleBatchEvidence;
import java.util.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleHistoricalTargetMappingTest {
    List<OracleBatchEvidence.Column> columns(){return List.of(new OracleBatchEvidence.Column(1,1,"ID","NUMBER",false,false),
            new OracleBatchEvidence.Column(2,2,"TXT","VARCHAR2",true,false),new OracleBatchEvidence.Column(3,3,"NOTE","VARCHAR2",true,true),
            new OracleBatchEvidence.Column(4,4,"EXTRA","VARCHAR2",true,false));}
    @Test void stableNullableUnionDoesNotEraseLogicalFieldAbsenceOrHiddenValue()throws Exception {
        var logical=new LinkedHashMap<String,Object>();logical.put("ID",0);logical.put("TXT","after");logical.put("NOTE","anchor");
        var target=OracleTargetBatchWriter.targetImage(logical,columns());
        assertEquals(List.of("ID","TXT","NOTE","EXTRA"),new ArrayList<>(target.keySet()));assertNull(target.get("EXTRA"));
        assertEquals("anchor",target.get("NOTE"));assertFalse(logical.containsKey("EXTRA"));
        assertSame(logical,OracleTargetBatchWriter.targetImage(logical,List.of()));
        assertTrue(OracleTargetBatchWriter.targetImage(null,columns()).isEmpty());
    }
    @Test void omittedRequiredOrUnmappedColumnRefuses() {
        assertEquals("ORACLE_TARGET_HISTORICAL_REQUIRED_COLUMN_ABSENT",assertThrows(SQLException.class,
                ()->OracleTargetBatchWriter.targetImage(Map.of("TXT","x"),columns())).getMessage());
        assertEquals("ORACLE_TARGET_HISTORICAL_COLUMN_UNMAPPED",assertThrows(SQLException.class,
                ()->OracleTargetBatchWriter.targetImage(Map.of("ID",0,"ALIEN","x"),columns())).getMessage());
    }
}
