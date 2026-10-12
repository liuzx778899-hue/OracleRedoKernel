package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Raw123 is a full-node retry sample, not evidence of surviving partial nodes. */
@EnabledIfSystemProperty(named="ork.array.retry",matches=".+")
class ArrayRetryFixtureTest {
    @Test void twoFailedBulkAttemptsPublishOnlyEighteenSuccessfulRowsAndKeepRootGuards()throws Exception {
        Path source=Path.of(System.getProperty("ork.array.retry")),baseline=source.resolve("baseline.snapshot");
        String baselineSha="551b854e5b04728db16cbba897b3d8c6a737f0c5d9479fa620fb2f311b1e67f3";
        var raw=new CaptureJournal().readRawClosed(source.resolve("inputs/123.raw"),"ab70466d175dd30df739a690c4f977449ba5d34c984bef31711697eba19e5a1a");
        var records=new RedoRecords().readCaptured(raw);var snapshot=FrozenHeapBaseline.read(baseline,baselineSha);
        var batch=HistoricalRecovery.committedPrefixCaptured(List.of(raw),baseline,baselineSha,50380795);
        assertEquals(18,batch.completed().events().size());assertEquals(0,batch.state().pendingTransactions());
        var ids=new TreeSet<Long>();
        for(var event:batch.completed().events()) {
            var row=event.row();assertEquals(HeapRowPatch.Kind.INSERT,row.operation());assertTrue(row.before().isEmpty());
            ids.add(OracleScalars.number(HexFormat.of().parseHex(row.after().get(0).hex())).longValueExact());
            assertEquals("kept-subnode",OracleScalars.varchar2(HexFormat.of().parseHex(row.after().get(1).hex()),"AL32UTF8"));
            assertEquals("partial-array",OracleScalars.varchar2(HexFormat.of().parseHex(row.after().get(2).hex()),"AL32UTF8"));
        }
        assertEquals(java.util.stream.LongStream.range(200,220).filter(i->i!=210 && i!=216).boxed().collect(java.util.stream.Collectors.toCollection(TreeSet::new)),ids);
        var root=records.stream().filter(r->r.block()==14701 && r.offset()==140).findFirst().orElseThrow();
        var original=root.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==1).findFirst().orElseThrow();var owner=UndoHeader.decode(original);
        assertTrue(ArchivedUndoInventory.arrayRootAssigned(root,original,owner));
        var head=root.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==2).findFirst().orElseThrow();
        assertThrows(RedoException.class,()->UndoHeadUpdate.decode(head).bindUndo(root));
        assertEquals(owner,UndoHeadUpdate.decode(head).bindArrayRoot(root));
        var poison=RedoBatch.copy(root);var changed=poison.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==2).findFirst().orElseThrow();changed.fields().get(0)[18]^=1;
        assertThrows(RedoException.class,()->ArchivedUndoInventory.arrayRootAssigned(poison,original,owner));
    }
}
