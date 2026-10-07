package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class TransactionLifecycleCheckpointTest {
    final TransactionLifecycleTest f=new TransactionLifecycleTest();
    String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    TransactionLifecycle restored(TransactionLifecycle state)throws Exception {
        byte[] bytes=state.checkpoint();var result=TransactionLifecycle.restore(bytes,sha(bytes));assertArrayEquals(bytes,result.checkpoint());return result;
    }
    @Test void restoresPendingIdentityAndClosesOnNextArchive()throws Exception {
        var state=restored(f.begun());state.archive(f.archive(9,200,300,42));state.undo(f.record(210,f.undo()),f.undo());
        var end=state.end(f.record(220,f.end(2)),f.end(2));state.finish();
        assertEquals(110,end.beginRecordScn());assertEquals(220,end.endRecordScn());assertEquals(new HeapRowPatch.Xid(10,32,1136),end.xid());
    }
    @Test void preservesClosedIdentityAndRejectsDuplicateEnd()throws Exception {
        var original=f.begun();original.end(f.record(120,f.end(2)),f.end(2));var state=restored(original);
        assertTrue(state.knowsEnd(f.record(130,f.end(2)),f.end(2)));assertThrows(RedoException.class,()->state.end(f.record(130,f.end(2)),f.end(2)));
    }
    @Test void restoredStateStillRejectsSourceChangeGapAndIncompleteFinish()throws Exception {
        var source=restored(f.begun());assertThrows(RedoException.class,()->source.archive(f.archive(9,200,300,43)));
        var gap=restored(f.begun());assertThrows(RedoException.class,()->gap.archive(f.archive(10,200,300,42)));
        var pending=restored(f.begun());assertThrows(RedoException.class,pending::finish);assertThrows(RedoException.class,pending::checkpoint);
    }
    @Test void externalDigestTruncationAndUntrustedCanonicalEditsRefuse()throws Exception {
        byte[] bytes=f.begun().checkpoint();assertThrows(RedoException.class,()->TransactionLifecycle.restore(bytes,"0".repeat(64)));
        byte[] cut=java.util.Arrays.copyOf(bytes,bytes.length-1);assertThrows(RedoException.class,()->TransactionLifecycle.restore(cut,sha(cut)));
        String text=new String(bytes,StandardCharsets.UTF_8);String pending=text.substring(text.indexOf("P|"));
        for(String edit:new String[]{text+pending,text.replace("|1136|10|110","|1136|10|300"),text.replace("P|677737149|3|","P|677737149|0|"),text.replace("ORK_TRANSACTION_IDENTITIES_V1","ORK_TRANSACTION_IDENTITIES_V2")}) {
            byte[] changed=edit.getBytes(StandardCharsets.UTF_8);assertThrows(RedoException.class,()->TransactionLifecycle.restore(changed,sha(changed)));
        }
    }
}
