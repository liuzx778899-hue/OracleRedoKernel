package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DictionaryObjectOwnerClosureTest {
    @Test void raw120ReplacementFollowsCreatorsAndExcludesClosedOwnersAndUnrelatedObjects()throws Exception {
        String path=System.getProperty("ork.lob.migration");assumeTrue(path!=null);
        var root=Path.of(path);
        var snapshot=FrozenHeapBaseline.read(root.resolve("baseline.snapshot"),"72555e21d09c917f33e2b2d7a95c34269cb11b3d16c738f7008d91979b2028d9");
        var raw=new CaptureJournal().readRawClosed(root.resolve("inputs/120.raw"),"6a2c6677ab29818dba14663850ca9cc56f0ba7564eec9227d0d9f387c8cb2f36");
        var inputs=RedoBatch.captures(List.of(raw));var finish=new HeapRowPatch.Xid(2,17,715);var create=new HeapRowPatch.Xid(10,9,5241);
        var owners=new LinkedHashSet<HeapRowPatch.Xid>(List.of(finish,new HeapRowPatch.Xid(10,18,5250)));
        DictionaryObjectOwnerClosure.extend(inputs,snapshot,owners);
        assertTrue(owners.contains(create));assertTrue(owners.contains(new HeapRowPatch.Xid(10,21,5246)));
        assertFalse(owners.contains(new HeapRowPatch.Xid(9,21,1033)));
        var restored=new LinkedHashSet<HeapRowPatch.Xid>(List.of(finish,new HeapRowPatch.Xid(10,18,5250)));
        DictionaryObjectOwnerClosure.extend(inputs,snapshot,restored,Set.of(create));
        assertFalse(restored.contains(create));assertTrue(restored.contains(finish));
    }
}
