package com.streamhouse.oracle;

import java.io.*;
import java.util.*;
import static com.streamhouse.oracle.DurableUndoState.*;

/** Committed full images and event history; pending physical rows remain in the physical state. */
final class HistoricalProducerState {
    record Restored(HistoricalRowState rows,List<ArchivedHeapRows.Change> changes) {}
    static byte[] encode(HistoricalRowState rows,List<ArchivedHeapRows.Change> changes)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_HISTORICAL_PRODUCER_V1");
        byte[] state=rows.checkpoint();out.writeInt(state.length);out.write(state);out.writeInt(changes.size());
        for(var c:changes) {
            var p=c.position();out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());
            out.writeInt(c.xid().usn());out.writeInt(c.xid().slot());out.writeLong(c.xid().sequence());
            out.writeLong(c.key().dataObjectId());out.writeLong(c.key().dba());out.writeInt(c.key().slot());out.writeUTF(c.kind().name());
            cells(out,c.before());cells(out,c.after());
        }
        out.flush();return bytes.toByteArray();
    }
    static Restored restore(byte[] bytes,String trustedSha256,FrozenHeapBaseline.Snapshot baseline,long leaf,
                            ArchivedPhysicalTransactions.Result physical)throws IOException {
        byte[] input=bytes.clone();if(!sha(input).equals(trustedSha256))throw new RedoException("PRODUCER_STATE_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));
            if(!in.readUTF().equals("ORK_HISTORICAL_PRODUCER_V1"))throw new RedoException("PRODUCER_STATE_FORMAT");
            byte[] state=in.readNBytes(count(in));var rows=HistoricalRowState.restore(state,sha(state),baseline);
            rows.requirePrefix(baseline,leaf,physical);
            if(!rows.dictionary().sources().equals(physical.sources()))throw new RedoException("PRODUCER_STATE_SOURCE_MISMATCH");
            var owners=new HashMap<ArchivedUndoInventory.Position,ArchivedPhysicalTransactions.Transaction>();
            for(var t:physical.transactions())for(var entry:t.graph().live())
                if(owners.putIfAbsent(entry.position(),t)!=null)throw new RedoException("PRODUCER_STATE_OWNER_COLLISION");
            var changes=new ArrayList<ArchivedHeapRows.Change>();var positions=new HashSet<ArchivedUndoInventory.Position>();
            ArchivedPhysicalTransactions.Transaction previous=null;
            var rowPositions=new HashMap<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position>();
            for(int i=0,n=count(in);i<n;i++) {
                var at=new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());
                var xid=new HeapRowPatch.Xid(in.readInt(),in.readInt(),in.readLong());
                var key=new ArchivedHeapRows.RowKey(in.readLong(),in.readLong(),in.readInt());
                var kind=HeapRowPatch.Kind.valueOf(in.readUTF());var before=cells(in);var after=cells(in);var owner=owners.get(at);
                if(owner==null || owner.identity().rolledBack() || owner.identity().endRecordScn()<=baseline.scn()
                        || !owner.identity().xid().equals(xid) || !positions.add(at) || key.dataObjectId()!=baseline.heap().dataObject()
                        || key.dba()<=0 || key.dba()>0xffffffffL || key.slot()<0 || key.slot()>65535
                        || owner.graph().live().stream().noneMatch(e->e.position().equals(at) && e.identity().dataObjectId()==key.dataObjectId()))
                    throw new RedoException("PRODUCER_STATE_CHANGE_OWNER_OR_KEY");
                if(previous!=null && (owner.identity().endRecordScn()<previous.identity().endRecordScn()
                        || owner.identity().endRecordScn()==previous.identity().endRecordScn() && compare(owner.commitPosition(),previous.commitPosition())<0))
                    throw new RedoException("PRODUCER_STATE_COMMIT_ORDER");
                previous=owner;var prior=rowPositions.put(key,at);
                if(prior!=null && compare(at,prior)<=0)throw new RedoException("PRODUCER_STATE_ROW_ORDER");
                var columns=rows.dictionary().schemaAt(owner.identity().endRecordScn(),owner.commitPosition());
                if(kind!=HeapRowPatch.Kind.INSERT && kind!=HeapRowPatch.Kind.UPDATE && kind!=HeapRowPatch.Kind.DELETE
                        || (kind==HeapRowPatch.Kind.INSERT)!=before.isEmpty() || (kind==HeapRowPatch.Kind.DELETE)!=after.isEmpty())
                    throw new RedoException("PRODUCER_STATE_OPERATION_IMAGES");
                if(!before.isEmpty())ArchivedHeapRows.requireCompleteValues(before,columns);
                if(!after.isEmpty())ArchivedHeapRows.requireCompleteValues(after,columns);
                changes.add(new ArchivedHeapRows.Change(at,xid,key,kind,before,after));
            }
            for(var e:rowPositions.entrySet()) {
                var last=rows.lastPositions().get(e.getKey());
                if(last==null || compare(last,e.getValue())<0)throw new RedoException("PRODUCER_STATE_ROW_FENCE");
            }
            if(in.available()!=0 || !Arrays.equals(input,encode(rows,changes)))throw new RedoException("PRODUCER_STATE_NONCANONICAL");
            return new Restored(rows,List.copyOf(changes));
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException|NoSuchElementException|EOFException|UTFDataFormatException e){throw new RedoException("PRODUCER_STATE_MALFORMED");}
    }
    private static void cells(DataOutputStream out,List<ArchivedHeapRows.Cell> cells)throws IOException {
        out.writeInt(cells.size());for(var c:cells){out.writeUTF(c.state().name());out.writeUTF(c.hex());}
    }
    private static List<ArchivedHeapRows.Cell> cells(DataInputStream in)throws IOException {
        var result=new ArrayList<ArchivedHeapRows.Cell>();for(int i=0,n=count(in);i<n;i++)result.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.valueOf(in.readUTF()),in.readUTF()));return List.copyOf(result);
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());
        if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0?Integer.compare(a.vector(),b.vector()):c;
    }
}
