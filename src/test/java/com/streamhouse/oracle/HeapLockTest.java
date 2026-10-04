package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapLockTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    List<byte[]> fields() {return List.of(hex("040d0001bc0106000a0021006c060000b509400207012e00008000809dd82300"),hex("9301000392010003fa1204010100010000000000"));}
    @Test void decodesActualLockInverseWithoutInventingColumnValues() throws Exception {
        var p=HeapRowPatch.decodeKdo(fields(),4,0x03000193,3);
        assertEquals(HeapRowPatch.Kind.LOCK,p.kind());assertEquals(0,p.slot());assertEquals(0,p.lockTo());
        assertNull(p.extent());assertTrue(p.columns().stream().allMatch(c->c.state()==HeapRowPatch.State.ABSENT));
    }
    @Test void refusesLockLayoutOutsideActualObservedProfile() {
        for (int at:new int[]{10,11,12,13,14,18,19}) {
            var f=fields();f.get(1)[at]=3;
            assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(f,4,0x03000193,3));
        }
        assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(List.of(fields().get(0),fields().get(1),new byte[]{1}),4,0x03000193,3));
    }
}
