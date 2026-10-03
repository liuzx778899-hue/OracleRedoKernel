package com.streamhouse.oracle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static com.streamhouse.oracle.RedoBytes.*;

/** Identity lifecycle only. Closing an identity does not authorize row events or savepoint cancellation. */
public final class TransactionLifecycle {
    private record Key(long uid,long container,long segment,int slot,long sequence) {}
    private record Identity(long uid,long container,HeapRowPatch.Xid xid) {}
    private record Begin(Identity identity,long scn) {}
    public record ClosedIdentity(HeapRowPatch.Xid xid,long containerUid,long containerId,
                                 long beginRecordScn,long endRecordScn,boolean rolledBack) {}
    private final Map<Key,Begin> pending=new HashMap<>();
    private final Map<Identity,Key> identities=new HashMap<>();
    private final Set<Key> closed=new HashSet<>();
    private ArchivedRedo.Header archive;
    private boolean failed;

    public void archive(ArchivedRedo.Header next) throws RedoException {
        checkHealthy();
        if (archive!=null && (next.dbid()!=archive.dbid() || next.activationId()!=archive.activationId()
                || next.resetlogsId()!=archive.resetlogsId() || next.resetlogsScn()!=archive.resetlogsScn()
                || next.thread()!=archive.thread() || next.compatibility()!=archive.compatibility()))
            refuse("ARCHIVE_SOURCE_IDENTITY_CHANGED");
        if (archive!=null && (next.sequence()!=archive.sequence()+1 || next.firstScn()!=archive.nextScn()))
            refuse("ARCHIVE_SEQUENCE_OR_SCN_GAP");
        archive=next;
    }

    public void begin(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        try {
            var start=TransactionStart.decode(vector);
            var xid=start.bindBegin(record);
            var key=new Key(record.containerUid(),vector.containerId(),start.segmentDba(),start.slot(),start.sequence());
            var identity=new Identity(record.containerUid(),vector.containerId(),xid);
            if (pending.containsKey(key) || closed.contains(key) || identities.containsKey(identity))
                refuse("DUPLICATE_TRANSACTION_BEGIN_IDENTITY");
            for (var active:pending.keySet()) {
                if (active.uid()==key.uid() && active.container()==key.container()
                        && active.segment()==key.segment() && active.slot()==key.slot())
                    refuse("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE");
            }
            pending.put(key,new Begin(identity,record.scn())); identities.put(identity,key);
        } catch (RedoException e) { failed=true; throw e; }
    }

    public void undo(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        try {
            var undo=UndoHeader.decode(vector);
            var identity=new Identity(record.containerUid(),vector.containerId(),undo.xid());
            if (!identities.containsKey(identity)) refuse("UNDO_WITHOUT_VERIFIED_TRANSACTION_BEGIN");
        } catch (RedoException e) { failed=true; throw e; }
    }

    public void requireActive(RedoRecords.Record record,HeapUndoPair pair) throws RedoException {
        checkRecord(record);
        if (record.containerUid()!=pair.containerUid()
                || !identities.containsKey(new Identity(pair.containerUid(),pair.containerId(),pair.undo().identity().xid())))
            refuse("PAIR_WITHOUT_ACTIVE_TRANSACTION_IDENTITY");
    }

    public boolean knowsEnd(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        try {
            // Selection predicate only: matching ends must still pass strict decode in end().
            if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=4 || vector.fields().isEmpty()
                    || vector.fields().get(0).length<8) refuse("UNREADABLE_TRANSACTION_END_IDENTITY");
            byte[] b=vector.fields().get(0);
            var key=new Key(record.containerUid(),vector.containerId(),vector.dba(),u16(b,0),u32(b,4));
            return pending.containsKey(key) || closed.contains(key);
        } catch (RedoException e) { failed=true; throw e; }
    }

    public ClosedIdentity end(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        try {
            var end=TransactionEnd.decode(vector);
            var key=new Key(record.containerUid(),vector.containerId(),end.segmentDba(),end.slot(),end.sequence());
            var begin=pending.get(key);
            if (begin==null) refuse("END_WITHOUT_VERIFIED_TRANSACTION_BEGIN");
            if (record.scn()<begin.scn()) refuse("END_SCN_BEFORE_BEGIN");
            pending.remove(key); identities.remove(begin.identity()); closed.add(key);
            return new ClosedIdentity(begin.identity().xid(),key.uid(),key.container(),begin.scn(),
                    record.scn(),end.rolledBack());
        } catch (RedoException e) { failed=true; throw e; }
    }

    public void finish() throws RedoException {
        checkHealthy();
        if (archive==null) refuse("NO_ARCHIVE_INPUT");
        if (!pending.isEmpty()) refuse("INCOMPLETE_TRANSACTION_IDENTITIES:"+pending.size());
    }

    private void checkRecord(RedoRecords.Record record) throws RedoException {
        checkHealthy();
        if (archive==null || record.scn()<archive.firstScn() || record.scn()>=archive.nextScn())
            refuse("RECORD_OUTSIDE_CURRENT_ARCHIVE_SCN");
    }
    private void checkHealthy() throws RedoException {
        if (failed) throw new RedoException("TRANSACTION_LIFECYCLE_ALREADY_REFUSED");
    }
    private void refuse(String reason) throws RedoException { failed=true; throw new RedoException(reason); }
}
