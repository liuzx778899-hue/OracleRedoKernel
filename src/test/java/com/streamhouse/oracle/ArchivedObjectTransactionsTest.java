package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class ArchivedObjectTransactionsTest {
    @TempDir Path dir;
    final ArchivedCompensationAssociationsTest f=new ArchivedCompensationAssociationsTest();
    ArchivedObjectTransactions read(byte[] raw,Set<Long> objects) throws Exception {
        Path p=dir.resolve("archive.dbf");Files.write(p,raw);return ArchivedObjectTransactions.read(List.of(p),677737149,3,objects);
    }
    @Test void discoversBothFullIdentitiesWithoutCallerListAndRoutesForeignMarker() throws Exception {
        var result=read(f.fixture(),Set.of(42L));assertEquals(List.of(f.a,f.b),result.transactions().stream().map(ArchivedObjectTransactions.Transaction::xid).toList());
        assertEquals(List.of(f.b,f.a),result.transactions().get(0).associations().entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        assertThrows(UnsupportedOperationException.class,()->result.transactions().clear());
    }
    @Test void incompleteDiscoveredWindowRefusesEntireDiscovery() {
        var raw=f.fixture();f.records.record(raw,4,130,List.of(f.marker(f.b.slot(),0x02400120)));seal(raw,2048);
        assertThrows(RedoException.class,()->read(raw,Set.of(42L)));
    }
    @Test void relevantMarkerWithoutAnyOriginalCannotDisappear() {
        var raw=f.fixture();f.records.record(raw,2,110,List.of());f.records.record(raw,3,120,List.of());seal(raw,1024);seal(raw,1536);
        assertEquals("UNDISCOVERED_OBJECT_TRANSACTION_COMPENSATION",assertThrows(RedoException.class,()->read(raw,Set.of(42L))).getMessage());
    }
    @Test void objectAndDataObjectScopeMismatchRefuses() {
        var raw=f.fixture();var u=f.undo(f.a,0x02402ccb,41);put(u.fields().get(1),4,43,4);
        f.records.record(raw,2,110,List.of(f.start(f.a,0x02400110,0x02402ccb,41),u));seal(raw,1024);
        assertEquals("OBJECT_TRANSACTION_MAPPING_MISMATCH",assertThrows(RedoException.class,()->read(raw,Set.of(42L))).getMessage());
    }
    @Test void emptyScopeAndInputRefuse() {
        assertThrows(RedoException.class,()->read(f.fixture(),Set.of()));
        assertThrows(RedoException.class,()->ArchivedObjectTransactions.read(List.of(),677737149,3,Set.of(42L)));
    }
}
