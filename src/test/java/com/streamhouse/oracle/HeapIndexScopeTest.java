package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class HeapIndexScopeTest {
    @TempDir Path directory;
    final ArchivedCompensationAssociationsTest f=new ArchivedCompensationAssociationsTest();
    final HeapIndexScope scope=new HeapIndexScope(new HeapIndexScope.Generation(42,43),new HeapIndexScope.Generation(44,45));
    byte[] fixture(long originalData,long markerData) {
        var raw=f.fixture();var a=f.undo(f.a,0x02402ccb,41);var b=f.undo(f.b,0x02402ccc,43);
        put(a.fields().get(1),4,originalData,4);put(b.fields().get(1),4,originalData,4);
        var am=f.marker(f.a.slot(),0x02400110);var bm=f.marker(f.b.slot(),0x02400120);
        put(am.fields().get(0),4,markerData,4);put(bm.fields().get(0),4,markerData,4);
        f.records.record(raw,2,110,List.of(f.start(f.a,0x02400110,0x02402ccb,41),a));
        f.records.record(raw,3,120,List.of(f.start(f.b,0x02400120,0x02402ccc,43),b));
        f.records.record(raw,4,130,List.of(bm,f.end(f.b,0x02400120)));f.records.record(raw,5,140,List.of(am,f.end(f.a,0x02400110)));
        for (int i=0;i<raw.length;i+=512)seal(raw,i);return raw;
    }
    List<Path> paths(byte[] raw) throws Exception {var path=directory.resolve("archive.dbf");Files.write(path,raw);return List.of(path);}
    @Test void distinctGenerationDiscoversBothOwnersAndRoutesCompleteMarkers() throws Exception {
        var result=ArchivedObjectTransactions.read(paths(fixture(43,43)),677737149,3,scope);
        assertEquals(List.of(f.a,f.b),result.transactions().stream().map(ArchivedObjectTransactions.Transaction::xid).toList());
        assertEquals(List.of(f.b,f.a),result.transactions().get(0).associations().entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
    }
    @Test void staleGenerationAndCrossRoleOriginalOrMarkerRefuseEvenWithAllIdsKnown() throws Exception {
        for (var pair:List.of(new long[]{42,43},new long[]{45,43},new long[]{43,45}))
            assertEquals("OBJECT_TRANSACTION_MAPPING_MISMATCH",assertThrows(RedoException.class,()->ArchivedObjectTransactions.read(paths(fixture(pair[0],pair[1])),677737149,3,scope)).getMessage());
        assertEquals("OBJECT_TRANSACTION_MAPPING_MISMATCH",assertThrows(RedoException.class,()->ArchivedObjectTransactions.read(paths(fixture(43,43)),677737149,3,Set.of(42L,43L,44L,45L))).getMessage());
    }
    @Test void rolesCannotShareEitherLogicalOrDataIdentity() {
        assertThrows(IllegalArgumentException.class,()->new HeapIndexScope(new HeapIndexScope.Generation(42,43),new HeapIndexScope.Generation(44,43)));
        assertThrows(IllegalArgumentException.class,()->new HeapIndexScope(new HeapIndexScope.Generation(42,43),new HeapIndexScope.Generation(43,45)));
    }
}
