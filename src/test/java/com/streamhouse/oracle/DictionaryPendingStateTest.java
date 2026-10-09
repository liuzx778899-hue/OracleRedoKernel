package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryPendingStateTest {
    @TempDir Path directory;
    PendingDictionaryRecoveryTest.Fixture fixture()throws Exception {
        var helper=new PendingDictionaryRecoveryTest();helper.directory=directory;return helper.fixture(2522800);
    }
    @Test void typedPendingSysRestoresWithoutRawAndLaterCommitMatchesStrictHistory()throws Exception {
        var f=fixture();var waiting=ArchivedDictionaryHistory.readPrefix(f.prefix(),f.snapshot());
        byte[] bytes=waiting.durablePendingState().checkpoint();var restored=DictionaryPendingState.restore(bytes,DurableUndoState.sha(bytes),f.snapshot());
        byte[] raw=Files.readAllBytes(directory.resolve("prefix.dbf"));Files.delete(directory.resolve("prefix.dbf"));
        var context=restored.decoded().contexts().get(0);assertEquals(1,context.rows.size());assertEquals(1,context.next);
        var column=context.rows.get(0).forward().columns().stream().filter(c->c.bytes().length>0).findFirst().orElseThrow();byte value=column.bytes()[0];column.bytes()[0]^=1;
        var again=restored.decoded().contexts().get(0).rows.get(0).forward().columns().stream().filter(c->c.bytes().length>0).findFirst().orElseThrow();assertEquals(value,again.bytes()[0]);
        Files.write(directory.resolve("prefix.dbf"),raw);
        var repeated=ArchivedDictionaryHistory.readRestored(f.prefix(),f.snapshot(),restored);
        assertArrayEquals(waiting.checkpoint(),repeated.checkpoint());assertThrows(RedoException.class,()->repeated.schemaAt(2522810));
        var closed=ArchivedDictionaryHistory.readRestored(f.closed(),f.snapshot(),restored);
        assertTrue(closed.pending().isEmpty());assertEquals(1,closed.commits().size());
        assertArrayEquals(ArchivedDictionaryHistory.read(f.closed(),f.snapshot()).checkpoint(),closed.checkpoint());
    }
    @Test void badDigestBaselineSourceCountAndTailRefuse()throws Exception {
        var f=fixture();byte[] bytes=ArchivedDictionaryHistory.readPrefix(f.prefix(),f.snapshot()).durablePendingState().checkpoint();
        assertThrows(RedoException.class,()->DictionaryPendingState.restore(bytes,"0".repeat(64),f.snapshot()));
        var s=f.snapshot();var other=new FrozenHeapBaseline.Snapshot("b".repeat(64),s.source(),s.scn(),s.heap(),s.index(),s.columns(),s.rows(),s.dictionaryAnchors(),s.dictionaryBaseline());
        assertThrows(RedoException.class,()->DictionaryPendingState.restore(bytes,DurableUndoState.sha(bytes),other));
        var count=bytes.clone();java.nio.ByteBuffer.wrap(count).putInt(2+"ORK_PENDING_DICTIONARY_STATE_V1".length()+2+64,Integer.MAX_VALUE);
        for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),count))
            assertThrows(RedoException.class,()->DictionaryPendingState.restore(bad,DurableUndoState.sha(bad),s));
        var state=DictionaryPendingState.restore(bytes,DurableUndoState.sha(bytes),s);
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.readRestored(RedoBatch.archives(List.of(directory.resolve("suffix.dbf"))),s,state));
        assertThrows(RedoException.class,()->ArchivedDictionaryHistory.readRestored(f.closed(),other,state));
        var decoded=state.decoded();decoded.contexts().get(0).beginScn++;
        byte[] wrongBegin=DictionaryPendingState.from(s,decoded.sources(),decoded.contexts()).checkpoint();
        assertThrows(RedoException.class,()->DictionaryPendingState.restore(wrongBegin,DurableUndoState.sha(wrongBegin),s));
    }
    @Test void nonemptyStatisticsAndUsageDependenciesRoundTripWithCompleteIdentity()throws Exception {
        for(boolean statistics:List.of(true,false)) {
            var stats=new DictionaryStatisticsChangesTest();stats.directory=directory;var usage=new DictionaryUsageChangesTest();
            FrozenHeapBaseline.Snapshot snapshot;
            List<RedoRecords.Record> records;
            if(statistics){snapshot=stats.snapshot();records=List.of(stats.row(),stats.index(0),stats.index(1));}
            else {
                var helper=new DictionaryBaselineTest();helper.directory=directory;
                String after="DICT_USAGE_TABLE|AFTER|SYS|COL_USAGE$|669|669\nDICT_USAGE_INDEX|AFTER|I_COL_USAGE$|670|670|4214064\n";
                snapshot=helper.read(helper.v3().replace("DICT_HISTORY_END|6",usage.section()+"DICT_HISTORY_END|6").replace("SOURCE_AFTER|",after+"SOURCE_AFTER|"));
                records=List.of(usage.record0(),usage.record1());
            }
            var source=snapshot.source();long sequence=statistics?81:83;
            var header=new ArchivedRedo.Header("a".repeat(64),51200,source.dbid(),source.activationId(),source.compatibility(),source.thread(),sequence,
                    source.resetlogsId(),source.resetlogsScn(),2522800,3000000,100);
            var entries=new ArrayList<ArchivedUndoInventory.Entry>();
            for(int i=0;i<records.size();i++) {
                var r=records.get(i);var v=r.vectors().stream().filter(x->x.opcodeMajor()==5 && x.opcodeMinor()==1).findFirst().orElseThrow();
                var h=UndoHeader.decode(v);if(i==0)h=new UndoHeader(h.xid(),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),true);
                byte[] m=v.fields().get(1);entries.add(new ArchivedUndoInventory.Entry(new ArchivedUndoInventory.Position(sequence,r.block(),r.offset(),r.vectors().indexOf(v)+1),
                        h,m[16]&255,m[17]&255,i==0?0:RedoBytes.u32(m,12),i==0?0:m[19]&255));
            }
            var inventory=new ArchivedUndoInventory(List.of(header),entries,source.containerUid(),source.containerId(),null);
            var c=new ArchivedDictionaryHistory.Context(inventory);var first=entries.get(0).identity();
            var start=new ArchivedCompensationAssociationsTest().start(first.xid(),37749008,first.uba().dba(),first.uba().record());
            ArchivedRedoTest.put(start.fields().get(0),12,first.uba().sequence(),2);
            var original=records.get(0);var beginUndo=original.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==1).findFirst().orElseThrow();
            var fields=new ArrayList<>(beginUndo.fields());byte[] metadata=Arrays.copyOf(fields.get(1),76);ArchivedRedoTest.put(metadata,20,0x0c08,2);fields.set(1,metadata);
            var begin=new RedoRecords.Vector(5,1,beginUndo.blockClass(),beginUndo.file(),beginUndo.dba(),original.scn(),0,source.containerId(),fields);
            var beginRecord=new RedoRecords.Record(original.block(),original.offset(),512,1,original.scn(),1,source.containerUid(),List.of(start,begin));
            c.lifecycle.archive(header);c.lifecycle.begin(beginRecord,start);c.start=TransactionStart.decode(start);c.beginScn=original.scn();c.next=entries.size();
            for(var entry:entries)c.graph.append(entry);
            HeapUndoPair p=statistics?stats.bind(snapshot,original).physical():usage.row(snapshot.dictionaryBaseline().usage()).physical();
            var u=p.undo();p=new HeapUndoPair(p.containerUid(),p.containerId(),p.forward(),new UndoRowPatch(first,u.inverse(),u.previousDba(),u.predecessorRecord(),u.wrap()));
            if(statistics){c.statisticsRows.add(DictionaryStatisticsChanges.fromPhysical(p,snapshot.dictionaryBaseline().statistics()));c.statisticsIndexes.addAll(stats.indexes(snapshot));}
            else {c.usageRows.add(DictionaryUsageChanges.fromPhysical(p,snapshot.dictionaryBaseline().usage()));c.usageIndexes.add(usage.index(snapshot.dictionaryBaseline().usage()));}
            byte[] bytes=DictionaryPendingState.from(snapshot,List.of(header),List.of(c)).checkpoint();
            var restored=DictionaryPendingState.restore(bytes,DurableUndoState.sha(bytes),snapshot);var decoded=restored.decoded().contexts().get(0);
            assertEquals(c.rowCount(),decoded.rowCount());assertEquals(c.indexCount(),decoded.indexCount());assertArrayEquals(bytes,restored.checkpoint());
        }
    }
}
