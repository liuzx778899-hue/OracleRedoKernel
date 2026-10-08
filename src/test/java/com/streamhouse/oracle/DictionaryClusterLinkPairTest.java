package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryClusterLinkPairTest {
    final FrozenHeapBaseline.ObjectRole role=new FrozenHeapBaseline.ObjectRole("SYS","COL$",21,2);
    byte[] hex(String value){return HexFormat.of().parseHex(value);}
    RedoRecords.Record record(boolean second) {
        var undo=new RedoRecords.Vector(5,1,36,11,37749505,2900125,second?3:2,3,new ArrayList<>(List.of(
                hex(second?"5000b615220000000a000300c70d00006501197b":"60001816220000000a000300c70d00006501187b"),
                hex(second?"150000000200000000000000000000000b0103180000c70d":"150000000200000000000000000000000b0103170000c70d"),
                hex(second?"020d2000bd0d00000103400265011800":"040d2000bd0d00000a000000ce080000070240021b0148000080008041792600"),
                hex(second?"a489400090004000ff120a0101000000004089a4000400408d86000301010400":"a489400090004000ff120a0101000000ac010194a489004089a4000402000400"))));
        var forward=new RedoRecords.Vector(11,10,1,9,4229540,2900125,second?2:1,3,new ArrayList<>(List.of(
                hex(second?"020d0000000000000103400265011900":"010d0000000000000a000300c70d00000103400265011800"),
                hex(second?"a489400090004000ff120a010100000000408d86000300408d86000301010400":"a489400090004000ff120a0101000000ac010194a48900408d86000302010400"))));
        return new RedoRecords.Record(second?51453:51452,second?100:324,256,1,2900125,1,677737149,List.of(undo,forward));
    }
    DictionaryClusterLinkPair bind(RedoRecords.Record r)throws Exception {return DictionaryClusterLinkPair.bind(r,r.vectors().get(1),role);}
    @Test void actualForwardAndBackwardPointersUseDirectionAndFullCurrentIdentity()throws Exception {
        for(boolean second:new boolean[]{false,true}) {
            var pair=bind(record(second));
            assertEquals(new HeapRowPatch.Xid(10,3,3527),pair.identity().xid());
            assertEquals(second?1:2,pair.forward().direction());assertEquals(4,pair.forward().slot());
            assertEquals(0x004089a4L,pair.inverse().linkBlock());assertEquals(4,pair.inverse().linkSlot());
            assertEquals(0x00408d86L,pair.forward().linkBlock());assertEquals(3,pair.forward().linkSlot());
        }
    }
    @Test void priorCommittedItlIsNotCurrentTransactionAuthority()throws Exception {
        var r=record(false);r.vectors().get(0).fields().get(2)[8]^=1;
        assertEquals(new HeapRowPatch.Xid(10,3,3527),bind(r).identity().xid());
    }
    @Test void identityDirectionSlotPointerAndGenerationPoisonRefuse() {
        for(int at:new int[]{8,16,20,22}) {
            var r=record(false);r.vectors().get(1).fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->bind(r));
        }
        for(int at:new int[]{0,10,11,28,30}) {
            var r=record(false);r.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->bind(r));
        }
        var r=record(false);Arrays.fill(r.vectors().get(1).fields().get(1),22,28,(byte)0);
        assertThrows(RedoException.class,()->bind(r));
        var poisoned=record(false);poisoned.vectors().get(0).fields().get(1)[4]^=1;
        assertThrows(RedoException.class,()->bind(poisoned));
    }
}
