import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;

/** Experimental raw-stream cursor hypothesis. Output is a research graph, never accepted CDC. */
public class UndoCursorProbe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);String[] parts=args[1].split("\\.");
        var xid=new HeapRowPatch.Xid(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Long.parseLong(parts[2]));
        if(args.length>2 && (args.length!=3 || !args[2].equals("--omit-cross-predecessor")))throw new IllegalArgumentException("unknown research mode");
        var header=new ArchivedRedo().scan(p);var state=new TransactionLifecycle();state.archive(header);
        var omitted=args.length==3 ? firstCrossPredecessor(p,header,xid) : null;
        if(omitted!=null)System.err.println("intentional_research_omission="+address(omitted)+";archive_bytes_unchanged");
        var output=new ArrayList<String>();HeapRowPatch.Uba[] cursor={null};int[] nodes={0},cross={0},ends={0};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149)return;
            var selected=new ArrayList<RedoRecords.Vector>();
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().get(0).length==20) {
                byte[] b=v.fields().get(0);
                if(half(b,8)==xid.usn() && half(b,10)==xid.slot() && word(b,12)==xid.sequence()
                        && !UndoHeader.decode(v).uba().equals(omitted))selected.add(v);
            }
            for(var u:selected) if(UndoHeader.decode(u).beginsTransaction()) {
                int matches=0;
                for(var v:r.vectors()) if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().get(0).length==32) {
                    byte[] b=v.fields().get(0);
                    if(word(b,8)==u.dba() && half(b,12)==half(u.fields().get(0),16) && b[14]==u.fields().get(0)[18]) {state.begin(r,v);matches++;}
                }
                if(matches!=1)throw new RedoException("CURSOR_BEGIN_CONTROL_COUNT");
            }
            for(var u:selected) {
                state.undo(r,u);var h=UndoHeader.decode(u);byte[] b=u.fields().get(1);
                HeapRowPatch.Uba previous=cursor[0];long previousDba=word(b,12);int rci=b[19]&255;
                if(h.beginsTransaction()) {
                    if(previous!=null || previousDba!=0 || rci!=0)throw new RedoException("CURSOR_BEGIN_MISMATCH");
                } else if(previousDba==0) {
                    var link=UndoLink.decode(u);
                    if(previous==null || !previous.equals(link.predecessor()))throw new RedoException("CURSOR_SAME_BLOCK_PREDECESSOR_MISMATCH");
                } else {
                    if(previous==null || previous.dba()!=previousDba || rci!=0)throw new RedoException("CURSOR_CROSS_BLOCK_DBA_MISMATCH");
                    int matches=0;
                    for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().get(0).length==32 && half(v.fields().get(0),16)==10) {
                        var a=UndoBlockAdvance.decode(v);
                        if(a.uba().equals(h.uba())) {var bound=state.advance(r,v);if(!bound.xid().equals(xid))throw new RedoException("CURSOR_ADVANCE_XID_MISMATCH");matches++;}
                    }
                    if(matches!=1)throw new RedoException("CURSOR_CROSS_CONTROL_COUNT");cross[0]++;
                }
                HeapRowPatch.Uba historical=null;long rowDba=0;int slot=-1;
                if((b[16]&255)==11 && (b[17]&255)==1) {
                    var inverse=UndoRowPatch.decode(u,2).inverse();rowDba=inverse.dba();slot=inverse.slot();
                    byte[] ktb=u.fields().get(2);
                    if(ktb[0]==2 && ktb.length==16)historical=new HeapRowPatch.Uba(word(ktb,8),half(ktb,12),ktb[14]&255);
                }
                output.add("NODE|"+r.block()+"|"+r.offset()+"|"+(r.vectors().indexOf(u)+1)+"|"+address(h.uba())+"|"+address(previous)+"|"+previousDba+"|"+rci+"|"+rowDba+"|"+slot+"|"+address(historical));
                cursor[0]=h.uba();nodes[0]++;
            }
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                var tail=TransactionEnd.undoTail(v);if(!tail.equals(cursor[0]))throw new RedoException("CURSOR_NATIVE_END_TAIL_MISMATCH");
                state.end(r,v);ends[0]++;output.add("END|"+r.block()+"|"+r.offset()+"|"+address(tail));
            }
        });state.finish();
        if(nodes[0]!=(omitted==null ? 120 : 119) || cross[0]<1 || ends[0]!=1)throw new RedoException("CURSOR_FIXTURE_COVERAGE_MISMATCH");
        output.forEach(System.out::println);System.err.println("nodes="+nodes[0]+";cross_controls="+cross[0]+";experimental_cursor_hypothesis_only;cdc_events=0");
    }
    /** Counterexample to local cursor constraints when an upstream selector silently loses a node. */
    static HeapRowPatch.Uba firstCrossPredecessor(Path p,ArchivedRedo.Header header,HeapRowPatch.Xid xid)throws Exception {
        HeapRowPatch.Uba[] previous={null},omitted={null};
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149 || omitted[0]!=null)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().get(0).length==20) {
                byte[] b=v.fields().get(0);
                if(half(b,8)!=xid.usn() || half(b,10)!=xid.slot() || word(b,12)!=xid.sequence())continue;
                var h=UndoHeader.decode(v);
                if(word(v.fields().get(1),12)!=0) {
                    if(previous[0]==null || previous[0].dba()!=word(v.fields().get(1),12))throw new RedoException("HOLE_EXPERIMENT_PRECONDITION_FAILED");
                    omitted[0]=previous[0];break;
                }
                previous[0]=h.uba();
            }
        });
        if(omitted[0]==null)throw new RedoException("HOLE_EXPERIMENT_NO_CROSS_BOUNDARY");
        return omitted[0];
    }
    static String address(HeapRowPatch.Uba u){return u==null ? "NONE" : u.dba()+":"+u.sequence()+":"+u.record();}
    static int half(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    static long word(byte[] b,int o){return half(b,o)|((long)half(b,o+2)<<16);}
}
