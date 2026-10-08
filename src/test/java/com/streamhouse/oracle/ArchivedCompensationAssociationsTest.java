package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedCompensationAssociationsTest {
    @TempDir Path dir;
    final ArchivedCompensationInventoryTest records=new ArchivedCompensationInventoryTest();
    final TransactionLifecycleTest vectors=new TransactionLifecycleTest();
    final HeapRowPatch.Xid a=records.xid,b=new HeapRowPatch.Xid(9,33,99);
    RedoRecords.Vector undo(HeapRowPatch.Xid xid,long dba,int rec) {
        var v=vectors.undo();byte[] id=v.fields().get(0),m=v.fields().get(1);
        put(id,8,xid.usn(),2);put(id,10,xid.slot(),2);put(id,12,xid.sequence(),4);put(id,18,rec,1);
        put(m,0,42,4);put(m,4,42,4);put(m,8,5,4);put(m,16,11,1);put(m,17,1,1);put(m,18,xid.slot(),1);put(m,22,1,2);
        return new RedoRecords.Vector(5,1,36,11,dba,110,1,3,v.fields());
    }
    RedoRecords.Vector start(HeapRowPatch.Xid xid,long segment,long dba,int rec) {
        var v=vectors.start();byte[] f=v.fields().get(0);
        put(f,0,xid.slot(),2);put(f,4,xid.sequence(),4);put(f,8,dba,4);put(f,14,rec,1);
        return new RedoRecords.Vector(5,2,35,11,segment,110,1,3,v.fields());
    }
    RedoRecords.Vector marker(int slot,long segment) {
        byte[] m=new byte[28];put(m,0,42,4);put(m,4,42,4);put(m,8,5,4);put(m,16,11,1);put(m,17,1,1);
        put(m,18,slot,1);put(m,20,0x0c18,2);put(m,22,1,2);
        return new RedoRecords.Vector(5,11,35,11,segment,130,1,3,List.of(m,new byte[8]));
    }
    RedoRecords.Vector end(HeapRowPatch.Xid xid,long segment) {
        var v=vectors.end(4);put(v.fields().get(0),0,xid.slot(),2);put(v.fields().get(0),4,xid.sequence(),4);
        return new RedoRecords.Vector(5,4,35,11,segment,130,1,3,v.fields());
    }
    byte[] fixture() {
        byte[] bytes=records.fixture(true,false);
        records.record(bytes,2,110,List.of(start(a,0x02400110,0x02402ccb,41),undo(a,0x02402ccb,41)));
        records.record(bytes,3,120,List.of(start(b,0x02400120,0x02402ccc,43),undo(b,0x02402ccc,43)));
        records.record(bytes,4,130,List.of(marker(b.slot(),0x02400120),end(b,0x02400120)));
        records.record(bytes,5,140,List.of(marker(a.slot(),0x02400110),end(a,0x02400110)));
        for (int i=0;i<bytes.length;i+=512)seal(bytes,i);return bytes;
    }
    ArchivedCompensationAssociations read(byte[] bytes) throws Exception {
        var p=dir.resolve("archive.dbf");Files.write(p,bytes);return ArchivedCompensationAssociations.read(List.of(p),677737149,3,a);
    }
    @Test void includesForeignMarkerAndBindsBeginThroughSegmentNotWrapOne() throws Exception {
        var associations=read(fixture());assertEquals(List.of(b,a),associations.entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        assertEquals(List.of(3L,2L),associations.entries().stream().map(e->e.originalPosition().block()).toList());
        assertThrows(UnsupportedOperationException.class,()->associations.entries().clear());
    }
    byte[] crossFixture() {
        var foreign=new HeapRowPatch.Xid(b.usn(),b.slot(),65536+b.sequence());
        byte[] bytes=java.util.Arrays.copyOf(fixture(),8*512);put(bytes,24,7,4);put(bytes,668,8,4);
        records.record(bytes,3,120,List.of(start(foreign,0x02400120,0x02402ccc,43),undo(foreign,0x02402ccc,43)));
        var original=undo(foreign,0x02402ccd,1);byte[] metadata=java.util.Arrays.copyOf(original.fields().get(1),24);
        put(metadata,12,0x02402ccc,4);put(metadata,19,0,1);put(metadata,20,0,2);put(metadata,22,foreign.sequence()&0xffff,2);
        original=new RedoRecords.Vector(5,1,36,11,original.dba(),125,1,3,List.of(original.fields().get(0),metadata));
        records.record(bytes,4,125,List.of(original));
        var marker=marker(foreign.slot(),0x02400120);put(marker.fields().get(0),12,0x02402ccc,4);
        put(marker.fields().get(0),20,16,2);put(marker.fields().get(0),22,foreign.sequence()&0xffff,2);
        records.record(bytes,5,130,List.of(marker,end(foreign,0x02400120)));
        records.record(bytes,6,140,List.of());records.record(bytes,7,150,List.of(marker(a.slot(),0x02400110),end(a,0x02400110)));
        for(int i=0;i<bytes.length;i+=512)seal(bytes,i);return bytes;
    }
    @Test void crossBlockMarkerUsesOwnedSegmentSlotAndFullSequenceWindow()throws Exception {
        var associations=read(crossFixture());var entry=associations.entries().get(0);
        assertEquals(new HeapRowPatch.Xid(b.usn(),b.slot(),65536+b.sequence()),entry.xid());
        assertEquals(4,entry.originalPosition().block());assertEquals(0x02402ccd,entry.original().uba().dba());
        for(int at:new int[]{8,12,18,22}) {
            byte[] raw=crossFixture();int marker=5*512+16+24;
            if(at==8)put(raw,marker+8,0x02400110,4);
            else {
                int field=marker+32+8;raw[field+at]^=1;
            }
            seal(raw,5*512);assertThrows(RedoException.class,()->read(raw));
        }
    }
    @Test void missingOriginalAndWrongBeginSegmentCannotBeExcluded() {
        byte[] bytes=fixture();var m=marker(b.slot(),0x02400110);records.record(bytes,4,130,List.of(m,end(b,0x02400120)));seal(bytes,2048);
        assertEquals("MISSING_COMPENSATION_HEADER_ASSOCIATION",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
    @Test void beginAssociationDecodesOnlyItsExactUbaButCannotHideItsInvalidControl()throws Exception {
        for(boolean selected:new boolean[]{false,true}) {
            byte[] bytes=fixture();var extra=start(b,0x02400120,selected?0x02402ccc:0x02402cdd,43);
            extra.fields().get(0)[16]=0;
            records.record(bytes,3,120,List.of(start(b,0x02400120,0x02402ccc,43),extra,undo(b,0x02402ccc,43)));seal(bytes,3*512);
            if(selected)assertThrows(RedoException.class,()->read(bytes));
            else assertEquals(List.of(b,a),read(bytes).entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        }
    }
    @Test void incompleteForeignWindowRefuses() {
        byte[] bytes=fixture();records.record(bytes,4,130,List.of(marker(b.slot(),0x02400120)));seal(bytes,2048);
        assertEquals("INCOMPLETE_TRANSACTION_IDENTITIES:1",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
    void visit(ArchivedCompensationAssociations associations,boolean change) throws Exception {
        Path path=dir.resolve("archive.dbf");var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if (v.opcodeMajor()!=5 || v.opcodeMinor()!=11)continue;
                if (change)v.fields().get(1)[7]=1;
                associations.associate(header,r,v,new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1));
            }
        });
    }
    @Test void exactSourceQueriesConsumeBothMarkers() throws Exception {
        var associations=read(fixture());visit(associations,false);associations.finish();
    }
    @Test void opaqueByteMutationRefusesAndLatches() throws Exception {
        var associations=read(fixture());assertEquals("ASSOCIATION_QUERY_CHANGED_FROM_OWNED_SOURCE",assertThrows(RedoException.class,()->visit(associations,true)).getMessage());
        assertEquals("COMPENSATION_ASSOCIATIONS_ALREADY_REFUSED",assertThrows(RedoException.class,associations::finish).getMessage());
    }
    @Test void omissionCannotFinish() throws Exception {
        var associations=read(fixture());assertThrows(RedoException.class,associations::finish);assertThrows(RedoException.class,associations::finish);
    }
    @Test void sourceIdentityMismatchRefuses() throws Exception {
        var associations=read(fixture());Path path=dir.resolve("archive.dbf");var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if (r.block()!=4)return;var v=r.vectors().get(0);
            assertEquals("ASSOCIATION_QUERY_SOURCE_MISMATCH",assertThrows(RedoException.class,()->associations.associate(vectors.archive(7,100,200,999),r,v,new ArchivedUndoInventory.Position(7,4,16,1))).getMessage());
        });
    }
    @Test void reorderedQueryRefuses() throws Exception {
        var associations=read(fixture());Path path=dir.resolve("archive.dbf");var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if (r.block()!=5)return;var v=r.vectors().get(0);
            assertEquals("ASSOCIATION_QUERY_INCOMPLETE_OR_REORDERED",assertThrows(RedoException.class,()->associations.associate(header,r,v,new ArchivedUndoInventory.Position(7,5,16,1))).getMessage());
        });
    }
    @Test void multiplePlausibleOriginalsRefuseRatherThanGuessingHead() {
        byte[] bytes=java.util.Arrays.copyOf(fixture(),3584);put(bytes,24,6,4);put(bytes,668,7,4);
        var originals=new java.util.ArrayList<RedoRecords.Vector>();
        for (int rec:new int[]{44,45}) {
            var begin=undo(b,0x02402ccc,rec);byte[] m=java.util.Arrays.copyOf(begin.fields().get(1),24);
            put(m,16,10,1);put(m,17,22,1);put(m,19,43,1);put(m,20,0,2);put(m,22,b.sequence(),2);
            originals.add(new RedoRecords.Vector(5,1,36,11,begin.dba(),125,1,3,List.of(begin.fields().get(0),m)));
        }
        records.record(bytes,4,125,originals);
        byte[] m=new byte[28];put(m,0,42,4);put(m,4,42,4);put(m,8,5,4);put(m,16,10,1);put(m,17,22,1);
        put(m,18,b.slot(),1);put(m,19,43,1);put(m,20,16,2);put(m,22,b.sequence(),2);
        var regular=new RedoRecords.Vector(5,6,36,11,0x02402ccc,130,1,3,List.of(m,new byte[8]));
        records.record(bytes,5,130,List.of(regular,end(b,0x02400120)));
        records.record(bytes,6,140,List.of(marker(a.slot(),0x02400110),end(a,0x02400110)));
        for (int i=0;i<bytes.length;i+=512)seal(bytes,i);
        assertEquals("AMBIGUOUS_COMPENSATION_HEADER_ASSOCIATION",assertThrows(RedoException.class,()->read(bytes)).getMessage());
    }
}
