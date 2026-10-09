package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class CompensationWindowStateTest {
    @TempDir Path dir;
    ArchivedCompensationAssociationsTest f=new ArchivedCompensationAssociationsTest();
    List<Path> fixture()throws Exception {
        byte[] first=f.fixture();f.records.record(first,5,140,List.of());put(first,676,1,4);
        for(int block=0;block<first.length/512;block++)seal(first,block*512);
        byte[] second=first.clone();Arrays.fill(second,1024,second.length,(byte)0);
        put(second,692,200,4);put(second,704,300,4);
        for(int block=1;block<second.length/512;block++){put(second,block*512,0x2201,2);put(second,block*512+4,block,4);put(second,block*512+8,8,4);}
        f.records.record(second,2,210,List.of(f.marker(f.a.slot(),0x02400110),f.end(f.a,0x02400110)));
        put(second,2*512+8,8,4);
        for(int block=0;block<second.length/512;block++)seal(second,block*512);
        var p=dir.resolve("first.dbf");var q=dir.resolve("second.dbf");Files.write(p,first);Files.write(q,second);return List.of(p,q);
    }
    @Test void conservativeForeignMarkerAndIdentitySurviveOldRawRemovalThenCloseFromSuffix()throws Exception {
        var paths=fixture();var old=ArchivedCompensationInventory.read(RedoBatch.archives(paths.subList(0,1)),677737149,3,f.a,true);
        assertFalse(old.complete());assertEquals(1,old.entries().size());assertEquals(f.b.slot(),old.entries().get(0).marker().slot());
        var full=ArchivedCompensationInventory.read(paths,677737149,3,f.a);byte[] bytes=old.durableCheckpoint();Files.delete(paths.get(0));
        var restored=ArchivedCompensationInventory.restoreDurable(bytes,DurableUndoState.sha(bytes));
        var extended=restored.extend(RedoBatch.archives(paths.subList(1,2)),false);assertTrue(extended.complete());assertEquals(2,extended.entries().size());
        assertArrayEquals(full.durableCheckpoint(),extended.durableCheckpoint());assertArrayEquals(bytes,restored.durableCheckpoint());
        byte[] closed=extended.durableCheckpoint();assertArrayEquals(closed,ArchivedCompensationInventory.restoreDurable(closed,DurableUndoState.sha(closed)).durableCheckpoint());
        var in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(closed));in.readUTF();in.skipNBytes(in.readInt());in.skipNBytes(in.readInt());in.skipNBytes(49);
        int segment=closed.length-in.available();byte[] changed=closed.clone();java.nio.ByteBuffer.wrap(changed).putLong(segment,extended.segmentDba()+1);
        assertThrows(RedoException.class,()->ArchivedCompensationInventory.restoreDurable(changed,DurableUndoState.sha(changed)));
        assertThrows(RedoException.class,()->extended.extend(RedoBatch.archives(paths.subList(1,2)),true));
    }
    @Test void restoredAssociationUsesWindowButStillRequiresEveryOwnerAndRawMarker()throws Exception {
        var paths=fixture();var batch=RedoBatch.archives(paths);
        var window=ArchivedCompensationInventory.read(batch.prefix(1),677737149,3,f.a,true);byte[] bytes=window.durableCheckpoint();
        var restored=ArchivedCompensationInventory.restoreDurable(bytes,DurableUndoState.sha(bytes));
        var associated=ArchivedCompensationAssociations.readRestored(batch,restored,false);var full=ArchivedCompensationAssociations.read(paths,677737149,3,f.a);
        assertEquals(full.entries(),associated.entries());assertEquals(List.of(f.b,f.a),associated.entries().stream().map(ArchivedCompensationAssociations.Entry::xid).toList());
        var consumed=new ArrayList<ArchivedCompensationAssociations.Entry>();for(var input:batch) {
            var header=input.header();input.visit(header,r->{for(int i=0;i<r.vectors().size();i++) {
                var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                if(associated.entries().stream().anyMatch(e->e.position().equals(at)))consumed.add(associated.associate(header,r,r.vectors().get(i),at));
            }});
        }
        associated.finish();assertEquals(full.entries(),consumed);
        assertThrows(RedoException.class,()->ArchivedCompensationAssociations.readRestored(batch.prefix(1),restored,false));
        assertThrows(RedoException.class,()->ArchivedCompensationAssociations.readRestored(batch.suffix(1),restored,false));
    }
    @Test void wrongDigestTruncationTailFormatAndRepeatedSourceRefuse()throws Exception {
        var paths=fixture();var window=ArchivedCompensationInventory.read(RedoBatch.archives(paths).prefix(1),677737149,3,f.a,true);byte[] bytes=window.durableCheckpoint();
        assertThrows(RedoException.class,()->ArchivedCompensationInventory.restoreDurable(bytes,"0".repeat(64)));
        byte[] format=bytes.clone();format[2]='X';for(byte[] bad:List.of(Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),format))
            assertThrows(RedoException.class,()->ArchivedCompensationInventory.restoreDurable(bad,DurableUndoState.sha(bad)));
        assertThrows(RedoException.class,()->window.extend(RedoBatch.archives(paths).prefix(1),true));assertArrayEquals(bytes,window.durableCheckpoint());
    }
}
