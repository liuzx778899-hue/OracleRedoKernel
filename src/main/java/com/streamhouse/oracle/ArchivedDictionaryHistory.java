package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Archive-owned complete committed SYS histories. Does not itself emit business events. */
public final class ArchivedDictionaryHistory {
    private static final Comparator<ArchivedUndoInventory.Position> POSITION_ORDER=
            Comparator.comparingLong(ArchivedUndoInventory.Position::archiveSequence)
                    .thenComparingLong(ArchivedUndoInventory.Position::block)
                    .thenComparingInt(ArchivedUndoInventory.Position::offset)
                    .thenComparingInt(ArchivedUndoInventory.Position::vector);
    record RowOperation(FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Patch forward,HeapRowPatch.Patch inverse,UndoHeader identity) {
        RowOperation(FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Patch forward,HeapRowPatch.Patch inverse){this(role,forward,inverse,null);}
    }
    public record Commit(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Entry> original,
                         int rowPairs,int indexPairs,int controls,List<DictionaryBaseline.Row> rows,ArchivedUndoInventory.Position commitPosition) {
        public Commit {original=List.copyOf(original);rows=List.copyOf(rows);}
    }
    public record Pending(HeapRowPatch.Xid xid,long beginScn,int originals,int rowPairs,int indexPairs,int controls,
                          String identitySha256,String graphSha256) {}
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
            return DictionaryHistoryRows.schema(snapshot,rowsAt(scn,position));
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
        int rowCount(){return rows.size()+statisticsRows.size()+usageRows.size();}
        int indexCount(){return indexes.size()+statisticsIndexes.size()+usageIndexes.size();}
        TransactionStart start;
        long beginScn;
        TransactionLifecycle.ClosedIdentity end;
        ArchivedUndoInventory.Position commitPosition;
        int next,controls;
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
        var identities=new LinkedHashSet<HeapRowPatch.Xid>();var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();var markers=new ArrayList<Long>();
        var saved=restored==null?null:restored.decoded();int prefixCount=saved==null?0:saved.sources().size();
        if(prefixCount>inputs.size())throw new RedoException("DICTIONARY_PENDING_SOURCE_PREFIX_CHANGED");
        for(int i=0;i<prefixCount;i++)if(!saved.sources().get(i).equals(inputs.get(i).header()))throw new RedoException("DICTIONARY_PENDING_SOURCE_PREFIX_CHANGED");
        if(incremental)for(var c:saved.contexts())identities.add(c.inventory.entries().get(0).identity().xid());
        var observedScns=new HashSet<Long>();
        int sourceIndex=0;
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);continuity.archive(header);sources.add(header);
            if(incremental && sourceIndex++<prefixCount)continue;
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                observedScns.add(r.scn());
                if(r.scn()<=snapshot.scn())return;
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
                    if(role!=null && affectsBaselineScope(header,r,v,role,snapshot,splitRestores)) {byte[] b=v.fields().get(0);identities.add(new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12)));}
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
                            markers.add(r.scn());break;
                        }
                    }
                }
            });
            if(!header.equals(input.header()))throw new RedoException("DICTIONARY_DISCOVERY_SOURCE_CHANGED");
        }
        continuity.finish();
        if(!incremental && observedClosed.size()!=closedOriginals.size())throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_MISSING");
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
                    var role=roles.get(h.objectId());if(role==null || role.dataObject()!=h.dataObjectId())throw new RedoException("UNKNOWN_OR_CONFLICTING_DICTIONARY_DEPENDENCY:"+at+":"+h.objectId()+":"+h.dataObjectId());
                    int count=0;
                    for(var row:r.vectors()) {
                        if(row.containerId()!=snapshot.source().containerId())continue;
                        if(role.name().equals("OBJ$") && row.opcodeMajor()==11) {
                            var p=DictionaryObjectUndoPair.bind(r,row,role,baseline.layouts().get("OBJ$").columns().size()).physical();
                            if(!p.undo().identity().equals(h))throw new RedoException("DICTIONARY_ROW_FULL_IDENTITY_CONFLICT");
                            c.lifecycle.requireActive(r,p);c.rows.add(new RowOperation(role,p.forward(),p.undo().inverse(),h));count++;
                        } else if((role.name().equals("TAB$") || role.name().equals("COL$")) && row.opcodeMajor()==11) {
                            var layout=baseline.layouts().get(role.name());var p=ClusterUndoPair.bind(r,row,role,layout.tableNumber(),layout.columns().size()-1);
                            if(!p.identity().equals(h))throw new RedoException("DICTIONARY_ROW_FULL_IDENTITY_CONFLICT");
                            c.rows.add(new RowOperation(role,p.forward().row(),p.inverse().row(),h));count++;
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
                    }
                    if(count!=1)throw new RedoException("DICTIONARY_ORIGINAL_TYPED_PAIR_COVERAGE");
                }
                for(var c:contexts.values())for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
                    if(old && resumed.contains(c))continue;
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==6 && c.start!=null && c.end==null)throw new RedoException("UNSUPPORTED_ACTIVE_SYS_COMPENSATION");
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==4 && c.lifecycle.knowsEnd(r,v)) {
                        c.end=c.lifecycle.end(r,v);if(c.end.rolledBack())throw new RedoException("UNSUPPORTED_SYS_ROLLBACK_HISTORY");
                        c.commitPosition=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(v)+1);
                        var graph=c.graph.close(TransactionEnd.undoTail(v));
                        if(graph.live().size()!=c.rowCount()+c.indexCount() || c.next!=c.inventory.entries().size())throw new RedoException("DICTIONARY_COMPLETE_LIVE_ORIGINAL_COVERAGE");
                    }
                }
            });
            if(!header.equals(input.header()))throw new RedoException("DICTIONARY_REPLAY_SOURCE_CHANGED");
        }
        var pending=new ArrayList<Pending>();
        for(var c:contexts.values()) {
            if(c.end!=null || !allowPending)c.lifecycle.finish();
            else {
                if(c.start==null || c.next!=c.inventory.entries().size() || c.next!=c.rowCount()+c.indexCount()+c.controls)
                    throw new RedoException("DICTIONARY_PENDING_ORIGINAL_COVERAGE");
                pending.add(new Pending(c.inventory.entries().get(0).identity().xid(),c.beginScn,c.next,c.rowCount(),c.indexCount(),c.controls,
                        digest(c.lifecycle.checkpoint()),digest(c.graph.checkpoint())));
            }
        }
        for(long scn:markers)if(contexts.values().stream().noneMatch(c->c.beginScn<=scn && (c.end==null?allowPending:c.end.endRecordScn()>=scn))
                && committed.stream().noneMatch(c->c.identity().beginRecordScn()<=scn && c.identity().endRecordScn()>=scn))throw new RedoException("TARGET_SCHEMA_MARKER_WITHOUT_COMPLETE_SYS_HISTORY");
        var ordered=new ArrayList<>(contexts.values().stream().filter(c->c.end!=null).toList());
        ordered.sort(Comparator.comparingLong((Context c)->c.end.endRecordScn()).thenComparing(c->c.commitPosition,POSITION_ORDER));
        var state=new DictionaryHistoryRows(snapshot,committed.isEmpty()?baseline.rows():committed.get(committed.size()-1).rows());
        var commits=new ArrayList<>(committed);var positions=new HashSet<ArchivedUndoInventory.Position>();
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
            var rows=state.apply(c.rows,c.indexes);
            if(!c.statisticsRows.isEmpty() || !c.statisticsIndexes.isEmpty())
                DictionaryStatisticsChanges.requireComplete(c.statisticsRows,c.statisticsIndexes,baseline.statistics(),snapshot.heap().logicalObject(),rows,baseline);
            commits.add(new Commit(c.end,c.inventory.entries(),c.rowCount(),c.indexCount(),c.controls,rows,c.commitPosition));
        }
        return new Timeline(snapshot,sources,commits,pending,clusterMaintenance,contexts.values().stream().filter(c->c.end==null).toList());
    }
    private static boolean affectsBaselineScope(ArchivedRedo.Header header,RedoRecords.Record r,RedoRecords.Vector undo,FrozenHeapBaseline.ObjectRole role,FrozenHeapBaseline.Snapshot snapshot,ArchivedDictionaryIndexRestores splitRestores)throws RedoException {
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
        byte[] k=fields.get(3);int op=k[10]&255,slot;
        if(op==5 && (k.length==29 || k.length==30))slot=u16(k,20);
        else if((op==3 || op==4) && k.length==20)slot=u16(k,16);
        else if(op==2 && (k.length==49 || k.length==52))slot=u16(k,42);
        else throw new RedoException("UNSUPPORTED_DICTIONARY_DISCOVERY_ROW");
        var key=new ArchivedHeapRows.RowKey(role.dataObject(),u32(k,0),slot);
        return snapshot.dictionaryAnchors().stream().anyMatch(a->a.dictionary().equals(role) && a.key().equals(key));
    }
    private static void controls(ArchivedRedo.Header header,RedoRecords.Record r,Context c,FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        var first=c.inventory.entries().get(0);boolean initial=first.position().archiveSequence()==header.sequence() && first.position().block()==r.block() && first.position().offset()==r.offset();
        for(var v:r.vectors()) {
            if(v.containerId()!=snapshot.source().containerId() || v.opcodeMajor()!=5 || v.opcodeMinor()!=2)continue;
            if(initial) {
                var start=TransactionStart.decode(v);if(!start.uba().equals(first.identity().uba()))continue;
                if(c.start!=null || !start.bindBegin(r).equals(first.identity().xid()))throw new RedoException("DICTIONARY_BEGIN_CONTROL_AMBIGUOUS");
                c.lifecycle.begin(r,v);c.start=start;c.beginScn=r.scn();
            } else if(c.start!=null && c.end==null && v.dba()==c.start.segmentDba()) {
                if(v.fields().isEmpty() || v.fields().get(0).length!=32)throw new RedoException("UNREADABLE_ACTIVE_DICTIONARY_CONTROL");
                byte[] b=v.fields().get(0);if(u16(b,0)!=c.start.slot())continue;c.lifecycle.observeSlotControl(r,v);
                var root=c.inventory.controlRoot();int flags=u16(b,16);
                if(root!=null && flags==2 && u32(b,8)==root.rowRoot().uba().dba() && u16(b,12)==root.rowRoot().uba().sequence() && (b[14]&255)==root.rowRoot().uba().record())continue;
                if(flags==10)c.lifecycle.advance(r,v);
                else if(flags==2)c.lifecycle.headUpdate(r,v);
                else throw new RedoException("UNSUPPORTED_ACTIVE_DICTIONARY_CONTROL");
            }
        }
    }
}
