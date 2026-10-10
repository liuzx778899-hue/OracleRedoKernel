package com.streamhouse.oracle;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MixedLobScopeTest {
    private final List<FrozenHeapBaseline.PhysicalColumn> columns=List.of(
            new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
            new FrozenHeapBaseline.PhysicalColumn(2,2,"TXT","VARCHAR2",true,false),
            new FrozenHeapBaseline.PhysicalColumn(3,3,"JSON_BODY","CLOB",true,false),
            new FrozenHeapBaseline.PhysicalColumn(4,4,"BIN","BLOB",true,false),
            new FrozenHeapBaseline.PhysicalColumn(5,5,"XML_BODY","CLOB",true,false));
    private CapturedLobTransactions.Scope scope(List<CapturedLobTransactions.Lob> lobs) {
        return new CapturedLobTransactions.Scope(1,3,10,11,LobLocator.Storage.SECUREFILE,columns,lobs);
    }
    @Test void rejectsTypeMismatchDuplicateColumnAndObjectCollisionBeforeReadingRaw() {
        var json=new CapturedLobTransactions.Lob(2,LobLocator.Kind.CLOB,12,13,0);
        var blob=new CapturedLobTransactions.Lob(3,LobLocator.Kind.BLOB,14,15,0);
        var xml=new CapturedLobTransactions.Lob(4,LobLocator.Kind.CLOB,16,17,0);
        for(var lobs:List.of(
                List.of(json,blob,new CapturedLobTransactions.Lob(4,LobLocator.Kind.BLOB,16,17,0)),
                List.of(json,blob,new CapturedLobTransactions.Lob(2,LobLocator.Kind.CLOB,16,17,0)),
                List.of(json,blob,new CapturedLobTransactions.Lob(4,LobLocator.Kind.CLOB,10,17,0)),
                List.of(json,xml))) {
            assertEquals("INVALID_LOB_TRANSACTION_SCOPE",assertThrows(RedoException.class,
                    ()->CapturedLobTransactions.read(List.of(),scope(lobs))).getMessage());
        }
    }
    @Test void lobMappingUsesPhysicalColumnAndTypeRatherThanSecondOrThirdColumn() {
        var s=scope(List.of(new CapturedLobTransactions.Lob(2,LobLocator.Kind.CLOB,12,13,0),
                new CapturedLobTransactions.Lob(3,LobLocator.Kind.BLOB,14,15,0),new CapturedLobTransactions.Lob(4,LobLocator.Kind.CLOB,16,17,0)));
        assertEquals(LobLocator.Kind.CLOB,s.lob(2).kind());assertEquals(14,s.object(3));assertEquals(16,s.object(4));
        assertTrue(s.isLob(16));assertTrue(s.isLobIndex(17));assertFalse(s.isLob(10));
        assertThrows(UnsupportedOperationException.class,()->s.lobs().clear());
    }
}
