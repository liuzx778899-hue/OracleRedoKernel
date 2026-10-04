package com.streamhouse.oracle;

/** Owned leaf position replay for intervening INSERTs. Other intervening operations remain refused. */
public final class IndexLeafPositions {
    private final ArchivedIndexLeafInventory inventory;
    private ArchivedUndoInventory.Position lastMatched;
    private boolean failed;

    public IndexLeafPositions(ArchivedIndexLeafInventory inventory) { this.inventory=inventory; }

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
