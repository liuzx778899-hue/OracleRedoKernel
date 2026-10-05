package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClusterUndoPairTest {
    byte[] hex(String s){return HexFormat.of().parseHex(s);}
    RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,22,11,37749054,2522906,1,3,List.of(
            hex("7c0048142200000003001e00c1020000b4012200"),hex("150000000200000000000000000000000b011e210000c102"),
            hex("020d0000000000003e014002b4012100"),hex("a489400090004000ff120501020000006c020405330020021500000000"),
            hex("00000400"),hex("80"),hex("5359535f4330303030345f32363130303531373a32373a303824")));}
    RedoRecords.Vector row(){return new RedoRecords.Vector(11,5,1,9,4229540,2522906,1,3,new ClusterRowPatchTest().update());}
    RedoRecords.Record record(RedoRecords.Vector... v){return new RedoRecords.Record(6713,164,300,1,2522906,3,677737149,List.of(v));}
    FrozenHeapBaseline.ObjectRole role(){return new FrozenHeapBaseline.ObjectRole("SYS","COL$",21,2);}
    ClusterUndoPair bind(RedoRecords.Vector u,RedoRecords.Vector r)throws Exception{return ClusterUndoPair.bind(record(u,r),r,role(),5,36);}
    @Test void cPrefixReferencesCurrentUndoAndHistoricalKtbIsNotIdentity()throws Exception {
        var p=bind(undo(),row());assertEquals(new HeapRowPatch.Xid(3,30,705),p.identity().xid());
        assertEquals(new HeapRowPatch.Uba(37749054,436,34),p.forward().row().uba());assertNull(p.forward().row().xid());
        assertEquals(51,p.inverse().row().slot());assertEquals(21,p.inverse().row().extent().sizeDelta());
    }
    @Test void duplicateMissingForeignAndChangedSlotRoleReferenceRefuse()throws Exception {
        var u=undo();var r=row();
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record(u,u,r),r,role(),5,36));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record(u,r,r),r,role(),5,36));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record(r),r,role(),5,36));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record(u),r,role(),5,36));
        var foreign=new RedoRecords.Vector(5,1,22,11,u.dba(),u.scn(),1,4,u.fields());
        assertThrows(RedoException.class,()->bind(foreign,r));
        assertThrows(RedoException.class,()->ClusterUndoPair.bind(record(u,r),r,new FrozenHeapBaseline.ObjectRole("SYS","COL$",4,2),5,36));
        for(int offset:new int[]{8,12,14}) {var bad=row();bad.fields().get(0)[offset]^=1;assertThrows(RedoException.class,()->bind(undo(),bad));}
        var slot=row();slot.fields().get(1)[20]^=1;assertThrows(RedoException.class,()->bind(undo(),slot));
    }
    @Test void changedMapDeltaWrapAndUnknownItlRefuse()throws Exception {
        var map=undo();map.fields().get(4)[2]=3;assertThrows(RedoException.class,()->bind(map,row()));
        var delta=undo();delta.fields().get(3)[24]++;assertThrows(RedoException.class,()->bind(delta,row()));
        var wrap=undo();wrap.fields().get(1)[22]++;assertThrows(RedoException.class,()->bind(wrap,row()));
        var itl=row();itl.fields().get(0)[0]=4;assertThrows(RedoException.class,()->bind(undo(),itl));
    }
    @Test void lockUsesCurrentFullFIdentityRatherThanForeignHistoricalLIdentity()throws Exception {
        var u=new RedoRecords.Vector(5,1,22,11,37749054,2522906,1,3,List.of(
                hex("54009e142200000003001e00c1020000b4012100"),hex("150000000200000000000000000000000b011e000000c102"),
                hex("040d0000000000000a001200e0080000670240021b013d0000800080187f2600"),hex("a489400090004000ff1204010200000033000500")));
        var r=new RedoRecords.Vector(11,4,1,9,4229540,2522906,1,3,List.of(
                hex("110d00000000000003001e00c10200003e014002b4012100000000000000000000000000000000000000000002010d001a7f26000080000002060080187f2600000000000000"),
                hex("a489400090004000ff1204010200000033000502")));
        var p=bind(u,r);assertEquals(new HeapRowPatch.Xid(3,30,705),p.forward().row().xid());
        assertEquals(p.identity().xid(),p.forward().row().xid());
        u.fields().get(0)[14]=1;assertThrows(RedoException.class,()->bind(u,r));
    }
}
