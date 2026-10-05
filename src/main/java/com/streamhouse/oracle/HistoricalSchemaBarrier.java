package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 raw 24.1 scope barrier. Does not reconstruct a historical dictionary. */
final class HistoricalSchemaBarrier {
    static void requireUnchanged(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot) throws IOException {
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);
            input.visit(header,record->requireUnchanged(record,snapshot));
            if(!header.equals(input.header())) throw new RedoException("SCHEMA_BARRIER_SOURCE_CHANGED");
        }
    }
    static void requireUnchanged(RedoRecords.Record record,FrozenHeapBaseline.Snapshot snapshot) throws RedoException {
        if(record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn()) return;
        for(var vector:record.vectors()) {
            if(vector.containerId()!=snapshot.source().containerId() || vector.opcodeMajor()!=24) continue;
            if(vector.opcodeMinor()!=1) throw new RedoException("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER_OPCODE");
            var fields=vector.fields();
            // Independently observed on CREATE/GRANT/ADD/DROP in seq56/57.
            // SQL text in field 7 is deliberately not interpreted or used as authority.
            if(fields.size()!=46 || fields.get(0).length!=24 || u32(fields.get(0),0)!=0x2ee0
                    || fields.get(3).length!=12 || fields.get(11).length!=4)
                throw new RedoException("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER");
            long object=u32(fields.get(3),4),affected=u32(fields.get(11),0);
            for(var role:java.util.List.of(snapshot.heap(),snapshot.index()))
                if(object==role.logicalObject() || affected==role.logicalObject()
                        || (Arrays.equals(fields.get(8),role.owner().getBytes(StandardCharsets.UTF_8))
                        && Arrays.equals(fields.get(9),role.name().getBytes(StandardCharsets.UTF_8))))
                    throw new RedoException("HISTORICAL_SCHEMA_BARRIER:scn="+record.scn()+":object="+role.logicalObject());
        }
    }
}
