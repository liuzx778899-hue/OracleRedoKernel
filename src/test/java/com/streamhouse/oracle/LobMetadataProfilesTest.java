package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobMetadataProfilesTest {
    byte[] hex(String value) {return HexFormat.of().parseHex(value);}
    RedoRecords.Vector start(String body) {return new RedoRecords.Vector(5,2,33,11,37748992,3043512,1,3,List.of(hex(body),hex("bd726528")));}
    @Test void acceptsObservedControlFbiAndPubBeginButRefusesUnknownFlagsAndParents()throws Exception {
        for(String body:List.of("0c0000001a030000210940022a01300051045000000000000000000000000000",
                "1700000019030000040940022a011b0051045000020000000000000000000000",
                "0e0000001a030000220940022a0101004904500098ad475c0000000000000000")) {
            var v=start(body);assertEquals(37748992,TransactionStart.decode(v).segmentDba());
            v.fields().get(0)[24]=1;assertThrows(RedoException.class,()->TransactionStart.decode(v));
        }
        var v=start("0c0000001a030000210940022a01300051045000000000000000000000000000");
        v.fields().get(0)[20]=3;assertThrows(RedoException.class,()->TransactionStart.decode(v));
    }
    @Test void storageBeginFlagsRequireTheExactNonPayloadStorageProfile()throws Exception {
        var fields=List.of(hex("6800bc061200000002001300a3020000cb004e77"),
                hex("01000000ffffffff050000000000000016031300880c010000000000d30c4002cb004d00e4462e00000000001d472e000000000000000000ffffffffffffffffd00c40020000000000000000"),new byte[20]);
        var v=new RedoRecords.Vector(5,1,34,11,37752019,3036429,1,3,fields);
        assertTrue(UndoHeader.decode(v).beginsTransaction());assertEquals(0xffffffffL,UndoHeader.decode(v).dataObjectId());
        fields.get(2)[0]=1;assertThrows(RedoException.class,()->UndoHeader.decode(v));fields.get(2)[0]=0;
        fields.get(1)[16]=11;assertThrows(RedoException.class,()->UndoHeader.decode(v));
    }
    @Test void lobExtentAxisIsScopedAndDoesNotRelaxTheOrdinaryProfile()throws Exception {
        var fields=List.of(hex("60003a062200000009000c001a0300002a013586"),hex("01000000e735010005000000000000000e050c3480001a03"),
                hex("1a860003040000001a860003020000007886000308000000"),hex("027bc921010000000000000000000000"),hex("0562c92101000000080000000000000000000000"));
        var u=new RedoRecords.Vector(5,1,34,11,37751073,3043512,1,3,fields);
        var f=new RedoRecords.Vector(14,4,4,12,50365978,3043512,1,3,List.of(hex("01000000788600030800000001000000"),
                hex("0c7f0000010000007886000379860003"),hex("0500000002000000100000000000000000000000")),79335);
        var r=new RedoRecords.Record(33990,44,264,1,3043512,22,677737149,List.of(u,f));
        assertEquals(8,ExtentMapPair.bindLob(r,u,f).blocks());assertThrows(RedoException.class,()->ExtentMapPair.bind(r,u,f));
        f.fields().get(1)[12]^=1;assertThrows(RedoException.class,()->ExtentMapPair.bindLob(r,u,f));
    }
}
