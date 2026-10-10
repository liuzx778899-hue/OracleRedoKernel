import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.HexFormat;

/** Raw compensation header profiles only; diagnostic output cannot authorize events. */
public class ArrayCompensationFieldsProbe {
    public static void main(String[] args)throws Exception {
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,record->{
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && vector.opcodeMinor()==6 && vector.fields().size()==3) {
                System.out.println("AT|"+record.block()+"|"+record.offset());
                for(var bytes:vector.fields())System.out.println(HexFormat.of().formatHex(bytes));
            }
            for(var vector:record.vectors())if(vector.opcodeMajor()==5 && vector.opcodeMinor()==1
                    && vector.fields().size()==4 && vector.fields().get(3).length>10 && vector.fields().get(3)[10]==12) {
                System.out.println("ARRAY_UNDO|"+record.block()+"|"+record.offset());
                System.out.println(HexFormat.of().formatHex(vector.fields().get(2)));
            }
            for(var vector:record.vectors())if(vector.opcodeMajor()==11 && vector.opcodeMinor()==12) {
                System.out.println("ARRAY_XR|"+record.block()+"|"+record.offset());
                System.out.println(HexFormat.of().formatHex(vector.fields().get(0)));
            }
        });
    }
}
