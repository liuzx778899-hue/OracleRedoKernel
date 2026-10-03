package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoChainTest {
    byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    RedoRecords.Vector begin() {
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,3,List.of(
                hex("8800c211120000000a0005006d040000f2002100"),
                hex("401d0100401d010005000000000000000b010500080c010000000000be2c4002f2001a009fc3200000000000a1c3200000000000401d0100bcc9200000000000ba2c40020000000000000000")));
    }
    RedoRecords.Vector link(int record,int predecessor) {
        byte[] a=hex("5c00da10220000000a0005006d040000f2002300"); a[18]=(byte)record;
        byte[] b=hex("401d0100401d010005000000000000000b01052200006d04"); b[19]=(byte)predecessor;
        return new RedoRecords.Vector(5,1,36,11,0x02402cbe,2148799,1,3,List.of(a,b));
    }
    UndoChain chain() { return new UndoChain(3,new HeapRowPatch.Xid(10,5,1133),new HeapRowPatch.Uba(0x02402cbe,242,33)); }
    @Test void followsExplicitHeapAndIndexLinksRegardlessOfArrivalOrder() throws Exception {
        var chain=chain(); var index=link(34,33); index.fields().get(1)[16]=10; index.fields().get(1)[17]=22;
        chain.add(link(35,34)); chain.add(begin()); chain.add(index);
        var result=chain.close(new HeapRowPatch.Uba(0x02402cbe,242,35));
        assertEquals(List.of(35,34,33),result.stream().map(n -> n.identity().uba().record()).toList());
        assertEquals(10,result.get(1).opcodeMajor());
    }
    @Test void missingIndexPredecessorRefusesAndLocksChain() throws Exception {
        var chain=chain(); chain.add(begin()); chain.add(link(35,34));
        assertThrows(RedoException.class,()->chain.close(null));
        assertThrows(RedoException.class,()->chain.add(link(34,33)));
    }
    @Test void rejectsDuplicateBranchAndEndTailMismatch() throws Exception {
        var duplicate=chain(); duplicate.add(begin()); assertThrows(RedoException.class,()->duplicate.add(begin()));
        var branch=chain(); branch.add(begin()); branch.add(link(34,33)); branch.add(link(35,33));
        assertThrows(RedoException.class,()->branch.close(null));
        var wrongTail=chain(); wrongTail.add(begin()); wrongTail.add(link(34,33));
        assertThrows(RedoException.class,()->wrongTail.close(new HeapRowPatch.Uba(0x02402cbe,242,33)));
    }
    @Test void rejectsDisconnectedCycleWrongXidAndMissingBegin() throws Exception {
        var cycle=chain(); cycle.add(begin()); cycle.add(link(34,35)); cycle.add(link(35,34));
        assertThrows(RedoException.class,()->cycle.close(null));
        var wrong=chain(); var v=link(34,33); v.fields().get(0)[8]=9;
        assertThrows(RedoException.class,()->wrong.add(v));
        var missing=chain(); missing.add(link(34,33)); assertThrows(RedoException.class,()->missing.close(null));
        var container=chain(); var seed=begin();
        var foreign=new RedoRecords.Vector(5,1,36,11,seed.dba(),seed.scn(),1,4,seed.fields());
        assertThrows(RedoException.class,()->container.add(foreign));
    }
    @Test void refusesUnprovenCrossBlockLinkAndWrongMetadata() {
        var cross=link(34,33); cross.fields().get(1)[12]=1;
        assertThrows(RedoException.class,()->UndoLink.decode(cross));
        var slot=link(34,33); slot.fields().get(1)[18]=6;
        assertThrows(RedoException.class,()->UndoLink.decode(slot));
        var noPrevious=link(34,0); assertThrows(RedoException.class,()->UndoLink.decode(noPrevious));
    }
    @Test void nativeEndTailIsAnExplicitReferenceNotAnInferredNeighbor() throws Exception {
        var end=new RedoRecords.Vector(5,4,35,11,0x02400110,2148800,1,3,List.of(
                hex("050000006d040000000000000900000002000000"),hex("be2c4002f20025004f00b21c00000000"),hex("ea44c16a")));
        assertEquals(new HeapRowPatch.Uba(0x02402cbe,242,37),TransactionEnd.undoTail(end));
        var without=new RedoRecords.Vector(5,4,35,11,0x02400110,2148800,1,3,List.of(end.fields().get(0),end.fields().get(2)));
        assertThrows(RedoException.class,()->TransactionEnd.undoTail(without));
    }
}
