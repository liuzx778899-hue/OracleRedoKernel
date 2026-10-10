package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexCompensationTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    // Raw108 RBA15191/20 and15200/84: independent native dump calls rowSize279 the total array size.
    RedoRecords.Vector arrayPurge(int slot) {
        byte[] leaf=hex("02250100170100001400000001000100010001000100010001000100010001000100010001000100010001000100010001000100");
        leaf[2]=(byte)slot;
        for(int i=0;i<20;i++)leaf[12+i*2]=(byte)slot;
        return new RedoRecords.Vector(10,3,1,12,0x0300b503L,3225993,1,3,List.of(
                hex("040d0000000000000a001000a71300002b0d4002af01450000800080fe363100"),leaf));
    }
    @Test void frozenArrayPurgeRetainsSuccessiveRepeatedSlotsAndAggregateSize()throws Exception {
        for(int slot:List.of(1,21)) {
            var purge=IndexCompensation.decodeArrayPurge(arrayPurge(slot));
            assertEquals(279,purge.rowSize());assertEquals(2,purge.itl());
            assertEquals(java.util.Collections.nCopies(20,slot),purge.slots());
        }
    }
    @Test void arrayPurgeRefusesTruncatedCountUnknownFlagsAndWrongSno() {
        var count=arrayPurge(1);count.fields().get(1)[8]--;
        assertThrows(RedoException.class,()->IndexCompensation.decodeArrayPurge(count));
        var flags=arrayPurge(1);flags.fields().get(1)[1]=0x21;
        assertThrows(RedoException.class,()->IndexCompensation.decodeArrayPurge(flags));
        var sno=arrayPurge(21);sno.fields().get(1)[2]++;
        assertThrows(RedoException.class,()->IndexCompensation.decodeArrayPurge(sno));
    }
    RedoRecords.Vector marker() {
        return new RedoRecords.Vector(5,6,22,11,37756973,2148795,2,3,List.of(
                hex("411d0100411d010005000000000000000a16042f10009d02040d0001"),hex("340a000002000000")));
    }
    RedoRecords.Vector purge() {
        return new RedoRecords.Vector(10,3,1,12,50331787,2148795,2,3,List.of(
                hex("040d000158c020000a0014006e040000be2c4002f200200000800080b9c92000"),hex("020101000d00")));
    }
    RedoRecords.Record record(RedoRecords.Vector... v) { return new RedoRecords.Record(30713,380,1000,1,2148795,1,677737149,List.of(v)); }
    IndexUndoPair pair() throws Exception {
        var row=new RedoRecords.Vector(10,2,1,12,50331787,2148795,1,3,List.of(
                hex("010d000152c02000030004009d0200002d2040028f013000"),hex("020001000d00"),hex("02c107"),hex("030000830003")));
        var undo=new RedoRecords.Vector(5,1,22,11,37756973,2148795,1,3,List.of(
                hex("5c00200a22000000030004009d0200008f013000"),hex("411d0100411d010005000000000000000a16042f00009d02"),
                hex("040d000158c020000a0014006e040000be2c4002f200200000800080b9c92000"),
                hex("030201008a0000038b000003000000000000992e"),hex("02c107")));
        return IndexUndoPair.bind(record(row,undo),row);
    }
    /** Synthetic begin establishes only the identity used by the frozen pair. */
    TransactionLifecycle active() throws Exception {
        var state=new TransactionLifecycle(); state.archive(new TransactionLifecycleTest().archive(8,2148750,2149085,42));
        byte[] b=new byte[32],u=new byte[20],m=new byte[76];
        ArchivedRedoTest.put(b,0,4,2);ArchivedRedoTest.put(b,4,669,4);ArchivedRedoTest.put(b,8,37756973,4);
        ArchivedRedoTest.put(b,12,399,2);b[14]=47;ArchivedRedoTest.put(b,16,0x52,2);
        ArchivedRedoTest.put(u,8,3,2);ArchivedRedoTest.put(u,10,4,2);ArchivedRedoTest.put(u,12,669,4);
        ArchivedRedoTest.put(u,16,399,2);u[18]=47;ArchivedRedoTest.put(m,20,0x0c08,2);
        var start=new RedoRecords.Vector(5,2,21,11,37748896,2148795,1,3,List.of(b,hex("bd726528")));
        var undo=new RedoRecords.Vector(5,1,22,11,37756973,2148795,1,3,List.of(u,m));
        state.begin(record(start,undo),start);return state;
    }
    @Test void matchesPurgeWithHistoricalKtbFromAnotherTransaction() throws Exception {
        var c=marker();var p=purge();var original=pair();
        assertSame(original,IndexCompensation.match(record(p,c),c,p,List.of(original),active()));
        assertEquals(new HeapRowPatch.Xid(3,4,669),original.undo().identity().xid());
        assertEquals(48,original.undo().identity().uba().record());
    }
    @Test void rejectsMissingDuplicateOriginalOrDuplicatePurgeAndLatches() throws Exception {
        for (int mode=0;mode<3;mode++) {
            var state=active();var c=marker();var p=purge();var original=pair();
            var history=mode==0 ? List.<IndexUndoPair>of() : mode==1 ? List.of(original,original) : List.of(original);
            var r=mode==2 ? record(c,p,p) : record(c,p);
            assertThrows(RedoException.class,()->IndexCompensation.match(r,c,p,history,state));
            assertEquals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED",assertThrows(RedoException.class,state::finish).getMessage());
        }
    }
    @Test void rejectsChangedPredecessorWrapAndPhysicalSlot() throws Exception {
        for (int mode=0;mode<3;mode++) {
            var c=marker();var p=purge();var state=active();var original=pair();
            if (mode==0)c.fields().get(0)[19]^=1;
            if (mode==1)c.fields().get(0)[22]^=1;
            if (mode==2)p.fields().get(1)[2]^=1;
            assertThrows(RedoException.class,()->IndexCompensation.match(record(c,p),c,p,List.of(original),state));
            assertThrows(RedoException.class,state::finish);
        }
    }
    @Test void requiresActiveFullIdentityAndRejectsLowWrapCollision() throws Exception {
        var c=marker();var p=purge();var original=pair();var inactive=new TransactionLifecycle();
        inactive.archive(new TransactionLifecycleTest().archive(8,2148750,2149085,42));
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,p),c,p,List.of(original),inactive));
        var u=original.undo();var h=u.identity();
        var fake=new UndoHeader(new HeapRowPatch.Xid(3,4,669+65536),h.uba(),h.objectId(),h.dataObjectId(),h.tablespace(),false);
        var alias=new IndexUndoPair(original.containerUid(),original.containerId(),original.forward(),
                new IndexUndoPair.Inverse(fake,u.kind(),u.indexId(),u.block(),u.itl(),u.flags(),u.sdc(),u.previousDba(),u.predecessorRecord(),u.wrap(),u.key(),u.keydata()));
        var state=active();
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,p),c,p,List.of(alias),state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void rejectsForwardModeForeignRecordAndContainer() throws Exception {
        var p=purge();p.fields().get(1)[1]=0;
        assertThrows(RedoException.class,()->IndexCompensation.decodePurge(p));
        var row=purge();var c=marker();var original=pair();var state=active();
        assertThrows(RedoException.class,()->IndexCompensation.match(record(row),c,row,List.of(original),state));
        var mixed=new RedoRecords.Vector(10,3,1,12,row.dba(),row.scn(),1,4,row.fields());var second=active();
        assertThrows(RedoException.class,()->IndexCompensation.match(record(c,mixed),c,mixed,List.of(original),second));
    }
}
