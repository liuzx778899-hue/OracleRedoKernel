package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssociationDiscoveryStateTest {
    @TempDir Path dir;
    @Test void oldForeignOwnerAndCandidatesSurviveRawRemovalThenRouteNewMarkerFromSuffix()throws Exception {
        var helper=new CompensationWindowStateTest();helper.dir=dir;var paths=helper.fixture();var f=helper.f;
        var inputs=RedoBatch.archives(paths);var index=AssociationDiscoveryState.read(inputs.prefix(1),677737149,3);
        var old=ArchivedCompensationAssociations.readIndexed(inputs.prefix(1),index.owner(inputs.prefix(1),f.a,true),true,index);
        assertEquals(List.of(f.b),old.entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        byte[] bytes=index.checkpoint();var headers=old.window().undo().sources();
        var fullIndex=AssociationDiscoveryState.read(inputs,677737149,3);
        var full=ArchivedCompensationAssociations.readIndexed(inputs,fullIndex.owner(inputs,f.a,false),false,fullIndex);
        byte[] expected=fullIndex.checkpoint();Files.delete(paths.get(0));
        var restored=AssociationDiscoveryState.restore(bytes,DurableUndoState.sha(bytes),headers,677737149,3);
        restored.visit(0,r->{for(var v:r.vectors())for(byte[] field:v.fields())Arrays.fill(field,(byte)0);});
        assertArrayEquals(bytes,restored.checkpoint());
        var advanced=restored.append(inputs.suffix(1));
        var result=ArchivedCompensationAssociations.readIndexed(inputs,advanced.owner(inputs,f.a,false),false,advanced);
        assertEquals(full.entries(),result.entries());assertEquals(List.of(f.b,f.a),result.entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        assertArrayEquals(expected,advanced.checkpoint());assertArrayEquals(bytes,restored.checkpoint());
        assertArrayEquals(expected,advanced.append(RedoBatch.archives(List.of())).checkpoint());
    }
    @Test void authenticatedStateCannotChangeContainerSourcesRecordsOrRequiredWindow()throws Exception {
        var helper=new CompensationWindowStateTest();helper.dir=dir;var inputs=RedoBatch.archives(helper.fixture()).prefix(1);
        var index=AssociationDiscoveryState.read(inputs,677737149,3);var window=index.owner(inputs,helper.f.a,true);byte[] bytes=index.checkpoint();var sources=window.undo().sources();
        assertThrows(RedoException.class,()->AssociationDiscoveryState.restore(bytes,"0".repeat(64),sources,677737149,3));
        assertThrows(RedoException.class,()->AssociationDiscoveryState.restore(bytes,DurableUndoState.sha(bytes),sources,677737149,4));
        assertThrows(RedoException.class,()->AssociationDiscoveryState.restore(bytes,DurableUndoState.sha(bytes),List.of(),677737149,3));
        var text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);String first=text.lines().filter(s->s.startsWith("R|")).findFirst().orElseThrow();
        byte[] raw=Base64.getDecoder().decode(first.substring(2));java.nio.ByteBuffer.wrap(raw).putLong(0,window.undo().sources().get(0).blocks());
        byte[] bad=text.replace(first,"R|"+Base64.getEncoder().encodeToString(raw)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(RedoException.class,()->AssociationDiscoveryState.restore(bad,DurableUndoState.sha(bad),sources,677737149,3));
        var empty=AssociationDiscoveryState.read(inputs,677737149,3);
        assertThrows(RedoException.class,()->empty.requireWindow(window));
        assertThrows(RedoException.class,()->index.append(inputs));
    }
    @Test void indexedRoutingPreservesExactBeginControlRefusal()throws Exception {
        var f=new ArchivedCompensationAssociationsTest();f.dir=dir;
        for(boolean invalid:new boolean[]{false,true}) {
            byte[] bytes=f.fixture();
            if(invalid) {
                var extra=f.start(f.b,0x02400120,0x02402ccc,43);extra.fields().get(0)[16]=0;
                f.records.record(bytes,3,120,List.of(f.start(f.b,0x02400120,0x02402ccc,43),extra,f.undo(f.b,0x02402ccc,43)));ArchivedRedoTest.seal(bytes,3*512);
            }
            var path=dir.resolve("indexed.dbf");Files.write(path,bytes);var inputs=RedoBatch.archives(List.of(path));
            var index=AssociationDiscoveryState.read(inputs,677737149,3);
            if(invalid)assertThrows(RedoException.class,()->ArchivedCompensationAssociations.readIndexed(inputs,index.owner(inputs,f.a,false),false,index));
            else assertEquals(ArchivedCompensationAssociations.read(List.of(path),677737149,3,f.a).entries(),
                    ArchivedCompensationAssociations.readIndexed(inputs,index.owner(inputs,f.a,false),false,index).entries());
        }
    }
    @Test void indexedRoutingCannotChooseBetweenMultiplePlausibleOriginals()throws Exception {
        var f=new ArchivedCompensationAssociationsTest();f.dir=dir;f.multiplePlausibleOriginalsRefuseRatherThanGuessingHead();
        var inputs=RedoBatch.archives(List.of(dir.resolve("archive.dbf")));var index=AssociationDiscoveryState.read(inputs,677737149,3);
        assertEquals("AMBIGUOUS_COMPENSATION_HEADER_ASSOCIATION",assertThrows(RedoException.class,
                ()->ArchivedCompensationAssociations.readIndexed(inputs,index.owner(inputs,f.a,false),false,index)).getMessage());
    }
}
