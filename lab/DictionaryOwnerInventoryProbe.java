import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.nio.*;
import java.util.*;

/** Complete raw owner profile inventory for diagnostics; never event or dictionary authority. */
public class DictionaryOwnerInventoryProbe {
    public static void main(String[] args)throws Exception {
        Path raw=Path.of(args[0]);var xid=new HeapRowPatch.Xid(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Long.parseLong(args[3]));
        var header=new ArchivedRedo().scan(raw);var counts=new TreeMap<String,Integer>();int[] originals={0};
        System.out.println("INPUT|"+header.sequence()+"|"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw))));
        new RedoRecords().visit(raw,header,r->{if(r.containerUid()!=677737149)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                if(v.fields().isEmpty() || v.fields().get(0).length!=20)throw new AssertionError("OWNER_INVENTORY_UNREADABLE_IDENTITY");
                var b=ByteBuffer.wrap(v.fields().get(0)).order(ByteOrder.LITTLE_ENDIAN);
                if(!new HeapRowPatch.Xid(Short.toUnsignedInt(b.getShort(8)),Short.toUnsignedInt(b.getShort(10)),Integer.toUnsignedLong(b.getInt(12))).equals(xid))continue;
                if(v.fields().size()<2 || v.fields().get(1).length<24)throw new AssertionError("OWNER_INVENTORY_UNREADABLE_METADATA");
                var m=v.fields().get(1);var bytes=ByteBuffer.wrap(m).order(ByteOrder.LITTLE_ENDIAN);
                String key=Integer.toUnsignedLong(bytes.getInt(0))+"/"+Integer.toUnsignedLong(bytes.getInt(4))+"/"+Integer.toUnsignedLong(bytes.getInt(8))+" undo="+(m[16]&255)+"."+(m[17]&255);
                try{UndoHeader.decode(v);}catch(RedoException failure){System.out.println("HEADER_REFUSED|"+r.block()+"|"+r.offset()+"|"+failure.getMessage()+"|flags="+Short.toUnsignedInt(bytes.getShort(20)));}
                var forward=r.vectors().stream().filter(f->f.containerId()==3 && f.opcodeMajor()!=5).map(f->f.opcodeMajor()+"."+f.opcodeMinor()).toList();
                counts.merge(key+" forward="+forward,1,Integer::sum);originals[0]++;
                System.out.println("ORIGINAL|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+key+"|"+forward);
            }
        });
        counts.forEach((k,n)->System.out.println("PROFILE|"+k+"|"+n));System.out.println("COUNT|"+originals[0]);
        if(!header.equals(new ArchivedRedo().scan(raw)))throw new AssertionError("OWNER_INVENTORY_INPUT_CHANGED");
    }
}
