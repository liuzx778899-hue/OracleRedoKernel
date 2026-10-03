import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real T007 raw metadata derivatives; archive bytes are never modified. */
public class NegativeUndoChainProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); var header=new ArchivedRedo().scan(path);
        var xid=new HeapRowPatch.Xid(10,5,1133); var vectors=new ArrayList<RedoRecords.Vector>();
        HeapRowPatch.Uba[] begin={null},tail={null};
        new RedoRecords().visit(path,header,r -> {
            if (r.containerUid()!=677737149) return;
            for (var v:r.vectors()) {
                if (v.containerId()!=3 || v.opcodeMajor()!=5) continue;
                if (v.opcodeMinor()==1 && v.fields().size()>=2 && v.fields().get(0).length==20) {
                    var b=v.fields().get(0);
                    if (half(b,8)!=10 || half(b,10)!=5 || word(b,12)!=1133) continue;
                    var h=UndoHeader.decode(v); vectors.add(v); if (h.beginsTransaction()) begin[0]=h.uba();
                } else if (v.opcodeMinor()==4 && v.dba()==0x02400110 && !v.fields().isEmpty() && v.fields().get(0).length>=8) {
                    var b=v.fields().get(0);
                    if (half(b,0)==5 && word(b,4)==1133) tail[0]=TransactionEnd.undoTail(v);
                }
            }
        });
        if (vectors.size()!=5 || begin[0]==null || tail[0]==null) throw new IllegalStateException("seed coverage");
        run(xid,begin[0],vectors,tail[0]);
        int middle=-1,last=-1;
        for (int i=0;i<vectors.size();i++) { int rec=UndoHeader.decode(vectors.get(i)).uba().record(); if(rec==34) middle=i; if(rec==37) last=i; }
        if (middle<0 || last<0) throw new IllegalStateException("seed index nodes");
        var missing=new ArrayList<>(vectors); missing.remove(middle);
        check("missing_middle_index",xid,begin[0],missing,tail[0],"UNDO_CHAIN_HOLE");
        var duplicate=new ArrayList<>(vectors); duplicate.add(vectors.get(middle));
        check("duplicate_index",xid,begin[0],duplicate,tail[0],"DUPLICATE_UNDO_CHAIN_ADDRESS");
        var wrongRci=new ArrayList<>(vectors); wrongRci.set(middle,mutate(vectors.get(middle),1,19,31));
        check("wrong_predecessor",xid,begin[0],wrongRci,tail[0],"UNDO_CHAIN_HOLE");
        var branch=new ArrayList<>(vectors); branch.set(last,mutate(vectors.get(last),1,19,33));
        check("branch",xid,begin[0],branch,tail[0],"UNDO_CHAIN_BRANCH");
        check("wrong_end_tail",xid,begin[0],vectors,new HeapRowPatch.Uba(tail[0].dba(),tail[0].sequence(),36),"UNDO_CHAIN_END_REFERENCE_MISMATCH");
        var cross=new ArrayList<>(vectors); cross.set(middle,mutate(vectors.get(middle),1,12,1));
        check("unproven_cross_block",xid,begin[0],cross,tail[0],"CROSS_BLOCK_UNDO_LINK_NOT_PROVEN");
        System.err.println("passed=6;seed=T007;input_sha256="+header.sha256()+";cdc_events=0");
    }
    static void run(HeapRowPatch.Xid x,HeapRowPatch.Uba begin,List<RedoRecords.Vector> v,HeapRowPatch.Uba tail) throws Exception {
        var chain=new UndoChain(3,x,begin); for (var n:v) chain.add(n); chain.close(tail);
    }
    static void check(String name,HeapRowPatch.Xid x,HeapRowPatch.Uba begin,List<RedoRecords.Vector> v,HeapRowPatch.Uba tail,String reason) throws Exception {
        try { run(x,begin,v,tail); } catch (RedoException e) { if(!reason.equals(e.getMessage())) throw e; System.out.println(name+"|REFUSED|"+reason); return; }
        throw new IllegalStateException("negative accepted: "+name);
    }
    static RedoRecords.Vector mutate(RedoRecords.Vector v,int field,int offset,int value) {
        var fields=new ArrayList<byte[]>(); for(var b:v.fields()) fields.add(b.clone()); fields.get(field)[offset]=(byte)value;
        return new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),fields);
    }
    static int half(byte[] b,int o) { return (b[o]&255)|((b[o+1]&255)<<8); }
    static long word(byte[] b,int o) { return half(b,o)|((long)half(b,o+2)<<16); }
}
