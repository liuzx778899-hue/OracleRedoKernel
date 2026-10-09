package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalDiscoveryStateTest {
    @TempDir Path dir;
    @Test void completeWindowSetMatchesTypedPendingAndClosedAndRejectsOmissions()throws Exception {
        var helper=new PhysicalRecoveryStateTest();helper.dir=dir;var receipt=helper.state();var baseline=helper.snapshot;
        var scope=new HeapIndexScope(new HeapIndexScope.Generation(baseline.heap().logicalObject(),baseline.heap().dataObject()),
                new HeapIndexScope.Generation(baseline.index().logicalObject(),baseline.index().dataObject()));
        var prefix=ArchivedPhysicalTransactions.readPrefix(helper.inputs,baseline.source().containerUid(),baseline.source().containerId(),scope,helper.leaf,baseline.columns().size());
        var windows=prefix.state().windows();assertEquals(2,windows.size());
        byte[] bytes=PhysicalDiscoveryState.encode(windows);
        var restored=PhysicalDiscoveryState.restore(bytes,baseline,receipt.prefix());assertEquals(windows,restored.state().windows());
        for(var changed:List.of(List.<String>of(),windows.subList(0,1),windows.subList(1,2),List.of(windows.get(0),windows.get(0))))
            assertThrows(RedoException.class,()->PhysicalDiscoveryState.restore(PhysicalDiscoveryState.encode(changed),baseline,receipt.prefix()));
        byte[] tail=Arrays.copyOf(bytes,bytes.length+1);tail[tail.length-1]='\n';
        assertThrows(RedoException.class,()->PhysicalDiscoveryState.restore(tail,baseline,receipt.prefix()));
    }
}
