package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobBitmapUndoPairTest {
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector vector(int major,int minor,int cls,long dba,long object,String... fields) {
        return new RedoRecords.Vector(major,minor,cls,12,dba,3043587,1,3,
                java.util.Arrays.stream(fields).map(this::hex).toList(),object);
    }
    RedoRecords.Record record(RedoRecords.Vector... vectors) {
        return new RedoRecords.Record(34208,16,1016,1,3043587,1,677737149,List.of(vectors));
    }
    RedoRecords.Vector original(boolean parent) {
        return vector(5,1,32,37751224,0xffffffffL,
                parent ? "44001a072200ffff08002100ed0200001d013500" : "3c006a062200000008002100ed0200001d013700",
                parent ? "e7350100e735010005000000000000000d1721340000ed02" : "e7350100e735010005000000000000000d1721360000ed02",
                parent ? "7886000308000000010000000900000002000000" : "1886000308000000000000000900000002000000",
                parent ? "04000000010000000000b78202000000" : "13a047de01000000");
    }
    RedoRecords.Vector primary(boolean parent,boolean inverse) {
        return vector(13,22,8,parent ? 50366072 : 50365976,79335,
                parent ? inverse ? "04000000010000000000b78202000000" : "04020a4901000000011a2d0002000000"
                        : inverse ? "13a047de01000000" : "147f000001000000");
    }
    RedoRecords.Vector marker(boolean parent) {
        return vector(5,6,32,37751224,0xffffffffL,
                parent ? "e7350100e735010005000000000000000d1721341000ed0278860003" : "e7350100e735010005000000000000000d1721361000ed0218860003",
                parent ? "50072e0002000000" : "ac062e0002000000");
    }
    RedoRecords.Vector parent(long dba,long object) {
        return vector(13,24,9,dba,object,"01000000010000000500000001000000");
    }
    LobBitmapUndoPair pair(boolean parent) throws Exception {
        var u=original(parent);return LobBitmapUndoPair.bind(record(u,primary(parent,false)),u,79335,50365977);
    }
    @Test void matchesBothObservedInverseProfilesAndExplicitParent() throws Exception {
        for(boolean parent:new boolean[]{false,true}) {
            var p=pair(parent);var m=marker(parent);var row=primary(parent,true);
            var r=parent ? record(row,m,parent(50365977,79335)) : record(row,m);
            assertEquals(p,LobBitmapUndoPair.matchCompensation(r,m,List.of(p),p.owner()));
            assertEquals(new HeapRowPatch.Xid(8,33,749),p.owner().xid());
            byte[] inverse=p.inverse();inverse[0]++;
            assertNotEquals(inverse[0],p.inverse()[0]);
        }
    }
    @Test void refusesWrongParentObjectInverseAndMissingCompanion() throws Exception {
        var p=pair(true);var m=marker(true);var row=primary(true,true);
        for(var r:List.of(record(row,m),record(row,m,parent(50365978,79335)),record(row,m,parent(50365977,79337))))
            assertThrows(RedoException.class,()->LobBitmapUndoPair.matchCompensation(r,m,List.of(p),p.owner()));
        row.fields().get(0)[2]^=1;
        assertThrows(RedoException.class,()->LobBitmapUndoPair.matchCompensation(record(row,m,parent(50365977,79335)),m,List.of(p),p.owner()));
    }
    @Test void refusesAmbiguousRowsOwnersAndWrongPredecessor() throws Exception {
        var p=pair(false);var m=marker(false);var row=primary(false,true);
        assertThrows(RedoException.class,()->LobBitmapUndoPair.matchCompensation(record(row,row,m),m,List.of(p),p.owner()));
        assertThrows(RedoException.class,()->LobBitmapUndoPair.matchCompensation(record(row,m),m,List.of(p,p),p.owner()));
        m.fields().get(0)[19]++;
        assertThrows(RedoException.class,()->LobBitmapUndoPair.matchCompensation(record(row,m),m,List.of(p),p.owner()));
    }
    @Test void refusesUnscopedOrUnpairedOriginal() {
        var u=original(true);var row=primary(true,false);
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u,row),u,79337,50365977));
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u,row),u,79335,0));
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u),u,79335,50365977));
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u,u,row),u,79335,50365977));
    }
    @Test void retainsObservedTrimCountAndRefusesUnknownCount() throws Exception {
        var u=original(false);var row=primary(false,false);
        u.fields().get(3)[4]=2;row.fields().get(0)[4]=2;
        var p=LobBitmapUndoPair.bind(record(u,row),u,79335,50365977);
        assertEquals(2,p.inverse()[4]);
        row.fields().get(0)[4]=1;
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u,row),u,79335,50365977));
        row.fields().get(0)[4]=3;
        assertThrows(RedoException.class,()->LobBitmapUndoPair.bind(record(u,row),u,79335,50365977));
    }
}
