import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Record-level mutations of a frozen real pair, not physical archive corruption tests. */
public class NegativeIndexPairProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); var header=new ArchivedRedo().scan(path);
        RedoRecords.Record[] source={null};
        new RedoRecords().visit(path,header,r -> { if (r.block()==30680 && r.offset()==280) source[0]=r; });
        if (source[0]==null) throw new RedoException("FROZEN_NEGATIVE_SOURCE_MISSING");
        int rejected=0;
        for (String mode:List.of("missing","duplicate_undo","duplicate_forward","container","uba_sequence",
                "xid","key","block","itl","mode","short_header","unknown_ktb")) {
            var vectors=new ArrayList<RedoRecords.Vector>();
            for (var v:source[0].vectors()) vectors.add(copy(v,v.containerId()));
            var undo=vectors.get(0); var row=vectors.get(1);
            switch (mode) {
                case "missing" -> vectors.remove(0);
                case "duplicate_undo" -> vectors.add(undo);
                case "duplicate_forward" -> vectors.add(row);
                case "container" -> vectors.set(0,copy(undo,4));
                case "uba_sequence" -> undo.fields().get(0)[16]^=1;
                case "xid" -> row.fields().get(0)[12]^=1;
                case "key" -> undo.fields().get(4)[2]^=1;
                case "block" -> undo.fields().get(3)[8]^=1;
                case "itl" -> undo.fields().get(3)[1]^=1;
                case "mode" -> row.fields().get(1)[1]=1;
                case "short_header" -> {
                    var fields=new ArrayList<>(undo.fields()); fields.set(0,new byte[19]);
                    vectors.set(0,new RedoRecords.Vector(5,1,undo.blockClass(),undo.file(),undo.dba(),undo.scn(),undo.sequence(),undo.containerId(),fields));
                }
                case "unknown_ktb" -> row.fields().get(0)[0]=4;
            }
            var record=new RedoRecords.Record(source[0].block(),source[0].offset(),source[0].length(),source[0].validity(),
                    source[0].scn(),source[0].subScn(),source[0].containerUid(),vectors);
            try { IndexUndoPair.bind(record,row); throw new IllegalStateException("NEGATIVE_ACCEPTED:"+mode); }
            catch (RedoException e) { System.out.println(mode+"|"+e.getMessage()); rejected++; }
        }
        if (rejected!=12) throw new IllegalStateException("NEGATIVE_COUNT_MISMATCH");
        System.err.println("real_record_derived_refusals=12;cdc_events=0");
    }
    static RedoRecords.Vector copy(RedoRecords.Vector v,long container) {
        var fields=new ArrayList<byte[]>(); for (byte[] field:v.fields()) fields.add(field.clone());
        return new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),container,fields);
    }
}
