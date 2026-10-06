import com.streamhouse.oracle.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Locate exact rejected dictionary discovery records; diagnostic output never creates events. */
public class DictionaryDiscoveryResearchProbe {
    static long u32(byte[] b,int at){return (b[at]&255L)|((b[at+1]&255L)<<8)|((b[at+2]&255L)<<16)|((b[at+3]&255L)<<24);}
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var method=ArchivedDictionaryHistory.class.getDeclaredMethod("affectsBaselineScope",RedoRecords.Record.class,RedoRecords.Vector.class,FrozenHeapBaseline.ObjectRole.class,FrozenHeapBaseline.Snapshot.class);
        method.setAccessible(true);var output=new ArrayList<String>();int[] errors={0};
        for(int a=2;a<args.length;a++) {
            var path=Path.of(args[a]);var header=new ArchivedRedo().scan(path);snapshot.requireSource(header);
            new RedoRecords().visit(path,header,r->{
                if(r.scn()<=snapshot.scn() || r.containerUid()!=snapshot.source().containerUid())return;
                for(int i=0;i<r.vectors().size();i++) {
                    var undo=r.vectors().get(i);
                    if(undo.containerId()!=snapshot.source().containerId() || undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1 || undo.fields().size()<2 || undo.fields().get(1).length<8)continue;
                    var role=snapshot.dictionaryBaseline().indexes().stream().map(DictionaryBaseline.Index::role).filter(x->x.logicalObject()==u32(undo.fields().get(1),0)).findFirst();
                    if(role.isEmpty())continue;
                    String at=header.sequence()+":"+r.block()+":"+r.offset()+":"+(i+1),result;
                    try{result="selected="+method.invoke(null,r,undo,role.get(),snapshot);}
                    catch(InvocationTargetException failure){result="REFUSED:"+failure.getCause().getMessage();errors[0]++;}
                    catch(ReflectiveOperationException failure){throw new java.io.IOException(failure);}
                    String opcodes=String.join(",",r.vectors().stream().map(v->v.opcodeMajor()+"."+v.opcodeMinor()).toList());
                    output.add("DISCOVERY|"+at+"|"+r.scn()+"|"+role.get().name()+"|"+UndoHeader.decode(undo)+"|"+opcodes+"|"+result);
                    if(result.startsWith("REFUSED"))for(int v=0;v<r.vectors().size();v++) {
                        var vector=r.vectors().get(v);output.add("VECTOR|"+at+"|"+(v+1)+"|"+vector.opcodeMajor()+"."+vector.opcodeMinor()+"|"+vector.dba()+"|"+vector.containerId());
                        for(int f=0;f<vector.fields().size();f++)output.add("FIELD|"+at+"|"+(v+1)+"|"+(f+1)+"|"+HexFormat.of().formatHex(vector.fields().get(f)));
                    }
                }
            });
        }
        System.out.println(String.join("\n",output));System.err.println("DIAGNOSTIC_ONLY|discovery_refusals="+errors[0]+"|events=0");
    }
}
