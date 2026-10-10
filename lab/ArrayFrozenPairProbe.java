import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Frozen owned ARRAY bytes; independent key/rowid alignment, no SQL or dump event input. */
public class ArrayFrozenPairProbe {
    static long be(byte[] value,int offset,int size) {long result=0;for(int i=0;i<size;i++)result=result*256+(value[offset+i]&255);return result;}
    public static void main(String[] args)throws Exception {
        Path path=Path.of(args[0]);String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        if(!digest.equals(args[1]))throw new AssertionError("INPUT_SHA");
        var capture=new CaptureJournal().readRawClosed(path,digest);
        var heap=new LinkedHashMap<String,ArchivedHeapRows.RowKey>();var indexes=new LinkedHashMap<String,ArchivedHeapRows.RowKey>();
        int[] nodes={0,0};
        for(var record:new RedoRecords().readCaptured(capture))for(var vector:record.vectors()) {
            if(vector.objectId()==78310 && vector.opcodeMajor()==11 && vector.opcodeMinor()==11) {
                var pairs=HeapArrayUndoPair.bind(record,vector,3);nodes[0]++;
                for(var pair:pairs) {
                    var f=pair.forward();String id=OracleScalars.number(f.columns().get(0).bytes()).toPlainString();
                    if(!OracleScalars.varchar2(f.columns().get(1).bytes(),"AL32UTF8").equals("block-seed"+"x".repeat(90))
                            || !OracleScalars.varchar2(f.columns().get(2).bytes(),"AL32UTF8").equals("anchor"))throw new AssertionError("ROW_BYTES");
                    if(heap.putIfAbsent(id,new ArchivedHeapRows.RowKey(78310,f.dba(),f.slot()))!=null)throw new AssertionError("DUPLICATE_HEAP_KEY");
                }
            }
            if(vector.objectId()==78311 && vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && vector.fields().size()==5) {
                var pairs=IndexArrayUndoPair.bind(record,vector);nodes[1]++;
                for(var pair:pairs) {
                    var f=pair.forward();String id=OracleScalars.number(Arrays.copyOfRange(f.key(),1,f.key().length)).toPlainString();
                    byte[] rowid=f.keydata();var key=new ArchivedHeapRows.RowKey(78310,be(rowid,0,4),(int)be(rowid,4,2));
                    if(indexes.putIfAbsent(id,key)!=null)throw new AssertionError("DUPLICATE_INDEX_KEY");
                }
                vector.fields().get(2)[0]++;
                try {IndexArrayUndoPair.bind(record,vector);throw new AssertionError("KEY_POISON_ACCEPTED");}
                catch(RedoException expected) {}
            }
        }
        if(heap.size()!=20 || !heap.equals(indexes) || nodes[0]!=7 || nodes[1]!=1)throw new AssertionError("ARRAY_PHYSICAL_COVERAGE:"+heap.size()+"/"+indexes.size()+"/"+Arrays.toString(nodes));
        System.out.println("ARRAY_FROZEN_PAIRS_PASS|rows=20|heapNodes=7|indexNodes=1|rowidDifferences=0|events=0");
    }
}
