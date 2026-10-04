package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KtbZRollbackTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector purge() {
        return new RedoRecords.Vector(10,3,1,12,50331995,2266876,2,3,List.of(hex("030d000000000000"),hex("030101000d00")));
    }
    RedoRecords.Vector delete() {
        return new RedoRecords.Vector(11,3,1,12,50331989,2266876,1,3,List.of(hex("030d0001c7000b00"),hex("5501000352010003fa120302010040000000009f")));
    }
    @Test void observedPurgeDecodesOnlyPhysicalLeafFields() throws Exception {
        assertEquals(new IndexCompensation.LeafRollback(IndexUndoPair.Kind.PURGE,50331995,3,1,13),IndexCompensation.decodeRollback(purge()));
    }
    @Test void zRestoreAndUnobservedKtbTailStayRefused() {
        var p=purge();var restore=new RedoRecords.Vector(10,5,1,12,p.dba(),p.scn(),2,3,p.fields());
        assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(restore));
        for (int i=1;i<8;i++) {
            var changed=purge();changed.fields().get(0)[i]^=1;
            assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(changed));
        }
    }
    @Test void purgeForwardModeZeroItlAndExtraFieldStayRefused() {
        for (int i:new int[]{0,1}) {
            var p=purge();p.fields().get(1)[i]=0;
            assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(p));
        }
        var p=purge();var extra=new RedoRecords.Vector(10,3,1,12,p.dba(),p.scn(),2,3,List.of(p.fields().get(0),p.fields().get(1),new byte[0]));
        assertThrows(RedoException.class,()->IndexCompensation.decodeRollback(extra));
    }
    @Test void shortZDoesNotRelaxOriginalMetadataOrActiveIdentity() throws Exception {
        var fixture=new IndexCompensationTest();var old=fixture.purge();
        var p=new RedoRecords.Vector(10,3,1,12,old.dba(),old.scn(),2,3,List.of(hex("030d000000000000"),old.fields().get(1)));
        var marker=fixture.marker();var pair=fixture.pair();
        assertSame(pair,IndexCompensation.match(fixture.record(p,marker),marker,p,List.of(pair),fixture.active()));
        var state=fixture.active();marker.fields().get(0)[19]^=1;
        assertThrows(RedoException.class,()->IndexCompensation.match(fixture.record(p,marker),marker,p,List.of(pair),state));
        assertThrows(RedoException.class,state::finish);
    }
    @Test void observedXrDeleteHasAbsentColumnsAndNoInventedIdentity() throws Exception {
        var p=new HeapRowPatch().decode(delete(),2);
        assertEquals(HeapRowPatch.Kind.DELETE,p.kind());assertEquals(2,p.xtype());assertEquals(0,p.slot());assertEquals(50331989,p.dba());
        assertNull(p.xid());assertNull(p.uba());
        assertTrue(p.columns().stream().allMatch(c->c.state()==HeapRowPatch.State.ABSENT && c.bytes().length==0));
    }
    @Test void xaAndPackedDeleteProfilesStayRefused() {
        for (int mode:new int[]{1,0x82}) {
            var d=delete();d.fields().get(1)[11]=(byte)mode;
            assertThrows(RedoException.class,()->new HeapRowPatch().decode(d,2));
        }
    }
    @Test void deleteWrongBlockOpcodeAndExtraColumnStayRefused() {
        for (int i:new int[]{0,10}) {
            var d=delete();d.fields().get(1)[i]^=1;
            assertThrows(RedoException.class,()->new HeapRowPatch().decode(d,2));
        }
        var d=delete();var extra=new RedoRecords.Vector(11,3,1,12,d.dba(),d.scn(),1,3,List.of(d.fields().get(0),d.fields().get(1),hex("c115")));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(extra,2));
    }
    @Test void truncatedKtbAndKdoStayRefused() {
        var d=delete();var shortKtb=new RedoRecords.Vector(11,3,1,12,d.dba(),d.scn(),1,3,List.of(java.util.Arrays.copyOf(d.fields().get(0),7),d.fields().get(1)));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(shortKtb,2));
        var shortKdo=new RedoRecords.Vector(11,3,1,12,d.dba(),d.scn(),1,3,List.of(d.fields().get(0),java.util.Arrays.copyOf(d.fields().get(1),19)));
        assertThrows(RedoException.class,()->new HeapRowPatch().decode(shortKdo,2));
    }
}
