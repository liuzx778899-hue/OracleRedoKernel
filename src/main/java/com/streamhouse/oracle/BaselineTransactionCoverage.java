package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete pre-acquisition initialization identities. Never covers an active or later commit. */
final class BaselineTransactionCoverage {
    record Receipt(TransactionLifecycle.ClosedIdentity identity,ArchivedUndoInventory.Position begin,
                   ArchivedUndoInventory.Position end,List<ArchivedUndoInventory.Position> originals) {}
    static List<Receipt> read(RedoBatch paths,long uid,long container,HeapIndexScope scope,long scn) throws IOException {
        var candidates=new HashSet<HeapRowPatch.Xid>();
        for(var path:paths)path.visit(path.header(),r->{
            if(r.containerUid()!=uid)return;
            for(var v:r.vectors()) {
                if(v.containerId()!=container || v.opcodeMajor()!=5 || v.opcodeMinor()!=1
                        || v.fields().size()<2 || v.fields().get(0).length!=20 || v.fields().get(1).length<24)continue;
                byte[] m=v.fields().get(1);
                if(!scope.generations().containsKey(u32(m,0)) && !scope.generations().containsValue(u32(m,4)))continue;
                boolean initialization=(m[16]==14 && m[17]==5) || r.vectors().stream().anyMatch(f->
                        f.containerId()==container && (f.opcodeMajor()==11 && f.opcodeMinor()==11
                            || f.opcodeMajor()==10 && f.opcodeMinor()==2 && f.fields().size()==5));
                if(initialization)candidates.add(xid(v));
            }
        });
        var receipts=new ArrayList<Receipt>();
        for(var xid:candidates) {
            var state=new TransactionLifecycle();var original=new ArrayList<ArchivedUndoInventory.Position>();
            var seen=new HashSet<HeapRowPatch.Uba>();HeapRowPatch.Uba[] head={null};
            ArchivedUndoInventory.Position[] begin={null},end={null};TransactionLifecycle.ClosedIdentity[] closed={null};
            boolean[] nonRow={false},active={false};
            for(var path:paths) {
                var header=path.header();state.archive(header);
                path.visit(header,r->{
                    if(r.containerUid()!=uid)return;
                    for(int i=0;i<r.vectors().size();i++) {
                        var v=r.vectors().get(i);
                        if(v.containerId()!=container || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || !xid(v).equals(xid))continue;
                        if(r.scn()<header.firstScn() || r.scn()>=header.nextScn())throw new RedoException("BASELINE_INITIALIZATION_SCN_OUTSIDE_ARCHIVE");
                        var h=header(r,v);byte[] m=v.fields().get(1);
                        if(!seen.add(h.uba()) || closed[0]!=null)throw new RedoException("BASELINE_INITIALIZATION_UNDO_REUSE_OR_AFTER_END");
                        if(h.beginsTransaction()) {
                            if(!original.isEmpty())throw new RedoException("BASELINE_INITIALIZATION_BEGIN_ORDER");
                            int count=0;
                            for(var control:r.vectors())if(control.containerId()==container && control.opcodeMajor()==5 && control.opcodeMinor()==2
                                    && control.fields().size()==2 && TransactionStart.decode(control).uba().equals(h.uba())) {
                                state.begin(r,control);count++;
                            }
                            if(count!=1 || u32(m,12)!=0 || m[19]!=0 || u16(m,22)!=1)
                                throw new RedoException("BASELINE_INITIALIZATION_BEGIN_BINDING");
                            begin[0]=position(header,r,i);active[0]=true;nonRow[0]=m[16]==5 && m[17]==7;
                        } else {
                            if(!active[0] || (m[18]&255)!=xid.slot() || u16(m,22)!=(xid.sequence()&0xffff))
                                throw new RedoException("BASELINE_INITIALIZATION_UNDO_IDENTITY");
                            boolean allocationRoot=nonRow[0] && original.size()==1;
                            if(allocationRoot) {
                                int count=0;
                                for(var control:r.vectors())if(control.containerId()==container && control.opcodeMajor()==5
                                        && control.opcodeMinor()==2 && UndoHeadUpdate.decode(control).uba().equals(h.uba()))count++;
                                if(count!=1 || h.uba().dba()!=head[0].dba() || h.uba().sequence()!=head[0].sequence()
                                        || h.uba().record()!=head[0].record()+1 || u32(m,12)!=0 || m[19]!=0)
                                    throw new RedoException("BASELINE_INITIALIZATION_ALLOCATION_ROOT");
                            } else if(u32(m,12)==0 ? !new HeapRowPatch.Uba(h.uba().dba(),h.uba().sequence(),m[19]&255).equals(head[0])
                                    : m[19]!=0 || u32(m,12)!=head[0].dba())
                                throw new RedoException("BASELINE_INITIALIZATION_UNDO_CHAIN");
                        }
                        head[0]=h.uba();original.add(position(header,r,i));
                    }
                    if(active[0])for(var v:r.vectors())if(v.containerId()==container && v.opcodeMajor()==5) {
                        if(v.opcodeMinor()==2)state.observeSlotControl(r,v);
                        if(v.opcodeMinor()==6 || v.opcodeMinor()==11)throw new RedoException("UNSUPPORTED_BASELINE_INITIALIZATION_COMPENSATION");
                    }
                    for(int i=0;i<r.vectors().size();i++) {
                        var v=r.vectors().get(i);
                        if(v.containerId()!=container || v.opcodeMajor()!=5 || v.opcodeMinor()!=4 || !state.knowsEnd(r,v))continue;
                        closed[0]=state.end(r,v);end[0]=position(header,r,i);active[0]=false;
                        if(closed[0].rolledBack())throw new RedoException("UNSUPPORTED_BASELINE_INITIALIZATION_ROLLBACK");
                        if(v.fields().size()==2 && (TransactionEnd.decode(v).flags()!=0 || seen.stream().anyMatch(a->
                                a.dba()!=head[0].dba() || a.sequence()!=head[0].sequence())))
                            throw new RedoException("UNSUPPORTED_BASELINE_INITIALIZATION_UNREFERENCED_COMMIT");
                        if(v.fields().size()!=2 && !TransactionEnd.undoTail(v).equals(head[0]))
                            throw new RedoException("BASELINE_INITIALIZATION_COMMIT_TAIL");
                    }
                });
                if(!header.equals(path.header()))throw new RedoException("BASELINE_INITIALIZATION_INPUT_CHANGED");
            }
            // A pending initialization still follows the ordinary parser; no acquisition can cover it.
            if(closed[0]!=null && closed[0].endRecordScn()<=scn) {
                state.finish();receipts.add(new Receipt(closed[0],begin[0],end[0],List.copyOf(original)));
            }
        }
        return List.copyOf(receipts);
    }
    private static HeapRowPatch.Xid xid(RedoRecords.Vector v)throws RedoException {
        if(v.fields().isEmpty() || v.fields().get(0).length!=20)throw new RedoException("UNREADABLE_BASELINE_INITIALIZATION_IDENTITY");
        byte[] b=v.fields().get(0);return new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));
    }
    private static UndoHeader header(RedoRecords.Record r,RedoRecords.Vector v)throws RedoException {
        var f=v.fields();if(f.size()<2 || f.get(1).length!=24 || u16(f.get(1),20)!=0x80)return UndoHeader.decode(v);
        byte[] m=f.get(1),b=f.get(0);var xid=xid(v);
        if(m[16]==14 && m[17]==5) {
            var forwards=r.vectors().stream().filter(x->x.containerId()==v.containerId() && x.opcodeMajor()==14 && x.opcodeMinor()==4).toList();
            if(forwards.size()!=1)throw new RedoException("BASELINE_EXTENT_FORWARD_COUNT");
            ExtentMapPair.bind(r,v,forwards.get(0));
        } else if(m[16]!=22 || m[17]!=3 || u32(m,0)!=1 || u32(m,4)!=0xffffffffL || f.size()!=3
                || f.get(2).length!=20 || !java.util.Arrays.equals(f.get(2),new byte[20]))
            throw new RedoException("UNSUPPORTED_BASELINE_STORAGE_UNDO");
        return new UndoHeader(xid,new HeapRowPatch.Uba(v.dba(),u16(b,16),b[18]&255),u32(m,0),u32(m,4),u32(m,8),false);
    }
    private static ArchivedUndoInventory.Position position(ArchivedRedo.Header h,RedoRecords.Record r,int i) {
        return new ArchivedUndoInventory.Position(h.sequence(),r.block(),r.offset(),i+1);
    }
}
