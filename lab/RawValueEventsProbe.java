import com.streamhouse.oracle.*;
import com.streamhouse.engines.oracle.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** New RAW fixture: production input is only trusted snapshot and raw members. */
public class RawValueEventsProbe {
    public static void main(String[] args)throws Exception {
        var baseline=Path.of(args[0]);var snapshot=FrozenHeapBaseline.read(baseline,args[1]);
        var captures=new ArrayList<CaptureJournal.Verified>();var leaves=new TreeSet<Long>();
        for(int i=2;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        for(var capture:captures)for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn())continue;
            boolean owned=false;
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && vector.opcodeMinor()==1) {
                if(vector.fields().size()<2 || vector.fields().get(0).length!=20 || vector.fields().get(1).length<24)throw new IllegalStateException("RAW_LEAF_OBJECT_HEADER_UNREADABLE");
                var metadata=java.nio.ByteBuffer.wrap(vector.fields().get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                long object=Integer.toUnsignedLong(metadata.getInt(0)),data=Integer.toUnsignedLong(metadata.getInt(4));
                if(object!=snapshot.index().logicalObject() && data!=snapshot.index().dataObject())continue;
                var undo=UndoHeader.decode(vector);
                if(undo.objectId()!=snapshot.index().logicalObject() || undo.dataObjectId()!=snapshot.index().dataObject())throw new IllegalStateException("RAW_LEAF_OWNER_CONFLICT");
                owned=true;
            }
            if(owned)for(var vector:record.vectors())if(vector.opcodeMajor()==10 && (vector.opcodeMinor()==2 || vector.opcodeMinor()==4)) {
                var pair=IndexUndoPair.bind(record,vector);
                if(pair.undo().identity().objectId()!=snapshot.index().logicalObject() || pair.undo().identity().dataObjectId()!=snapshot.index().dataObject())throw new IllegalStateException("RAW_LEAF_OWNER_CONFLICT");
                leaves.add(pair.forward().block());
            }
        }
        if(leaves.size()!=1)throw new IllegalStateException("RAW_INDEX_LEAF_NOT_UNIQUE");
        var batch=CapturedHistoricalHeapBatch.read(captures,baseline,args[1],leaves.first());
        var projected=OracleBatchProjection.project(batch);
        var events=projected.entries().stream().map(OracleBatchProjection.Entry::envelope).toList();
        if(events.size()!=4)throw new AssertionError("RAW_COMMITTED_EVENT_COUNT:"+events.size());
        System.out.println(new ObjectMapper().writeValueAsString(Map.of("leaf",leaves.first(),"evidence",projected.evidence(),"events",events)));
    }
}
