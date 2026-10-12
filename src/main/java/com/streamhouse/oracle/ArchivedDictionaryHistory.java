package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Archive-owned complete committed SYS histories. Does not itself emit business events. */
public final class ArchivedDictionaryHistory {
    private record SchemaMarker(long scn,HeapRowPatch.Xid owner) {}
    private static final Comparator<ArchivedUndoInventory.Position> POSITION_ORDER=
            Comparator.comparingLong(ArchivedUndoInventory.Position::archiveSequence)
                    .thenComparingLong(ArchivedUndoInventory.Position::block)
                    .thenComparingInt(ArchivedUndoInventory.Position::offset)
                    .thenComparingInt(ArchivedUndoInventory.Position::vector);
    record RowOperation(FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Patch forward,HeapRowPatch.Patch inverse,UndoHeader identity,Integer clusterKeyIndex) {
        RowOperation(FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Patch forward,HeapRowPatch.Patch inverse,UndoHeader identity){this(role,forward,inverse,identity,null);}
        RowOperation(FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Patch forward,HeapRowPatch.Patch inverse){this(role,forward,inverse,null);}
    }
    public record Commit(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Entry> original,
                         int rowPairs,int indexPairs,int controls,List<DictionaryBaseline.Row> rows,ArchivedUndoInventory.Position commitPosition,
                         List<FrozenHeapBaseline.ObjectRole> generations) {
        public Commit {original=List.copyOf(original);rows=List.copyOf(rows);generations=List.copyOf(generations);}
        public Commit(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Entry> original,int rowPairs,int indexPairs,int controls,
                      List<DictionaryBaseline.Row> rows,ArchivedUndoInventory.Position position) {this(identity,original,rowPairs,indexPairs,controls,rows,position,List.of());}
    }
    public record Pending(HeapRowPatch.Xid xid,long beginScn,int originals,int rowPairs,int indexPairs,int controls,
                          String identitySha256,String graphSha256) {}
    static Timeline committedState(FrozenHeapBaseline.Snapshot snapshot,List<ArchivedRedo.Header> sources,List<Commit> commits) {
        return new Timeline(snapshot,sources,commits);
    }
    public static final class Timeline {
        private final FrozenHeapBaseline.Snapshot snapshot;
        private final List<ArchivedRedo.Header> sources;
        private final List<Commit> commits;
        private final List<Pending> pending;
        private final List<ArchivedDictionaryClusterMaintenance.Closed> clusterMaintenance;
        private final List<Context> waitingContexts;
        private Timeline(FrozenHeapBaseline.Snapshot snapshot,List<ArchivedRedo.Header> sources,List<Commit> commits) {
            this(snapshot,sources,commits,List.of(),List.of());
        }
        private Timeline(FrozenHeapBaseline.Snapshot snapshot,List<ArchivedRedo.Header> sources,List<Commit> commits,List<Pending> pending,
                         List<ArchivedDictionaryClusterMaintenance.Closed> clusterMaintenance) {
            this(snapshot,sources,commits,pending,clusterMaintenance,List.of());
        }
        private Timeline(FrozenHeapBaseline.Snapshot snapshot,List<ArchivedRedo.Header> sources,List<Commit> commits,List<Pending> pending,
                         List<ArchivedDictionaryClusterMaintenance.Closed> clusterMaintenance,List<Context> waitingContexts) {
            this.snapshot=snapshot;this.sources=List.copyOf(sources);this.commits=List.copyOf(commits);
            this.pending=List.copyOf(pending);
            this.clusterMaintenance=List.copyOf(clusterMaintenance);
            this.waitingContexts=List.copyOf(waitingContexts);
        }
        public List<ArchivedRedo.Header> sources(){return sources;}
        public List<Commit> commits(){return commits;}
        public List<Pending> pending(){return pending;}
        public String baselineSha256(){return snapshot.sha256();}
        public HistoricalDictionaryState materializedState(){return HistoricalDictionaryState.from(snapshot,sources,commits,pending);}
        public DictionaryPendingState durablePendingState()throws IOException {
            if(pending.size()!=waitingContexts.size())throw new RedoException("DICTIONARY_PENDING_CONTEXT_MISSING");
            return DictionaryPendingState.from(snapshot,sources,waitingContexts);
        }
        /** Source-bound dictionary state receipt; the frozen baseline and raw prefix remain required. */
        public byte[] checkpoint()throws RedoException {
            var out=new StringBuilder("ORK_DICTIONARY_TIMELINE_V1\nB|").append(snapshot.sha256()).append('\n');
            for(var source:sources)out.append("S|").append(digest(source.toString())).append('\n');
            // Preserve V1 receipts; commit position is re-derived from the already bound raw input.
            for(var commit:commits)out.append("C|").append(digest("Commit[identity="+commit.identity()+", original="+commit.original()
                    +", rowPairs="+commit.rowPairs()+", indexPairs="+commit.indexPairs()+", controls="+commit.controls()+", rows="+commit.rows()+"]")).append('\n');
            for(var commit:commits)if(!commit.generations().isEmpty())out.append("G|").append(digest(commit.commitPosition()+":"+commit.generations())).append('\n');
            for(var active:pending)out.append("P|").append(digest(active.toString())).append('\n');
            for(var maintenance:clusterMaintenance)out.append("M|").append(digest(maintenance.toString())).append('\n');
            return out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        void requireBoundTo(RedoBatch inputs,FrozenHeapBaseline.Snapshot baseline)throws IOException {
            if(!snapshot.equals(baseline) || inputs.size()!=sources.size())throw new RedoException("HISTORY_TIMELINE_BASELINE_OR_INPUT_MISMATCH");
            for(int i=0;i<inputs.size();i++)if(!sources.get(i).equals(inputs.get(i).header()))throw new RedoException("HISTORY_TIMELINE_SOURCE_CHANGED");
        }
        void requireAddedNullDefault(FrozenHeapBaseline.PhysicalColumn column,long scn)throws RedoException {
            requireAddedNullDefault(column,scn,null);
        }
        void requireAddedNullDefault(FrozenHeapBaseline.PhysicalColumn column,long scn,ArchivedUndoInventory.Position position)throws RedoException {
            var baseline=snapshot.dictionaryBaseline();var layout=baseline.layouts().get("COL$");
            int defaultAt=-1;for(int i=0;i<layout.columns().size();i++)if(layout.columns().get(i).name().equals("DEFAULT$"))defaultAt=i;
            for(var row:rowsAt(scn,position))if(row.role().equals("COL$") && baseline.number(row,"INTCOL#")==column.internal()
                    && baseline.text(row,"NAME").equals(column.name())) {
                if(!column.nullable() || defaultAt<0 || row.values().get(defaultAt).state()!=HeapRowPatch.State.NULL)
                    throw new RedoException("UNSUPPORTED_HISTORICAL_ADDED_COLUMN_DEFAULT");return;
            }
            throw new RedoException("HISTORICAL_ADDED_COLUMN_METADATA_MISSING");
        }
        public List<FrozenHeapBaseline.PhysicalColumn> schemaAt(long scn)throws RedoException {
            return schemaAt(scn,null);
        }
        public List<FrozenHeapBaseline.PhysicalColumn> schemaAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {
            if(pending.stream().anyMatch(p->p.beginScn()<=scn))throw new RedoException("DICTIONARY_SCHEMA_PENDING_SYS");
            return DictionaryHistoryRows.schema(snapshot,rowsAt(scn,position),heapAt(scn,position),indexAt(scn,position));
        }
        public FrozenHeapBaseline.ObjectRole heapAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {return objectAt(snapshot.heap(),scn,position);}
        public FrozenHeapBaseline.ObjectRole indexAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {return objectAt(snapshot.index(),scn,position);}
        public List<FrozenHeapBaseline.LobColumn> lobsAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {
            var result=new ArrayList<FrozenHeapBaseline.LobColumn>();
            for(var lob:snapshot.lobs())result.add(new FrozenHeapBaseline.LobColumn(lob.column(),lob.storage(),objectAt(lob.lob(),scn,position),objectAt(lob.index(),scn,position)));
            for(var commit:commits) {
                if(commit.identity().endRecordScn()>scn || commit.identity().endRecordScn()==scn && position!=null && POSITION_ORDER.compare(commit.commitPosition(),position)>0)break;
                if(isMigration(commit.generations(),snapshot)) {
                    result.clear();
                    for(int i=0;i<snapshot.lobs().size();i++)result.add(new FrozenHeapBaseline.LobColumn(snapshot.lobs().get(i).column(),LobLocator.Storage.SECUREFILE,commit.generations().get(2+i*2),commit.generations().get(3+i*2)));
                }
            }
            return List.copyOf(result);
        }
        private FrozenHeapBaseline.ObjectRole objectAt(FrozenHeapBaseline.ObjectRole role,long scn,ArchivedUndoInventory.Position position)throws RedoException {
            rowsAt(scn,position);var result=role;
            for(var commit:commits) {
                if(commit.identity().endRecordScn()>scn || commit.identity().endRecordScn()==scn
                        && position!=null && POSITION_ORDER.compare(commit.commitPosition(),position)>0)break;
                if(isMigration(commit.generations(),snapshot)) {
                    if(role.equals(snapshot.heap()))result=commit.generations().get(0);
                    else if(role.equals(snapshot.index()))result=commit.generations().get(1);
                } else for(var next:commit.generations())if(next.logicalObject()==result.logicalObject())result=next;
            }
            return result;
        }
        private List<DictionaryBaseline.Row> rowsAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {
            if(scn<snapshot.scn() || scn>=sources.get(sources.size()-1).nextScn())throw new RedoException("DICTIONARY_SCHEMA_SCN_OUTSIDE_VERIFIED_WINDOW");
            List<DictionaryBaseline.Row> rows=snapshot.dictionaryBaseline().rows();
            for(var commit:commits) {
                if(commit.identity().endRecordScn()>scn || commit.identity().endRecordScn()==scn
                        && position!=null && POSITION_ORDER.compare(commit.commitPosition(),position)>0)break;
                rows=commit.rows();
            }
            return rows;
        }
    }
    static boolean isMigration(List<FrozenHeapBaseline.ObjectRole> roles,FrozenHeapBaseline.Snapshot snapshot) {
        return !snapshot.lobs().isEmpty() && roles.size()==2+2*snapshot.lobs().size() && roles.get(0).logicalObject()!=snapshot.heap().logicalObject();
    }
    static final class Context {
        final ArchivedUndoInventory inventory;
        final UndoHistory graph;
        final TransactionLifecycle lifecycle;
        final List<RowOperation> rows=new ArrayList<>();
        final List<DictionaryIndexUndoPair> indexes=new ArrayList<>();
        final List<DictionaryStatisticsChanges.Row> statisticsRows=new ArrayList<>();
        final List<DictionaryIndexUndoPair> statisticsIndexes=new ArrayList<>();
        final List<DictionaryUsageChanges.Row> usageRows=new ArrayList<>();
        final List<DictionaryIndexUndoPair> usageIndexes=new ArrayList<>();
        final List<LobDroppedColumn> lobDrops=new ArrayList<>();
        final List<DictionaryObjectIndexPair.Pair> objectIndexes=new ArrayList<>();
        final List<DictionaryDataObjectIndexPair> dataIndexes=new ArrayList<>();
        final List<HeapUndoPair> deferredRows=new ArrayList<>();
        final List<IndexUndoPair> deferredIndexes=new ArrayList<>();
        final List<DictionaryRedefinitionAuxiliary.Row> auxiliaryRows=new ArrayList<>();
        final List<DictionaryRedefinitionAuxiliary.Index> auxiliaryIndexes=new ArrayList<>();
        final List<DictionaryRedefinitionAuxiliary.ClusterRow> auxiliaryClusterRows=new ArrayList<>();
        int rowCount(){return rows.size()+statisticsRows.size()+usageRows.size()+lobDrops.size()+deferredRows.size()+auxiliaryRows.size()+auxiliaryClusterRows.size();}
        int indexCount(){return indexes.size()+statisticsIndexes.size()+usageIndexes.size()+objectIndexes.size()+dataIndexes.size()+deferredIndexes.size()+auxiliaryIndexes.size();}
        TransactionStart start;
        long beginScn;
        TransactionLifecycle.ClosedIdentity end;
        ArchivedUndoInventory.Position commitPosition;
        int next,controls;
        LobTruncateProof truncate;
        Context(ArchivedUndoInventory inventory){this(inventory,new UndoHistory(inventory),new TransactionLifecycle());}
        Context(ArchivedUndoInventory inventory,UndoHistory graph,TransactionLifecycle lifecycle){this.inventory=inventory;this.graph=graph;this.lifecycle=lifecycle;}
    }
    public static Timeline read(List<Path> archives,Path baseline,String trustedReceipt)throws IOException {
        return read(RedoBatch.archives(archives),FrozenHeapBaseline.read(baseline,trustedReceipt));
    }
    public static Timeline readCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedReceipt)throws IOException {
        return read(RedoBatch.captures(captures),FrozenHeapBaseline.read(baseline,trustedReceipt));
    }
    /** Waiting receipt only; uncommitted SYS rows never become a schema or event authority. */
    public static Timeline prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedReceipt)throws IOException {
        return readPrefix(RedoBatch.captures(captures),FrozenHeapBaseline.read(baseline,trustedReceipt));
    }
    /** Reconstructs dictionary values from verified raw bytes, never from a caller-supplied schema. */
    public static Timeline restoreCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedReceipt,
                                           byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        return restore(RedoBatch.captures(captures),FrozenHeapBaseline.read(baseline,trustedReceipt),checkpoint,trustedCheckpointSha256);
    }
    static Timeline restore(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,byte[] checkpoint,String trustedSha256)throws IOException {
        if(!digest(checkpoint).equals(trustedSha256))throw new RedoException("DICTIONARY_CHECKPOINT_DIGEST_MISMATCH");
        String[] lines=new String(checkpoint,java.nio.charset.StandardCharsets.UTF_8).split("\n");
        if(lines.length<3 || !lines[0].equals("ORK_DICTIONARY_TIMELINE_V1") || !lines[1].equals("B|"+snapshot.sha256()))
            throw new RedoException("DICTIONARY_CHECKPOINT_BASELINE");
        int count=0;
        while(count+2<lines.length && lines[count+2].startsWith("S|")) {
            if(count>=inputs.size() || !lines[count+2].equals("S|"+digest(inputs.get(count).header().toString())))
                throw new RedoException("DICTIONARY_CHECKPOINT_SOURCE_PREFIX_CHANGED");
            count++;
        }
        if(count==0)throw new RedoException("DICTIONARY_CHECKPOINT_SOURCE_PREFIX_CHANGED");
        var expected=readPrefix(inputs.prefix(count),snapshot).checkpoint();
        if(!Arrays.equals(checkpoint,expected))throw new RedoException("DICTIONARY_CHECKPOINT_STATE_CHANGED");
        return read(inputs,snapshot);
    }
    private static String digest(String value)throws RedoException {return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String digest(byte[] value)throws RedoException {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException e){throw new RedoException("SHA256_UNAVAILABLE");}
    }
    static Timeline read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        return read(inputs,snapshot,false);
    }
    static Timeline readPrefix(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        return read(inputs,snapshot,true);
    }
    static Timeline readRestored(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,DictionaryPendingState pending)throws IOException {
        pending.requireBaseline(snapshot);
        return read(inputs,snapshot,true,pending);
    }
    static Timeline readRestored(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,DictionaryPendingState pending,List<Commit> committed)throws IOException {
        pending.requireBaseline(snapshot);
        return read(inputs,snapshot,true,pending,committed);
    }
    /** Requires the complete authenticated producer capsule, including every prior committed and pending SYS owner. */
    static Timeline readRestoredIncremental(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,DictionaryPendingState pending,List<Commit> committed)throws IOException {
        pending.requireBaseline(snapshot);
        return read(inputs,snapshot,true,pending,committed,true);
    }
    private static Timeline read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,boolean allowPending)throws IOException {
        return read(inputs,snapshot,allowPending,null);
    }
    private static Timeline read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,boolean allowPending,DictionaryPendingState restored)throws IOException {
        return read(inputs,snapshot,allowPending,restored,List.of());
    }
    private static Timeline read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,boolean allowPending,DictionaryPendingState restored,List<Commit> committed)throws IOException {
        return read(inputs,snapshot,allowPending,restored,committed,false);
    }
    private static Timeline read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,boolean allowPending,DictionaryPendingState restored,List<Commit> committed,boolean incremental)throws IOException {
        if(snapshot.dictionaryBaseline()==null)throw new RedoException("MISSING_V3_COMPLETE_DICTIONARY_BASELINE");
        if(inputs.isEmpty())throw new RedoException("NO_DICTIONARY_REDO_INPUT");
        var baseline=snapshot.dictionaryBaseline();var roles=new HashMap<Long,FrozenHeapBaseline.ObjectRole>();
        var closedOwners=new HashSet<HeapRowPatch.Xid>();var closedOriginals=new HashMap<ArchivedUndoInventory.Position,UndoHeader>();
        var observedClosed=new HashSet<ArchivedUndoInventory.Position>();
        for(var c:committed) {
            if(!closedOwners.add(c.identity().xid()))throw new RedoException("DICTIONARY_COMMITTED_OWNER_COLLISION");
            for(var e:c.original())if(closedOriginals.putIfAbsent(e.position(),e.identity())!=null)throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_COLLISION");
        }
        for(var layout:baseline.layouts().values())roles.put(layout.role().logicalObject(),layout.role());
        for(var index:baseline.indexes())roles.put(index.role().logicalObject(),index.role());
        var splitRestores=ArchivedDictionaryIndexRestores.read(inputs,snapshot);
        var clusterMaintenance=ArchivedDictionaryClusterMaintenance.read(inputs,snapshot);
        var clusterOriginals=new HashMap<ArchivedUndoInventory.Position,UndoHeader>();
        for(var closed:clusterMaintenance)for(var e:closed.original())if(clusterOriginals.putIfAbsent(e.position(),e.identity())!=null)
            throw new RedoException("CLUSTER_MAINTENANCE_ORIGINAL_OWNER_COLLISION");
        var identities=new LinkedHashSet<HeapRowPatch.Xid>();var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();var markers=new ArrayList<SchemaMarker>();
        var saved=restored==null?null:restored.decoded();int prefixCount=saved==null?0:saved.sources().size();
        if(prefixCount>inputs.size())throw new RedoException("DICTIONARY_PENDING_SOURCE_PREFIX_CHANGED");
        for(int i=0;i<prefixCount;i++)if(!saved.sources().get(i).equals(inputs.get(i).header()))throw new RedoException("DICTIONARY_PENDING_SOURCE_PREFIX_CHANGED");
        if(incremental)for(var c:saved.contexts())identities.add(c.inventory.entries().get(0).identity().xid());
        var observedScns=new HashSet<Long>();
        var objectAnchors=new HashMap<ArchivedHeapRows.RowKey,Long>();
        for(var anchor:snapshot.dictionaryAnchors())if(anchor.dictionary().name().equals("OBJ$"))objectAnchors.put(anchor.key(),anchor.targetObject());
        int sourceIndex=0;
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);continuity.archive(header);sources.add(header);
            if(incremental && sourceIndex++<prefixCount)continue;
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                observedScns.add(r.scn());
                if(r.scn()<=snapshot.scn())return;
                var objectTouches=trackObjectAnchors(r,snapshot,objectAnchors);
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=snapshot.source().containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=1)continue;
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                    if(!closedOwners.isEmpty() && !v.fields().isEmpty() && v.fields().get(0).length==20) {
                        byte[] b=v.fields().get(0);var xid=new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));
                        if(closedOwners.contains(xid)) {
                            var h=UndoHeader.decode(v);
                            if(!h.equals(closedOriginals.get(at)) || !observedClosed.add(at))throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_CHANGED");
                            continue;
                        }
                    }
                    var cluster=clusterOriginals.get(at);
                    if(cluster!=null) {
                        if(!cluster.equals(UndoHeader.decode(v)))throw new RedoException("CLUSTER_MAINTENANCE_ORIGINAL_CHANGED");
                        continue;
                    }
                    var physical=splitRestores.maintenance.get(at);
                    if(physical!=null) {
                        if(!physical.equals(UndoHeader.decode(v)))throw new RedoException("DICTIONARY_SPLIT_ORIGINAL_CHANGED");
                        continue;
                    }
                    if(v.fields().size()<2 || v.fields().get(0).length!=20 || v.fields().get(1).length<8)throw new RedoException("UNREADABLE_DICTIONARY_DISCOVERY_IDENTITY");
                    var role=roles.get(u32(v.fields().get(1),0));
                    if(role!=null && affectsBaselineScope(header,r,v,role,snapshot,splitRestores,objectTouches)) {byte[] b=v.fields().get(0);identities.add(new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12)));}
                }
                for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==24) {
                    if(incremental && v.opcodeMinor()==4 && v.fields().size()==2 && v.fields().get(1).length==16) {
                        long first=u32(v.fields().get(1),0);
                        if(!observedScns.contains(first))for(int i=0;i<prefixCount;i++) {
                            var old=inputs.get(i);var oldHeader=old.header();
                            if(!oldHeader.equals(saved.sources().get(i)))throw new RedoException("DICTIONARY_DISCOVERY_SOURCE_CHANGED");
                            if(first>=oldHeader.firstScn() && first<oldHeader.nextScn()) {
                                old.visit(oldHeader,record->{if(record.containerUid()==snapshot.source().containerUid() && record.scn()==first)observedScns.add(first);});
                                if(!oldHeader.equals(old.header()))throw new RedoException("DICTIONARY_DISCOVERY_SOURCE_CHANGED");
                            }
                        }
                    }
                    HistoricalSchemaBarrier.requireMarkerProfile(r,v,snapshot.scn(),observedScns);
                    if(v.opcodeMinor()==1)for(var role:List.of(snapshot.heap(),snapshot.index())) {
                        var f=v.fields();
                        if(u32(f.get(3),4)==role.logicalObject() || HistoricalSchemaBarrier.affectsObject(v,role.logicalObject())
                                || Arrays.equals(f.get(8),role.owner().getBytes(java.nio.charset.StandardCharsets.UTF_8)) && Arrays.equals(f.get(9),role.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                            byte[] marker=f.get(0);
                            markers.add(new SchemaMarker(r.scn(),new HeapRowPatch.Xid(u16(marker,4),u16(marker,6),u32(marker,8))));break;
                        }
                    }
                }
            });
            if(!header.equals(input.header()))throw new RedoException("DICTIONARY_DISCOVERY_SOURCE_CHANGED");
        }
        continuity.finish();
        DictionaryObjectOwnerClosure.extend(inputs,snapshot,identities,closedOwners);
        if(!incremental && observedClosed.size()!=closedOriginals.size())throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_MISSING");
        // These dependencies belong to an already selected SYS owner; they do not select unrelated system work.
        for(var role:List.of(new FrozenHeapBaseline.ObjectRole("SYS","I_OBJ1",36,36),
                new FrozenHeapBaseline.ObjectRole("SYS","I_OBJ2",37,37),new FrozenHeapBaseline.ObjectRole("SYS","I_OBJ4",39,39),
                new FrozenHeapBaseline.ObjectRole("SYS","I_OBJ5",40,40),new FrozenHeapBaseline.ObjectRole("SYS","DEFERRED_STG$",81,81),
                new FrozenHeapBaseline.ObjectRole("SYS","I_DEFERRED_STG1",82,82)))roles.put(role.logicalObject(),role);
        for(var role:DictionaryRedefinitionAuxiliary.roles())roles.put(role.logicalObject(),role);
        // Auxiliary statistics do not select a schema history; every dependency of a selected SYS transaction is still owned below.
        if(baseline.statistics()!=null) {
            var statistics=baseline.statistics();roles.put(statistics.table().role().logicalObject(),statistics.table().role());
            for(var index:statistics.indexes())roles.put(index.role().logicalObject(),index.role());
        }
        if(baseline.usage()!=null) {
            var usage=baseline.usage();roles.put(usage.table().role().logicalObject(),usage.table().role());
            roles.put(usage.index().role().logicalObject(),usage.index().role());
        }
        if(snapshot.scn()<sources.get(0).firstScn() || snapshot.scn()>=sources.get(sources.size()-1).nextScn())throw new RedoException("DICTIONARY_BASELINE_SCN_OUTSIDE_INPUT_COVERAGE");
        if(prefixCount>sources.size() || saved!=null && !saved.sources().equals(sources.subList(0,prefixCount)))throw new RedoException("DICTIONARY_PENDING_SOURCE_PREFIX_CHANGED");
        var contexts=new LinkedHashMap<HeapRowPatch.Xid,Context>();var originals=new HashMap<ArchivedUndoInventory.Position,Context>();
        var resumed=new HashSet<Context>();
        for(var xid:identities) {
            var prior=saved==null?null:saved.contexts().stream().filter(c->c.inventory.entries().get(0).identity().xid().equals(xid)).findFirst().orElse(null);
            var extended=prior==null?null:prefixCount==inputs.size()?prior.graph:prior.graph.extend(inputs.suffix(prefixCount));
            var inventory=extended==null?ArchivedUndoInventory.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),xid):extended.inventory();
            if(!inventory.sources().equals(sources))throw new RedoException("DICTIONARY_INVENTORY_SOURCE_CHANGED");
            Context c;
            if(prior==null)c=new Context(inventory);
            else {
                c=new Context(inventory,extended,prior.lifecycle);
                c.start=prior.start;c.beginScn=prior.beginScn;c.next=prior.next;c.controls=prior.controls;
                c.rows.addAll(prior.rows);c.indexes.addAll(prior.indexes);c.statisticsRows.addAll(prior.statisticsRows);c.statisticsIndexes.addAll(prior.statisticsIndexes);
                c.usageRows.addAll(prior.usageRows);c.usageIndexes.addAll(prior.usageIndexes);resumed.add(c);
            }
            c.truncate=LobTruncateProof.read(inputs,snapshot,inventory);
            contexts.put(xid,c);
            for(var e:inventory.entries())if(originals.putIfAbsent(e.position(),c)!=null)throw new RedoException("DICTIONARY_ORIGINAL_OWNER_COLLISION");
        }
        if(saved!=null && resumed.size()!=saved.contexts().size())throw new RedoException("DICTIONARY_PENDING_OWNER_MISSING");
        for(var input:inputs) {
            var header=input.header();if(!sources.contains(header))throw new RedoException("DICTIONARY_REPLAY_SOURCE_CHANGED");
            boolean old=prefixCount>0 && header.sequence()<=sources.get(prefixCount-1).sequence();
            for(var c:contexts.values())if(!old || !resumed.contains(c))c.lifecycle.archive(header);
            if(incremental && old && contexts.values().stream().noneMatch(c->!resumed.contains(c)
                    && c.inventory.entries().stream().anyMatch(e->e.position().archiveSequence()<=sources.get(prefixCount-1).sequence())))continue;
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var c:contexts.values())if(!old || !resumed.contains(c))controls(header,r,c,snapshot);
                for(int i=0;i<r.vectors().size();i++) {
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);var c=originals.get(at);if(c==null)continue;
                    if(old && resumed.contains(c))continue;
                    if(c.end!=null || r.scn()<=snapshot.scn())throw new RedoException("DICTIONARY_BASELINE_CROSSES_SYS_TRANSACTION");
                    var v=r.vectors().get(i);var h=UndoHeader.decode(v);var e=c.inventory.entries().get(c.next++);
                    if(!e.position().equals(at) || !e.identity().equals(h))throw new RedoException("DICTIONARY_ORIGINAL_COVERAGE_CHANGED");
                    c.lifecycle.undo(r,v);c.graph.append(e);
                    if(c.inventory.controlRoot()!=null && h.equals(c.inventory.controlRoot().allocationBegin())) {c.controls++;continue;}
                    if(c.truncate!=null) {
                        var materialized=c.truncate.row(h);
                        if(materialized==null)c.controls++;else c.rows.add(materialized);
                        continue;
                    }
                    var role=roles.get(h.objectId());
                    if(role==null && !snapshot.lobs().isEmpty() && h.objectId()==snapshot.heap().logicalObject()) {
                        c.lobDrops.add(LobDroppedColumn.bind(r,v,snapshot));continue;
                    }
                    if(role==null || role.dataObject()!=h.dataObjectId())throw new RedoException("UNKNOWN_OR_CONFLICTING_DICTIONARY_DEPENDENCY:"+at+":"+h.objectId()+":"+h.dataObjectId());
                    int count=0;
                    for(var row:r.vectors()) {
                        if(row.containerId()!=snapshot.source().containerId())continue;
                        // A record may contain changes to several dictionaries. The vector header carries DATAOBJ#.
                        if(row.objectId()>=0 && row.objectId()!=role.dataObject())continue;
                        if((row.opcodeMajor()==10 || row.opcodeMajor()==11) && !HeapUndoPair.referencesOriginal(row,h.uba()))continue;
                        try {
                        if(role.name().equals("OBJ$") && row.opcodeMajor()==11) {
                            var p=DictionaryObjectUndoPair.bind(r,row,role,baseline.layouts().get("OBJ$").columns().size()).physical();
                            if(!p.undo().identity().equals(h))throw new RedoException("DICTIONARY_ROW_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p);c.rows.add(new RowOperation(role,p.forward(),p.undo().inverse(),h));count++;
                        } else if(role.logicalObject()==81 && row.opcodeMajor()==11) {
                            var p=DictionaryDeferredStoragePair.bind(r,row);
                            if(!p.undo().identity().equals(h))throw new RedoException("DICTIONARY_DEFERRED_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p);c.deferredRows.add(p);count++;
                        } else if(role.logicalObject()==82 && row.opcodeMajor()==10) {
                            var p=DictionaryDeferredStoragePair.bindIndex(r,row);
                            if(!p.undo().identity().equals(h))throw new RedoException("DICTIONARY_DEFERRED_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p);c.deferredIndexes.add(p);count++;
                        } else if(List.of(36L,37L,40L).contains(role.logicalObject()) && row.opcodeMajor()==10) {
                            var p=DictionaryObjectIndexPair.bind(r,row,role.logicalObject());
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("DICTIONARY_OBJECT_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.objectIndexes.add(p);count++;
                        } else if(role.logicalObject()==39 && row.opcodeMajor()==10) {
                            var p=DictionaryDataObjectIndexPair.bind(r,row);
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("DICTIONARY_DATA_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.dataIndexes.add(p);count++;
                        } else if(DictionaryRedefinitionAuxiliary.table(role.logicalObject()) && row.opcodeMajor()==11) {
                            var p=DictionaryRedefinitionAuxiliary.bind(r,row,role.logicalObject());
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("DICTIONARY_AUXILIARY_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.auxiliaryRows.add(p);count++;
                        } else if(DictionaryRedefinitionAuxiliary.index(role.logicalObject()) && row.opcodeMajor()==10) {
                            var p=DictionaryRedefinitionAuxiliary.bindIndex(r,row,role.logicalObject());
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("DICTIONARY_AUXILIARY_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.auxiliaryIndexes.add(p);count++;
                        } else if(DictionaryRedefinitionAuxiliary.cluster(role.logicalObject()) && row.opcodeMajor()==11) {
                            var p=DictionaryRedefinitionAuxiliary.bindCluster(r,row,role.logicalObject());
                            if(!p.physical().identity().equals(h))throw new RedoException("DICTIONARY_AUXILIARY_CLUSTER_FULL_IDENTITY_CONFLICT");
                            c.auxiliaryClusterRows.add(p);count++;
                        } else if((role.name().equals("TAB$") || role.name().equals("COL$")) && row.opcodeMajor()==11) {
                            var layout=baseline.layouts().get(role.name());var p=ClusterUndoPair.bind(r,row,role,layout.tableNumber(),layout.columns().size()-1);
                            if(!p.identity().equals(h))throw new RedoException("DICTIONARY_ROW_FULL_IDENTITY_CONFLICT");
                            c.rows.add(new RowOperation(role,p.forward().row(),p.inverse().row(),h,p.forward().clusterKeyIndex()));count++;
                        } else if(role.name().equals("HIST_HEAD$") && row.opcodeMajor()==11) {
                            var p=DictionaryStatisticsChanges.bind(r,row,baseline.statistics());
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("STATISTICS_ROW_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.statisticsRows.add(p);count++;
                        } else if(role.name().startsWith("I_HH_") && row.opcodeMajor()==10) {
                            var scope=baseline.statistics().indexes().stream().filter(x->x.role().equals(role)).findFirst().orElseThrow();
                            var p=DictionaryIndexUndoPair.bind(r,row,role,scope.indexDba(),"AL32UTF8");
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("STATISTICS_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.statisticsIndexes.add(p);count++;
                        } else if(role.name().equals("COL_USAGE$") && row.opcodeMajor()==11) {
                            var p=DictionaryUsageChanges.bind(r,row,baseline.usage());
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("USAGE_ROW_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.usageRows.add(p);count++;
                        } else if(role.name().equals("I_COL_USAGE$") && row.opcodeMajor()==10) {
                            var p=DictionaryIndexUndoPair.bind(r,row,role,baseline.usage().index().indexDba(),"AL32UTF8");
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("USAGE_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.usageIndexes.add(p);count++;
                        } else if(role.name().startsWith("I_COL") && row.opcodeMajor()==10 && (row.opcodeMinor()==2 || row.opcodeMinor()==4 || row.opcodeMinor()==5)) {
                            var scope=baseline.indexes().stream().filter(x->x.role().equals(role)).findFirst().orElseThrow();
                            var p=row.opcodeMinor()==5?splitRestores.restores.get(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(row)+1)):DictionaryIndexUndoPair.bind(r,row,role,scope.indexDba(),"AL32UTF8");
                            if(p==null || !p.role().equals(role) || p.indexDba()!=scope.indexDba())throw new RedoException("MISSING_INPUT_OWNED_DICTIONARY_RESTORE");
                            if(!p.physical().undo().identity().equals(h))throw new RedoException("DICTIONARY_INDEX_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p.physical());c.indexes.add(p);count++;
                        }
                        } catch(RedoException failure) {
                            var contextual=new RedoException(failure.getMessage()+":original="+at+":role="+role.name()+":row="+row.opcodeMajor()+"."+row.opcodeMinor()+":dataObject="+row.objectId()+":dba="+row.dba());
                            contextual.initCause(failure);throw contextual;
                        }
                    }
                    if(count!=1)throw new RedoException("DICTIONARY_ORIGINAL_TYPED_PAIR_COVERAGE");
                }
                for(var c:contexts.values())for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
                    if(old && resumed.contains(c))continue;
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==11 && c.start!=null && c.end==null) {
                        if(v.dba()!=c.start.segmentDba() || v.fields().isEmpty() || v.fields().get(0).length<19
                                || (v.fields().get(0)[18]&255)!=c.inventory.entries().get(0).identity().xid().slot())continue;
                        var marker=UndoCompensation.decode(v);
                        if(marker.dba()!=c.start.segmentDba() || marker.slot()!=c.inventory.entries().get(0).identity().xid().slot())continue;
                        var auth=c.auxiliaryRows.stream().filter(row->row.logical()==61).map(DictionaryRedefinitionAuxiliary.Row::physical).toList();
                        if(marker.objectId()!=61 || marker.dataObjectId()!=61 || marker.tablespace()!=0 || auth.isEmpty())
                            throw new RedoException("UNSUPPORTED_ACTIVE_SYS_BEGIN_COMPENSATION");
                        var rollback=r.vectors().stream().filter(row->row.containerId()==v.containerId() && row.opcodeMajor()==11 && row.opcodeMinor()==3).toList();
                        if(rollback.size()!=1)throw new RedoException("DICTIONARY_AUTHORIZATION_COMPENSATION_ROW_COUNT");
                        var pair=HeapCompensation.matchBegin(r,v,rollback.get(0),auth,8,c.lifecycle);c.graph.compensate(pair.undo().identity());
                        c.auxiliaryRows.removeIf(row->row.logical()==61 && row.physical().undo().identity().equals(pair.undo().identity()));c.controls++;
                    }
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==6 && c.start!=null && c.end==null)throw new RedoException("UNSUPPORTED_ACTIVE_SYS_COMPENSATION");
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==4 && c.lifecycle.knowsEnd(r,v)) {
                        c.end=c.lifecycle.end(r,v);if(c.end.rolledBack())throw new RedoException("UNSUPPORTED_SYS_ROLLBACK_HISTORY");
                        c.commitPosition=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(v)+1);
                        var graph=c.graph.close(TransactionEnd.undoTail(v));
                        if(graph.live().size()!=c.rowCount()+c.indexCount()+(c.truncate==null?0:c.controls-1)
                                || c.next!=c.inventory.entries().size())throw new RedoException("DICTIONARY_COMPLETE_LIVE_ORIGINAL_COVERAGE");
                    }
                }
            });
            if(!header.equals(input.header()))throw new RedoException("DICTIONARY_REPLAY_SOURCE_CHANGED");
        }
        var pending=new ArrayList<Pending>();
        for(var c:contexts.values()) {
            if(c.end!=null || !allowPending)c.lifecycle.finish();
            else {
                if(c.truncate!=null)throw new RedoException("LOB_TRUNCATE_PENDING_REQUIRES_COMPLETE_RAW");
                if(!c.objectIndexes.isEmpty() || !c.dataIndexes.isEmpty() || !c.deferredRows.isEmpty() || !c.deferredIndexes.isEmpty()
                        || !c.auxiliaryRows.isEmpty() || !c.auxiliaryIndexes.isEmpty() || !c.auxiliaryClusterRows.isEmpty())
                    throw new RedoException("DICTIONARY_REDEFINITION_PENDING_REQUIRES_COMPLETE_RAW");
                if(!c.lobDrops.isEmpty())throw new RedoException("LOB_DROP_PENDING_DICTIONARY_NOT_IMPLEMENTED");
                if(c.start==null || c.next!=c.inventory.entries().size() || c.next!=c.rowCount()+c.indexCount()+c.controls)
                    throw new RedoException("DICTIONARY_PENDING_ORIGINAL_COVERAGE");
                pending.add(new Pending(c.inventory.entries().get(0).identity().xid(),c.beginScn,c.next,c.rowCount(),c.indexCount(),c.controls,
                        digest(c.lifecycle.checkpoint()),digest(c.graph.checkpoint())));
            }
        }
        for(var marker:markers)if(contexts.values().stream().noneMatch(c->(c.beginScn<=marker.scn() || c.inventory.entries().get(0).identity().xid().equals(marker.owner()))
                && (c.end==null?allowPending:c.end.endRecordScn()>=marker.scn()))
                && committed.stream().noneMatch(c->(c.identity().beginRecordScn()<=marker.scn() || c.identity().xid().equals(marker.owner()))
                    && c.identity().endRecordScn()>=marker.scn()))throw new RedoException("TARGET_SCHEMA_MARKER_WITHOUT_COMPLETE_SYS_HISTORY:"+marker.scn());
        var ordered=new ArrayList<>(contexts.values().stream().filter(c->c.end!=null).toList());
        ordered.sort(Comparator.comparingLong((Context c)->c.end.endRecordScn()).thenComparing(c->c.commitPosition,POSITION_ORDER));
        var state=new DictionaryHistoryRows(snapshot,committed.isEmpty()?baseline.rows():committed.get(committed.size()-1).rows());
        var commits=new ArrayList<>(committed);var positions=new HashSet<ArchivedUndoInventory.Position>();
        var migration=new LobMigrationProof(snapshot);
        for(var c:committed)positions.add(c.commitPosition());
        for(var c:ordered) {
            if(c.end.endRecordScn()<=snapshot.scn() || !positions.add(c.commitPosition))throw new RedoException("AMBIGUOUS_DICTIONARY_COMMIT_POSITION");
            if(!commits.isEmpty()) {
                var last=commits.get(commits.size()-1);
                if(c.end.endRecordScn()<last.identity().endRecordScn() || c.end.endRecordScn()==last.identity().endRecordScn()
                        && POSITION_ORDER.compare(c.commitPosition,last.commitPosition())<=0)throw new RedoException("DICTIONARY_COMMITTED_PREFIX_ORDER");
            }
            if(!c.usageRows.isEmpty() || !c.usageIndexes.isEmpty())
                DictionaryUsageChanges.requireComplete(c.usageRows,c.usageIndexes,baseline.usage(),snapshot.heap().logicalObject(),state.current(),baseline);
            if(c.truncate==null)DictionaryObjectDependencies.require(c.rows,c.objectIndexes,c.dataIndexes,c.deferredRows,c.deferredIndexes);
            var knownParents=new HashSet<Long>();
            for(var row:state.current())if(row.role().equals("OBJ$") && baseline.number(row,"TYPE#")==2)knownParents.add(row.target());
            for(var row:c.rows)if(row.role().name().equals("OBJ$") && (row.forward().kind()==HeapRowPatch.Kind.INSERT || row.forward().kind()==HeapRowPatch.Kind.DELETE)) {
                var image=row.forward().kind()==HeapRowPatch.Kind.INSERT?row.forward():row.inverse();
                if(OracleScalars.number(image.columns().get(6).bytes()).compareTo(java.math.BigDecimal.valueOf(2))==0)knownParents.add(OracleScalars.number(image.columns().get(0).bytes()).longValueExact());
            }
            DictionaryRedefinitionAuxiliary.requireComplete(c.auxiliaryRows,c.auxiliaryIndexes,c.auxiliaryClusterRows,knownParents);
            var priorRows=state.current();var rows=state.apply(c.rows,c.indexes);
            if(!c.lobDrops.isEmpty()) {
                var prior=DictionaryHistoryRows.schema(snapshot,priorRows);var next=DictionaryHistoryRows.schema(snapshot,rows);
                if(prior.size()!=next.size()+1 || !next.equals(prior.subList(0,next.size()))
                        || List.of("BLOB","CLOB").contains(prior.get(next.size()).type())
                        || c.lobDrops.stream().anyMatch(d->d.column()!=next.size()))
                    throw new RedoException("LOB_DROP_DICTIONARY_COLUMN_CONFLICT");
            }
            if(!c.statisticsRows.isEmpty() || !c.statisticsIndexes.isEmpty())
                DictionaryStatisticsChanges.requireComplete(c.statisticsRows,c.statisticsIndexes,baseline.statistics(),snapshot.heap().logicalObject(),rows,baseline);
            var bindings=c.truncate==null && !snapshot.lobs().isEmpty()?migration.apply(c,priorRows,rows):List.<FrozenHeapBaseline.ObjectRole>of();
            commits.add(new Commit(c.end,c.inventory.entries(),c.rowCount(),c.indexCount(),c.controls,rows,c.commitPosition,c.truncate==null?bindings:c.truncate.next()));
        }
        return new Timeline(snapshot,sources,commits,pending,clusterMaintenance,contexts.values().stream().filter(c->c.end==null).toList());
    }
    private static Set<ArchivedHeapRows.RowKey> trackObjectAnchors(RedoRecords.Record record,FrozenHeapBaseline.Snapshot snapshot,Map<ArchivedHeapRows.RowKey,Long> current)throws RedoException {
        var touched=new HashSet<ArchivedHeapRows.RowKey>();
        for(var row:record.vectors())if(row.containerId()==snapshot.source().containerId() && row.objectId()==18 && row.opcodeMajor()==11 && row.opcodeMinor()>=2 && row.opcodeMinor()<=5) {
            if(row.fields().size()<2)throw new RedoException("UNREADABLE_DICTIONARY_OBJECT_ANCHOR");
            byte[] k=row.fields().get(1);int slot;
            if(row.opcodeMinor()==2 && (k.length==49 || k.length==52))slot=u16(k,42);
            else if(row.opcodeMinor()==5 && (k.length==29 || k.length==30))slot=u16(k,20);
            else if((row.opcodeMinor()==3 || row.opcodeMinor()==4) && k.length==20)slot=u16(k,16);
            else continue;
            var key=new ArchivedHeapRows.RowKey(18,row.dba(),slot);if(!current.containsKey(key))continue;
            long wanted=snapshot.dictionaryAnchors().stream().filter(a->a.key().equals(key)).findFirst().orElseThrow().targetObject();
            if(row.opcodeMinor()==4 || row.opcodeMinor()==5 && !changesObjectNumber(row)) {
                if(current.get(key)==wanted)touched.add(key);continue;
            }
            // Discovery reads only OBJ# from a structurally complete physical image.
            // Selected owners still pass DictionaryObjectUndoPair's narrower semantic profiles below.
            var patch=row.opcodeMinor()==5 && k.length==30?new HeapRowPatch().decodeDictionaryObject(row,37):new HeapRowPatch().decodeDictionaryAuxiliary(row,37);long before=current.get(key),after=before;
            if(patch.kind()==HeapRowPatch.Kind.DELETE)after=0;
            else if(patch.columns().get(0).state()==HeapRowPatch.State.VALUE)after=OracleScalars.number(patch.columns().get(0).bytes()).longValueExact();
            if(before==wanted || after==wanted)touched.add(key);current.put(key,after);
        }
        return touched;
    }
    private static boolean changesObjectNumber(RedoRecords.Vector row)throws RedoException {
        if(row.fields().size()<3)throw new RedoException("UNREADABLE_DICTIONARY_OBJECT_COLUMN_MAP");
        byte[] map=row.fields().get(2),k=row.fields().get(1);int count=k[23]&255;
        if((k[11]&0x80)!=0 && map.length==2)return u16(map,0)==0;
        if(map.length!=count*2)throw new RedoException("UNREADABLE_DICTIONARY_OBJECT_COLUMN_MAP");
        for(int i=0;i<count;i++)if(u16(map,i*2)==0)return true;return false;
    }
    private static boolean affectsBaselineScope(ArchivedRedo.Header header,RedoRecords.Record r,RedoRecords.Vector undo,FrozenHeapBaseline.ObjectRole role,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryIndexRestores splitRestores,Set<ArchivedHeapRows.RowKey> objectTouches)throws RedoException {
        var fields=undo.fields();
        if(fields.get(1).length<24 || u32(fields.get(1),4)!=role.dataObject() || u32(fields.get(1),8)!=0)
            throw new RedoException("DICTIONARY_DISCOVERY_GENERATION_CONFLICT");
        if(role.name().startsWith("I_COL")) {
            var scope=snapshot.dictionaryBaseline().indexes().stream().filter(x->x.role().equals(role)).findFirst().orElseThrow();
            boolean selected=false;int count=0;
            for(var v:r.vectors())if(v.containerId()==undo.containerId() && v.opcodeMajor()==10 && (v.opcodeMinor()==2 || v.opcodeMinor()==4 || v.opcodeMinor()==5)) {
                var pair=v.opcodeMinor()==5?splitRestores.restores.get(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(v)+1)):DictionaryIndexUndoPair.bind(r,v,role,scope.indexDba(),"AL32UTF8");
                if(pair==null || !pair.role().equals(role) || pair.indexDba()!=scope.indexDba())throw new RedoException("MISSING_INPUT_OWNED_DICTIONARY_RESTORE");
                if(!pair.physical().undo().identity().equals(UndoHeader.decode(undo)))throw new RedoException("DICTIONARY_DISCOVERY_INDEX_IDENTITY_CONFLICT");
                selected|=pair.key().objectId()==snapshot.heap().logicalObject();count++;
            }
            if(count!=1)throw new RedoException("DICTIONARY_DISCOVERY_INDEX_COVERAGE");return selected;
        }
        if(fields.size()<4 || fields.get(3).length<20)throw new RedoException("UNREADABLE_DICTIONARY_DISCOVERY_ROW");
        if(role.name().equals("TAB$") && fields.get(1)[16]==14 && fields.get(1)[17]==5) {
            DictionarySpaceHwmPair.require(r,undo,role);return false;
        }
        byte[] k=fields.get(3);int op=k[10]&255,slot;
        if(role.name().equals("TAB$") && (op==10 || op==6 || op==2 && k.length==54))
            return affectsObservedTabCluster(r,undo,role,snapshot);
        if(op==5 && (k.length==29 || k.length==30))slot=u16(k,20);
        else if((op==3 || op==4) && k.length==20)slot=u16(k,16);
        else if(op==2 && (k.length==49 || k.length==52))slot=u16(k,42);
        else throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        var key=new ArchivedHeapRows.RowKey(role.dataObject(),u32(k,0),slot);
        if(role.name().equals("OBJ$"))return objectTouches.contains(key);
        return snapshot.dictionaryAnchors().stream().anyMatch(a->a.dictionary().equals(role) && a.key().equals(key));
    }
    // Seq102 native diagnostics locate these physical operations. This only selects scope;
    // an operation touching an anchor still requires the existing complete history parser.
    static boolean affectsObservedTabCluster(RedoRecords.Record r,RedoRecords.Vector undo,FrozenHeapBaseline.ObjectRole role,FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        var fields=undo.fields();byte[] k=fields.get(3);int op=k[10]&255;
        if(!role.owner().equals("SYS") || !role.name().equals("TAB$") || u32(k,0)==0 || k[11]!=1 || k[12]==0 || k[13]!=0 || u16(k,14)!=0)
            throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        if(op==10) {
            if(r.vectors().size()!=2)throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
            var pair=DictionaryClusterLinkPair.bind(r,r.vectors().get(1),role);
            return touchesClusterAnchor(snapshot,role,pair.forward().block(),pair.forward().slot())
                    || touchesClusterAnchor(snapshot,role,pair.forward().linkBlock(),pair.forward().linkSlot())
                    || touchesClusterAnchor(snapshot,role,pair.inverse().linkBlock(),pair.inverse().linkSlot());
        }
        if(k.length!=54 || k[16]!=0x6c || k[18]!=44 || k[44]!=1 || fields.size()!=48)
            throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        int slot=u16(k,42);boolean selected=touchesClusterAnchor(snapshot,role,u32(k,0),slot);
        if(op==2)return selected;
        if(op!=6 || r.vectors().size()!=2)throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        var row=r.vectors().get(1);
        if(row.opcodeMajor()!=11 || row.opcodeMinor()!=6 || row.containerId()!=undo.containerId() || row.fields().size()!=2)
            throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        var h=UndoHeader.decode(undo);byte[] ktb=row.fields().get(0);
        if(ktb.length!=16 || ktb[0]!=2 || ktb[1]!=0x0d || u32(ktb,8)!=h.uba().dba()
                || u16(ktb,12)!=h.uba().sequence() || (ktb[14]&255)!=h.uba().record()
                || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject())
            throw new RedoException("DICTIONARY_DISCOVERY_ROW_IDENTITY_CONFLICT");
        byte[] next=row.fields().get(1);
        if(next.length!=48 || u32(next,0)!=u32(k,0) || row.dba()!=u32(k,0) || next[10]!=6 || next[11]!=1
                || next[12]!=k[12] || next[13]!=0 || u16(next,14)!=0 || next[16]!=0x20 || next[18]!=0
                || next[44]!=1 || u16(next,42)!=slot || u32(next,28)==0)
            throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        return selected || touchesClusterAnchor(snapshot,role,u32(next,28),u16(next,32));
    }
    private static boolean touchesClusterAnchor(FrozenHeapBaseline.Snapshot snapshot,FrozenHeapBaseline.ObjectRole role,long block,int slot) {
        return snapshot.dictionaryAnchors().stream().anyMatch(a->a.key().dataObjectId()==role.dataObject()
                && a.key().dba()==block && a.key().slot()==slot);
    }
    private static void controls(ArchivedRedo.Header header,RedoRecords.Record r,Context c,FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        var first=c.inventory.entries().get(0);boolean initial=first.position().archiveSequence()==header.sequence() && first.position().block()==r.block() && first.position().offset()==r.offset();
        for(var v:r.vectors()) {
            if(v.containerId()!=snapshot.source().containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=2)continue;
            if(initial) {
                if(v.fields().isEmpty() || v.fields().get(0).length!=32)throw new RedoException("UNREADABLE_DICTIONARY_BEGIN_CONTROL");
                byte[] b=v.fields().get(0);
                if(!first.identity().uba().equals(new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255)))continue;
                var start=TransactionStart.decode(v);if(!start.uba().equals(first.identity().uba()))continue;
                if(c.start!=null || !start.bindBegin(r).equals(first.identity().xid()))throw new RedoException("DICTIONARY_BEGIN_CONTROL_AMBIGUOUS");
                c.lifecycle.begin(r,v);c.start=start;c.beginScn=r.scn();
            } else if(c.start!=null && c.end==null && v.dba()==c.start.segmentDba()) {
                if(v.fields().isEmpty() || v.fields().get(0).length!=32)throw new RedoException("UNREADABLE_ACTIVE_DICTIONARY_CONTROL");
                byte[] b=v.fields().get(0);if(u16(b,0)!=c.start.slot())continue;c.lifecycle.observeSlotControl(r,v);
                var root=c.inventory.controlRoot();int flags=u16(b,16);
                if(root!=null && flags==2 && u32(b,8)==root.rowRoot().uba().dba() && u16(b,12)==root.rowRoot().uba().sequence() && (b[14]&255)==root.rowRoot().uba().record())continue;
                if(flags==10)c.lifecycle.advance(r,v);
                else if(flags==2) {
                    var candidate=new HeapRowPatch.Uba(u32(b,8),u16(b,12),b[14]&255);
                    var retry=c.inventory.entries().stream().filter(e->e.identity().objectId()==61 && !e.identity().beginsTransaction()
                            && e.previousDba()==0 && e.rci()==0 && e.identity().uba().equals(candidate)).findFirst();
                    if(retry.isPresent()) {DictionaryAuthorizationRetry.headUpdate(r,v,retry.get().identity(),c.graph,c.lifecycle);continue;}
                    try {c.lifecycle.headUpdate(r,v);}
                    catch(RedoException failure) {
                        var contextual=new RedoException(failure.getMessage()+":record="+r.block()+"/"+r.offset()+":owner="+first.identity().xid()+":control="+HexFormat.of().formatHex(b));
                        contextual.initCause(failure);throw contextual;
                    }
                }
                else throw new RedoException("UNSUPPORTED_ACTIVE_DICTIONARY_CONTROL");
            }
        }
    }
}
