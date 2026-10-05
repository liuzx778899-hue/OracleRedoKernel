import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Discover a unique index leaf from target-owned raw redo after the trusted baseline, without native dump input. */
public class RawDmlLeafProbe {
    public static void main(String[] args) throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);var leaves=new TreeSet<Long>();
        for (int i=2;i<args.length;i+=2) {
            var capture=new CaptureJournal().read(Path.of(args[i]),args[i+1]);
            for (var record:new RedoRecords().readCaptured(capture)) {
                if (record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn()) continue;
                boolean owned=false;
                for (var vector:record.vectors()) if (vector.opcodeMajor()==5 && vector.opcodeMinor()==1) {
                    // Match the kernel's object routing before decoding target-specific undo semantics.
                    if(vector.fields().size()<2 || vector.fields().get(0).length!=20 || vector.fields().get(1).length<24)
                        throw new IllegalStateException("RAW_LEAF_OBJECT_HEADER_UNREADABLE");
                    var metadata=java.nio.ByteBuffer.wrap(vector.fields().get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    long object=Integer.toUnsignedLong(metadata.getInt(0)),data=Integer.toUnsignedLong(metadata.getInt(4));
                    if(object!=snapshot.index().logicalObject() && data!=snapshot.index().dataObject())continue;
                    var undo=UndoHeader.decode(vector);
                    if(undo.objectId()!=snapshot.index().logicalObject() || undo.dataObjectId()!=snapshot.index().dataObject())
                        throw new IllegalStateException("RAW_LEAF_OBJECT_GENERATION_CONFLICT");
                    owned=true;
                }
                if (!owned) continue;
                for (var vector:record.vectors()) if (vector.opcodeMajor()==10 && (vector.opcodeMinor()==2 || vector.opcodeMinor()==4)) {
                    var pair=IndexUndoPair.bind(record,vector);
                    if (pair.undo().identity().objectId()!=snapshot.index().logicalObject() || pair.undo().identity().dataObjectId()!=snapshot.index().dataObject()) throw new IllegalStateException("RAW_LEAF_OWNER_CONFLICT");
                    leaves.add(pair.forward().block());
                }
            }
        }
        if (leaves.size()!=1) throw new IllegalStateException("RAW_INDEX_LEAF_NOT_UNIQUE");
        System.out.println("LEAF|"+leaves.first());
    }
}
