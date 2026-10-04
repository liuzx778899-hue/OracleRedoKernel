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
    private ArchivedObjectTransactions(List<ArchivedRedo.Header> sources,List<Transaction> transactions) {
        this.sources=List.copyOf(sources);this.transactions=List.copyOf(transactions);
    }
    public static ArchivedObjectTransactions read(List<Path> paths,long uid,long container,Set<Long> objects) throws IOException {
        paths=List.copyOf(paths);objects=Set.copyOf(objects);
        if (paths.isEmpty() || objects.isEmpty()) throw new RedoException("EMPTY_OBJECT_TRANSACTION_SCOPE_OR_INPUT");
        final Set<Long> scope=objects;var xids=new LinkedHashSet<HeapRowPatch.Xid>();
        var relevantMarkers=new ArrayList<ArchivedUndoInventory.Position>();var sources=new ArrayList<ArchivedRedo.Header>();
        var continuity=new TransactionLifecycle();var scanner=new ArchivedRedo();
        for (var path:paths) {
            var header=scanner.scan(path);continuity.archive(header);sources.add(header);
            new RedoRecords().visit(path,header,r->{
                if (r.containerUid()!=uid) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (v.containerId()!=container || v.opcodeMajor()!=5) continue;
                    long object,data;
                    if (v.opcodeMinor()==1) {
                        var h=UndoHeader.decode(v);object=h.objectId();data=h.dataObjectId();
                        if (scope.contains(object) || scope.contains(data)) xids.add(h.xid());
                    } else if (v.opcodeMinor()==6 || v.opcodeMinor()==11) {
                        var c=UndoCompensation.decode(v);object=c.objectId();data=c.dataObjectId();
                        if (scope.contains(object) || scope.contains(data))
                            relevantMarkers.add(new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1));
                    } else continue;
                    if ((scope.contains(object) || scope.contains(data)) && (!scope.contains(object) || !scope.contains(data)))
                        throw new RedoException("OBJECT_TRANSACTION_MAPPING_MISMATCH");
                }
            });
            if (!header.equals(scanner.scan(path))) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
        }
        var transactions=new ArrayList<Transaction>();var routed=new java.util.HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
        for (var xid:xids) {
            var associations=ArchivedCompensationAssociations.read(paths,uid,container,xid);
            if (!sources.equals(associations.window().undo().sources())) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
            for (var original:associations.window().undo().entries())
                if (!scope.contains(original.identity().objectId()) || !scope.contains(original.identity().dataObjectId()))
                    throw new RedoException("OBJECT_TRANSACTION_UNDO_OUTSIDE_SCOPE");
            for (var a:associations.entries()) {
                var previous=routed.putIfAbsent(a.position(),a);
                if (previous!=null && !previous.equals(a)) throw new RedoException("OBJECT_TRANSACTION_ROUTING_DISAGREEMENT");
            }
            transactions.add(new Transaction(xid,associations));
        }
        for (var at:relevantMarkers) {
            var a=routed.get(at);
            if (a==null || !xids.contains(a.xid())) throw new RedoException("UNDISCOVERED_OBJECT_TRANSACTION_COMPENSATION");
        }
        for (int i=0;i<paths.size();i++)
            if (!sources.get(i).equals(scanner.scan(paths.get(i)))) throw new RedoException("OBJECT_TRANSACTION_INPUT_CHANGED");
        return new ArchivedObjectTransactions(sources,transactions);
    }
    public List<ArchivedRedo.Header> sources() {return sources;}
    public List<Transaction> transactions() {return transactions;}
}
