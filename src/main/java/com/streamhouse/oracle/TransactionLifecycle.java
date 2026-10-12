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

    void requireRecoveryArchive(ArchivedRedo.Header expected)throws RedoException {
        if(!expected.equals(archive))throw new RedoException("PHYSICAL_STATE_IDENTITY_SOURCE");
    }
    void requireRecoveryClosed(HeapRowPatch.Xid xid,long segment,long uid,long container)throws RedoException {
        if(!closed.contains(new Key(uid,container,segment,xid.slot(),xid.sequence())))throw new RedoException("COMPENSATION_WINDOW_CLOSED_IDENTITY");
    }
    void requireRecoveryPending(HeapRowPatch.Xid xid,long segment,long uid,long container)throws RedoException {
        requireRecoveryPending(xid,segment,uid,container,-1);
    }
    void requireRecoveryPending(HeapRowPatch.Xid xid,long segment,long uid,long container,long beginScn)throws RedoException {
        var begin=pending.get(new Key(uid,container,segment,xid.slot(),xid.sequence()));
        if(begin==null || !begin.identity().xid().equals(xid) || beginScn>=0 && begin.scn()!=beginScn)throw new RedoException("PHYSICAL_STATE_PENDING_IDENTITY");
    }

    /** Identity state only. The caller must durably retain its digest and the bound raw input. */
    public byte[] checkpoint() throws RedoException {
        checkHealthy();if(archive==null)throw new RedoException("NO_ARCHIVE_INPUT");
        var out=new StringBuilder("ORK_TRANSACTION_IDENTITIES_V1\nA|")
                .append(java.util.Base64.getEncoder().encodeToString(archive.sha256().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(long value:new long[]{archive.bytes(),archive.dbid(),archive.activationId(),archive.compatibility(),archive.thread(),archive.sequence(),
                archive.resetlogsId(),archive.resetlogsScn(),archive.firstScn(),archive.nextScn(),archive.blocks()})out.append('|').append(value);
        out.append('\n');
        var order=java.util.Comparator.comparingLong(Key::uid).thenComparingLong(Key::container).thenComparingLong(Key::segment)
                .thenComparingInt(Key::slot).thenComparingLong(Key::sequence);
        for(var key:pending.keySet().stream().sorted(order).toList()) {
            var begin=pending.get(key);out.append("P");checkpointKey(out,key);
            out.append('|').append(begin.identity().xid().usn()).append('|').append(begin.scn()).append('\n');
        }
        for(var key:closed.stream().sorted(order).toList()){out.append("C");checkpointKey(out,key);out.append('\n');}
        return out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void checkpointKey(StringBuilder out,Key key) {
        out.append('|').append(key.uid()).append('|').append(key.container()).append('|').append(key.segment())
                .append('|').append(key.slot()).append('|').append(key.sequence());
    }
    /** A digest supplied by the same untrusted file does not establish checkpoint authority. */
    public static TransactionLifecycle restore(byte[] bytes,String trustedSha256)throws RedoException {
        try {
            String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!digest.equals(trustedSha256))throw new RedoException("TRANSACTION_CHECKPOINT_DIGEST_MISMATCH");
            String[] lines=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).split("\n");
            if(lines.length<2 || !lines[0].equals("ORK_TRANSACTION_IDENTITIES_V1"))throw new RedoException("TRANSACTION_CHECKPOINT_FORMAT");
            String[] a=lines[1].split("\\|",-1);if(a.length!=13 || !a[0].equals("A"))throw new RedoException("TRANSACTION_CHECKPOINT_ARCHIVE");
            var state=new TransactionLifecycle();
            state.archive=new ArchivedRedo.Header(new String(java.util.Base64.getDecoder().decode(a[1]),java.nio.charset.StandardCharsets.UTF_8),
                    Long.parseLong(a[2]),Long.parseLong(a[3]),Long.parseLong(a[4]),Long.parseLong(a[5]),Integer.parseInt(a[6]),
                    Long.parseLong(a[7]),Long.parseLong(a[8]),Long.parseLong(a[9]),Long.parseLong(a[10]),Long.parseLong(a[11]),Long.parseLong(a[12]));
            if(state.archive.thread()<1 || state.archive.firstScn()>=state.archive.nextScn() || state.archive.blocks()<3)
                throw new RedoException("TRANSACTION_CHECKPOINT_ARCHIVE");
            var slots=new HashSet<String>();
            for(int i=2;i<lines.length;i++) {
                String[] f=lines[i].split("\\|",-1);boolean active=f[0].equals("P");
                if(f.length!=(active?8:6) || (!active && !f[0].equals("C")))throw new RedoException("TRANSACTION_CHECKPOINT_IDENTITY");
                var key=new Key(Long.parseLong(f[1]),Long.parseLong(f[2]),Long.parseLong(f[3]),Integer.parseInt(f[4]),Long.parseLong(f[5]));
                if(key.uid()<1 || key.container()<1 || key.segment()<1 || key.segment()>0xffffffffL || key.slot()<0 || key.slot()>65535
                        || key.sequence()<0 || key.sequence()>0xffffffffL || state.pending.containsKey(key) || state.closed.contains(key))
                    throw new RedoException("TRANSACTION_CHECKPOINT_IDENTITY");
                if(active) {
                    int usn=Integer.parseInt(f[6]);long scn=Long.parseLong(f[7]);
                    if(usn<1 || usn>65535 || scn<state.archive.resetlogsScn() || scn>=state.archive.nextScn()
                            || !slots.add(key.uid()+"|"+key.container()+"|"+key.segment()+"|"+key.slot()))
                        throw new RedoException("TRANSACTION_CHECKPOINT_ACTIVE_IDENTITY");
                    var identity=new Identity(key.uid(),key.container(),new HeapRowPatch.Xid(usn,key.slot(),key.sequence()));
                    if(state.identities.putIfAbsent(identity,key)!=null)throw new RedoException("TRANSACTION_CHECKPOINT_ACTIVE_IDENTITY");
                    state.pending.put(key,new Begin(identity,scn));
                } else state.closed.add(key);
            }
            if(!java.util.Arrays.equals(bytes,state.checkpoint()))throw new RedoException("TRANSACTION_CHECKPOINT_NONCANONICAL");
            return state;
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException | java.security.NoSuchAlgorithmException e){throw new RedoException("TRANSACTION_CHECKPOINT_FORMAT");}
    }

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

    /** Identity exclusion guard only; zero sequence controls still need advance/head-update decoding. */
    public void observeSlotControl(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        if (vector.opcodeMajor()!=5 || vector.opcodeMinor()!=2 || !record.vectors().contains(vector))
            refuse("NOT_SLOT_CONTROL_IN_RECORD");
        for (var key:pending.keySet()) {
            if (key.uid()!=record.containerUid() || key.container()!=vector.containerId() || key.segment()!=vector.dba()) continue;
            if (vector.fields().isEmpty() || vector.fields().get(0).length<8)
                refuse("UNREADABLE_ACTIVE_SEGMENT_SLOT_CONTROL");
            byte[] b=vector.fields().get(0);
            if (u16(b,0)!=key.slot()) continue;
            long sequence=u32(b,4);
            if (sequence!=0 && sequence!=key.sequence()) refuse("TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE");
        }
    }

    public void requireActive(RedoRecords.Record record,HeapUndoPair pair) throws RedoException {
        checkRecord(record);
        if (record.containerUid()!=pair.containerUid()
                || !identities.containsKey(new Identity(pair.containerUid(),pair.containerId(),pair.undo().identity().xid())))
            refuse("PAIR_WITHOUT_ACTIVE_TRANSACTION_IDENTITY");
    }

    public void requireActive(RedoRecords.Record record,IndexUndoPair pair) throws RedoException {
        checkRecord(record);
        if (record.containerUid()!=pair.containerUid()
                || !identities.containsKey(new Identity(pair.containerUid(),pair.containerId(),pair.undo().identity().xid())))
            refuse("INDEX_PAIR_WITHOUT_ACTIVE_TRANSACTION_IDENTITY");
    }

    public UndoHeader advance(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        checkRecord(record);
        try {
            if (!record.vectors().contains(vector)) refuse("UNDO_ADVANCE_NOT_IN_RECORD");
            var advance=UndoBlockAdvance.decode(vector);var h=advance.bindUndo(record);
            int controls=0;
            for (var candidate:record.vectors()) {
                if (candidate.containerId()!=vector.containerId() || candidate.opcodeMajor()!=5 || candidate.opcodeMinor()!=2
                        || candidate.dba()!=vector.dba() || candidate.fields().isEmpty() || candidate.fields().get(0).length!=32
                        || u16(candidate.fields().get(0),16)!=10) continue;
                var other=UndoBlockAdvance.decode(candidate);
                if (other.uba().equals(advance.uba())) controls++;
            }
            if (controls!=1) refuse("AMBIGUOUS_UNDO_ADVANCE_CONTROL_UBA");
            var key=identities.get(new Identity(record.containerUid(),vector.containerId(),h.xid()));
            if (key==null || key.segment()!=advance.segmentDba() || key.slot()!=advance.slot())
                refuse("UNDO_ADVANCE_WITHOUT_ACTIVE_SEGMENT_IDENTITY");
            return h;
        } catch (RedoException e) { failed=true; throw e; }
    }

    public UndoHeader headUpdate(RedoRecords.Record record,RedoRecords.Vector vector) throws RedoException {
        return headUpdate(record,vector,null);
    }
    UndoHeader arrayRootHeadUpdate(RedoRecords.Record record,RedoRecords.Vector vector,UndoHistory graph)throws RedoException {
        return headUpdate(record,vector,graph);
    }
    private UndoHeader headUpdate(RedoRecords.Record record,RedoRecords.Vector vector,UndoHistory graph)throws RedoException {
        checkRecord(record);
        try {
            if (!record.vectors().contains(vector)) refuse("UNDO_HEAD_UPDATE_NOT_IN_RECORD");
            var update=UndoHeadUpdate.decode(vector);var h=graph==null?update.bindUndo(record):update.bindArrayRoot(record);
            if(graph!=null && !graph.isCompensatedRestartRoot(h))refuse("ARRAY_ROOT_PREVIOUS_HEAD_NOT_CANCELLED");
            int controls=0;
            for (var candidate:record.vectors()) {
                if (candidate.containerId()!=vector.containerId() || candidate.opcodeMajor()!=5 || candidate.opcodeMinor()!=2
                        || candidate.dba()!=vector.dba() || candidate.fields().size()!=1 || candidate.fields().get(0).length!=32
                        || u16(candidate.fields().get(0),16)!=2) continue;
                if (UndoHeadUpdate.decode(candidate).uba().equals(update.uba())) controls++;
            }
            if (controls!=1) refuse("AMBIGUOUS_UNDO_HEAD_UPDATE_CONTROL_UBA");
            var key=identities.get(new Identity(record.containerUid(),vector.containerId(),h.xid()));
            if (key==null || key.segment()!=update.segmentDba() || key.slot()!=update.slot())
                refuse("UNDO_HEAD_UPDATE_WITHOUT_ACTIVE_SEGMENT_IDENTITY");
            return h;
        } catch (RedoException e) { failed=true; throw e; }
    }

    public HeapRowPatch.Xid beginCompensationIdentity(RedoRecords.Record record,RedoRecords.Vector marker) throws RedoException {
        return segmentCompensationIdentity(record,marker,true);
    }

    public HeapRowPatch.Xid crossCompensationIdentity(RedoRecords.Record record,RedoRecords.Vector marker) throws RedoException {
        return segmentCompensationIdentity(record,marker,false);
    }

    private HeapRowPatch.Xid segmentCompensationIdentity(RedoRecords.Record record,RedoRecords.Vector marker,boolean begin) throws RedoException {
        checkRecord(record);
        try {
            var c=UndoCompensation.decode(marker);
            if (!(begin ? c.beginsTransaction() : c.crossesUndoBlock()) || !record.vectors().contains(marker))
                refuse(begin ? "NOT_BEGIN_COMPENSATION_IN_RECORD" : "NOT_CROSS_COMPENSATION_IN_RECORD");
            HeapRowPatch.Xid xid=null;
            for (var entry:pending.entrySet()) {
                var key=entry.getKey();
                if (key.uid()!=record.containerUid() || key.container()!=marker.containerId()
                        || key.segment()!=c.dba() || key.slot()!=c.slot()) continue;
                if (xid!=null) refuse(begin ? "AMBIGUOUS_BEGIN_COMPENSATION_IDENTITY" : "AMBIGUOUS_CROSS_COMPENSATION_IDENTITY");
                xid=entry.getValue().identity().xid();
            }
            if (xid==null) refuse(begin ? "BEGIN_COMPENSATION_WITHOUT_ACTIVE_IDENTITY" : "CROSS_COMPENSATION_WITHOUT_ACTIVE_IDENTITY");
            return xid;
        } catch (RedoException e) { failed=true; throw e; }
    }

    void compensationRefused() { failed=true; }
    void requireArchiveSource(ArchivedRedo.Header expected) throws RedoException {
        checkHealthy();
        if (!expected.equals(archive)) refuse("INDEX_POSITION_ARCHIVE_SOURCE_MISMATCH");
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
