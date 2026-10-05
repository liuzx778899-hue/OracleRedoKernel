import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.*;

/** Frozen archive research only: full-XID originals and their segment-slot controls, no inferred edges. */
public class SysControlResearchProbe {
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
    static boolean selected(RedoRecords.Vector v,HeapRowPatch.Xid xid) {
        if(v.containerId()!=3||v.opcodeMajor()!=5||v.opcodeMinor()!=1)return false;
        if(v.fields().isEmpty()||v.fields().get(0).length!=20)throw new IllegalStateException("UNREADABLE_RESEARCH_UNDO_IDENTITY");
        byte[] b=v.fields().get(0);
        return half(b,8)==xid.usn()&&half(b,10)==xid.slot()&&word(b,12)==xid.sequence();
    }
    public static void main(String[] args) throws Exception {
        var path=Path.of(args[0]);var parts=args[1].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Long.parseLong(parts[2]));
        var header=new ArchivedRedo().scan(path);var segments=new HashSet<Long>();
        var records=new ArrayList<RedoRecords.Record>();
        new RedoRecords().visit(path,header,r->{ if(r.containerUid()==677737149)records.add(r); });
        for(var r:records) {
            var ubas=new HashSet<HeapRowPatch.Uba>();
            for(var v:r.vectors())if(selected(v,xid)) {
                ubas.add(UndoHeader.decode(v).uba());
            }
            for(var v:r.vectors())if(v.containerId()==3&&v.opcodeMajor()==5&&v.opcodeMinor()==2
                    &&!v.fields().isEmpty()&&v.fields().get(0).length==32) {
                byte[] b=v.fields().get(0);
                if(half(b,0)==xid.slot()&&ubas.contains(new HeapRowPatch.Uba(word(b,8),half(b,12),b[14]&255)))segments.add(v.dba());
            }
        }
        if(segments.size()!=1)throw new IllegalStateException("RESEARCH_SEGMENT_NOT_UNIQUE:"+segments);
        long segment=segments.iterator().next();
        for(var r:records) {
            boolean selected=false;
            for(var v:r.vectors())if(v.containerId()==3&&v.opcodeMajor()==5) {
                if(selected(v,xid))selected=true;
                if((v.opcodeMinor()==2||v.opcodeMinor()==4)&&v.dba()==segment&&!v.fields().isEmpty()
                        &&v.fields().get(0).length>=8&&half(v.fields().get(0),0)==xid.slot()
                        &&word(v.fields().get(0),4)==xid.sequence())selected=true;
            }
            if(!selected)continue;
            System.out.println("RECORD|"+header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+r.subScn()+"|"+r.containerUid());
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);
                System.out.println("VECTOR|"+(i+1)+"|"+v.opcodeMajor()+"."+v.opcodeMinor()+"|"+v.dba()+"|"+v.scn()+"|"+v.containerId());
                for(int f=0;f<v.fields().size();f++)System.out.println("FIELD|"+f+"|"+HexFormat.of().formatHex(v.fields().get(f)));
            }
        }
        System.err.println("research_segment="+segment+";no_predecessor_inference;cdc_events=0");
    }
}
