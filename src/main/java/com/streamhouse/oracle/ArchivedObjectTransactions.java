package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Source-owned object transaction discovery and header routing, not physical cancellation or event authorization. */
public final class ArchivedObjectTransactions {
    public record Transaction(HeapRowPatch.Xid xid,ArchivedCompensationAssociations associations) {}
    private final List<ArchivedRedo.Header> sources;
    private final List<Transaction> transactions;
    private final AssociationDiscoveryState discovery;
    private ArchivedObjectTransactions(List<ArchivedRedo.Header> sources,List<Transaction> transactions,AssociationDiscoveryState discovery) {
        this.sources=List.copyOf(sources);this.transactions=List.copyOf(transactions);this.discovery=discovery;
    }
    public static ArchivedObjectTransactions read(List<Path> paths,long uid,long container,Set<Long> objects) throws IOException {
        return read(RedoBatch.archives(paths),uid,container,objects.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(o->o,o->o)));
    }
    public static ArchivedObjectTransactions read(List<Path> paths,long uid,long container,HeapIndexScope roles) throws IOException {
        return read(RedoBatch.archives(paths),uid,container,roles);
    }
    static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,HeapIndexScope roles) throws IOException {
        return read(paths,uid,container,roles.generations());
    }
    static ArchivedObjectTransactions readPending(RedoBatch paths,long uid,long container,HeapIndexScope roles) throws IOException {
        return read(paths,uid,container,roles.generations(),true);
    }
    static ArchivedObjectTransactions readCovered(RedoBatch paths,long uid,long container,HeapIndexScope roles,
                                                  boolean allowPending,Set<HeapRowPatch.Xid> covered)throws IOException {
        return read(paths,uid,container,roles.generations(),allowPending,covered);
    }
    static ArchivedObjectTransactions readCoveredRestored(RedoBatch paths,long uid,long container,HeapIndexScope roles,
                                                          boolean allowPending,Set<HeapRowPatch.Xid> covered,List<String> windows)throws IOException {
        var saved=new java.util.HashMap<HeapRowPatch.Xid,ArchivedCompensationInventory>();
        for(var value:windows) {
            byte[] bytes=java.util.Base64.getDecoder().decode(value);var window=ArchivedCompensationInventory.restoreDurable(bytes,DurableUndoState.sha(bytes));
            if(saved.putIfAbsent(window.undo().entries().get(0).identity().xid(),window)!=null)throw new RedoException("OBJECT_TRANSACTION_DUPLICATE_SAVED_WINDOW");
        }
        return read(paths,uid,container,roles.generations(),allowPending,covered,saved);
    }
    static ArchivedObjectTransactions readIndexed(RedoBatch paths,long uid,long container,HeapIndexScope roles,boolean allowPending,
                                                   Set<HeapRowPatch.Xid> covered,List<String> windows,AssociationDiscoveryState discovery)throws IOException {
        discovery.require(paths,uid,container);
        var saved=new java.util.HashMap<HeapRowPatch.Xid,ArchivedCompensationInventory>();
        for(var value:windows) {
            byte[] bytes=java.util.Base64.getDecoder().decode(value);var window=ArchivedCompensationInventory.restoreDurable(bytes,DurableUndoState.sha(bytes));
            discovery.requireWindow(window);
            if(saved.putIfAbsent(window.undo().entries().get(0).identity().xid(),window)!=null)throw new RedoException("OBJECT_TRANSACTION_DUPLICATE_SAVED_WINDOW");
        }
        return read(paths,uid,container,roles.generations(),allowPending,covered,saved,discovery);
    }
    private static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,java.util.Map<Long,Long> generations) throws IOException {
        return read(paths,uid,container,generations,false);
    }
    private static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,java.util.Map<Long,Long> generations,boolean allowPending) throws IOException {
        return read(paths,uid,container,generations,allowPending,Set.of());
    }
    private static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,java.util.Map<Long,Long> generations,
                                                    boolean allowPending,Set<HeapRowPatch.Xid> covered) throws IOException {
        return read(paths,uid,container,generations,allowPending,covered,java.util.Map.of());
    }
    private static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,java.util.Map<Long,Long> generations,
                                                    boolean allowPending,Set<HeapRowPatch.Xid> covered,java.util.Map<HeapRowPatch.Xid,ArchivedCompensationInventory> saved) throws IOException {
        return read(paths,uid,container,generations,allowPending,covered,saved,null);
    }
    private static ArchivedObjectTransactions read(RedoBatch paths,long uid,long container,java.util.Map<Long,Long> generations,
                                                    boolean allowPending,Set<HeapRowPatch.Xid> covered,java.util.Map<HeapRowPatch.Xid,ArchivedCompensationInventory> saved,AssociationDiscoveryState discovery) throws IOException {
        if (paths.isEmpty() || generations.isEmpty()) throw new RedoException("EMPTY_OBJECT_TRANSACTION_SCOPE_OR_INPUT");
        var scope=new java.util.HashSet<Long>(generations.keySet());scope.addAll(generations.values());var xids=new LinkedHashSet<HeapRowPatch.Xid>();
        var relevantMarkers=new ArrayList<ArchivedUndoInventory.Position>();var sources=new ArrayList<ArchivedRedo.Header>();
        var continuity=new TransactionLifecycle();
        for (int source=0;source<paths.size();source++) {
            var path=paths.get(source);
            var header=path.header();continuity.archive(header);sources.add(header);
            RedoRecords.Visitor visitor=r->{
                if (r.containerUid()!=uid) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (v.containerId()!=container || v.opcodeMajor()!=5) continue;
                    long object,data;
                    if (v.opcodeMinor()==1) {
                        if (v.fields().size()<2 || v.fields().get(0).length!=20 || v.fields().get(1).length<24)
                            throw new RedoException("UNREADABLE_OBJECT_TRANSACTION_ORIGINAL");
                        object=RedoBytes.u32(v.fields().get(1),0);data=RedoBytes.u32(v.fields().get(1),4);
                        byte[] identity=v.fields().get(0);
                        if(covered.contains(new HeapRowPatch.Xid(RedoBytes.u16(identity,8),RedoBytes.u16(identity,10),RedoBytes.u32(identity,12))))continue;
                        if (scope.contains(object) || scope.contains(data)) xids.add(UndoHeader.decode(v).xid());
                    } else if (v.opcodeMinor()==6 || v.opcodeMinor()==11) {
                        if ((v.fields().size()!=2 && v.fields().size()!=3) || v.fields().get(0).length!=28 || v.fields().get(1).length!=8)
                            throw new RedoException("UNREADABLE_OBJECT_TRANSACTION_COMPENSATION");
                        object=RedoBytes.u32(v.fields().get(0),0);data=RedoBytes.u32(v.fields().get(0),4);
                        if (scope.contains(object) || scope.contains(data)) {
                            UndoCompensation.decode(v);
                            relevantMarkers.add(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1));
                        }
                    } else continue;
                    if ((scope.contains(object) || scope.contains(data)) && !Long.valueOf(data).equals(generations.get(object)))
                        throw new RedoException("OBJECT_TRANSACTION_MAPPING_MISMATCH");
                }
            };
            if(discovery==null)path.visit(header,visitor);else discovery.visit(source,visitor);
            if (!header.equals(path.header())) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
        }
        var transactions=new ArrayList<Transaction>();var routed=new java.util.HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
        for (var xid:xids) {
            var associations=discovery!=null?ArchivedCompensationAssociations.readIndexed(paths,discovery.owner(paths,xid,allowPending),allowPending,discovery)
                    :saved.containsKey(xid)?ArchivedCompensationAssociations.readRestored(paths,saved.get(xid),allowPending)
                    :ArchivedCompensationAssociations.read(paths,uid,container,xid,allowPending);
            if (!sources.equals(associations.window().undo().sources())) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
            for (var original:associations.window().undo().entries())
                if (!Long.valueOf(original.identity().dataObjectId()).equals(generations.get(original.identity().objectId())))
                    throw new RedoException("OBJECT_TRANSACTION_UNDO_OUTSIDE_SCOPE");
            for (var a:associations.entries()) {
                var previous=routed.putIfAbsent(a.position(),a);
                if (previous!=null && !previous.equals(a)) throw new RedoException("OBJECT_TRANSACTION_ROUTING_DISAGREEMENT");
            }
            transactions.add(new Transaction(xid,associations));
        }
        if(!xids.containsAll(saved.keySet()))throw new RedoException("OBJECT_TRANSACTION_SAVED_WINDOW_OWNER_MISSING");
        for (var at:relevantMarkers) {
            var a=routed.get(at);
            if (a==null || !xids.contains(a.xid())) throw new RedoException("UNDISCOVERED_OBJECT_TRANSACTION_COMPENSATION");
        }
        for (int i=0;i<paths.size();i++)
            if (!sources.get(i).equals(paths.get(i).header())) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
        return new ArchivedObjectTransactions(sources,transactions,discovery);
    }
    public List<ArchivedRedo.Header> sources() {return sources;}
    public List<Transaction> transactions() {return transactions;}
    AssociationDiscoveryState discovery() {return discovery;}
}
