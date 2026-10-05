package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClusterRowPatchTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    List<byte[]> update(){return List.of(hex("020d2600008000003e014002b4012200"),
            hex("a489400090004000ff120501020000006c02040533002002ebff000000"),hex("00000400"),hex("80"),hex("4558545241"));}
    List<byte[]> insert() {
        var f=new ArrayList<byte[]>();f.add(new byte[16]);
        f.add(hex("a489400090004000ff120201020000006c02200403002c04ffff01000000000000000000000000006200330005001b8c2f000000"));
        for(String v:new String[]{"80","c105","c2021d","80","5359535f4330303030345f32363130303531373a32373a303824","c102","c2021d","80","","","80","","","c105","c3041d","c2094a","c102","80","","","80","80","c2021d","","","","","","c3024053","","80","80"})f.add(hex(v));
        return f;
    }
    @Test void insertRequiresBitmapAndCompleteRowSizeWhileTailRemainsAbsent()throws Exception {
        var p=ClusterRowPatch.decode(insert(),2,4229540,5,36);
        assertEquals(HeapRowPatch.State.NULL,p.row().columns().get(8).state());
        assertEquals(HeapRowPatch.State.ABSENT,p.row().columns().get(32).state());
        for(int at:new int[]{40,46,47,48,49}) {
            var f=insert();f.get(1)[at]^=1;
            assertThrows(RedoException.class,()->ClusterRowPatch.decode(f,2,4229540,5,36));
        }
        var f=insert();f.set(10,hex("80"));
        assertThrows(RedoException.class,()->ClusterRowPatch.decode(f,2,4229540,5,36));
    }
    @Test void preservesSparseColumnsAndClusterIdentityWithoutCreatingParentIdentity() throws Exception {
        var p=ClusterRowPatch.decode(update(),5,4229540,5,36);
        assertEquals(5,p.tableNumber());assertNull(p.clusterKeyIndex());assertEquals(51,p.row().slot());
        assertNull(p.row().xid());assertNull(p.row().uba());
        assertEquals(-21,p.row().extent().sizeDelta());assertEquals(32,p.row().extent().storedColumns());
        assertEquals(HeapRowPatch.State.ABSENT,p.row().columns().get(35).state());
        assertEquals(HeapRowPatch.State.VALUE,p.row().columns().get(4).state());assertArrayEquals(hex("4558545241"),p.row().columns().get(4).bytes());
        assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(update(),5,4229540,36));
    }
    @Test void samePhysicalBlockDoesNotMergeTabAndColAndRejectsUnknownProfiles() throws Exception {
        assertThrows(RedoException.class,()->ClusterRowPatch.decode(update(),5,4229540,1,44));
        assertThrows(RedoException.class,()->ClusterRowPatch.decode(update(),5,4229541,5,36));
        for(int offset:new int[]{10,11,13,14,16,19,22,23,26,28}) {
            var f=update();f.get(1)[offset]^=0x40;
            assertThrows(RedoException.class,()->ClusterRowPatch.decode(f,5,4229540,5,36),"offset "+offset);
        }
        var repeated=update();repeated.get(2)[2]=0;
        assertThrows(RedoException.class,()->ClusterRowPatch.decode(repeated,5,4229540,5,36));
    }
    @Test void clusteredLockRetainsToTwoAndCannotEnterOrdinaryHeapProfile() throws Exception {
        var f=List.of(new byte[70],hex("a489400090004000ff1204010200000033000502"));
        var p=ClusterRowPatch.decode(f,4,4229540,5,36);
        assertEquals(HeapRowPatch.Kind.LOCK,p.row().kind());assertEquals(2,p.row().lockTo());
        assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(f,4,4229540,36));
        f.get(1)[19]=1;assertThrows(RedoException.class,()->ClusterRowPatch.decode(f,4,4229540,5,36));
    }
}
