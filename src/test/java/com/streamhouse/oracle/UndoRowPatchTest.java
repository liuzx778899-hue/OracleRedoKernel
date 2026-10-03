package com.streamhouse.oracle;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoRowPatchTest {
    static final String FIRST="5c00da10220000000a0005006d040000f2002300";
    static final String HEADER="401d0100401d010005000000000000000b01052200006d04";
    static final String KTB="020db77100000000be2c4002f2002100";
    static final String KDO="8300000382000003fa120501010000002c01120000000401fdff000000";
    RedoRecords.Vector vector(String... fields) {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,3,
                Arrays.stream(fields).map(HexFormat.of()::parseHex).toList());
    }
    @Test void updateBeforeValueHasUndoIdentityAndAbsentKey() throws Exception {
        var u=UndoRowPatch.decode(vector(FIRST,HEADER,KTB,KDO,"0100","6b656570"),4);
        assertEquals(new HeapRowPatch.Xid(10,5,1133),u.identity().xid());
        assertEquals(new HeapRowPatch.Uba(0x02402cbe,242,35),u.identity().uba());
        assertEquals(34,u.predecessorRecord());
        assertEquals(HeapRowPatch.Kind.UPDATE,u.inverse().kind());
        assertEquals("keep",OracleScalars.varchar2(u.inverse().columns().get(1).bytes(),"AL32UTF8"));
        assertEquals(HeapRowPatch.State.ABSENT,u.inverse().columns().get(0).state());
        assertNull(u.inverse().xid()); assertNull(u.inverse().uba());
    }
    @Test void insertInverseDeleteDoesNotInventPriorRow() throws Exception {
        var u=UndoRowPatch.decode(vector("44007c10220000000a0005006d040000f2002471",
                "401d0100401d010005000000000000000b01052300006d04",
                "020d000000000000be2c4002f2002300","8300000382000003fa1203010100000002000000"),4);
        assertEquals(HeapRowPatch.Kind.DELETE,u.inverse().kind()); assertEquals(2,u.inverse().slot());
        assertTrue(u.inverse().columns().stream().allMatch(c -> c.state()==HeapRowPatch.State.ABSENT));
        assertEquals(36,u.identity().uba().record());
    }
    @Test void historicalKtbIdentityIsNotUndoIdentity() throws Exception {
        var u=UndoRowPatch.decode(vector("c4009415120000000a0003006e040000f2001800",
                "401d0100401d010005000000000000000b010300080c010000000000be2c4002f200170098c32000000000009ac320000000000000000000b3c9200000000000ba2c40020000000000000000",
                "040d0000000000000a000c0051040000be2c4002f2000700008000809ac92000",
                "8300000382000003fa120201010000002c00042e7f7800001068c27b000000000000000000000000180000000000000000",
                "c102","62657461","c202182e","787e0a04090a0b"),4);
        assertEquals(new HeapRowPatch.Xid(10,3,1134),u.identity().xid());
        assertEquals(HeapRowPatch.Kind.INSERT,u.inverse().kind());
        assertEquals("beta",OracleScalars.varchar2(u.inverse().columns().get(1).bytes(),"AL32UTF8"));
        assertNull(u.inverse().xid());
    }
    @Test void retainsNullAndDoesNotAcceptWrongWrapOrSlot() throws Exception {
        var u=UndoRowPatch.decode(vector(FIRST,HEADER,KTB,KDO,"0100",""),4);
        assertEquals(HeapRowPatch.State.NULL,u.inverse().columns().get(1).state());
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector(FIRST,
                "401d0100401d010005000000000000000b01052200006e04",KTB,KDO,"0100","6b656570"),4));
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector(FIRST,
                "401d0100401d010005000000000000000b01062200006d04",KTB,KDO,"0100","6b656570"),4));
    }
    @Test void refusesUnknownKtbTruncationAndColumnMapPoison() {
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector(FIRST,HEADER,"00",KDO,"0100","6b656570"),4));
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector(FIRST,HEADER,KTB,"00"),4));
        assertThrows(RedoException.class,()->UndoRowPatch.decode(vector(FIRST,HEADER,KTB,KDO,"0400","6b656570"),4));
    }
}
