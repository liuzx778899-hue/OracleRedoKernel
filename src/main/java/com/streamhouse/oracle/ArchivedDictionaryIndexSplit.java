package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Archive-owned observed I_COL3 split closure. Does not authorize logical dictionary or business events. */
public final class ArchivedDictionaryIndexSplit {
    public static final class Closed {
        private final TransactionLifecycle.ClosedIdentity identity;
        private final List<ArchivedUndoInventory.Entry> original;
        private final ArchivedUndoInventory.Position endPosition;
        private final ArchivedUndoInventory.Position allocationPosition;
        private final DictionaryIndexLeafRows before,newLeaf,oldLeaf;
        private final int pendingSlot;
        private Closed(Context c,int slot) {identity=c.end;original=c.inventory.entries();endPosition=c.endPosition;allocationPosition=c.allocationPosition;before=c.before;newLeaf=c.newLeaf;oldLeaf=c.oldLeaf;pendingSlot=slot;}
        public TransactionLifecycle.ClosedIdentity identity(){return identity;}
        public List<ArchivedUndoInventory.Entry> original(){return original;}
        public ArchivedUndoInventory.Position endPosition(){return endPosition;}
        public ArchivedUndoInventory.Position allocationPosition(){return allocationPosition;}
        public DictionaryIndexLeafRows before(){return before;}
        public DictionaryIndexLeafRows newLeaf(){return newLeaf;}
        public DictionaryIndexLeafRows oldLeaf(){return oldLeaf;}
        public int pendingSlot(){return pendingSlot;}
    }
    private static final class Context {
        final ArchivedUndoInventory inventory;
        final UndoHistory graph;
        final TransactionLifecycle lifecycle=new TransactionLifecycle();
        TransactionStart start;
        TransactionLifecycle.ClosedIdentity end;
        ArchivedUndoInventory.Position endPosition;
        ArchivedUndoInventory.Position allocationPosition;
        long allocationHead,allocationTail,allocationNeighbor,beginScn;
        int file;
        DictionaryIndexMaintenancePair lock,link,branch;
        DictionaryIndexLeafRows before,newLeaf,oldLeaf;
        int next;
        Context(ArchivedUndoInventory inventory){this.inventory=inventory;graph=new UndoHistory(inventory);}
    }
    public static List<Closed> read(List<Path> archives,Path baseline,String receipt)throws IOException {
        return read(RedoBatch.archives(archives),FrozenHeapBaseline.read(baseline,receipt));
    }
    public static List<Closed> readCaptured(List<CaptureJournal.Verified> captures,Path baseline,String receipt)throws IOException {
        return read(RedoBatch.captures(captures),FrozenHeapBaseline.read(baseline,receipt));
    }
    static List<Closed> read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        if(inputs.isEmpty() || snapshot.dictionaryBaseline()==null)throw new RedoException("MISSING_SPLIT_INPUT_OR_DICTIONARY_BASELINE");
        var scope=snapshot.dictionaryBaseline().indexes().stream().filter(x->x.role().name().equals("I_COL3")).findFirst().orElseThrow();
        var xids=new LinkedHashSet<HeapRowPatch.Xid>();var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);continuity.archive(header);sources.add(header);
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
                for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                    if(v.fields().size()<2 || v.fields().get(1).length<24)throw new RedoException("UNREADABLE_SPLIT_DISCOVERY_SCOPE");
                    byte[] b=v.fields().get(1);
                    if(u32(b,0)==scope.role().logicalObject() && b[16]==10 && b[17]==21)xids.add(UndoHeader.decode(v).xid());
                }
            });
            if(!header.equals(input.header()))throw new RedoException("SPLIT_DISCOVERY_SOURCE_CHANGED");
        }
        continuity.finish();
        if(snapshot.scn()<sources.get(0).firstScn() || snapshot.scn()>=sources.get(sources.size()-1).nextScn())throw new RedoException("SPLIT_BASELINE_OUTSIDE_INPUT_COVERAGE");
        var contexts=new ArrayList<Context>();
        for(var xid:xids) {
            var inventory=ArchivedUndoInventory.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),xid);
            if(inventory.entries().size()!=5 || inventory.controlRoot()!=null || !inventory.sources().equals(sources))throw new RedoException("UNSUPPORTED_SPLIT_ORIGINAL_COVERAGE");
            contexts.add(new Context(inventory));
        }
        for(var input:inputs) {
            var header=input.header();if(!sources.contains(header))throw new RedoException("SPLIT_REPLAY_SOURCE_CHANGED");
            for(var c:contexts)c.lifecycle.archive(header);
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var c:contexts)replay(header,r,c,scope.role(),scope.indexDba(),snapshot);
            });
            if(!header.equals(input.header()))throw new RedoException("SPLIT_REPLAY_SOURCE_CHANGED");
        }
        var result=new ArrayList<Closed>();for(var c:contexts){c.lifecycle.finish();result.add(close(c));}
        for(var input:inputs) {
            var header=input.header();if(!sources.contains(header))throw new RedoException("SPLIT_AUDIT_SOURCE_CHANGED");
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var c:contexts) {
                    var first=c.inventory.entries().get(0).position();
                    if(compare(header,r,first)<0 || compare(header,r,c.endPosition)>0
                            || c.inventory.entries().stream().anyMatch(e->sameRecord(e.position(),header,r)) || sameRecord(c.endPosition,header,r)
                            || sameRecord(c.allocationPosition,header,r))continue;
                    for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()
                            && (v.dba()==c.before.block() || v.dba()==c.newLeaf.block() || v.dba()==c.link.block() || v.dba()==c.branch.block()))
                        throw new RedoException("UNCLASSIFIED_SPLIT_PAGE_WRITE");
                }
            });
            if(!header.equals(input.header()))throw new RedoException("SPLIT_AUDIT_SOURCE_CHANGED");
        }
        return List.copyOf(result);
    }
    private static int compare(ArchivedRedo.Header h,RedoRecords.Record r,ArchivedUndoInventory.Position p) {
        int c=Long.compare(h.sequence(),p.archiveSequence());if(c!=0)return c;
        c=Long.compare(r.block(),p.block());return c!=0?c:Integer.compare(r.offset(),p.offset());
    }
    private static void replay(ArchivedRedo.Header header,RedoRecords.Record r,Context c,FrozenHeapBaseline.ObjectRole role,long segment,FrozenHeapBaseline.Snapshot snapshot)throws RedoException {
        boolean activeBefore=c.start!=null && c.end==null;boolean handled=false;
        var first=c.inventory.entries().get(0);boolean initial=sameRecord(first.position(),header,r);
        for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==2) {
            if(initial) {
                var s=TransactionStart.decode(v);if(!s.uba().equals(first.identity().uba()))continue;
                if(c.start!=null)throw new RedoException("AMBIGUOUS_SPLIT_BEGIN");c.lifecycle.begin(r,v);c.start=s;c.beginScn=r.scn();
            } else if(c.start!=null && c.end==null && v.dba()==c.start.segmentDba()) {
                if(v.fields().isEmpty() || v.fields().get(0).length!=32)throw new RedoException("UNREADABLE_ACTIVE_SPLIT_CONTROL");
                if(u16(v.fields().get(0),0)!=c.start.slot())continue;
                c.lifecycle.observeSlotControl(r,v);c.lifecycle.advance(r,v);
            }
        }
        if(c.next<c.inventory.entries().size()) {
            var e=c.inventory.entries().get(c.next);
            if(sameRecord(e.position(),header,r)) {
                handled=true;
                if(c.end!=null || r.scn()<=snapshot.scn() || r.vectors().size()!=(c.next<3?3:2))throw new RedoException("SPLIT_ORIGINAL_RECORD_COVERAGE");
                var undo=r.vectors().get(e.position().vector()-1);var h=UndoHeader.decode(undo);
                if(!h.equals(e.identity()) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0 || e.opcodeMajor()!=10 || e.opcodeMinor()!=21)
                    throw new RedoException("SPLIT_ORIGINAL_IDENTITY_OR_GENERATION_CONFLICT");
                var forwards=r.vectors().stream().filter(v->v.containerId()==undo.containerId() && v.opcodeMajor()==10).toList();
                if(forwards.size()!=1)throw new RedoException("SPLIT_TYPED_FORWARD_COVERAGE");var f=forwards.get(0);
                int expected=new int[]{6,9,8,11,15}[c.next];if(f.opcodeMinor()!=expected)throw new RedoException("UNSUPPORTED_SPLIT_NODE_ORDER");
                if(c.next==0)c.file=f.file();else if(f.file()!=c.file)throw new RedoException("SPLIT_FORWARD_FILE_CONFLICT");
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);int major=i==r.vectors().size()-1?10:5,minor=i==r.vectors().size()-1?expected:c.next<3 && i==0?2:1;
                    if(v.containerId()!=undo.containerId() || v.opcodeMajor()!=major || v.opcodeMinor()!=minor)throw new RedoException("SPLIT_UNCLASSIFIED_ORIGINAL_VECTOR");
                }
                c.lifecycle.undo(r,undo);c.graph.append(e);
                switch(c.next) {
                    case 0 -> c.lock=DictionaryIndexMaintenancePair.bind(r,f,role,segment);
                    case 1 -> {
                        currentR(f,h,1);if(f.fields().size()!=2 || f.fields().get(1).length!=1 || f.fields().get(1)[0]!=1)throw new RedoException("UNSUPPORTED_SPLIT_APPLY_ITL");
                        c.before=DictionaryIndexLeafRows.before(undo,role,segment);if(c.before.block()!=f.dba())throw new RedoException("SPLIT_BEFORE_BLOCK_CONFLICT");
                    }
                    case 2 -> {
                        if(c.allocationPosition==null)throw new RedoException("MISSING_SPLIT_FREELIST_CONTROL");
                        currentR(f,h,2);var u=undo.fields();
                        if(u.size()!=5 || u.get(3).length!=12 || u.get(3)[0]!=13 || u32(u.get(3),4)!=f.dba() || u32(u.get(3),8)!=segment
                                || !Arrays.equals(u.get(4),new byte[]{1,0}) || u.get(2).length!=68 || u.get(2)[0]!=5 || u.get(2)[1]!=0x0d)
                            throw new RedoException("UNSUPPORTED_SPLIT_NEW_LEAF_INVERSE");
                        for(int i=20;i<68;i++)if(u.get(2)[i]!=0)throw new RedoException("SPLIT_NEW_LEAF_INVERSE_NOT_EMPTY");
                        c.newLeaf=DictionaryIndexLeafRows.rebuilt(f);
                    }
                    case 3 -> c.link=DictionaryIndexMaintenancePair.bind(r,f,role,segment);
                    case 4 -> c.branch=DictionaryIndexMaintenancePair.bind(r,f,role,segment);
                }
                c.next++;
            }
        }
        if(!handled && c.start!=null && c.end==null && r.vectors().size()==2
                && r.vectors().get(0).opcodeMajor()==13 && r.vectors().get(0).opcodeMinor()==7 && r.vectors().get(0).dba()==segment) {
            var a=r.vectors().get(0);var b=r.vectors().get(1);
            if(c.before==null || c.newLeaf!=null || c.allocationPosition!=null || a.containerId()!=snapshot.source().containerId()
                    || b.containerId()!=a.containerId() || a.blockClass()!=4 || b.blockClass()!=1 || a.file()!=c.file || b.file()!=c.file
                    || b.opcodeMajor()!=13 || b.opcodeMinor()!=6 || b.dba()!=c.before.nextLink() || a.fields().size()!=1 || b.fields().size()!=1
                    || a.fields().get(0).length!=32 || b.fields().get(0).length!=12 || r.scn()<c.beginScn || r.scn()>=header.nextScn())
                throw new RedoException("UNSUPPORTED_SPLIT_FREELIST_CONTROL");
            byte[] af=a.fields().get(0),bf=b.fields().get(0);
            // Native LUPD_UNLBLK / LCLR. af[20..23] is opaque ccnt, never a transaction identity.
            if(u32(af,0)!=4 || u32(af,4)!=1 || u32(af,8)!=0 || u32(af,12)!=1 || u32(af,16)!=0
                    || u32(af,24)==0 || u32(af,28)==0 || u32(bf,0)!=1 || u32(bf,4)!=0 || u32(bf,8)!=0)
                throw new RedoException("UNSUPPORTED_SPLIT_FREELIST_FIELDS");
            c.allocationPosition=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),1);
            c.allocationHead=u32(af,24);c.allocationTail=u32(af,28);c.allocationNeighbor=b.dba();handled=true;
        }
        for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
            if(c.start!=null && c.end==null && v.opcodeMajor()==5 && v.opcodeMinor()==6)throw new RedoException("UNSUPPORTED_SPLIT_COMPENSATION");
            if(v.opcodeMajor()==5 && v.opcodeMinor()==4 && c.lifecycle.knowsEnd(r,v)) {
                handled=true;
                c.end=c.lifecycle.end(r,v);var graph=c.graph.close(TransactionEnd.undoTail(v));
                if(c.end.rolledBack() || c.next!=5 || graph.live().size()!=5 || r.vectors().size()!=2 || r.vectors().get(0)!=v)throw new RedoException("SPLIT_END_ORIGINAL_COVERAGE");
                var f=r.vectors().get(1);if(f.containerId()!=v.containerId() || f.file()!=c.file || f.fields().isEmpty() || f.fields().get(0).length!=0)throw new RedoException("MISSING_SPLIT_END_OLD_LEAF_REBUILD");
                c.oldLeaf=DictionaryIndexLeafRows.rebuilt(f);c.endPosition=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),2);
            }
        }
        if(activeBefore && !handled)for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
            boolean knownBlock=c.lock!=null && v.dba()==c.lock.block() || c.before!=null && v.dba()==c.before.nextLink()
                    || c.newLeaf!=null && v.dba()==c.newLeaf.block() || c.branch!=null && v.dba()==c.branch.block();
            if(knownBlock || v.opcodeMajor()==10 && currentXid(v,first.identity().xid()))throw new RedoException("UNCLASSIFIED_ACTIVE_SPLIT_FORWARD");
        }
    }
    private static boolean currentXid(RedoRecords.Vector v,HeapRowPatch.Xid xid) {
        if(v.fields().isEmpty())return false;byte[] b=v.fields().get(0);int at;
        if(b.length>=24 && (b[0]==1 || b[0]==17))at=8;
        else if(b.length>=44 && b[0]==5)at=20;
        else return false;
        return new HeapRowPatch.Xid(u16(b,at),u16(b,at+2),u32(b,at+4)).equals(xid);
    }
    private static boolean sameRecord(ArchivedUndoInventory.Position p,ArchivedRedo.Header h,RedoRecords.Record r){return p.archiveSequence()==h.sequence() && p.block()==r.block() && p.offset()==r.offset();}
    private static void currentR(RedoRecords.Vector f,UndoHeader h,int count)throws RedoException {
        if(f.fields().isEmpty())throw new RedoException("MISSING_SPLIT_CURRENT_R_ITL");byte[] b=f.fields().get(0);
        if(b.length!=20+24*count || b[0]!=5 || b[1]!=0x0d || (b[8]&255)!=count
                || !new HeapRowPatch.Xid(u16(b,20),u16(b,22),u32(b,24)).equals(h.xid())
                || !new HeapRowPatch.Uba(u32(b,28),u16(b,32),b[34]&255).equals(h.uba())
                || (u16(b,36)&0xfffe)!=0x4000)throw new RedoException("SPLIT_CURRENT_R_IDENTITY_CONFLICT");
        for(int i=44;i<b.length;i++)if(b[i]!=0)throw new RedoException("UNSUPPORTED_SPLIT_OTHER_R_ITL");
    }
    private static Closed close(Context c)throws RedoException {
        if(c.end==null || c.before==null || c.newLeaf==null || c.oldLeaf==null || c.allocationPosition==null)throw new RedoException("INCOMPLETE_SPLIT_CLOSURE");
        long old=c.before.block(),fresh=c.newLeaf.block(),neighbor=c.before.nextLink();
        if(new HashSet<>(List.of(old,fresh,neighbor,c.branch.block())).size()!=4 || neighbor==0
                || c.allocationHead!=fresh || c.allocationNeighbor!=neighbor || List.of(old,fresh,neighbor,c.branch.block(),c.lock.indexDba()).contains(c.allocationTail)
                || c.lock.block()!=old || c.oldLeaf.block()!=old || c.newLeaf.previousLink()!=old || c.newLeaf.nextLink()!=neighbor
                || c.oldLeaf.nextLink()!=fresh || c.link.block()!=neighbor || c.link.previousLink()!=old || c.link.nextLink()!=fresh
                || c.branch.nextLink()!=fresh || c.branch.separatorParent()!=c.newLeaf.rows().get(0).key().objectId()
                || c.oldLeaf.rows().get(c.oldLeaf.rows().size()-1).key().objectId()>=c.newLeaf.rows().get(0).key().objectId())
            throw new RedoException("SPLIT_LINK_BRANCH_OR_RANGE_CONFLICT");
        var remaining=new HashSet<String>();for(var row:c.before.rows())if(!remaining.add(row.rawHex()))throw new RedoException("SPLIT_DUPLICATE_BEFORE_ROW");
        int slot=-1;
        for(var image:List.of(c.oldLeaf,c.newLeaf))for(int i=0;i<image.rows().size();i++) {
            var row=image.rows().get(i);
            if(!remaining.remove(row.rawHex())) {
                if(slot!=-1 || image!=c.oldLeaf || row.flags()!=1 || row.itl()!=c.lock.itl())throw new RedoException("SPLIT_ROW_CONSERVATION_CONFLICT");slot=i;
            }
        }
        if(!remaining.isEmpty() || slot<0)throw new RedoException("SPLIT_ROW_CONSERVATION_CONFLICT");return new Closed(c,slot);
    }
}
