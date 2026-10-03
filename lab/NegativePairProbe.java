import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mutations of one real parsed target record; does not modify archive bytes or the database. */
public class NegativePairProbe {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); var header=new ArchivedRedo().scan(path);
        RedoRecords.Record[] selected={null}; RedoRecords.Vector[] row={null},undo={null};
        new RedoRecords().visit(path,header,r -> {
            if (selected[0]!=null) return;
            for (var u:r.vectors()) if (TransactionLifecycleProbe.targetUndo(u)) {
                var identity=UndoHeader.decode(u);
                for (var v:r.vectors()) {
                    if (v.opcodeMajor()!=11 || v.opcodeMinor()!=2 || v.containerId()!=3) continue;
                    var p=new HeapRowPatch().decode(v,4);
                    if (p.xtype()==1 && identity.uba().equals(p.uba())) { selected[0]=r; row[0]=v; undo[0]=u; return; }
                }
            }
        });
        if (selected[0]==null) throw new IllegalStateException("no real seed record");
        var seed=selected[0]; var forward=row[0]; var inverse=undo[0];
        HeapUndoPair.bind(seed,forward,4);
        var missing=new ArrayList<>(seed.vectors()); missing.remove(inverse);
        check("missing_undo",copy(seed,missing),forward,"MISSING_ROW_UNDO_UBA");
        var duplicate=new ArrayList<>(seed.vectors()); duplicate.add(inverse);
        check("duplicate_undo",copy(seed,duplicate),forward,"AMBIGUOUS_ROW_UNDO_UBA");
        var duplicateRow=new ArrayList<>(seed.vectors()); duplicateRow.add(forward);
        check("duplicate_row",copy(seed,duplicateRow),forward,"AMBIGUOUS_FORWARD_ROW_UBA");
        var slot=mutate(inverse,3,16,1); var slotVectors=new ArrayList<>(seed.vectors()); slotVectors.set(slotVectors.indexOf(inverse),slot);
        check("inverse_slot_poison",copy(seed,slotVectors),forward,"ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH");
        var xid=mutate(forward,0,12,(forward.fields().get(0)[12]&255)^1); var xidVectors=new ArrayList<>(seed.vectors()); xidVectors.set(xidVectors.indexOf(forward),xid);
        check("forward_xid_poison",copy(seed,xidVectors),xid,"ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH");
        var xr=mutate(forward,1,11,2); var xrVectors=new ArrayList<>(seed.vectors()); xrVectors.set(xrVectors.indexOf(forward),xr);
        check("restored_xr_pointer",copy(seed,xrVectors),xr,"NOT_FORWARD_ROW_UNDO_REFERENCE");
        System.err.printf("passed=6;seed=%d/%d;input_sha256=%s;cdc_events=0%n",seed.block(),seed.offset(),header.sha256());
    }
    static RedoRecords.Vector mutate(RedoRecords.Vector v,int field,int offset,int value) {
        var fields=new ArrayList<byte[]>(); for (var b:v.fields()) fields.add(b.clone()); fields.get(field)[offset]=(byte)value;
        return new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),fields);
    }
    static RedoRecords.Record copy(RedoRecords.Record r,List<RedoRecords.Vector> v) {
        return new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),v);
    }
    static void check(String name,RedoRecords.Record r,RedoRecords.Vector v,String reason) throws Exception {
        try { HeapUndoPair.bind(r,v,4); } catch (RedoException e) {
            if (!e.getMessage().equals(reason)) throw e;
            System.out.println(name+"|REFUSED|"+reason); return;
        }
        throw new IllegalStateException("negative case accepted: "+name);
    }
}
