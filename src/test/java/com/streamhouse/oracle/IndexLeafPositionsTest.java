package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class IndexLeafPositionsTest {
    @TempDir Path dir;
    final ArchivedIndexLeafInventoryTest fixture=new ArchivedIndexLeafInventoryTest();
    record Context(ArchivedIndexLeafInventory inventory,IndexUndoPair pair,RedoRecords.Record record,
                   RedoRecords.Vector row,RedoRecords.Vector marker,TransactionLifecycle state,IndexLeafPositions positions) {}

    Context load(byte[] bytes) throws Exception {
        var path=dir.resolve("archive.dbf");Files.write(path,bytes);
        var inventory=ArchivedIndexLeafInventory.read(List.of(path),677737149,3,fixture.xid,fixture.object,fixture.leaf);
        var records=new ArrayList<RedoRecords.Record>();var header=inventory.window().undo().sources().get(0);
        new RedoRecords().visit(path,header,records::add);
        var state=new TransactionLifecycle();state.archive(header);
        var begin=records.stream().filter(r->r.block()==2).findFirst().orElseThrow();
        var start=begin.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==2).findFirst().orElseThrow();state.begin(begin,start);
        var original=records.stream().filter(r->r.block()==3).findFirst().orElseThrow();
        var forward=original.vectors().stream().filter(v->v.opcodeMajor()==10).findFirst().orElseThrow();var pair=IndexUndoPair.bind(original,forward);
        state.requireActive(original,pair);
        var rollback=records.stream().filter(r->r.block()==5).findFirst().orElseThrow();
        var row=rollback.vectors().stream().filter(v->v.opcodeMajor()==10).findFirst().orElseThrow();
        var marker=rollback.vectors().stream().filter(v->v.opcodeMajor()==5 && v.opcodeMinor()==6).findFirst().orElseThrow();
        return new Context(inventory,pair,rollback,row,marker,state,new IndexLeafPositions(inventory));
    }
    ArchivedUndoInventory.Position at(Context c) {return c.inventory().entries().get(2).position();}
    IndexUndoPair match(Context c) throws Exception {return IndexCompensation.match(c.record(),c.marker(),c.row(),List.of(c.pair()),c.state(),c.positions(),at(c));}
    void refused(Context c) {
        assertEquals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED",assertThrows(RedoException.class,c.state()::finish).getMessage());
        assertEquals("INDEX_LEAF_POSITIONS_ALREADY_REFUSED",assertThrows(RedoException.class,()->c.positions().slotBefore(c.pair(),c.record(),c.marker(),c.row(),at(c),c.state())).getMessage());
    }
    @Test void foreignInsertMovesTargetAndStaticMatcherStillRefuses() throws Exception {
        var c=load(fixture.fixture());assertEquals(1,c.pair().forward().slot());assertEquals(2,IndexCompensation.decodeRollback(c.row()).slot());
        assertSame(c.pair(),match(c));
        var old=load(fixture.fixture());assertEquals("MISSING_INDEX_COMPENSATION_ORIGINAL",assertThrows(RedoException.class,()->IndexCompensation.match(old.record(),old.marker(),old.row(),List.of(old.pair()),old.state())).getMessage());
    }
    @Test void largerSlotInsertDoesNotMoveTarget() throws Exception {
        byte[] bytes=fixture.fixture();var insert=fixture.index.insert();put(insert.fields().get(1),2,2,2);
        fixture.records.record(bytes,4,125,List.of(fixture.index.purgeUndo(),insert));seal(bytes,2048);
        // Same frozen rollback structure, slot corrected to the unshifted target in this synthetic case.
        int data=5*512+16+24+32+8;put(bytes,data+32+2,1,2);seal(bytes,2560);
        var c=load(bytes);assertEquals(1,c.positions().slotBefore(c.pair(),c.record(),c.marker(),c.row(),at(c),c.state()));assertSame(c.pair(),match(c));
    }
    @Test void changedQuerySlotOrHistoricalBytesCannotMasqueradeAsOwnedInput() throws Exception {
        for (int mode=0;mode<3;mode++) {
            var c=load(fixture.fixture());
            if (mode==2) c.marker().fields().get(0)[24]^=1;
            else c.row().fields().get(mode==0 ? 1 : 0)[2]^=1;
            assertEquals("INDEX_POSITION_QUERY_CHANGED_FROM_OWNED_SOURCE",assertThrows(RedoException.class,()->match(c)).getMessage());refused(c);
        }
    }
    @Test void changedPairKeyCannotBeUsed() throws Exception {
        var c=load(fixture.fixture());c.pair().undo().key()[2]^=1;
        assertEquals("INDEX_POSITION_ORIGINAL_OR_QUERY_NOT_OWNED",assertThrows(RedoException.class,()->match(c)).getMessage());refused(c);
    }
    @Test void repeatMatchingRefusesAndLatchesBothOwners() throws Exception {
        var c=load(fixture.fixture());match(c);
        assertEquals("INDEX_POSITION_QUERY_REPEATED_OR_REORDERED",assertThrows(RedoException.class,()->match(c)).getMessage());refused(c);
    }
    @Test void foreignSourcePositionAndMissingQueryRefuseAndLatch() throws Exception {
        for (int mode=0;mode<2;mode++) {
            var c=load(fixture.fixture());var p=mode==0 ? new ArchivedUndoInventory.Position(8,5,16,1) : null;
            assertThrows(RedoException.class,()->IndexCompensation.match(c.record(),c.marker(),c.row(),List.of(c.pair()),c.state(),c.positions(),p));refused(c);
        }
    }
    @Test void sourceIdentityMismatchCannotBorrowPositionProof() throws Exception {
        var c=load(fixture.fixture());var foreign=new TransactionLifecycle();
        foreign.archive(new TransactionLifecycleTest().archive(7,100,200,43));
        assertEquals("INDEX_POSITION_ARCHIVE_SOURCE_MISMATCH",assertThrows(RedoException.class,()->IndexCompensation.match(c.record(),c.marker(),c.row(),List.of(c.pair()),foreign,c.positions(),at(c))).getMessage());
        assertThrows(RedoException.class,foreign::finish);
    }
    @Test void unsupportedInterveningDeleteRemainsRefused() throws Exception {
        byte[] bytes=fixture.fixture();fixture.records.record(bytes,4,125,List.of(fixture.index.delete(),fixture.index.restoreUndo()));seal(bytes,2048);
        var c=load(bytes);
        assertEquals("UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:DELETE",assertThrows(RedoException.class,()->match(c)).getMessage());refused(c);
    }
    @Test void duplicateCandidatesCannotReuseAnOtherwiseValidPositionProof() throws Exception {
        var c=load(fixture.fixture());
        assertEquals("AMBIGUOUS_INDEX_COMPENSATION_ORIGINAL",assertThrows(RedoException.class,()->IndexCompensation.match(c.record(),c.marker(),c.row(),List.of(c.pair(),c.pair()),c.state(),c.positions(),at(c))).getMessage());refused(c);
    }
    @Test void typedForwardIdentityCannotBeForgedEvenWithTheCorrectUndo() throws Exception {
        var c=load(fixture.fixture());var f=c.pair().forward();
        var foreign=new IndexUndoPair.Forward(f.kind(),f.block(),f.itl(),f.slot(),f.rowSize(),new HeapRowPatch.Xid(10,32,1137),f.uba(),f.key(),f.keydata());
        var forged=new IndexUndoPair(c.pair().containerUid(),c.pair().containerId(),foreign,c.pair().undo());
        assertEquals("INDEX_POSITION_ORIGINAL_OR_QUERY_NOT_OWNED",assertThrows(RedoException.class,()->IndexCompensation.match(c.record(),c.marker(),c.row(),List.of(forged),c.state(),c.positions(),at(c))).getMessage());refused(c);
    }
}
