package com.streamhouse.oracle;

/** Owned leaf position replay. Cancellation and current undo head require a separate graph proof. */
public final class IndexLeafPositions {
    private final ArchivedIndexLeafInventory inventory;
    private final boolean fullReplay;
    private ArchivedUndoInventory.Position lastMatched;
    private boolean failed;

    public IndexLeafPositions(ArchivedIndexLeafInventory inventory) { this(inventory,false); }
    private IndexLeafPositions(ArchivedIndexLeafInventory inventory,boolean fullReplay) { this.inventory=inventory;this.fullReplay=fullReplay; }

    /** Owns both physical leaf coverage and full-XID original-header association traversal. */
    public static IndexLeafPositions read(java.util.List<java.nio.file.Path> paths,long uid,long container,HeapRowPatch.Xid xid,
                                          long indexObject,long leafDba) throws java.io.IOException {
        return read(paths,uid,container,xid,indexObject,indexObject,leafDba);
    }
    public static IndexLeafPositions read(java.util.List<java.nio.file.Path> paths,long uid,long container,HeapRowPatch.Xid xid,
                                          long indexObject,long indexDataObject,long leafDba) throws java.io.IOException {
        return read(RedoBatch.archives(paths),uid,container,xid,indexObject,indexDataObject,leafDba);
    }
    static IndexLeafPositions read(RedoBatch paths,long uid,long container,HeapRowPatch.Xid xid,
                                          long indexObject,long indexDataObject,long leafDba) throws java.io.IOException {
        return read(paths,uid,container,xid,indexObject,indexDataObject,leafDba,false);
    }
    static IndexLeafPositions read(RedoBatch paths,long uid,long container,HeapRowPatch.Xid xid,
                                          long indexObject,long indexDataObject,long leafDba,boolean allowPending) throws java.io.IOException {
        var leaf=ArchivedIndexLeafInventory.read(paths,uid,container,xid,indexObject,indexDataObject,leafDba,allowPending);
        var associations=ArchivedCompensationAssociations.read(paths,uid,container,xid,allowPending);
        if (!leaf.window().undo().sources().equals(associations.window().undo().sources())
                || !leaf.window().entries().equals(associations.window().entries()))
            throw new RedoException("INDEX_POSITION_ASSOCIATION_SOURCE_CHANGED");
        var entries=leaf.entries();var cancelled=new java.util.HashSet<Integer>();
        for (int i=0;i<entries.size();i++) {
            var e=entries.get(i);if (e.marker()==null) continue;
            ArchivedCompensationAssociations.Entry association=null;
            for (int j=0;j<associations.entries().size();j++) {
                var a=associations.entries().get(j);var m=associations.window().entries().get(j);
                if (!sameRecord(e.position(),a.position()) || !m.marker().equals(e.marker())) continue;
                if (association!=null) throw new RedoException("AMBIGUOUS_INDEX_POSITION_ASSOCIATION");
                association=a;
            }
            if (association==null) throw new RedoException("MISSING_INDEX_POSITION_ASSOCIATION");
            int original=-1;
            for (int j=0;j<i;j++) {
                var o=entries.get(j);
                if (!association.original().equals(o.original()) || !sameRecord(o.position(),association.originalPosition())) continue;
                if (original!=-1) throw new RedoException("AMBIGUOUS_INDEX_POSITION_ROLLBACK_ORIGINAL");
                original=j;
            }
            if (original<0) throw new RedoException("INDEX_POSITION_ROLLBACK_ORIGINAL_OUTSIDE_LEAF_WINDOW");
            var o=entries.get(original);
            if ((e.kind()==IndexUndoPair.Kind.PURGE ? IndexUndoPair.Kind.INSERT : IndexUndoPair.Kind.DELETE)!=o.kind()
                    || e.itl()!=o.itl() || e.rowSize()!=o.rowSize() || replaySlot(entries,original,i)!=e.slot())
                throw new RedoException("INDEX_POSITION_ROLLBACK_PHYSICAL_MISMATCH");
            if (!cancelled.add(original)) throw new RedoException("INDEX_POSITION_ORIGINAL_ALREADY_CANCELLED");
        }
        return new IndexLeafPositions(leaf,true);
    }
    private static boolean sameRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        return a.archiveSequence()==b.archiveSequence() && a.block()==b.block() && a.offset()==b.offset();
    }
    static int replaySlot(java.util.List<ArchivedIndexLeafInventory.Entry> entries,int original,int target) throws RedoException {
        int slot=entries.get(original).slot();
        for (int i=original+1;i<target;i++) {
            var e=entries.get(i);
            if (e.kind()==IndexUndoPair.Kind.INSERT && e.slot()<=slot) {
                if (slot==65535) throw new RedoException("INDEX_POSITION_SLOT_OVERFLOW");
                slot++;
            } else if (e.kind()==IndexUndoPair.Kind.PURGE) {
                if (e.slot()==slot) throw new RedoException("INDEX_POSITION_TRACKED_ROW_PURGED");
                if (e.slot()<slot) slot--;
            }
        }
        return slot;
    }

    int slotBefore(IndexUndoPair pair,RedoRecords.Record record,RedoRecords.Vector marker,RedoRecords.Vector row,
                   ArchivedUndoInventory.Position at,TransactionLifecycle state) throws RedoException {
        if (failed) throw new RedoException("INDEX_LEAF_POSITIONS_ALREADY_REFUSED");
        try {
            if (lastMatched!=null && compare(at,lastMatched)<=0) throw new RedoException("INDEX_POSITION_QUERY_REPEATED_OR_REORDERED");
            var sources=inventory.window().undo().sources();ArchivedRedo.Header source=null;
            for (var h:sources) if (h.sequence()==at.archiveSequence()) source=h;
            if (source==null) throw new RedoException("INDEX_POSITION_SOURCE_NOT_OWNED");
            state.requireArchiveSource(source);
            if (record.containerUid()!=inventory.window().undo().containerUid() || row.containerId()!=inventory.window().undo().containerId()
                    || !record.vectors().contains(row) || !record.vectors().contains(marker)
                    || record.block()!=at.block() || record.offset()!=at.offset() || record.vectors().indexOf(row)+1!=at.vector())
                throw new RedoException("INDEX_POSITION_QUERY_RECORD_OR_VECTOR_MISMATCH");
            if (!pair.undo().identity().xid().equals(inventory.window().undo().entries().get(0).identity().xid()))
                throw new RedoException("INDEX_POSITION_ORIGINAL_OUTSIDE_TARGET_TRANSACTION");
            int original=-1,target=-1;
            var entries=inventory.entries();String signature=ArchivedIndexLeafInventory.pairSignature(pair);
            for (int i=0;i<entries.size();i++) {
                var e=entries.get(i);
                if (signature.equals(e.pairSignature())) {
                    if (original!=-1) throw new RedoException("AMBIGUOUS_INDEX_POSITION_ORIGINAL");
                    original=i;
                }
                if (e.position().equals(at)) target=i;
            }
            if (original<0 || target<=original) throw new RedoException("INDEX_POSITION_ORIGINAL_OR_QUERY_NOT_OWNED");
            var query=entries.get(target);
            if (query.marker()==null || query.recordScn()!=record.scn()
                    || !query.rowSignature().equals(ArchivedIndexLeafInventory.vectorSignature(row))
                    || !query.markerSignature().equals(ArchivedIndexLeafInventory.vectorSignature(marker)))
                throw new RedoException("INDEX_POSITION_QUERY_CHANGED_FROM_OWNED_SOURCE");
            int slot=entries.get(original).slot();
            if (fullReplay) return replaySlot(entries,original,target);
            for (int i=original+1;i<target;i++) {
                var e=entries.get(i);
                if (e.kind()!=IndexUndoPair.Kind.INSERT)
                    throw new RedoException("UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:"+e.kind());
                if (e.slot()<=slot) {
                    if (slot==65535) throw new RedoException("INDEX_POSITION_SLOT_OVERFLOW");
                    slot++;
                }
            }
            return slot;
        } catch (RedoException e) { failed=true;throw e; }
    }

    void matched(ArchivedUndoInventory.Position at) { lastMatched=at; }
    void refused() { failed=true; }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());
        if (c==0) c=Long.compare(a.block(),b.block());
        if (c==0) c=Integer.compare(a.offset(),b.offset());
        return c==0 ? Integer.compare(a.vector(),b.vector()) : c;
    }
}
