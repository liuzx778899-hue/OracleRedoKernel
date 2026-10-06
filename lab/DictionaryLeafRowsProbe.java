import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Typed leaf byte diagnostics only. Does not establish complete split closure or emit events. */
public class DictionaryLeafRowsProbe {
    static long word(byte[] b,int at){return (b[at]&255L)|((b[at+1]&255L)<<8)|((b[at+2]&255L)<<16)|((b[at+3]&255L)<<24);}
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);var scope=snapshot.dictionaryBaseline().indexes().stream().filter(x->x.role().name().equals("I_COL3")).findFirst().orElseThrow();
        var path=Path.of(args[0]);var header=new ArchivedRedo().scan(path);snapshot.requireSource(header);
        var images=new LinkedHashMap<String,DictionaryIndexLeafRows>();long[] oldBlock={0};
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
            boolean selected=false;
            for(var u:r.vectors())if(u.opcodeMajor()==5 && u.opcodeMinor()==1 && u.containerId()==snapshot.source().containerId()) {
                if(u.fields().size()<2 || u.fields().get(1).length<8)throw new RedoException("RESEARCH_UNREADABLE_INDEX_SCOPE");
                if(word(u.fields().get(1),0)!=scope.role().logicalObject())continue;
                selected=true;
                if(u.fields().size()==5 && u.fields().get(3).length==12 && u.fields().get(3)[0]==14) {
                    var image=DictionaryIndexLeafRows.before(u,scope.role(),scope.indexDba());
                    if(images.putIfAbsent("BEFORE",image)!=null)throw new RedoException("RESEARCH_DUPLICATE_BEFORE");oldBlock[0]=image.block();
                }
            }
            for(var v:r.vectors())if(v.opcodeMajor()==10 && v.opcodeMinor()==8 && v.containerId()==snapshot.source().containerId()
                    && (selected || oldBlock[0]!=0 && v.dba()==oldBlock[0])) {
                String kind=v.fields().get(0).length==0?"OLD":"NEW";
                if(images.putIfAbsent(kind,DictionaryIndexLeafRows.rebuilt(v))!=null)throw new RedoException("RESEARCH_DUPLICATE_REBUILD");
            }
        });
        if(images.size()!=3)throw new RedoException("RESEARCH_LEAF_IMAGE_COVERAGE");
        for(var entry:images.entrySet()) {
            var v=entry.getValue();System.out.println("IMAGE|"+entry.getKey()+"|"+v.block()+"|"+v.previousLink()+"|"+v.nextLink()+"|"+v.rows().size());
            for(int i=0;i<v.rows().size();i++) {
                var row=v.rows().get(i);var key=row.key();System.out.println("ROW|"+entry.getKey()+"|"+i+"|"+row.flags()+"|"+row.itl()+"|"+key.objectId()+"|"+key.columnNumber()+"|"+key.rowDba()+"|"+key.rowSlot()+"|"+row.rawHex());
            }
        }
        System.err.println("DIAGNOSTIC_ONLY|typed_images=3|complete_split=false|events=0");
    }
}
