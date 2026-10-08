package com.streamhouse.oracle;

import java.io.IOException;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Input-owned, closed cluster-key extension. Value rows are unchanged; no logical DDL is inferred. */
final class ArchivedDictionaryClusterMaintenance {
    record Closed(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Entry> original,
                  long parent,long oldBlock,int oldSlot,long newBlock,int newSlot) {
        Closed {original=List.copyOf(original);}
    }
    private static final class Context {
        final ArchivedUndoInventory inventory;
        final TransactionLifecycle lifecycle=new TransactionLifecycle();
        final UndoHistory graph;
        final List<RedoRecords.Record> records=new ArrayList<>();
        TransactionStart start;
        TransactionLifecycle.ClosedIdentity end;
        int next;
        Context(ArchivedUndoInventory inventory){this.inventory=inventory;graph=new UndoHistory(inventory);}
    }
    static List<Closed> read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        var role=snapshot.dictionaryBaseline().layouts().get("COL$").role();
        var xids=new LinkedHashSet<HeapRowPatch.Xid>();var sources=new ArrayList<ArchivedRedo.Header>();
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);sources.add(header);
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid() || r.scn()<=snapshot.scn())return;
                for(var row:r.vectors())if(row.containerId()==snapshot.source().containerId() && row.opcodeMajor()==11 && row.opcodeMinor()==10) {
                    // Only the same-record SYS identity selects this observed maintenance profile.
                    if(r.vectors().size()!=2 || r.vectors().get(0).opcodeMajor()!=5 || r.vectors().get(0).opcodeMinor()!=1)continue;
                    var h=UndoHeader.decode(r.vectors().get(0));
                    if(h.objectId()==role.logicalObject())xids.add(DictionaryClusterLinkPair.bind(r,row,role).identity().xid());
                }
            });
            if(!header.equals(input.header()))throw new RedoException("CLUSTER_MAINTENANCE_SOURCE_CHANGED");
        }
        var contexts=new ArrayList<Context>();
        for(var xid:xids) {
            var inventory=ArchivedUndoInventory.read(inputs,snapshot.source().containerUid(),snapshot.source().containerId(),xid);
            if(inventory.entries().size()!=5 || inventory.controlRoot()!=null || !inventory.sources().equals(sources))
                throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_ORIGINALS");
            contexts.add(new Context(inventory));
        }
        for(var input:inputs) {
            var header=input.header();for(var c:contexts)c.lifecycle.archive(header);
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var c:contexts) {
                    var first=c.inventory.entries().get(0);
                    boolean initial=same(first.position(),header,r);
                    for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==2) {
                        if(initial) {
                            var start=TransactionStart.decode(v);
                            if(!start.uba().equals(first.identity().uba()))continue;
                            if(c.start!=null || !start.bindBegin(r).equals(first.identity().xid()))throw new RedoException("CLUSTER_MAINTENANCE_BEGIN_CONFLICT");
                            c.lifecycle.begin(r,v);c.start=start;
                        } else if(c.start!=null && c.end==null && v.dba()==c.start.segmentDba()) {
                            c.lifecycle.observeSlotControl(r,v);
                            if(v.fields().isEmpty() || v.fields().get(0).length!=32)throw new RedoException("UNREADABLE_CLUSTER_MAINTENANCE_CONTROL");
                            if(u16(v.fields().get(0),0)==c.start.slot())c.lifecycle.advance(r,v);
                        }
                    }
                    if(c.next<5 && same(c.inventory.entries().get(c.next).position(),header,r)) {
                        var e=c.inventory.entries().get(c.next);var v=r.vectors().get(e.position().vector()-1);
                        if(c.end!=null || r.scn()<=snapshot.scn() || !UndoHeader.decode(v).equals(e.identity()))
                            throw new RedoException("CLUSTER_MAINTENANCE_ORIGINAL_CHANGED");
                        c.lifecycle.undo(r,v);c.graph.append(e);c.records.add(r);c.next++;
                    }
                    for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
                        if(v.opcodeMajor()==5 && v.opcodeMinor()==6 && c.start!=null && c.end==null)
                            throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_COMPENSATION");
                        if(v.opcodeMajor()==5 && v.opcodeMinor()==4 && c.lifecycle.knowsEnd(r,v)) {
                            c.end=c.lifecycle.end(r,v);var graph=c.graph.close(TransactionEnd.undoTail(v));
                            if(c.end.rolledBack() || c.next!=5 || graph.live().size()!=5 || r.vectors().size()!=1)
                                throw new RedoException("CLUSTER_MAINTENANCE_END_COVERAGE");
                        }
                    }
                }
            });
            if(!header.equals(input.header()))throw new RedoException("CLUSTER_MAINTENANCE_SOURCE_CHANGED");
        }
        var result=new ArrayList<Closed>();
        for(var c:contexts) {c.lifecycle.finish();result.add(close(c,snapshot,role));}
        return List.copyOf(result);
    }
    private static boolean same(ArchivedUndoInventory.Position p,ArchivedRedo.Header h,RedoRecords.Record r) {
        return p.archiveSequence()==h.sequence() && p.block()==r.block() && p.offset()==r.offset();
    }
    private static Closed close(Context c,FrozenHeapBaseline.Snapshot snapshot,FrozenHeapBaseline.ObjectRole role)throws RedoException {
        if(c.end==null || c.records.size()!=5)throw new RedoException("MISSING_CLUSTER_MAINTENANCE_COMMIT");
        var first=c.records.get(0);var last=c.records.get(4);
        var a=index(first,true);var z=index(last,false);
        var b=DictionaryClusterLinkPair.bind(c.records.get(2),c.records.get(2).vectors().get(1),role);
        var f=DictionaryClusterLinkPair.bind(c.records.get(3),c.records.get(3).vectors().get(1),role);
        long clusterSegment=u32(c.records.get(2).vectors().get(1).fields().get(1),4);
        if(clusterSegment!=u32(c.records.get(3).vectors().get(1).fields().get(1),4)
                || clusterSegment!=u32(c.records.get(1).vectors().get(1).fields().get(1),4))
            throw new RedoException("CLUSTER_MAINTENANCE_SEGMENT_CONFLICT");
        if(!a.identity.xid().equals(c.end.xid()) || !z.identity.xid().equals(c.end.xid())
                || a.identity.objectId()!=z.identity.objectId() || a.identity.dataObjectId()!=z.identity.dataObjectId()
                || a.block!=z.block || a.segment!=z.segment || a.slot!=z.slot || a.itl!=z.itl || !Arrays.equals(a.key,z.key)
                || b.forward().direction()!=2 || f.forward().direction()!=1 || !b.identity().xid().equals(c.end.xid()) || !f.identity().xid().equals(c.end.xid())
                || b.forward().block()!=f.forward().block() || b.forward().slot()!=f.forward().slot()
                || b.forward().linkBlock()!=f.forward().linkBlock() || b.forward().linkSlot()!=f.forward().linkSlot()
                || b.inverse().linkBlock()!=b.forward().block() || b.inverse().linkSlot()!=b.forward().slot()
                || f.inverse().linkBlock()!=b.forward().block() || f.inverse().linkSlot()!=b.forward().slot()
                || b.inverse().lock()!=0 || f.inverse().lock()!=1)
            throw new RedoException("CLUSTER_MAINTENANCE_TOPOLOGY_CONFLICT");
        var old=b.inverse();var fresh=b.forward();
        if(pointerBlock(z.before)!=old.block() || pointerSlot(z.before)!=old.slot() || z.before[6]!=1 || z.before[7]!=0
                || pointerBlock(z.after)!=fresh.linkBlock() || pointerSlot(z.after)!=fresh.linkSlot() || z.after[6]!=2 || z.after[7]!=0)
            throw new RedoException("CLUSTER_MAINTENANCE_INDEX_POINTER_CONFLICT");
        long parent=OracleScalars.number(Arrays.copyOfRange(a.key,1,a.key.length)).longValueExact();
        if(parent!=snapshot.heap().logicalObject())throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_PARENT");
        keyInsert(c.records.get(1),role,c.end.xid(),a.key,fresh,old);
        // Cluster links/keydata only: retained TAB$/COL$ value rows must still occupy the old block,
        // never either cluster-key slot. No value-row movement is accepted by this profile.
        for(var row:snapshot.dictionaryBaseline().rows())if(row.role().equals("TAB$") || row.role().equals("COL$")) {
            if(row.target()!=parent || row.key().dba()!=old.block() || row.key().slot()==old.slot()
                    || row.key().dba()==fresh.linkBlock() && row.key().slot()==fresh.linkSlot())
                throw new RedoException("CLUSTER_MAINTENANCE_BASELINE_VALUE_CONFLICT");
        }
        return new Closed(c.end,c.inventory.entries(),parent,old.block(),old.slot(),fresh.linkBlock(),fresh.linkSlot());
    }
    private record Index(UndoHeader identity,long segment,long block,int slot,int itl,byte[] key,byte[] before,byte[] after) {}
    private static Index index(RedoRecords.Record r,boolean first)throws RedoException {
        if(r.vectors().size()!=(first?3:2))throw new RedoException("CLUSTER_MAINTENANCE_INDEX_RECORD_COVERAGE");
        var undo=r.vectors().get(first?1:0);var row=r.vectors().get(first?2:1);var h=UndoHeader.decode(undo);
        var u=undo.fields();var v=row.fields();byte[] m=u.get(1);
        if(row.opcodeMajor()!=10 || row.opcodeMinor()!=18 || row.containerId()!=undo.containerId() || row.blockClass()!=1
                || h.tablespace()!=0 || h.objectId()==0 || h.objectId()!=h.dataObjectId() || h.beginsTransaction()!=first || m[16]!=10 || m[17]!=22
                || u.size()!=(first?5:6) || v.size()!=(first?2:3) || u.get(3).length!=20 || v.get(1).length!=6)
            throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_INDEX");
        current(row,h);byte[] leaf=u.get(3),key=u.get(4),forward=v.get(1);
        if(leaf[0]!=18 || leaf[1]==0 || leaf[2]!=(first?1:5) || u32(leaf,4)==0 || u32(leaf,8)!=row.dba()
                // Native seq80/82 SDC differs (2/1); it is not the current XID, extent or pointer authority.
                || forward[0]!=leaf[1] || forward[1]!=0 || key.length<2 || (key[0]&255)!=key.length-1
                || u16(forward,4)!=8+key.length+4 || !first && (u.get(5).length!=8 || v.get(2).length!=8))
            throw new RedoException("CLUSTER_MAINTENANCE_INDEX_KEY_OR_EXTENT");
        return new Index(h,u32(leaf,4),row.dba(),u16(forward,2),leaf[1]&255,key,first?null:u.get(5),first?null:v.get(2));
    }
    private static void current(RedoRecords.Vector row,UndoHeader h)throws RedoException {
        byte[] k=row.fields().get(0);int at;
        if(k.length==24 && k[0]==1 && k[1]==13) {
            if(!new HeapRowPatch.Xid(u16(k,8),u16(k,10),u32(k,12)).equals(h.xid()))throw new RedoException("CLUSTER_MAINTENANCE_CURRENT_XID");at=16;
        } else if(k.length==16 && k[0]==2 && k[1]==13)at=8;
        else throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_CURRENT_KTB");
        if(!new HeapRowPatch.Uba(u32(k,at),u16(k,at+4),k[at+6]&255).equals(h.uba()))throw new RedoException("CLUSTER_MAINTENANCE_CURRENT_UBA");
    }
    private static long pointerBlock(byte[] b){return (long)(b[0]&255)<<24 | (long)(b[1]&255)<<16 | (long)(b[2]&255)<<8 | b[3]&255;}
    private static int pointerSlot(byte[] b){return (b[4]&255)<<8 | b[5]&255;}
    private static void keyInsert(RedoRecords.Record r,FrozenHeapBaseline.ObjectRole role,HeapRowPatch.Xid xid,byte[] key,
                                  DictionaryClusterLinkPair.Link fresh,DictionaryClusterLinkPair.Link old)throws RedoException {
        if(r.vectors().size()!=2)throw new RedoException("CLUSTER_MAINTENANCE_KEY_RECORD_COVERAGE");
        var undo=r.vectors().get(0);var row=r.vectors().get(1);var h=UndoHeader.decode(undo);var u=undo.fields();var v=row.fields();
        if(!h.xid().equals(xid) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject() || h.tablespace()!=0
                || h.beginsTransaction() || u.get(1)[16]!=11 || u.get(1)[17]!=1
                || row.containerId()!=undo.containerId() || row.opcodeMajor()!=11 || row.opcodeMinor()!=2
                || u.size()!=4 || v.size()!=3 || u.get(3).length!=20 || v.get(1).length!=49)
            throw new RedoException("UNSUPPORTED_CLUSTER_MAINTENANCE_KEY_INSERT");
        current(row,h);byte[] k=v.get(1),prior=u.get(3);
        if(row.dba()!=fresh.linkBlock() || u32(k,0)!=row.dba() || u32(prior,0)!=row.dba() || u32(k,4)!=u32(prior,4)
                || k[10]!=2 || prior[10]!=3 || k[11]!=1 || prior[11]!=1 || k[16]!=(byte)0xac || k[17]!=1 || k[18]!=1
                || u16(k,40)!=24 || u16(k,42)!=fresh.linkSlot() || k[44]!=0 || k[45]!=0
                || u16(prior,16)!=fresh.linkSlot() || prior[18]!=0 || !Arrays.equals(v.get(2),Arrays.copyOfRange(key,1,key.length))
                || u32(k,20)!=old.block() || u16(k,24)!=old.slot() || u32(k,28)!=old.block() || u16(k,32)!=old.slot())
            throw new RedoException("CLUSTER_MAINTENANCE_KEY_OR_BACKLINK_CONFLICT");
    }
}
