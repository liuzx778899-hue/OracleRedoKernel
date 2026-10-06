package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryIndexMaintenancePairTest {
    byte[] hex(String value){return HexFormat.of().parseHex(value);}
    final FrozenHeapBaseline.ObjectRole role=new FrozenHeapBaseline.ObjectRole("SYS","I_COL3",50,50);
    RedoRecords.Vector undo(int op) {
        String first=op==11?"58005c1f220000000900090094020000e6000200":"5400021f220016000900090094020000e6000300";
        String metadata=op==11?"320000003200000000000000000000000a15090100009402":"320000003200000000000000000000000a15090200009402";
        String descriptor=op==11?"0bc0a0b42e514000c0014000":"10c0a0b4c1014000c0014000";
        return new RedoRecords.Vector(5,1,34,11,0x02402f51,2567426,1,3,List.of(hex(first),hex(metadata),
                hex("040da0b4777b00000a000c00ff080000f82940021c01010000c000007f242700"),hex(descriptor),hex(op==11?"010100002d514000":"01002b01")));
    }
    RedoRecords.Vector forward(int op) {
        var fields=new ArrayList<byte[]>();
        fields.add(hex(op==11?"010d0000000000000900090094020000512f4002e6000200":"010d0900940200000900090094020000512f4002e6000300"));
        fields.add(hex(op==11?"010000002f514000":"01012b012f514000"));
        if(op==15)fields.add(hex("04c3083552fe"));
        return new RedoRecords.Vector(10,op,1,9,op==11?0x0040512e:0x004001c1,2567426,1,3,fields);
    }
    RedoRecords.Record record(RedoRecords.Vector...vectors){return new RedoRecords.Record(126619,224,236,1,2567426,8,677737149,List.of(vectors));}
    DictionaryIndexMaintenancePair bind(RedoRecords.Vector u,RedoRecords.Vector f)throws Exception {
        return DictionaryIndexMaintenancePair.bind(record(u,f),f,role,0x004001c0);
    }
    @Test void bindsNativeLinkAndBranchWithoutUsingForeignUndoItlAsCurrentIdentity()throws Exception {
        var link=bind(undo(11),forward(11));assertEquals(new HeapRowPatch.Xid(9,9,660),link.identity().xid());
        assertEquals(0x0040512d,link.previousLink());assertEquals(0x0040512f,link.nextLink());
        var branch=bind(undo(15),forward(15));assertEquals(DictionaryIndexMaintenancePair.Kind.BRANCH_INSERT,branch.kind());
        assertEquals(299,branch.slot());assertEquals(0x0040512f,branch.nextLink());assertEquals(75281,branch.separatorParent());
    }
    @Test void refusesMismatchedCurrentIdentityUbaAndDuplicatePairs() {
        for(int op:List.of(11,15))for(int at:List.of(8,10,12,16,20,22)) {
            var f=forward(op);f.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->bind(undo(op),f));
        }
        var u=undo(15);var f=forward(15);
        assertThrows(RedoException.class,()->DictionaryIndexMaintenancePair.bind(record(u,u,f),f,role,0x004001c0));
        assertThrows(RedoException.class,()->DictionaryIndexMaintenancePair.bind(record(u,f,f),f,role,0x004001c0));
    }
    @Test void refusesUnknownInverseActionSegmentGenerationAndSlot() {
        for(int at:List.of(0,4,8)) {
            var u=undo(15);u.fields().get(3)[at]^=1;assertThrows(RedoException.class,()->bind(u,forward(15)));
        }
        var u=undo(15);u.fields().get(1)[4]^=1;assertThrows(RedoException.class,()->bind(u,forward(15)));
        var slot=undo(15);slot.fields().get(4)[2]^=1;assertThrows(RedoException.class,()->bind(slot,forward(15)));
        var parent=forward(15);parent.fields().get(2)[5]=0;assertThrows(RedoException.class,()->bind(undo(15),parent));
    }
    @Test void refusesUnknownLinkFlagsAndUnchangedLink() {
        var flags=forward(11);flags.fields().get(1)[1]=1;assertThrows(RedoException.class,()->bind(undo(11),flags));
        var unchanged=forward(11);unchanged.fields().get(1)[4]=0x2d;assertThrows(RedoException.class,()->bind(undo(11),unchanged));
    }
}
