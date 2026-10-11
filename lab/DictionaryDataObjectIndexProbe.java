import com.streamhouse.oracle.*;
import java.nio.*;
import java.nio.file.*;

/** Frozen raw physical profile verification; never emits business events. */
public class DictionaryDataObjectIndexProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);long minimum=Long.parseLong(args[1]);var header=new ArchivedRedo().scan(raw);int[] count={0};
        new RedoRecords().visit(raw,header,r->{if(r.containerUid()!=677737149 || r.scn()<=minimum)return;
            for(var u:r.vectors())if(u.containerId()==3 && u.opcodeMajor()==5 && u.opcodeMinor()==1 && u.fields().size()>1
                    && u.fields().get(1).length>=24 && ByteBuffer.wrap(u.fields().get(1)).order(ByteOrder.LITTLE_ENDIAN).getInt()==39) {
                int matched=0;
                for(var row:r.vectors())if(row.containerId()==3 && row.opcodeMajor()==10 && row.objectId()==39) {
                    DictionaryDataObjectIndexPair pair;
                    try { pair=DictionaryDataObjectIndexPair.bind(r,row); }
                    catch(RedoException failure) {
                        System.err.println("REFUSED|"+r.block()+"|"+r.offset()+"|"+r.scn());
                        for(int i=0;i<row.fields().size();i++)System.err.println("FORWARD|"+i+"|"+java.util.HexFormat.of().formatHex(row.fields().get(i)));
                        for(int i=0;i<u.fields().size();i++)System.err.println("UNDO|"+i+"|"+java.util.HexFormat.of().formatHex(u.fields().get(i)));
                        throw failure;
                    }
                    if(!pair.physical().undo().identity().equals(UndoHeader.decode(u)))throw new AssertionError("DATA_OBJECT_INDEX_IDENTITY_CHANGED");
                    System.out.println("PAIR|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+pair.physical().forward().kind()+"|"+pair.dataObject()+"|"+pair.type()+"|"+pair.spare3()+"|"+pair.rowDba()+"|"+pair.rowSlot());
                    matched++;count[0]++;
                }
                if(matched!=1)throw new AssertionError("DATA_OBJECT_INDEX_PAIR_COVERAGE");
            }
        });
        if(!header.equals(new ArchivedRedo().scan(raw)))throw new AssertionError("DATA_OBJECT_INDEX_SOURCE_CHANGED");
        System.out.println("PAIRS|"+count[0]);
    }
}
