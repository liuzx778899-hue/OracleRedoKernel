package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.HeapRowPatch.*;

class CapturedLobBatchTest {
    private static final String SHA="9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995";
    private List<CaptureJournal.Verified> captures()throws Exception {
        return List.of(new CaptureJournal().readRawClosed(Path.of(System.getProperty("ork.lob.fixture")),SHA));
    }
    private CapturedLobTransactions.Scope scope(LobLocator.Storage storage) {
        return storage==LobLocator.Storage.BASICFILE
                ?new CapturedLobTransactions.Scope(677737149,3,79334,79339,storage,79335,79336,50365977,79337,79338,50365993)
                :new CapturedLobTransactions.Scope(677737149,3,79340,79345,storage,79341,79342,0,79343,79344,0);
    }
    @Test void rejectsMissingScopeBeforeReadingInput() {
        assertEquals("INVALID_LOB_TRANSACTION_SCOPE",assertThrows(RedoException.class,()->CapturedLobBatch.read(List.of(),null)).getMessage());
    }
    @Test void rejectsMalformedClobInsteadOfReplacingCharacters()throws Exception {
        assertThrows(RedoException.class,()->CapturedSecureFileLobValues.logical(LobLocator.Kind.CLOB,new byte[]{(byte)0xd8,0}));
        assertThrows(RedoException.class,()->CapturedSecureFileLobValues.logical(LobLocator.Kind.CLOB,new byte[]{0}));
        assertArrayEquals(new byte[]{0,(byte)0xff},CapturedSecureFileLobValues.logical(LobLocator.Kind.BLOB,new byte[]{0,(byte)0xff}));
    }
    @Test @EnabledIfSystemProperty(named="ork.lob.fixture",matches=".+")
    void frozenBasicFileCompleteEventsAndRollback()throws Exception {check(LobLocator.Storage.BASICFILE,10,2);}
    @Test @EnabledIfSystemProperty(named="ork.lob.fixture",matches=".+")
    void frozenSecureFileCompleteEventsAndRollback()throws Exception {check(LobLocator.Storage.SECUREFILE,9,1);}
    private void check(LobLocator.Storage storage,int transactions,int rollbacks)throws Exception {
        var batch=CapturedLobBatch.read(captures(),scope(storage));assertEquals(transactions,batch.transactions().size());
        assertEquals(storage==LobLocator.Storage.BASICFILE?2:14,batch.maintenanceTransactions());
        assertEquals(storage==LobLocator.Storage.BASICFILE?16:114,batch.maintenanceOriginals());
        assertEquals(rollbacks,batch.transactions().stream().filter(t->t.identity().rolledBack()).count());
        assertEquals(8,batch.events().size());assertEquals(8,batch.events().stream().map(CapturedHeapBatch.Event::eventId).distinct().count());
        long previous=0;for(var event:batch.events()) {assertFalse(event.transaction().rolledBack());assertTrue(event.transaction().endRecordScn()>previous);previous=event.transaction().endRecordScn();}
        var large=batch.events().get(1);assertEquals(Kind.UPDATE,large.operation());
        assertEquals("00ff80c328000102",large.before().get(1).hex());assertEquals("736565642de4b8ad",large.before().get(2).hex());
        assertEquals(10000,large.after().get(1).hex().length()/2);assertEquals(15000,large.after().get(2).hex().length()/2);
        assertEquals(new ArchivedHeapRows.Cell(State.VALUE,""),batch.events().get(3).after().get(1));
        assertEquals(new ArchivedHeapRows.Cell(State.NULL,""),batch.events().get(4).after().get(1));
        assertEquals(Kind.DELETE,batch.events().get(5).operation());assertTrue(batch.events().get(5).after().isEmpty());
        assertEquals(10000,batch.events().get(6).after().get(1).hex().length()/2);
        assertEquals(512,batch.events().get(7).after().get(1).hex().length()/2);
        assertEquals(128,new String(HexFormat.of().parseHex(batch.events().get(7).after().get(2).hex()),java.nio.charset.StandardCharsets.UTF_8).length());
        for(var t:batch.transactions())if(t.identity().rolledBack())assertTrue(t.graph().live().isEmpty());
    }
    @Test @EnabledIfSystemProperty(named="ork.lob.fixture",matches=".+")
    void frozenForeignLobObjectCannotProduceBatch()throws Exception {
        var s=scope(LobLocator.Storage.BASICFILE);
        var wrong=new CapturedLobTransactions.Scope(s.uid(),s.container(),s.heap(),s.primaryIndex(),s.storage(),99999,s.blobIndex(),s.blobBitmap(),s.clob(),s.clobIndex(),s.clobBitmap());
        assertEquals("LOB_GRAPH_UNKNOWN_OBJECT",assertThrows(RedoException.class,()->CapturedLobBatch.read(captures(),wrong)).getMessage());
    }
}
