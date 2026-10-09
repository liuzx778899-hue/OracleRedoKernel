package com.streamhouse.oracle;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.DurableUndoState.*;
import static com.streamhouse.oracle.PhysicalRecoveryState.*;

/** Uncommitted SYS values and dependency pairs, never a committed schema authority. */
public final class DictionaryPendingState {
    record Decoded(List<ArchivedRedo.Header> sources,List<ArchivedDictionaryHistory.Context> contexts) {}
    private final byte[] bytes;
    private final FrozenHeapBaseline.Snapshot baseline;
    private DictionaryPendingState(byte[] bytes,FrozenHeapBaseline.Snapshot baseline){this.bytes=bytes.clone();this.baseline=baseline;}
    static DictionaryPendingState from(FrozenHeapBaseline.Snapshot baseline,List<ArchivedRedo.Header> sources,List<ArchivedDictionaryHistory.Context> contexts)throws IOException {
        return new DictionaryPendingState(encode(baseline,sources,contexts),baseline);
    }
    public byte[] checkpoint(){return bytes.clone();}
    public static DictionaryPendingState restore(byte[] bytes,String trustedStateSha256,Path baseline,String trustedBaselineSha256)throws IOException {
        return restore(bytes,trustedStateSha256,FrozenHeapBaseline.read(baseline,trustedBaselineSha256));
    }
    static DictionaryPendingState restore(byte[] bytes,String trustedStateSha256,FrozenHeapBaseline.Snapshot baseline)throws IOException {
        byte[] copy=bytes.clone();if(!sha(copy).equals(trustedStateSha256))throw new RedoException("DICTIONARY_PENDING_DIGEST_MISMATCH");
        var state=new DictionaryPendingState(copy,baseline);state.decoded();return state;
    }
    void requireBaseline(FrozenHeapBaseline.Snapshot expected)throws RedoException {
        if(!baseline.sha256().equals(expected.sha256()))throw new RedoException("DICTIONARY_PENDING_BASELINE_CHANGED");
    }
    Decoded decoded()throws IOException {
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));
            if(!in.readUTF().equals("ORK_PENDING_DICTIONARY_STATE_V1") || !in.readUTF().equals(baseline.sha256()))throw new RedoException("DICTIONARY_PENDING_BASELINE_OR_FORMAT");
            var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();
            for(int i=0,n=count(in);i<n;i++) {
                var h=new ArchivedRedo.Header(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());
                baseline.requireSource(h);continuity.archive(h);
                if(!h.sha256().matches("[0-9a-f]{64}") || h.bytes()<=0 || h.blocks()<3 || h.sequence()<1)throw new RedoException("DICTIONARY_PENDING_SOURCE");sources.add(h);
            }
            if(sources.isEmpty() || baseline.scn()<sources.get(0).firstScn() || baseline.scn()>=sources.get(sources.size()-1).nextScn())throw new RedoException("DICTIONARY_PENDING_SOURCE_COVERAGE");
            var contexts=new ArrayList<ArchivedDictionaryHistory.Context>();var owners=new HashSet<HeapRowPatch.Xid>();
            for(int i=0,n=count(in);i<n;i++) {
                byte[] graphBytes=in.readNBytes(count(in)),identity=in.readNBytes(count(in));var graph=UndoHistory.restoreDurable(graphBytes,sha(graphBytes));
                var lifecycle=TransactionLifecycle.restore(identity,sha(identity));var inventory=graph.inventory();var xid=inventory.entries().get(0).identity().xid();
                if(!inventory.sources().equals(sources) || inventory.containerUid()!=baseline.source().containerUid() || inventory.containerId()!=baseline.source().containerId()
                        || !owners.add(xid))throw new RedoException("DICTIONARY_PENDING_INVENTORY_SCOPE");
                var c=new ArchivedDictionaryHistory.Context(inventory,graph,lifecycle);
                c.start=new TransactionStart(in.readLong(),in.readInt(),in.readLong(),uba(in),in.readInt(),in.readLong(),in.readLong());
                c.beginScn=in.readLong();c.next=in.readInt();c.controls=in.readInt();
                if(c.start.slot()!=xid.slot() || c.start.sequence()!=xid.sequence() || !c.start.uba().equals(inventory.entries().get(0).identity().uba())
                        || c.start.containerUid()!=inventory.containerUid() || c.start.containerId()!=inventory.containerId()
                        || c.beginScn<=baseline.scn() || c.beginScn>=sources.get(sources.size()-1).nextScn() || c.next!=inventory.entries().size() || c.controls<0)
                    throw new RedoException("DICTIONARY_PENDING_START_OR_COVERAGE");
                lifecycle.requireRecoveryArchive(sources.get(sources.size()-1));lifecycle.requireRecoveryPending(xid,c.start.segmentDba(),inventory.containerUid(),inventory.containerId(),c.beginScn);
                for(int j=0,m=count(in);j<m;j++) {
                    var role=role(in);var h=undo(in);var forward=patch(in);var inverse=patch(in);member(inventory,inventory.containerUid(),inventory.containerId(),h,null,h.uba());
                    var layout=baseline.dictionaryBaseline().layouts().get(role.name());
                    if(layout==null || !List.of("OBJ$","TAB$","COL$").contains(role.name()) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject()
                            || forward.dba()!=inverse.dba() || forward.slot()!=inverse.slot() || forward.columns().size()!=layout.columns().size()-(layout.tableNumber()<0?0:1)
                            || inverse.columns().size()!=forward.columns().size())throw new RedoException("DICTIONARY_PENDING_ROW_SCOPE");
                    c.rows.add(new ArchivedDictionaryHistory.RowOperation(role,forward,inverse,h));
                }
                indexes(in,c.indexes,inventory);
                for(int j=0,m=count(in);j<m;j++) {
                    if(baseline.dictionaryBaseline().statistics()==null)throw new RedoException("DICTIONARY_PENDING_STATISTICS_SCOPE");
                    var row=new DictionaryStatisticsChanges.Row(heap(in,inventory),in.readLong(),in.readInt(),in.readInt());
                    var decoded=DictionaryStatisticsChanges.fromPhysical(row.physical(),baseline.dictionaryBaseline().statistics());
                    if(row.object()!=decoded.object() || row.column()!=decoded.column() || row.internal()!=decoded.internal())throw new RedoException("DICTIONARY_PENDING_STATISTICS_IDENTITY");c.statisticsRows.add(row);
                }
                indexes(in,c.statisticsIndexes,inventory);
                for(int j=0,m=count(in);j<m;j++) {
                    if(baseline.dictionaryBaseline().usage()==null)throw new RedoException("DICTIONARY_PENDING_USAGE_SCOPE");
                    var row=new DictionaryUsageChanges.Row(heap(in,inventory),in.readLong(),in.readInt());
                    var decoded=DictionaryUsageChanges.fromPhysical(row.physical(),baseline.dictionaryBaseline().usage());
                    if(row.object()!=decoded.object() || row.internal()!=decoded.internal())throw new RedoException("DICTIONARY_PENDING_USAGE_IDENTITY");c.usageRows.add(row);
                }
                indexes(in,c.usageIndexes,inventory);
                var selected=new ArrayList<UndoHeader>();for(var row:c.rows)selected.add(row.identity());
                for(var list:List.of(c.indexes,c.statisticsIndexes,c.usageIndexes))for(var pair:list)selected.add(pair.physical().undo().identity());
                for(var row:c.statisticsRows)selected.add(row.physical().undo().identity());for(var row:c.usageRows)selected.add(row.physical().undo().identity());
                var root=inventory.controlRoot();int controls=root==null?0:1;
                if(root!=null)selected.add(root.allocationBegin());
                if(c.next!=c.rowCount()+c.indexCount()+c.controls || c.controls!=controls || new HashSet<>(selected).size()!=selected.size()
                        || !new HashSet<>(selected).equals(new HashSet<>(inventory.entries().stream().map(ArchivedUndoInventory.Entry::identity).toList())))
                    throw new RedoException("DICTIONARY_PENDING_TYPED_COVERAGE");contexts.add(c);
            }
            if(in.available()!=0 || !Arrays.equals(bytes,encode(baseline,sources,contexts)))throw new RedoException("DICTIONARY_PENDING_NONCANONICAL");
            return new Decoded(List.copyOf(sources),List.copyOf(contexts));
        }catch(RedoException e){throw e;}
        catch(IllegalArgumentException|EOFException|UTFDataFormatException e){throw new RedoException("DICTIONARY_PENDING_MALFORMED");}
    }
    private static byte[] encode(FrozenHeapBaseline.Snapshot baseline,List<ArchivedRedo.Header> sources,List<ArchivedDictionaryHistory.Context> contexts)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_PENDING_DICTIONARY_STATE_V1");out.writeUTF(baseline.sha256());out.writeInt(sources.size());
        for(var h:sources) {
            out.writeUTF(h.sha256());out.writeLong(h.bytes());out.writeLong(h.dbid());out.writeLong(h.activationId());out.writeLong(h.compatibility());out.writeInt(h.thread());
            out.writeLong(h.sequence());out.writeLong(h.resetlogsId());out.writeLong(h.resetlogsScn());out.writeLong(h.firstScn());out.writeLong(h.nextScn());out.writeLong(h.blocks());
        }
        out.writeInt(contexts.size());for(var c:contexts) {
            byte[] graph=c.graph.durableCheckpoint(),identity=c.lifecycle.checkpoint();out.writeInt(graph.length);out.write(graph);out.writeInt(identity.length);out.write(identity);
            var s=c.start;out.writeLong(s.segmentDba());out.writeInt(s.slot());out.writeLong(s.sequence());uba(out,s.uba());out.writeInt(s.flags());out.writeLong(s.containerUid());out.writeLong(s.containerId());
            out.writeLong(c.beginScn);out.writeInt(c.next);out.writeInt(c.controls);
            out.writeInt(c.rows.size());for(var row:c.rows){role(out,row.role());undo(out,row.identity());patch(out,row.forward());patch(out,row.inverse());}indexes(out,c.indexes);
            out.writeInt(c.statisticsRows.size());for(var row:c.statisticsRows){heap(out,row.physical());out.writeLong(row.object());out.writeInt(row.column());out.writeInt(row.internal());}indexes(out,c.statisticsIndexes);
            out.writeInt(c.usageRows.size());for(var row:c.usageRows){heap(out,row.physical());out.writeLong(row.object());out.writeInt(row.internal());}indexes(out,c.usageIndexes);
        }
        out.flush();return bytes.toByteArray();
    }
    private static void role(DataOutputStream out,FrozenHeapBaseline.ObjectRole role)throws IOException {out.writeUTF(role.name());out.writeUTF(role.owner());out.writeLong(role.logicalObject());out.writeLong(role.dataObject());}
    private FrozenHeapBaseline.ObjectRole role(DataInputStream in)throws IOException {
        String name=in.readUTF(),owner=in.readUTF();long logical=in.readLong(),data=in.readLong();var roles=new ArrayList<FrozenHeapBaseline.ObjectRole>();var b=baseline.dictionaryBaseline();
        for(var layout:b.layouts().values())roles.add(layout.role());for(var index:b.indexes())roles.add(index.role());
        if(b.statistics()!=null){roles.add(b.statistics().table().role());for(var index:b.statistics().indexes())roles.add(index.role());}
        if(b.usage()!=null){roles.add(b.usage().table().role());roles.add(b.usage().index().role());}
        return roles.stream().filter(r->r.name().equals(name) && r.owner().equals(owner) && r.logicalObject()==logical && r.dataObject()==data).findFirst()
                .orElseThrow(()->new RedoException("DICTIONARY_PENDING_ROLE"));
    }
    private static void heap(DataOutputStream out,HeapUndoPair p)throws IOException {
        out.writeLong(p.containerUid());out.writeLong(p.containerId());patch(out,p.forward());undo(out,p.undo().identity());patch(out,p.undo().inverse());
        out.writeLong(p.undo().previousDba());out.writeInt(p.undo().predecessorRecord());out.writeInt(p.undo().wrap());
    }
    private static HeapUndoPair heap(DataInputStream in,ArchivedUndoInventory inventory)throws IOException {
        long uid=in.readLong(),container=in.readLong();var forward=patch(in);var h=undo(in);var inverse=patch(in);
        var pair=new HeapUndoPair(uid,container,forward,new UndoRowPatch(h,inverse,in.readLong(),in.readInt(),in.readInt()));
        member(inventory,uid,container,h,forward.xid(),forward.uba());return pair;
    }
    private static void indexes(DataOutputStream out,List<DictionaryIndexUndoPair> pairs)throws IOException {
        out.writeInt(pairs.size());for(var pair:pairs) {
            role(out,pair.role());out.writeLong(pair.indexDba());var p=pair.physical();out.writeLong(p.containerUid());out.writeLong(p.containerId());var f=p.forward();
            out.writeUTF(f.kind().name());out.writeLong(f.block());out.writeInt(f.itl());out.writeInt(f.slot());out.writeInt(f.rowSize());optionalXid(out,f.xid());optionalUba(out,f.uba());data(out,f.key());data(out,f.keydata());
            var u=p.undo();undo(out,u.identity());out.writeUTF(u.kind().name());out.writeLong(u.indexId());out.writeLong(u.block());out.writeInt(u.itl());out.writeInt(u.flags());out.writeInt(u.sdc());out.writeLong(u.previousDba());out.writeInt(u.predecessorRecord());out.writeInt(u.wrap());data(out,u.key());data(out,u.keydata());
        }
    }
    private void indexes(DataInputStream in,List<DictionaryIndexUndoPair> pairs,ArchivedUndoInventory inventory)throws IOException {
        for(int i=0,n=count(in);i<n;i++) {
            var role=role(in);long index=in.readLong(),uid=in.readLong(),container=in.readLong();
            var f=new IndexUndoPair.Forward(IndexUndoPair.Kind.valueOf(in.readUTF()),in.readLong(),in.readInt(),in.readInt(),in.readInt(),optionalXid(in),optionalUba(in),data(in),data(in));
            var u=new IndexUndoPair.Inverse(undo(in),IndexUndoPair.Kind.valueOf(in.readUTF()),in.readLong(),in.readLong(),in.readInt(),in.readInt(),in.readInt(),in.readLong(),in.readInt(),in.readInt(),data(in),data(in));
            member(inventory,uid,container,u.identity(),f.xid(),f.uba());
            var b=baseline.dictionaryBaseline();var scopes=new ArrayList<>(b.indexes());if(b.statistics()!=null)scopes.addAll(b.statistics().indexes());if(b.usage()!=null)scopes.add(b.usage().index());
            if(u.identity().objectId()!=role.logicalObject() || u.identity().dataObjectId()!=role.dataObject() || index!=u.indexId()
                    || scopes.stream().noneMatch(s->s.role().equals(role) && s.indexDba()==index))throw new RedoException("DICTIONARY_PENDING_INDEX_SCOPE");
            byte[] rowdata=f.kind()==IndexUndoPair.Kind.INSERT || f.kind()==IndexUndoPair.Kind.RESTORE?f.keydata():u.keydata();
            pairs.add(new DictionaryIndexUndoPair(role,index,new IndexUndoPair(uid,container,f,u),DictionaryIndexKey.decode(role.name(),u.key(),rowdata,"AL32UTF8")));
        }
    }
    private static void member(ArchivedUndoInventory inventory,long uid,long container,UndoHeader h,HeapRowPatch.Xid xid,HeapRowPatch.Uba uba)throws RedoException {
        if(uid!=inventory.containerUid() || container!=inventory.containerId() || !h.uba().equals(uba) || xid!=null && !h.xid().equals(xid)
                || inventory.entries().stream().noneMatch(e->e.identity().equals(h)))throw new RedoException("DICTIONARY_PENDING_PAIR_IDENTITY");
    }
}
