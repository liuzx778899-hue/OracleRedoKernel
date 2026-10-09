package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Conservative PDB-window marker coverage. Inclusion does not establish marker ownership. */
public final class ArchivedCompensationInventory {
    public record Entry(ArchivedUndoInventory.Position position,UndoCompensation marker) {}
    private final ArchivedUndoInventory undo;
    private final List<Entry> entries;
    private final ArchivedUndoInventory.Position firstRecord,lastRecord;
    private final boolean complete;
    private final long segmentDba;
    private final byte[] identityCheckpoint;

    ArchivedCompensationInventory(ArchivedUndoInventory undo,List<Entry> entries,
                                 ArchivedUndoInventory.Position firstRecord,ArchivedUndoInventory.Position lastRecord,boolean complete,long segmentDba,byte[] identityCheckpoint) {
        this.undo=undo;this.entries=List.copyOf(entries);
        this.firstRecord=firstRecord;this.lastRecord=lastRecord;
        this.complete=complete;
        this.segmentDba=segmentDba;
        this.identityCheckpoint=identityCheckpoint.clone();
    }

    /** Includes every 5.6/5.11 in the target's verified record window, including unrelated objects. */
    public static ArchivedCompensationInventory read(List<Path> archives,long uid,long container,HeapRowPatch.Xid xid) throws IOException {
        return read(RedoBatch.archives(archives),uid,container,xid);
    }
    static ArchivedCompensationInventory read(RedoBatch archives,long uid,long container,HeapRowPatch.Xid xid) throws IOException {
        return read(archives,uid,container,xid,false);
    }
    static ArchivedCompensationInventory read(RedoBatch archives,long uid,long container,HeapRowPatch.Xid xid,boolean allowPending) throws IOException {
        return read(archives,uid,container,xid,allowPending,null);
    }
    ArchivedCompensationInventory extend(RedoBatch suffix,boolean allowPending)throws IOException {
        if(complete)throw new RedoException("COMPENSATION_WINDOW_ALREADY_COMPLETE");
        return read(suffix,undo.containerUid(),undo.containerId(),undo.entries().get(0).identity().xid(),allowPending,this);
    }
    public byte[] durableCheckpoint()throws IOException {return CompensationWindowState.encode(this,identityCheckpoint);}
    public static ArchivedCompensationInventory restoreDurable(byte[] bytes,String trustedSha256)throws IOException {return CompensationWindowState.restore(bytes,trustedSha256);}
    private static ArchivedCompensationInventory read(RedoBatch archives,long uid,long container,HeapRowPatch.Xid xid,boolean allowPending,ArchivedCompensationInventory prefix)throws IOException {
        var undo=prefix==null?ArchivedUndoInventory.read(archives,uid,container,xid):ArchivedUndoInventory.extend(archives,prefix.undo());
        var state=prefix==null?new TransactionLifecycle():TransactionLifecycle.restore(prefix.identityCheckpoint,DurableUndoState.sha(prefix.identityCheckpoint));
        var entries=new ArrayList<Entry>();if(prefix!=null)entries.addAll(prefix.entries());
        int[] next={0},begins={0},ends={0};boolean[] active={false};
        long[] segment={0};
        ArchivedUndoInventory.Position[] window=new ArchivedUndoInventory.Position[2];
        int prefixCount=prefix==null?0:prefix.undo().sources().size();
        if(prefix!=null){next[0]=prefix.undo().entries().size();begins[0]=1;active[0]=true;segment[0]=prefix.segmentDba();window[0]=prefix.firstRecord();}
        for (int source=0;source<archives.size();source++) {
            var path=archives.get(source);var header=path.header();
            if (!header.equals(undo.sources().get(prefixCount+source))) throw new RedoException("COMPENSATION_INVENTORY_INPUT_CHANGED");
            state.archive(header);
            path.visit(header,record->{
                if (record.containerUid()!=uid) return;
                var originals=new ArrayList<RedoRecords.Vector>();
                for (int i=0;i<record.vectors().size();i++) {
                    var v=record.vectors().get(i);
                    if (v.containerId()!=container || v.opcodeMajor()!=5 || v.opcodeMinor()!=1) continue;
                    if (v.fields().isEmpty() || v.fields().get(0).length!=20)
                        throw new RedoException("UNREADABLE_UNDO_INVENTORY_IDENTITY");
                    byte[] identity=v.fields().get(0);
                    if (!new HeapRowPatch.Xid(u16(identity,8),u16(identity,10),u32(identity,12)).equals(xid)) continue;
                    var h=UndoHeader.decode(v);
                    var position=new ArchivedUndoInventory.Position(header.sequence(),record.block(),record.offset(),i+1);
                    if (next[0]>=undo.entries().size() || !undo.entries().get(next[0]).position().equals(position))
                        throw new RedoException("COMPENSATION_INVENTORY_UNDO_COVERAGE_CHANGED");
                    next[0]++;originals.add(v);
                    if (!h.beginsTransaction()) continue;
                    int matches=0;
                    for (var control:record.vectors()) {
                        if (control.containerId()!=container || control.opcodeMajor()!=5 || control.opcodeMinor()!=2
                                || control.fields().isEmpty() || control.fields().get(0).length<15) continue;
                        byte[] b=control.fields().get(0);
                        if (u32(b,8)!=h.uba().dba() || u16(b,12)!=h.uba().sequence() || (b[14]&255)!=h.uba().record()) continue;
                        state.begin(record,control);segment[0]=control.dba();matches++;
                    }
                    if (matches!=1) throw new RedoException("COMPENSATION_INVENTORY_BEGIN_CONTROL_COUNT");
                    begins[0]++;active[0]=true;
                    window[0]=new ArchivedUndoInventory.Position(header.sequence(),record.block(),record.offset(),0);
                }
                for (var original:originals) state.undo(record,original);
                if (active[0]) for (var control:record.vectors()) {
                    if (control.containerId()==container && control.opcodeMajor()==5 && control.opcodeMinor()==2)
                        state.observeSlotControl(record,control);
                }
                // Record phases deliberately ignore vector ordering, as begin/end may precede undo vectors.
                if (active[0]) for (int i=0;i<record.vectors().size();i++) {
                    var v=record.vectors().get(i);
                    if (v.containerId()!=container || v.opcodeMajor()!=5 || (v.opcodeMinor()!=6 && v.opcodeMinor()!=11)) continue;
                    entries.add(new Entry(new ArchivedUndoInventory.Position(header.sequence(),record.block(),record.offset(),i+1),
                            UndoCompensation.decode(v)));
                }
                for (var v:record.vectors()) {
                    if (v.containerId()!=container || v.opcodeMajor()!=5 || v.opcodeMinor()!=4 || !state.knowsEnd(record,v)) continue;
                    state.end(record,v);ends[0]++;active[0]=false;
                    window[1]=new ArchivedUndoInventory.Position(header.sequence(),record.block(),record.offset(),0);
                }
            });
            if (!header.equals(path.header())) throw new RedoException("COMPENSATION_INVENTORY_INPUT_CHANGED");
        }
        boolean pending=allowPending && active[0] && ends[0]==0;
        if(pending) {
            state.checkpoint();var last=undo.sources().get(undo.sources().size()-1);
            window[1]=new ArchivedUndoInventory.Position(last.sequence(),last.blocks()-1,Integer.MAX_VALUE,0);
        } else state.finish();
        if (begins[0]!=1 || (!pending && ends[0]!=1) || next[0]!=undo.entries().size())
            throw new RedoException("COMPENSATION_INVENTORY_TRANSACTION_WINDOW_INCOMPLETE");
        return new ArchivedCompensationInventory(undo,entries,window[0],window[1],!pending,segment[0],state.checkpoint());
    }

    public ArchivedUndoInventory undo() { return undo; }
    public List<Entry> entries() { return entries; }
    public boolean complete() {return complete;}
    public long segmentDba() {return segmentDba;}
    /** Inclusive verified record window; vector zero denotes the record, not a change vector. */
    public ArchivedUndoInventory.Position firstRecord() { return firstRecord; }
    public ArchivedUndoInventory.Position lastRecord() { return lastRecord; }
    public void requireCompleteSelection(List<ArchivedUndoInventory.Position> selected) throws RedoException {
        if (!entries.stream().map(Entry::position).toList().equals(selected))
            throw new RedoException("COMPENSATION_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED");
    }
}
