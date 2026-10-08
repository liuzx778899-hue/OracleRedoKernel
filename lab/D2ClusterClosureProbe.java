package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;

/** Exercises the input-owned complete maintenance reader on frozen raw input. */
public class D2ClusterClosureProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);
        var closed=ArchivedDictionaryClusterMaintenance.read(RedoBatch.archives(List.of(Path.of(args[0]))),snapshot);
        if(closed.size()!=1 || closed.get(0).original().size()!=5 || closed.get(0).parent()!=75185
                || !closed.get(0).identity().xid().equals(new HeapRowPatch.Xid(10,3,3527)))
            throw new RedoException("OBSERVED_CLUSTER_MAINTENANCE_MISMATCH");
        System.out.println(closed.get(0));
    }
}
