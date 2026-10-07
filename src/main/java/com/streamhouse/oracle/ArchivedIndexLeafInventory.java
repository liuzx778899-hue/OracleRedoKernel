package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Complete selected leaf operations in a verified transaction window, including other transactions. */
public final class ArchivedIndexLeafInventory {
    public record Entry(ArchivedUndoInventory.Position position,long recordScn,IndexUndoPair.Kind kind,
                        long block,int itl,int slot,int rowSize,UndoHeader original,UndoCompensation marker,
                        String key,String keydata,String pairSignature,String rowSignature,String markerSignature) {}
    private final ArchivedCompensationInventory window;
    private final List<Entry> entries;
    private final long indexObject,leafDba;

    private ArchivedIndexLeafInventory(ArchivedCompensationInventory window,List<Entry> entries,long indexObject,long leafDba) {
        this.window=window;this.entries=List.copyOf(entries);this.indexObject=indexObject;this.leafDba=leafDba;
    }

    /** Owns traversal and pair decoding. Block/object mapping still requires a separate dictionary gate. */
    public static ArchivedIndexLeafInventory read(List<Path> archives,long uid,long container,HeapRowPatch.Xid xid,
                                                 long indexObject,long leafDba) throws IOException {
        return read(archives,uid,container,xid,indexObject,indexObject,leafDba);
    }
    public static ArchivedIndexLeafInventory read(List<Path> archives,long uid,long container,HeapRowPatch.Xid xid,
                                                 long indexObject,long indexDataObject,long leafDba) throws IOException {
        return read(RedoBatch.archives(archives),uid,container,xid,indexObject,indexDataObject,leafDba);
    }
    static ArchivedIndexLeafInventory read(RedoBatch archives,long uid,long container,HeapRowPatch.Xid xid,
                                                 long indexObject,long indexDataObject,long leafDba) throws IOException {
        return read(archives,uid,container,xid,indexObject,indexDataObject,leafDba,false);
    }
    static ArchivedIndexLeafInventory read(RedoBatch archives,long uid,long container,HeapRowPatch.Xid xid,
                                                 long indexObject,long indexDataObject,long leafDba,boolean allowPending) throws IOException {
        var window=ArchivedCompensationInventory.read(archives,uid,container,xid,allowPending);
        var entries=new ArrayList<Entry>();
        for (int source=0;source<archives.size();source++) {
            var path=archives.get(source);var header=path.header();
            if (!header.equals(window.undo().sources().get(source))) throw new RedoException("INDEX_LEAF_INVENTORY_INPUT_CHANGED");
            path.visit(header,r -> {
                if (r.containerUid()!=uid) return;
                var recordPosition=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),0);
                if (compare(recordPosition,window.firstRecord())<0 || compare(recordPosition,window.lastRecord())>0) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);
                    if (v.containerId()!=container || v.dba()!=leafDba) continue;
                    if (r.scn()<header.firstScn() || r.scn()>=header.nextScn())
                        throw new RedoException("INDEX_LEAF_INVENTORY_RECORD_OUTSIDE_ARCHIVE_SCN");
                    if (v.opcodeMajor()==4 && v.opcodeMinor()==1) {
                        SingleEntryBlockCleanout.require(r,v);continue;
                    }
                    var position=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                    if (v.opcodeMajor()==10 && (v.opcodeMinor()==2 || v.opcodeMinor()==4 || v.opcodeMinor()==5 && v.fields().size()==3)) {
                        var pair=IndexUndoPair.bind(r,v);var f=pair.forward();var u=pair.undo();
                        if (u.identity().objectId()!=indexObject || u.identity().dataObjectId()!=indexDataObject)
                            throw new RedoException("INDEX_LEAF_INVENTORY_OBJECT_MISMATCH");
                        byte[] data=f.kind()==IndexUndoPair.Kind.INSERT ? f.keydata() : u.keydata();
                        entries.add(new Entry(position,r.scn(),f.kind(),f.block(),f.itl(),f.slot(),f.rowSize(),u.identity(),null,
                                HexFormat.of().formatHex(u.key()),HexFormat.of().formatHex(data),pairSignature(pair),vectorSignature(v),null));
                    } else if (v.opcodeMajor()==10 && (v.opcodeMinor()==3 || v.opcodeMinor()==5)) {
                        var leaf=IndexCompensation.decodeRollback(v);UndoCompensation marker=null;String markerSignature=null;
                        long rows=r.vectors().stream().filter(other -> other.containerId()==container && other.dba()==leafDba
                                && other.opcodeMajor()==10 && (other.opcodeMinor()==3 || other.opcodeMinor()==5)).count();
                        if (rows!=1) throw new RedoException("AMBIGUOUS_INDEX_LEAF_INVENTORY_ROLLBACK");
                        for (var c:r.vectors()) {
                            if (c.containerId()!=container || c.opcodeMajor()!=5 || (c.opcodeMinor()!=6 && c.opcodeMinor()!=11)) continue;
                            var candidate=UndoCompensation.decode(c);
                            if (candidate.opcodeMajor()!=10 || candidate.opcodeMinor()!=22 || candidate.objectId()!=indexObject
                                    || candidate.dataObjectId()!=indexDataObject) continue;
                            if (marker!=null) throw new RedoException("AMBIGUOUS_INDEX_LEAF_INVENTORY_MARKER");
                            marker=candidate;markerSignature=vectorSignature(c);
                        }
                        if (marker==null) throw new RedoException("MISSING_INDEX_LEAF_INVENTORY_MARKER");
                        entries.add(new Entry(position,r.scn(),leaf.kind(),leaf.block(),leaf.itl(),leaf.slot(),leaf.rowSize(),null,marker,null,null,null,vectorSignature(v),markerSignature));
                    } else throw new RedoException("UNSUPPORTED_INDEX_LEAF_WINDOW_OPCODE:"+v.opcodeMajor()+"."+v.opcodeMinor());
                }
            });
            if (!header.equals(path.header())) throw new RedoException("INDEX_LEAF_INVENTORY_INPUT_CHANGED");
        }
        if (entries.isEmpty()) throw new RedoException("TARGET_INDEX_LEAF_NOT_FOUND_IN_WINDOW");
        return new ArchivedIndexLeafInventory(window,entries,indexObject,leafDba);
    }

    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int result=Long.compare(a.archiveSequence(),b.archiveSequence());
        if (result==0) result=Long.compare(a.block(),b.block());
        return result==0 ? Integer.compare(a.offset(),b.offset()) : result;
    }
    public ArchivedCompensationInventory window() { return window; }
    public List<Entry> entries() { return entries; }
    public long indexObject() { return indexObject; }
    public long leafDba() { return leafDba; }
    // Canonical immutable descriptors bind typed pairs and query vectors to the owned source traversal.
    static String pairSignature(IndexUndoPair pair) {
        var f=pair.forward();var u=pair.undo();
        return pair.containerUid()+"|"+pair.containerId()+"|"+f.kind()+"|"+f.block()+"|"+f.itl()+"|"+f.slot()+"|"+f.rowSize()
                +"|"+f.xid()+"|"+f.uba()+"|"+hex(f.key())+"|"+hex(f.keydata())+"|"+u.identity()+"|"+u.kind()+"|"+u.indexId()
                +"|"+u.block()+"|"+u.itl()+"|"+u.flags()+"|"+u.sdc()+"|"+u.previousDba()+"|"+u.predecessorRecord()+"|"+u.wrap()
                +"|"+hex(u.key())+"|"+hex(u.keydata());
    }
    static String vectorSignature(RedoRecords.Vector v) {
        var text=new StringBuilder(v.opcodeMajor()+"|"+v.opcodeMinor()+"|"+v.blockClass()+"|"+v.file()+"|"+v.dba()+"|"+v.scn()+"|"+v.sequence()+"|"+v.containerId());
        for (byte[] b:v.fields()) text.append('|').append(b.length).append(':').append(hex(b));
        return text.toString();
    }
    private static String hex(byte[] bytes) { return bytes==null ? "-" : HexFormat.of().formatHex(bytes); }
    public void requireCompleteSelection(List<ArchivedUndoInventory.Position> selected) throws RedoException {
        if (!entries.stream().map(Entry::position).toList().equals(selected))
            throw new RedoException("INDEX_LEAF_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED");
    }
}
