package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleRawProjectionTest {
    @Test void projectsBinaryIncludingZeroAndInvalidUtf8WithoutMutableArrays()throws Exception {
        var column=new FrozenHeapBaseline.PhysicalColumn(1,1,"BIN","RAW",true,false);
        var value=OracleBatchProjection.image(List.of(column),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"00FFC328"))).get("BIN");
        assertEquals(new OracleRawValue("00ffc328"),value);
        byte[] bytes=((OracleRawValue)value).bytes();bytes[0]=1;
        assertArrayEquals(new byte[]{0,(byte)255,(byte)195,40},((OracleRawValue)value).bytes());
        assertNull(OracleBatchProjection.image(List.of(column),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,""))).get("BIN"));
        assertThrows(IllegalArgumentException.class,()->new OracleRawValue(""));
    }
}
