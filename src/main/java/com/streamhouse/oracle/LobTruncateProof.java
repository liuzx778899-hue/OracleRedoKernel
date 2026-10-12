package com.streamhouse.oracle;

import java.io.IOException;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete observed LOB TRUNCATE dependencies. Lifecycle/undo closure still belongs to ArchivedDictionaryHistory. */
final class LobTruncateProof {
    private record Owned(RedoRecords.Record record,RedoRecords.Vector undo) {}
    private final Map<UndoHeader,ArchivedDictionaryHistory.RowOperation> rows=new HashMap<>();
    private final Set<UndoHeader> parsed=new HashSet<>();
    private final List<FrozenHeapBaseline.ObjectRole> next=new ArrayList<>();
    List<FrozenHeapBaseline.ObjectRole> next(){return List.copyOf(next);}
    ArchivedDictionaryHistory.RowOperation row(UndoHeader owner)throws RedoException {
        if(!parsed.contains(owner))throw new RedoException("LOB_TRUNCATE_UNPARSED_ORIGINAL");return rows.get(owner);
    }
    static LobTruncateProof read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,ArchivedUndoInventory inventory)throws IOException {
        var root=inventory.controlRoot();
        if(snapshot.lobs().isEmpty() || root==null || root.rowRoot().objectId()!=snapshot.index().logicalObject()
                || root.rowRoot().dataObjectId()!=snapshot.index().dataObject() || inventory.entries().get(1).opcodeMajor()!=13
                || inventory.entries().get(1).opcodeMinor()!=23)return null;
        var owned=new LinkedHashMap<ArchivedUndoInventory.Position,Owned>();
        var positions=new HashSet<ArchivedUndoInventory.Position>();for(var entry:inventory.entries())positions.add(entry.position());
        for(var input:inputs) {
            var header=input.header();
            input.visit(header,r->{for(int i=0;i<r.vectors().size();i++) {
                var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                if(positions.contains(at) && owned.putIfAbsent(at,new Owned(r,r.vectors().get(i)))!=null)throw new RedoException("LOB_TRUNCATE_DUPLICATE_SOURCE_POSITION");
            }});
            if(!header.equals(input.header()))throw new RedoException("LOB_TRUNCATE_SOURCE_CHANGED");
        }
        if(owned.size()!=inventory.entries().size())throw new RedoException("LOB_TRUNCATE_SOURCE_COVERAGE");
        var result=new LobTruncateProof();var roles=new LinkedHashMap<Long,FrozenHeapBaseline.ObjectRole>();var types=new HashMap<Long,Long>();
        roles.put(snapshot.heap().logicalObject(),snapshot.heap());types.put(snapshot.heap().logicalObject(),2L);
        roles.put(snapshot.index().logicalObject(),snapshot.index());types.put(snapshot.index().logicalObject(),1L);
        for(var lob:snapshot.lobs()){roles.put(lob.lob().logicalObject(),lob.lob());types.put(lob.lob().logicalObject(),21L);}
        var baseline=snapshot.dictionaryBaseline();var layout=baseline.layouts().get("OBJ$");
        var acquired=baseline.rows().stream().filter(r->r.role().equals("OBJ$") && r.target()==snapshot.heap().logicalObject()).findFirst().orElseThrow();
        long owner=baseline.number(acquired,"OWNER#");
        var objects=new LinkedHashMap<Long,DictionaryObjectUndoPair>();var indexes=new ArrayList<DictionaryDataObjectIndexPair>();
        for(var item:owned.values()) {
            var h=UndoHeader.decode(item.undo());
            if(h.objectId()==18) {
                var row=forward(item,11,5);var pair=DictionaryObjectUndoPair.bind(item.record(),row,layout.role(),37);
                if(!pair.physical().undo().identity().equals(h))throw new RedoException("LOB_TRUNCATE_OBJECT_OWNER");
                long object=number(pair.physical().forward().columns().get(0));
                if(!roles.containsKey(object) || objects.putIfAbsent(object,pair)!=null)throw new RedoException("LOB_TRUNCATE_OBJECT_SCOPE");
            } else if(h.objectId()==39) {
                var pair=DictionaryDataObjectIndexPair.bind(item.record(),forward(item,10,-1));
                if(!pair.physical().undo().identity().equals(h))throw new RedoException("LOB_TRUNCATE_INDEX_OWNER");indexes.add(pair);
            }
        }
        if(objects.size()!=roles.size() || indexes.size()!=roles.size()*2)throw new RedoException("LOB_TRUNCATE_OBJECT_INDEX_COVERAGE");
        var data=new HashMap<Long,FrozenHeapBaseline.ObjectRole>();
        for(var target:roles.values()) {
            var object=objects.get(target.logicalObject());var p=object.physical().forward();
            var before=indexes.stream().filter(i->i.rowDba()==p.dba() && i.rowSlot()==p.slot() && i.physical().forward().kind()==IndexUndoPair.Kind.DELETE).toList();
            var after=indexes.stream().filter(i->i.rowDba()==p.dba() && i.rowSlot()==p.slot() && i.physical().forward().kind()==IndexUndoPair.Kind.INSERT).toList();
            if(before.size()!=1 || after.size()!=1)throw new RedoException("LOB_TRUNCATE_OBJECT_INDEX_ROW_COVERAGE");
            var next=DictionaryDataObjectIndexPair.requireGeneration(object,target,types.get(target.logicalObject()),owner,before.get(0),after.get(0));
            result.next.add(next);if(data.put(target.dataObject(),next)!=null)throw new RedoException("LOB_TRUNCATE_DATA_OBJECT_COLLISION");
        }
        var segments=new HashSet<Long>();var failures=new ArrayList<String>();boolean table=false,index=false;
        for(var entry:inventory.entries()) {
            var item=owned.get(entry.position());var h=entry.identity();
            if(!h.equals(UndoHeader.decode(item.undo())))throw new RedoException("LOB_TRUNCATE_ORIGINAL_CHANGED");
            if(h.equals(root.allocationBegin()))continue;
            if(h.objectId()==18) {
                var object=objects.values().stream().filter(p->p.physical().undo().identity().equals(h)).findFirst().orElseThrow();
                long target=number(object.physical().forward().columns().get(0));
                if(target==snapshot.heap().logicalObject() || target==snapshot.index().logicalObject()) {
                    var p=object.physical();requireAnchor(snapshot,"OBJ$",target,p.forward());
                    result.rows.put(h,new ArchivedDictionaryHistory.RowOperation(layout.role(),p.forward(),p.undo().inverse(),h));
                }
            } else if(h.objectId()==39) {
                if(indexes.stream().noneMatch(p->p.physical().undo().identity().equals(h)))throw new RedoException("LOB_TRUNCATE_INDEX_MISSING");
            } else if(h.objectId()==14 || h.objectId()==19 || h.objectId()==4) {
                String name=h.objectId()==14?"SEG$":h.objectId()==19?"IND$":"TAB$";
                int columns=h.objectId()==14?16:h.objectId()==19?36:baseline.layouts().get("TAB$").columns().size()-1;
                var role=new FrozenHeapBaseline.ObjectRole("SYS",name,h.objectId(),h.objectId()==14?8:2);
                var pair=ClusterUndoPair.bind(item.record(),forward(item,11,5),role,h.objectId()==14?2:h.objectId()==19?3:1,columns);
                if(!pair.identity().equals(h))throw new RedoException("LOB_TRUNCATE_CLUSTER_OWNER");
                var before=pair.inverse().row();var after=pair.forward().row();
                if(h.objectId()==14) {
                    boolean flags=before.columns().get(15).state()!=HeapRowPatch.State.ABSENT;
                    requireColumns(before,after,flags?Set.of(14,15):Set.of(14));long old=number(before.columns().get(14));var next=data.get(old);
                    if(next==null || number(after.columns().get(14))!=next.dataObject() || !segments.add(old))throw new RedoException("LOB_TRUNCATE_SEGMENT_GENERATION");
                    if(flags) {number(before.columns().get(15));number(after.columns().get(15));}
                    else if(snapshot.lobs().stream().noneMatch(l->l.lob().logicalObject()==next.logicalObject() && l.storage()==LobLocator.Storage.SECUREFILE))
                        throw new RedoException("LOB_TRUNCATE_SEGMENT_FLAGS_PROFILE");
                } else if(h.objectId()==19) {
                    requireColumns(before,after,Set.of(1));
                    if(index || number(before.columns().get(1))!=snapshot.index().dataObject()
                            || number(after.columns().get(1))!=data.get(snapshot.index().dataObject()).dataObject())throw new RedoException("LOB_TRUNCATE_PRIMARY_GENERATION");index=true;
                } else {
                    requireColumns(before,after,Set.of(0,4,35));requireAnchor(snapshot,"TAB$",snapshot.heap().logicalObject(),after);
                    if(table || number(before.columns().get(0))!=snapshot.heap().dataObject()
                            || number(after.columns().get(0))!=data.get(snapshot.heap().dataObject()).dataObject()
                            || before.columns().get(4).state()!=HeapRowPatch.State.NULL || after.columns().get(4).state()!=HeapRowPatch.State.NULL)throw new RedoException("LOB_TRUNCATE_TABLE_GENERATION");
                    OracleScalars.date(before.columns().get(35).bytes());OracleScalars.date(after.columns().get(35).bytes());table=true;
                    result.rows.put(h,new ArchivedDictionaryHistory.RowOperation(role,after,before,h));
                }
            } else {
                var next=data.get(h.dataObjectId());
                if(next==null)throw new RedoException("LOB_TRUNCATE_FOREIGN_STORAGE");
                try { LobGenerationStoragePair.require(item.record(),item.undo(),next.logicalObject(),h.dataObjectId(),next.dataObject(),types.get(next.logicalObject())==1?4:3); }
                catch(RedoException failure) { failures.add(failure.getMessage()+":"+entry.position()+":"+entry.opcodeMajor()+"."+entry.opcodeMinor());continue; }
            }
            if(!result.parsed.add(h))throw new RedoException("LOB_TRUNCATE_DUPLICATE_IDENTITY");
        }
        if(!failures.isEmpty())throw new RedoException("LOB_TRUNCATE_STORAGE_REFUSED:"+String.join(";",failures));
        if(!table || !index || segments.size()!=roles.size() || result.parsed.size()!=inventory.entries().size()-1)
            throw new RedoException("LOB_TRUNCATE_COMPLETE_DEPENDENCY_COVERAGE");
        return result;
    }
    private static RedoRecords.Vector forward(Owned item,int major,int minor)throws RedoException {
        var candidates=item.record().vectors().stream().filter(v->v.containerId()==item.undo().containerId() && v.opcodeMajor()==major && (minor<0 || v.opcodeMinor()==minor)).toList();
        if(candidates.size()!=1)throw new RedoException("LOB_TRUNCATE_FORWARD_COVERAGE");return candidates.get(0);
    }
    private static void requireColumns(HeapRowPatch.Patch before,HeapRowPatch.Patch after,Set<Integer> changed)throws RedoException {
        for(int i=0;i<before.columns().size();i++)if((before.columns().get(i).state()!=HeapRowPatch.State.ABSENT)!=changed.contains(i)
                || (after.columns().get(i).state()!=HeapRowPatch.State.ABSENT)!=changed.contains(i))throw new RedoException("LOB_TRUNCATE_COLUMN_SET");
    }
    private static long number(HeapRowPatch.Column cell)throws RedoException {
        if(cell.state()!=HeapRowPatch.State.VALUE)throw new RedoException("LOB_TRUNCATE_NUMBER_MISSING");
        try {long n=OracleScalars.number(cell.bytes()).longValueExact();if(n<0 || n>0xffffffffL)throw new RedoException("LOB_TRUNCATE_NUMBER_RANGE");return n;}
        catch(ArithmeticException e){throw new RedoException("LOB_TRUNCATE_NUMBER_INTEGER");}
    }
    private static void requireAnchor(FrozenHeapBaseline.Snapshot snapshot,String name,long target,HeapRowPatch.Patch row)throws RedoException {
        if(snapshot.dictionaryAnchors().stream().noneMatch(a->a.dictionary().name().equals(name) && a.targetObject()==target
                && a.key().dba()==row.dba() && a.key().slot()==row.slot()))throw new RedoException("LOB_TRUNCATE_ACQUIRED_ROW_ANCHOR");
    }
}
