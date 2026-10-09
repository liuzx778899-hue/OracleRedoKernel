package com.streamhouse.oracle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.DurableUndoState.*;

/** Authenticated physical prefix values. Association discovery still requires the verified raw prefix. */
public final class PhysicalRecoveryState {
    private final byte[] bytes;
    private final FrozenHeapBaseline.Snapshot baseline;
    private final long leaf;
    private PhysicalRecoveryState(byte[] bytes,FrozenHeapBaseline.Snapshot baseline,long leaf) {
        this.bytes=bytes.clone();this.baseline=baseline;this.leaf=leaf;
    }
    static PhysicalRecoveryState from(RedoBatch inputs,FrozenHeapBaseline.Snapshot baseline,long leaf,
                                      ArchivedPhysicalTransactions.Prefix prefix)throws IOException {
        var graphs=new ArrayList<byte[]>();
        for(var p:prefix.state().pending()) {
            var inventory=ArchivedUndoInventory.read(inputs,baseline.source().containerUid(),baseline.source().containerId(),p.xid());
            byte[] checkpoint=p.graphCheckpoint().getBytes(StandardCharsets.UTF_8);
            graphs.add(UndoHistory.restore(inventory,checkpoint,sha(checkpoint)).durableCheckpoint());
        }
        return new PhysicalRecoveryState(encode(baseline,leaf,prefix,graphs),baseline,leaf);
    }
    public byte[] checkpoint(){return bytes.clone();}
    /** Caller must authenticate the state SHA through durable control state. */
    public static PhysicalRecoveryState restore(byte[] bytes,String trustedStateSha256,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        return restore(bytes,trustedStateSha256,FrozenHeapBaseline.read(baseline,trustedBaselineSha256),leaf);
    }
    static PhysicalRecoveryState restore(byte[] bytes,String trustedStateSha256,FrozenHeapBaseline.Snapshot baseline,long leaf)throws IOException {
        byte[] copy=bytes.clone();if(!sha(copy).equals(trustedStateSha256))throw new RedoException("PHYSICAL_STATE_DIGEST_MISMATCH");
        var state=new PhysicalRecoveryState(copy,baseline,leaf);state.prefix();return state;
    }
    // Decode afresh so mutable heap/index byte arrays cannot mutate the authenticated state.
    ArchivedPhysicalTransactions.Prefix prefix()throws IOException {
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));
            if(!in.readUTF().equals("ORK_PHYSICAL_STATE_V1") || !in.readUTF().equals(baseline.sha256()) || in.readLong()!=leaf)
                throw new RedoException("PHYSICAL_STATE_BASELINE_LEAF_OR_FORMAT");
            var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();
            for(int i=0,n=count(in);i<n;i++) {
                var h=new ArchivedRedo.Header(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),
                        in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());
                if(!h.sha256().matches("[0-9a-f]{64}") || h.bytes()<=0 || h.blocks()<3 || h.sequence()<=0)throw new RedoException("PHYSICAL_STATE_SOURCE");
                baseline.requireSource(h);continuity.archive(h);sources.add(h);
            }
            if(sources.isEmpty())throw new RedoException("PHYSICAL_STATE_EMPTY_SOURCE");
            byte[] identityBytes=in.readNBytes(count(in));String identity=new String(identityBytes,StandardCharsets.UTF_8);
            var lifecycle=TransactionLifecycle.restore(identityBytes,sha(identityBytes));
            lifecycle.requireRecoveryArchive(sources.get(sources.size()-1));
            var closed=new ArrayList<ArchivedPhysicalTransactions.Transaction>();var owners=new HashSet<HeapRowPatch.Xid>();
            for(int i=0,n=count(in);i<n;i++) {
                var id=new TransactionLifecycle.ClosedIdentity(xid(in),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readBoolean());
                scope(id.containerUid(),id.containerId());if(!owners.add(id.xid()))throw new RedoException("PHYSICAL_STATE_DUPLICATE_OWNER");
                var operations=operations(in,sources,id.xid());var original=entries(in,sources,id.xid());
                var live=entries(in,sources,id.xid());var compensated=new ArrayList<UndoHeader>();
                for(int j=0,m=count(in);j<m;j++){var h=undo(in);owner(h,id.xid());compensated.add(h);}
                var graph=new UndoHistory.Result(original,live,List.copyOf(compensated),optionalUba(in),optionalUba(in));
                if(!original.containsAll(live) || compensated.stream().anyMatch(h->original.stream().noneMatch(e->e.identity().equals(h))))
                    throw new RedoException("PHYSICAL_STATE_CLOSED_GRAPH");
                closed.add(new ArchivedPhysicalTransactions.Transaction(id,operations,graph,position(in,sources)));
            }
            var pending=new ArrayList<ArchivedPhysicalTransactions.PendingGraph>();var graphs=new ArrayList<byte[]>();
            for(int i=0,n=count(in);i<n;i++) {
                var xid=xid(in);if(!owners.add(xid))throw new RedoException("PHYSICAL_STATE_DUPLICATE_OWNER");
                long segment=in.readLong();int next=in.readInt();var operations=operations(in,sources,xid);
                lifecycle.requireRecoveryPending(xid,segment,baseline.source().containerUid(),baseline.source().containerId());
                var heaps=new ArrayList<HeapUndoPair>();
                for(int j=0,m=count(in);j<m;j++) {
                    long uid=in.readLong(),container=in.readLong();scope(uid,container);var forward=patch(in);var h=undo(in);owner(h,xid);
                    var inverse=patch(in);var u=new UndoRowPatch(h,inverse,in.readLong(),in.readInt(),in.readInt());
                    if(!h.uba().equals(forward.uba()) || forward.xid()!=null && !xid.equals(forward.xid()))throw new RedoException("PHYSICAL_STATE_HEAP_OWNER");
                    heaps.add(new HeapUndoPair(uid,container,forward,u));
                }
                var indexes=new ArrayList<IndexUndoPair>();
                for(int j=0,m=count(in);j<m;j++) {
                    long uid=in.readLong(),container=in.readLong();scope(uid,container);
                    var f=new IndexUndoPair.Forward(IndexUndoPair.Kind.valueOf(in.readUTF()),in.readLong(),in.readInt(),in.readInt(),in.readInt(),optionalXid(in),optionalUba(in),data(in),data(in));
                    var h=undo(in);owner(h,xid);
                    var u=new IndexUndoPair.Inverse(h,IndexUndoPair.Kind.valueOf(in.readUTF()),in.readLong(),in.readLong(),in.readInt(),in.readInt(),in.readInt(),
                            in.readLong(),in.readInt(),in.readInt(),data(in),data(in));
                    if(f.block()!=leaf || u.block()!=leaf || !h.uba().equals(f.uba()) || f.xid()!=null && !xid.equals(f.xid()))throw new RedoException("PHYSICAL_STATE_INDEX_OWNER_OR_LEAF");
                    indexes.add(new IndexUndoPair(uid,container,f,u));
                }
                var markers=new ArrayList<ArchivedUndoInventory.Position>();for(int j=0,m=count(in);j<m;j++)markers.add(position(in,sources));
                byte[] graphBytes=in.readNBytes(count(in));var graph=UndoHistory.restoreDurable(graphBytes,sha(graphBytes));graphs.add(graphBytes);
                var inventory=graph.inventory();scope(inventory.containerUid(),inventory.containerId());
                if(!inventory.sources().equals(sources) || next!=inventory.entries().size() || inventory.entries().stream().anyMatch(e->!e.identity().xid().equals(xid))
                        || operations.stream().anyMatch(o->inventory.entries().stream().noneMatch(e->e.identity().equals(o.original())))
                        || heaps.stream().anyMatch(p->inventory.entries().stream().noneMatch(e->e.identity().equals(p.undo().identity())))
                        || indexes.stream().anyMatch(p->inventory.entries().stream().noneMatch(e->e.identity().equals(p.undo().identity()))))
                    throw new RedoException("PHYSICAL_STATE_PENDING_GRAPH_SCOPE");
                pending.add(new ArchivedPhysicalTransactions.PendingGraph(xid,segment,next,operations,List.copyOf(heaps),List.copyOf(indexes),List.copyOf(markers),
                        new String(graph.checkpoint(),StandardCharsets.UTF_8)));
            }
            var prefix=new ArchivedPhysicalTransactions.Prefix(new ArchivedPhysicalTransactions.Result(List.copyOf(sources),List.copyOf(closed)),
                    new ArchivedPhysicalTransactions.PendingState(List.copyOf(sources),List.copyOf(pending),identity));
            if(in.available()!=0 || !Arrays.equals(bytes,encode(baseline,leaf,prefix,graphs)))throw new RedoException("PHYSICAL_STATE_NONCANONICAL");
            return prefix;
        }catch(RedoException e){throw e;}
        catch(IllegalArgumentException|EOFException|UTFDataFormatException e){throw new RedoException("PHYSICAL_STATE_MALFORMED");}
    }
    private void scope(long uid,long container)throws RedoException {
        if(uid!=baseline.source().containerUid() || container!=baseline.source().containerId())throw new RedoException("PHYSICAL_STATE_CONTAINER");
    }
    private static void owner(UndoHeader h,HeapRowPatch.Xid xid)throws RedoException {if(!h.xid().equals(xid))throw new RedoException("PHYSICAL_STATE_XID");}
    private static byte[] encode(FrozenHeapBaseline.Snapshot baseline,long leaf,ArchivedPhysicalTransactions.Prefix prefix,List<byte[]> graphs)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_PHYSICAL_STATE_V1");out.writeUTF(baseline.sha256());out.writeLong(leaf);
        out.writeInt(prefix.state().sources().size());for(var h:prefix.state().sources()) {
            out.writeUTF(h.sha256());out.writeLong(h.bytes());out.writeLong(h.dbid());out.writeLong(h.activationId());out.writeLong(h.compatibility());out.writeInt(h.thread());
            out.writeLong(h.sequence());out.writeLong(h.resetlogsId());out.writeLong(h.resetlogsScn());out.writeLong(h.firstScn());out.writeLong(h.nextScn());out.writeLong(h.blocks());
        }
        byte[] identity=prefix.state().identityCheckpoint().getBytes(StandardCharsets.UTF_8);out.writeInt(identity.length);out.write(identity);out.writeInt(prefix.completed().transactions().size());
        for(var t:prefix.completed().transactions()) {
            var id=t.identity();xid(out,id.xid());out.writeLong(id.containerUid());out.writeLong(id.containerId());out.writeLong(id.beginRecordScn());out.writeLong(id.endRecordScn());out.writeBoolean(id.rolledBack());
            operations(out,t.operations());entries(out,t.graph().original());entries(out,t.graph().live());out.writeInt(t.graph().compensated().size());
            for(var h:t.graph().compensated())undo(out,h);optionalUba(out,t.graph().allocationTail());optionalUba(out,t.graph().activeHead());position(out,t.commitPosition());
        }
        out.writeInt(prefix.state().pending().size());int g=0;
        for(var p:prefix.state().pending()) {
            xid(out,p.xid());out.writeLong(p.segment());out.writeInt(p.next());operations(out,p.operations());out.writeInt(p.heaps().size());
            for(var pair:p.heaps()) {
                out.writeLong(pair.containerUid());out.writeLong(pair.containerId());patch(out,pair.forward());var u=pair.undo();undo(out,u.identity());patch(out,u.inverse());
                out.writeLong(u.previousDba());out.writeInt(u.predecessorRecord());out.writeInt(u.wrap());
            }
            out.writeInt(p.indexes().size());for(var pair:p.indexes()) {
                out.writeLong(pair.containerUid());out.writeLong(pair.containerId());var f=pair.forward();out.writeUTF(f.kind().name());out.writeLong(f.block());out.writeInt(f.itl());out.writeInt(f.slot());out.writeInt(f.rowSize());
                optionalXid(out,f.xid());optionalUba(out,f.uba());data(out,f.key());data(out,f.keydata());var u=pair.undo();undo(out,u.identity());out.writeUTF(u.kind().name());
                out.writeLong(u.indexId());out.writeLong(u.block());out.writeInt(u.itl());out.writeInt(u.flags());out.writeInt(u.sdc());out.writeLong(u.previousDba());out.writeInt(u.predecessorRecord());out.writeInt(u.wrap());data(out,u.key());data(out,u.keydata());
            }
            out.writeInt(p.markers().size());for(var at:p.markers())position(out,at);byte[] graph=graphs.get(g++);out.writeInt(graph.length);out.write(graph);
        }
        out.flush();return bytes.toByteArray();
    }
    private static void patch(DataOutputStream out,HeapRowPatch.Patch p)throws IOException {
        out.writeUTF(p.kind().name());out.writeLong(p.dba());out.writeInt(p.slot());out.writeInt(p.xtype());optionalXid(out,p.xid());optionalUba(out,p.uba());out.writeInt(p.columns().size());
        for(var c:p.columns()){out.writeUTF(c.state().name());data(out,c.bytes());}out.writeBoolean(p.extent()!=null);
        if(p.extent()!=null){var e=p.extent();out.writeInt(e.storedColumns());out.writeInt(e.changedColumns());out.writeInt(e.removedColumn());integer(out,e.sizeDelta());}integer(out,p.lockTo());
    }
    private static HeapRowPatch.Patch patch(DataInputStream in)throws IOException {
        var kind=HeapRowPatch.Kind.valueOf(in.readUTF());long dba=in.readLong();int slot=in.readInt(),xtype=in.readInt();var xid=optionalXid(in);var uba=optionalUba(in);
        var columns=new ArrayList<HeapRowPatch.Column>();for(int i=0,n=count(in);i<n;i++)columns.add(new HeapRowPatch.Column(HeapRowPatch.State.valueOf(in.readUTF()),data(in)));
        var extent=in.readBoolean()?new HeapRowPatch.RowExtent(in.readInt(),in.readInt(),in.readInt(),integer(in)):null;
        if(dba<=0 || dba>0xffffffffL || slot<0 || slot>65535 || columns.size()>255)throw new RedoException("PHYSICAL_STATE_PATCH");
        return new HeapRowPatch.Patch(kind,dba,slot,xtype,xid,uba,List.copyOf(columns),extent,integer(in));
    }
    private static void operations(DataOutputStream out,List<ArchivedPhysicalTransactions.Operation> values)throws IOException {
        out.writeInt(values.size());for(var o:values){position(out,o.position());undo(out,o.original());out.writeBoolean(o.compensation());}
    }
    private static List<ArchivedPhysicalTransactions.Operation> operations(DataInputStream in,List<ArchivedRedo.Header> sources,HeapRowPatch.Xid xid)throws IOException {
        var list=new ArrayList<ArchivedPhysicalTransactions.Operation>();for(int i=0,n=count(in);i<n;i++){var p=position(in,sources);var h=undo(in);owner(h,xid);list.add(new ArchivedPhysicalTransactions.Operation(p,h,in.readBoolean()));}return List.copyOf(list);
    }
    private static void entries(DataOutputStream out,List<ArchivedUndoInventory.Entry> values)throws IOException {
        out.writeInt(values.size());for(var e:values){position(out,e.position());undo(out,e.identity());out.writeInt(e.opcodeMajor());out.writeInt(e.opcodeMinor());out.writeLong(e.previousDba());out.writeInt(e.rci());}
    }
    private static List<ArchivedUndoInventory.Entry> entries(DataInputStream in,List<ArchivedRedo.Header> sources,HeapRowPatch.Xid xid)throws IOException {
        var list=new ArrayList<ArchivedUndoInventory.Entry>();for(int i=0,n=count(in);i<n;i++){var p=position(in,sources);var h=undo(in);owner(h,xid);list.add(new ArchivedUndoInventory.Entry(p,h,in.readInt(),in.readInt(),in.readLong(),in.readInt()));}return List.copyOf(list);
    }
    private static void position(DataOutputStream out,ArchivedUndoInventory.Position p)throws IOException {out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());}
    private static ArchivedUndoInventory.Position position(DataInputStream in,List<ArchivedRedo.Header> sources)throws IOException {
        var p=new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());
        if(p.block()<1 || p.offset()<0 || p.offset()>=512 || p.vector()<1 || sources.stream().noneMatch(h->h.sequence()==p.archiveSequence() && p.block()<h.blocks()))throw new RedoException("PHYSICAL_STATE_POSITION");return p;
    }
    private static void xid(DataOutputStream out,HeapRowPatch.Xid x)throws IOException {out.writeInt(x.usn());out.writeInt(x.slot());out.writeLong(x.sequence());}
    private static HeapRowPatch.Xid xid(DataInputStream in)throws IOException {
        var x=new HeapRowPatch.Xid(in.readInt(),in.readInt(),in.readLong());if(x.usn()<1 || x.usn()>65535 || x.slot()<0 || x.slot()>65535 || x.sequence()<0 || x.sequence()>0xffffffffL)throw new RedoException("PHYSICAL_STATE_XID");return x;
    }
    private static void optionalXid(DataOutputStream out,HeapRowPatch.Xid x)throws IOException {out.writeBoolean(x!=null);if(x!=null)xid(out,x);}
    private static HeapRowPatch.Xid optionalXid(DataInputStream in)throws IOException {return in.readBoolean()?xid(in):null;}
    private static void optionalUba(DataOutputStream out,HeapRowPatch.Uba u)throws IOException {out.writeBoolean(u!=null);if(u!=null)uba(out,u);}
    private static HeapRowPatch.Uba optionalUba(DataInputStream in)throws IOException {return in.readBoolean()?uba(in):null;}
    private static void integer(DataOutputStream out,Integer v)throws IOException {out.writeBoolean(v!=null);if(v!=null)out.writeInt(v);}
    private static Integer integer(DataInputStream in)throws IOException {return in.readBoolean()?in.readInt():null;}
    private static void data(DataOutputStream out,byte[] v)throws IOException {out.writeBoolean(v!=null);if(v!=null){out.writeInt(v.length);out.write(v);}}
    private static byte[] data(DataInputStream in)throws IOException {return in.readBoolean()?in.readNBytes(count(in)):null;}
}
