package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete selected 5.1 inventory from frozen archives. Not a transaction graph or event authorization. */
public final class ArchivedUndoInventory {
    public record Position(long archiveSequence,long block,int offset,int vector) {}
    public record Entry(Position position,UndoHeader identity,int opcodeMajor,int opcodeMinor,long previousDba,int rci) {}
    private final List<ArchivedRedo.Header> sources;
    private final List<Entry> entries;
    private final long containerUid,containerId;
    private final UndoControlRoot controlRoot;

    ArchivedUndoInventory(List<ArchivedRedo.Header> sources,List<Entry> entries,long containerUid,long containerId,UndoControlRoot controlRoot) {
        this.sources=List.copyOf(sources);this.entries=List.copyOf(entries);
        this.containerUid=containerUid;this.containerId=containerId;
        this.controlRoot=controlRoot;
    }

    /** Owns the complete record traversal; callers cannot certify a supplied vector subset. */
    public static ArchivedUndoInventory read(List<Path> archives,long containerUid,long containerId,HeapRowPatch.Xid xid) throws IOException {
        return read(RedoBatch.archives(archives),containerUid,containerId,xid);
    }
    static ArchivedUndoInventory read(RedoBatch archives,long containerUid,long containerId,HeapRowPatch.Xid xid) throws IOException {
        return read(archives,containerUid,containerId,xid,null);
    }
    static ArchivedUndoInventory extend(RedoBatch suffix,ArchivedUndoInventory prefix)throws IOException {
        return read(suffix,prefix.containerUid(),prefix.containerId(),prefix.entries().get(0).identity().xid(),prefix);
    }
    private static ArchivedUndoInventory read(RedoBatch archives,long containerUid,long containerId,HeapRowPatch.Xid xid,ArchivedUndoInventory prefix)throws IOException {
        if (archives.isEmpty()) throw new RedoException("NO_ARCHIVE_INPUT");
        var continuity=new TransactionLifecycle();
        var sources=new ArrayList<ArchivedRedo.Header>();var entries=new ArrayList<Entry>();
        TransactionStart[] controlStart={null};UndoControlRoot[] root={null};
        var authorizationRoots=new java.util.HashSet<HeapRowPatch.Uba>();boolean[] verifiedAuthorizationRetry={false};
        if(prefix!=null) {
            for(var source:prefix.sources()){continuity.archive(source);sources.add(source);}
            entries.addAll(prefix.entries());root[0]=prefix.controlRoot();if(root[0]!=null)controlStart[0]=root[0].start();
        }
        for (var path:archives) {
            var header=path.header();continuity.archive(header);
            path.visit(header,record->{
                if (record.containerUid()!=containerUid) return;
                for (int index=0;index<record.vectors().size();index++) {
                    var vector=record.vectors().get(index);
                    if (vector.containerId()!=containerId || vector.opcodeMajor()!=5 || vector.opcodeMinor()!=1) continue;
                    // An unreadable identity in the requested container cannot safely be excluded.
                    if (vector.fields().isEmpty() || vector.fields().get(0).length!=20)
                        throw new RedoException("UNREADABLE_UNDO_INVENTORY_IDENTITY");
                    byte[] b=vector.fields().get(0);
                    var identity=new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));
                    if (!identity.equals(xid)) continue;
                    if (record.scn()<header.firstScn() || record.scn()>=header.nextScn())
                        throw new RedoException("UNDO_INVENTORY_RECORD_OUTSIDE_ARCHIVE_SCN");
                    var undo=UndoHeader.decode(vector);byte[] metadata=vector.fields().get(1);
                    if (undo.uba().record()==0 || (metadata[18]&255)!=xid.slot())
                        throw new RedoException("UNDO_INVENTORY_ADDRESS_OR_SLOT_MISMATCH");
                    long previous=u32(metadata,12);int rci=metadata[19]&255;
                    boolean nonRowBegin=undo.beginsTransaction() && metadata[16]==5 && metadata[17]==7;
                    boolean firstRoot=controlStart[0]!=null && entries.size()==1;
                    if(nonRowBegin) {
                        if(!entries.isEmpty()) throw new RedoException("NON_ROW_BEGIN_NOT_FIRST_IN_INVENTORY");
                        controlStart[0]=UndoControlRoot.begin(record,vector);
                    } else {
                        if(undo.beginsTransaction() && !entries.isEmpty())throw new RedoException("UNDO_INVENTORY_REPEATED_BEGIN");
                        if(firstRoot)root[0]=UndoControlRoot.bind(controlStart[0],entries.get(0).identity(),record,vector);
                    }
                    boolean authorizationRoot=false;
                    boolean arrayRoot=!undo.beginsTransaction() && !entries.isEmpty() && entries.get(0).identity().beginsTransaction()
                            && previous==0 && rci==0 && metadata[16]==11 && metadata[17]==1 && arrayRootAssigned(record,vector,undo);
                    if(!undo.beginsTransaction() && !firstRoot && previous==0 && rci==0 && undo.objectId()==61 && undo.dataObjectId()==61 && undo.tablespace()==0) {
                        if(!verifiedAuthorizationRetry[0]) {authorizationRoots.addAll(DictionaryAuthorizationRetry.roots(archives,containerUid,containerId,xid));verifiedAuthorizationRetry[0]=true;}
                        authorizationRoot=authorizationRoots.contains(undo.uba());
                    }
                    if (undo.beginsTransaction() ? previous!=0 || rci!=0 || u16(metadata,22)!=1
                            : u16(metadata,22)!=(xid.sequence()&0xffff) || (!firstRoot && !authorizationRoot && !arrayRoot && (previous==0 ? rci==0 : rci!=0)))
                        throw new RedoException("UNDO_INVENTORY_METADATA_LINK_MISMATCH");
                    entries.add(new Entry(new Position(header.sequence(),record.block(),record.offset(),index+1),undo,
                            metadata[16]&255,metadata[17]&255,previous,rci));
                }
            });
            if (!header.equals(path.header())) throw new RedoException("UNDO_INVENTORY_INPUT_CHANGED");
            sources.add(header);
        }
        if (entries.isEmpty()) throw new RedoException("TARGET_UNDO_NOT_FOUND");
        if(controlStart[0]!=null && root[0]==null) throw new RedoException("NON_ROW_BEGIN_WITHOUT_FIRST_ROOT");
        return new ArchivedUndoInventory(sources,entries,containerUid,containerId,root[0]);
    }

    public List<ArchivedRedo.Header> sources() { return sources; }
    /** Inventory recognition only; the physical graph must prove the previous head was completely cancelled. */
    static boolean arrayRootAssigned(RedoRecords.Record record,RedoRecords.Vector undo,UndoHeader owner)throws RedoException {
        if(undo.fields().size()!=4 || undo.fields().get(3).length<22 || undo.fields().get(3)[10]!=12)return false;
        int heads=0,rows=0;
        for(var v:record.vectors())if(v.containerId()==undo.containerId()) {
            if(v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.fields().size()==1 && v.fields().get(0).length==32) {
                byte[] b=v.fields().get(0);if(u16(b,0)!=owner.xid().slot())continue;
                var head=UndoHeadUpdate.decode(v);
                if(!head.uba().equals(owner.uba()) || u16(b,18)!=u16(undo.fields().get(0),0))throw new RedoException("ARRAY_ROOT_HEAD_ASSIGNMENT_MISMATCH");heads++;
            }
            if(v.opcodeMajor()==11 && v.opcodeMinor()==11 && HeapUndoPair.referencesOriginal(v,owner.uba()))rows++;
        }
        return heads==1 && rows==1;
    }
    public List<Entry> entries() { return entries; }
    public long containerUid() { return containerUid; }
    public long containerId() { return containerId; }
    public UndoControlRoot controlRoot() { return controlRoot; }

    /** Exact position/order coverage only; a pass does not prove links, commit, or surviving effects. */
    public void requireCompleteSelection(List<Position> selected) throws RedoException {
        if (!entries.stream().map(Entry::position).toList().equals(selected))
            throw new RedoException("UNDO_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED");
    }
}
