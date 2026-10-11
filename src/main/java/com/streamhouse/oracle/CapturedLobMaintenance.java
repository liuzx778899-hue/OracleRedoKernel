package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.CapturedLobTransactions.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed LOB maintenance transactions. Complete identities/links, never business events or baseline coverage. */
final class CapturedLobMaintenance {
    record Receipt(TransactionLifecycle.ClosedIdentity identity,List<ArchivedUndoInventory.Position> originals,long object,long extent,long blocks) {
        Receipt {originals=List.copyOf(originals);}
    }
    static List<Receipt> read(CapturedLobTransactions input)throws IOException {
        return read(input,null);
    }
    static List<Receipt> read(CapturedLobTransactions input,ArchivedDictionaryHistory.Timeline history)throws IOException {
        var scope=input.scope();var candidates=new HashSet<HeapRowPatch.Xid>();var business=new HashSet<HeapRowPatch.Xid>();
        var objects=scope.objects();
        for(var t:input.transactions())business.add(t.identity().xid());
        if(history!=null)for(var commit:history.commits())business.add(commit.identity().xid());
        for(var at:input.records())for(var v:at.record().vectors())if(v.containerId()==scope.container() && v.opcodeMajor()==5 && v.opcodeMinor()==1) {
            var f=v.fields();if(f.size()<2 || f.get(0).length!=20 || f.get(1).length<24)throw new RedoException("LOB_MAINTENANCE_UNREADABLE_SCOPE");
            var m=f.get(1);long object=u32(m,0),data=u32(m,4);
            if(!objects.contains(object) && !objects.contains(data))continue;
            var xid=xid(v);if(!business.contains(xid))candidates.add(xid);
        }
        var receipts=new ArrayList<Receipt>();
        for(var xid:candidates) {
            if(scope.storage()==LobLocator.Storage.SECUREFILE) {receipts.add(secureMetadata(input,xid));continue;}
            var state=new TransactionLifecycle();ArchivedRedo.Header source=null;var originals=new ArrayList<ArchivedUndoInventory.Position>();
            var seen=new HashSet<HeapRowPatch.Uba>();HeapRowPatch.Uba head=null;TransactionLifecycle.ClosedIdentity closed=null;
            long object=0,extent=0,segment=0;int stage=0;
            for(var at:input.records()) {
                if(!at.source().equals(source)) {state.archive(at.source());source=at.source();}
                var record=at.record();
                for(int i=0;i<record.vectors().size();i++) {
                    var v=record.vectors().get(i);if(v.containerId()!=scope.container() || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || !xid(v).equals(xid))continue;
                    requireScn(at);
                    var f=v.fields();var m=f.get(1);var b=f.get(0);int major=m[16]&255,minor=m[17]&255;
                    var uba=new HeapRowPatch.Uba(v.dba(),u16(b,16),b[18]&255);
                    if(closed!=null || !seen.add(uba) || uba.record()==0 || (m[18]&255)!=xid.slot())throw new RedoException("LOB_MAINTENANCE_ORIGINAL_IDENTITY");
                    if(stage==0) {
                        var owner=UndoHeader.decode(v);
                        if(!owner.beginsTransaction() || major!=5 || minor!=7 || owner.objectId()!=0 || owner.dataObjectId()!=0
                                || u32(m,12)!=0 || m[19]!=0 || u16(m,22)!=1)throw new RedoException("LOB_MAINTENANCE_BEGIN_PROFILE");
                        int count=0;for(var control:record.vectors())if(control.containerId()==scope.container() && control.opcodeMajor()==5 && control.opcodeMinor()==2
                                && control.fields().size()==2 && TransactionStart.decode(control).uba().equals(uba)) {state.begin(record,control);count++;}
                        if(count!=1)throw new RedoException("LOB_MAINTENANCE_BEGIN_COUNT");
                    } else {
                        if(u16(m,22)!=(xid.sequence()&0xffff) || !new HeapRowPatch.Uba(uba.dba(),uba.sequence(),stage==1?uba.record()-1:m[19]&255).equals(head)
                                || u32(m,12)!=0 || (stage==1 && m[19]!=0))throw new RedoException("LOB_MAINTENANCE_PREDECESSOR");
                        if(stage==1) {
                            if(major!=22 || minor!=3 || f.size()!=3 || f.get(2).length!=20 || !Arrays.equals(f.get(2),new byte[20])
                                    || u16(m,20)!=0x80 || u32(m,0)!=1 || u32(m,4)!=0xffffffffL)
                                throw new RedoException("LOB_MAINTENANCE_SPACE_HEADER");
                            int count=0;for(var control:record.vectors())if(control.containerId()==scope.container() && control.opcodeMajor()==5 && control.opcodeMinor()==2
                                    && UndoHeadUpdate.decode(control).uba().equals(uba))count++;
                            if(count!=1)throw new RedoException("LOB_MAINTENANCE_ROOT_HEAD");
                        } else if(stage==2) {
                            object=u32(m,0);long parent=scope.bitmapFor(object);
                            if(major!=13 || minor!=25 || parent==0 || u32(m,4)!=object || f.size()!=4 || f.get(2).length!=8 || f.get(3).length!=20
                                    || u32(f.get(2),0)!=parent || u32(f.get(2),4)!=9 || u32(f.get(3),0)!=3 || u32(f.get(3),4)!=1
                                    || u32(f.get(3),12)!=1 || u32(f.get(3),16)!=0)throw new RedoException("LOB_MAINTENANCE_PARENT_UNDO");
                            extent=u32(f.get(3),8);var forward=only(record,scope,13,24,object,parent);var bytes=forward.fields().get(0);
                            if(forward.fields().size()!=1 || bytes.length!=20 || u32(bytes,0)!=2 || u32(bytes,4)!=1 || u32(bytes,8)!=extent
                                    || u32(bytes,12)!=1 || u32(bytes,16)!=1)throw new RedoException("LOB_MAINTENANCE_PARENT_PAIR");
                        } else if(stage==3) {
                            if(major!=13 || minor!=23 || u32(m,0)!=object || u32(m,4)!=object || f.size()!=4 || f.get(2).length!=20 || f.get(3).length!=12
                                    || u32(f.get(2),0)!=extent || u32(f.get(2),4)!=8 || u32(f.get(2),8)!=0 || u32(f.get(2),12)!=9 || u32(f.get(2),16)!=2
                                    || u32(f.get(3),0)!=8 || u32(f.get(3),4)!=extent || u32(f.get(3),8)!=1)
                                throw new RedoException("LOB_MAINTENANCE_FORMAT_UNDO");
                        } else if(stage==4) {
                            if(major!=13 || minor!=29 || u32(m,0)!=object || u32(m,4)!=object || f.size()!=4 || f.get(2).length!=20 || f.get(3).length!=20
                                    || u32(f.get(2),4)!=0 || u32(f.get(2),8)!=2 || u32(f.get(2),12)!=4 || u32(f.get(2),16)!=7
                                    || u32(f.get(3),0)!=1 || u32(f.get(3),12)!=0 || u32(f.get(3),16)!=0)
                                throw new RedoException("LOB_MAINTENANCE_SEGMENT_UNDO");
                            segment=u32(f.get(2),0);var forward=only(record,scope,13,28,object,segment);var bytes=forward.fields().get(0);
                            if(forward.fields().size()!=1 || bytes.length!=20 || u32(bytes,0)!=1 || u32(bytes,4)!=extent
                                    || u32(bytes,8)!=u32(f.get(3),8) || u32(bytes,12)!=0 || u32(bytes,16)!=0)
                                throw new RedoException("LOB_MAINTENANCE_SEGMENT_PAIR");
                        } else if(stage==5) {
                            if(major!=14 || minor!=5 || u32(m,0)!=1 || u32(m,4)!=object || u16(m,20)!=0x80)throw new RedoException("LOB_MAINTENANCE_EXTENT_IDENTITY");
                            var forward=only(record,scope,14,4,object,segment);
                            var pair=ExtentMapPair.bindLob(record,v,forward);
                            if(pair.segment()!=segment || pair.extent()!=extent || pair.blocks()!=8)throw new RedoException("LOB_MAINTENANCE_EXTENT_PAIR");
                        } else if(stage==6 || stage==7) {
                            long logical=stage==6?93:14,data=stage==6?10:8;
                            if(major!=11 || minor!=1 || u32(m,0)!=logical || u32(m,4)!=data)throw new RedoException("LOB_MAINTENANCE_STATISTICS_SCOPE");
                            var forwards=record.vectors().stream().filter(x->x.containerId()==scope.container() && x.opcodeMajor()==11 && x.opcodeMinor()==5 && x.objectId()==data).toList();
                            if(forwards.size()!=1)throw new RedoException("LOB_MAINTENANCE_STATISTICS_COUNT");
                            statistics(v,forwards.get(0),xid,uba,stage==6?7:16,stage==6?2:15);
                        } else throw new RedoException("LOB_MAINTENANCE_UNEXPECTED_ORIGINAL");
                    }
                    head=uba;stage++;originals.add(at.position(i));
                }
                if(head==null || closed!=null)continue;
                for(var v:record.vectors())if(v.containerId()==scope.container() && v.opcodeMajor()==5) {
                    if(v.opcodeMinor()==2)state.observeSlotControl(record,v);
                    if(v.opcodeMinor()==6 || v.opcodeMinor()==11)throw new RedoException("LOB_MAINTENANCE_COMPENSATION_UNSUPPORTED");
                    if(v.opcodeMinor()==4 && state.knowsEnd(record,v)) {
                        closed=state.end(record,v);
                        if(stage!=8 || closed.rolledBack() || (v.fields().size()==2?TransactionEnd.decode(v).flags()!=0:!TransactionEnd.undoTail(v).equals(head)))
                            throw new RedoException("LOB_MAINTENANCE_END_OR_TAIL");
                    }
                }
            }
            state.finish();if(closed==null)throw new RedoException("LOB_MAINTENANCE_INCOMPLETE");receipts.add(new Receipt(closed,originals,object,extent,8));
        }
        receipts.sort(java.util.Comparator.comparingLong(r->r.identity().endRecordScn()));return List.copyOf(receipts);
    }
    private static Receipt secureMetadata(CapturedLobTransactions input,HeapRowPatch.Xid xid)throws IOException {
        var scope=input.scope();var state=new TransactionLifecycle();ArchivedRedo.Header source=null;
        var originals=new ArrayList<ArchivedUndoInventory.Position>();var seen=new HashSet<HeapRowPatch.Uba>();HeapRowPatch.Uba head=null;
        long object=0,extent=0,blocks=0;TransactionLifecycle.ClosedIdentity closed=null;
        // Native-matched SHB redo may follow its end in physical RBA while carrying an earlier SCN.
        // Keep original positions; closure still requires every original and the exact referenced tail.
        var ordered=new ArrayList<>(input.records());ordered.sort(java.util.Comparator.comparingLong((At a)->a.record().scn())
                .thenComparingInt(a->a.record().subScn()).thenComparing((a,b)->compare(a.position(0),b.position(0))));
        for(var at:ordered) {
            if(!at.source().equals(source)) {state.archive(at.source());source=at.source();}var r=at.record();
            for(int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if(v.containerId()!=scope.container() || v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || !xid(v).equals(xid))continue;
                requireScn(at);
                var f=v.fields();var m=f.get(1);var b=f.get(0);var uba=new HeapRowPatch.Uba(v.dba(),u16(b,16),b[18]&255);
                if(closed!=null || seen.contains(uba) || uba.record()==0 || (m[18]&255)!=xid.slot())throw new RedoException("SECUREFILE_METADATA_ORIGINAL_IDENTITY");
                if(head==null) {
                    var owner=UndoHeader.decode(v);
                    if(!owner.beginsTransaction() || u32(m,12)!=0 || m[19]!=0 || u16(m,22)!=1)throw new RedoException("SECUREFILE_METADATA_BEGIN");
                    int count=0;for(var control:r.vectors())if(control.containerId()==scope.container() && control.opcodeMajor()==5 && control.opcodeMinor()==2
                            && control.fields().size()==2 && TransactionStart.decode(control).uba().equals(uba)) {state.begin(r,control);count++;}
                    if(count!=1)throw new RedoException("SECUREFILE_METADATA_BEGIN_COUNT");
                } else {
                    int flags=u16(m,20);if(flags!=0 && !(flags==0x80 && m[17]==3 && m[16]==22 || flags==0x80 && m[16]==14 && m[17]==5))
                        throw new RedoException("SECUREFILE_METADATA_UNKNOWN_UNDO_FLAGS");
                    if(u16(m,22)!=(xid.sequence()&0xffff))throw new RedoException("SECUREFILE_METADATA_LINK_SCOPE");
                    // PUB format/control roots have rci=0; retain separate roots, never invent an edge to the preceding allocation.
                    int rci=m[19]&255;
                    if(u32(m,12)!=0) {
                        if(rci!=0 || u32(m,12)!=head.dba())throw new RedoException("SECUREFILE_METADATA_BLOCK_PREDECESSOR");
                        int count=0;for(var control:r.vectors())if(control.containerId()==scope.container() && control.opcodeMajor()==5 && control.opcodeMinor()==2
                                && (control.fields().size()==1 || control.fields().size()==2) && u16(control.fields().get(0),16)==10 && UndoBlockAdvance.decode(control).uba().equals(uba)) {
                            state.advance(r,control);count++;
                        }
                        if(count!=1)throw new RedoException("SECUREFILE_METADATA_ADVANCE_COUNT");
                    } else if(rci!=0 && !seen.contains(new HeapRowPatch.Uba(uba.dba(),uba.sequence(),rci)))throw new RedoException("SECUREFILE_METADATA_PREDECESSOR_MISSING");
                }
                int major=m[16]&255,minor=m[17]&255;
                if(major==5 && minor==7) {
                    if(!originals.isEmpty() || u32(m,0)!=0 || u32(m,4)!=0 || f.size()!=2)throw new RedoException("SECUREFILE_METADATA_ALLOCATION_BEGIN");
                } else if(major==22 && minor==3) {
                    if(u32(m,0)!=1 || u32(m,4)!=0xffffffffL || f.size()!=3 || !Arrays.equals(f.get(2),new byte[20]))throw new RedoException("SECUREFILE_METADATA_SPACE_HEADER");
                } else if(major==14 && minor==5) {
                    object=u32(m,4);if(u32(m,0)!=1 || !scope.isLob(object) || u16(m,20)!=0x80)throw new RedoException("SECUREFILE_METADATA_EXTENT_SCOPE");
                    var pair=secureExtent(r,v,scope,object);extent=pair[0];blocks=pair[1];
                } else if(major==13 && (minor==50 || minor==54 || minor==56)) {
                    long target=u32(m,0);if(!scope.isLob(target) || u32(m,4)!=target || u16(m,20)!=0 || f.size()<4)
                        throw new RedoException("SECUREFILE_METADATA_CONTROL_SCOPE");
                    var metadata=f.get(2);var body=f.get(3);long mode=u32(body,0);
                    if(minor==50 && metadata.length==12 && body.length==40 && u16(body,0)==15 && f.size()==4) {
                        requireHbbReservationPair(r,v,target);
                    }
                    boolean profile=minor==50 && metadata.length==12 && u32(metadata,8)==8 &&
                            ((mode==1 && body.length==76 && f.size()==4) || (mode==2 && body.length==80 && f.size()==4)
                             || (mode==4 && body.length==20 && ((f.size()==5 && f.get(4).length==8)
                                 || (f.size()==7 && f.get(4).length==8 && f.get(5).length==76 && f.get(6).length==76)))
                             || (mode==8 && body.length==332 && f.size()==4)
                             || (u16(body,0)==15 && body.length==40 && f.size()==4))
                        || minor==54 && metadata.length==8 && u32(metadata,4)==10 && f.size()==4 &&
                            ((mode==8 && body.length==88) || (mode==14 && body.length==5288))
                        || minor==56 && metadata.length==8 && u32(metadata,4)==4 && f.size()==4 &&
                            ((mode==3 && body.length==44) || (mode==65543 && body.length==8));
                    if(!profile || u32(metadata,0)==0)throw new RedoException("UNSUPPORTED_SECUREFILE_METADATA_CONTROL_PROFILE");
                } else if(major==11 && minor==1) {
                    long logical=u32(m,0),data=u32(m,4);boolean metric=logical==93 && data==10,segment=logical==14 && data==8;
                    if(!metric && !segment)throw new RedoException("SECUREFILE_METADATA_SYS_SCOPE");
                    if(f.size()==4 && segment) {
                        var kdo=f.get(3);if(kdo.length!=20 || kdo[10]!=3 || kdo[11]!=1 || f.get(2).length!=32 || f.get(2)[0]!=4)
                            throw new RedoException("SECUREFILE_METADATA_SYS_DELETE_PROFILE");
                        var rows=r.vectors().stream().filter(x->x.containerId()==scope.container() && x.opcodeMajor()==11 && x.opcodeMinor()==2 && x.objectId()==8).toList();
                        if(rows.size()!=1 || rows.get(0).fields().size()!=18 || rows.get(0).fields().get(1).length!=50 || u32(kdo,0)!=rows.get(0).dba())
                            throw new RedoException("SECUREFILE_METADATA_SYS_INSERT_PAIR");
                        var fs=rows.get(0).fields();var k=fs.get(1);
                        if(k[10]!=2 || k[11]!=1 || k[16]!=0x6c || k[18]!=16 || u16(k,42)!=u16(kdo,16) || k[44]!=kdo[18])
                            throw new RedoException("SECUREFILE_METADATA_SYS_INSERT_ROW");
                        int size=20;for(int c=0;c<16;c++) {var cell=fs.get(c+2);size+=cell.length;
                            if(((k[45+c/8]>>>(c%8))&1)!=(cell.length==0?1:0))throw new RedoException("SECUREFILE_METADATA_SYS_INSERT_NULL_BITMAP");}
                        if(size!=u16(k,40))throw new RedoException("SECUREFILE_METADATA_SYS_INSERT_SIZE");
                        var ktb=rows.get(0).fields().get(0);
                        if(ktb.length<24 || !new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12)).equals(xid)
                                || !new HeapRowPatch.Uba(u32(ktb,16),u16(ktb,20),ktb[22]&255).equals(uba))throw new RedoException("SECUREFILE_METADATA_SYS_DELETE_OWNER");
                    } else {
                        var rows=r.vectors().stream().filter(x->x.containerId()==scope.container() && x.opcodeMajor()==11 && x.opcodeMinor()==5 && x.objectId()==data).toList();
                        if(rows.size()!=1)throw new RedoException("SECUREFILE_METADATA_SYS_UPDATE_COUNT");statistics(v,rows.get(0),xid,uba,metric?7:16,metric?2:15);
                    }
                } else throw new RedoException("UNSUPPORTED_SECUREFILE_METADATA_ORIGINAL");
                seen.add(uba);head=uba;originals.add(at.position(i));
            }
            if(head==null || closed!=null)continue;
            for(var v:r.vectors())if(v.containerId()==scope.container() && v.opcodeMajor()==5) {
                if(v.opcodeMinor()==2)state.observeSlotControl(r,v);
                if(v.opcodeMinor()==6 || v.opcodeMinor()==11)throw new RedoException("SECUREFILE_METADATA_COMPENSATION_UNSUPPORTED");
                if(v.opcodeMinor()==4 && state.knowsEnd(r,v)) {
                    closed=state.end(r,v);var end=TransactionEnd.decode(v);
                    if(closed.rolledBack() || (end.flags()==0x10?false:v.fields().size()==2?end.flags()!=0:!TransactionEnd.undoTail(v).equals(head)))
                        throw new RedoException("SECUREFILE_METADATA_END_OR_TAIL:"+xid+":"+head+":"+TransactionEnd.decode(v)+":"+java.util.HexFormat.of().formatHex(v.fields().get(1)));
                }
            }
        }
        state.finish();if(closed==null)throw new RedoException("SECUREFILE_METADATA_INCOMPLETE");return new Receipt(closed,originals,object,extent,blocks);
    }
    private static long[] secureExtent(RedoRecords.Record r,RedoRecords.Vector undo,Scope scope,long object)throws RedoException {
        var f=undo.fields();if(f.size()!=5 || f.get(2).length!=24 || f.get(3).length!=16 || f.get(4).length!=20)throw new RedoException("SECUREFILE_METADATA_EXTENT_LAYOUT");
        var map=f.get(2);long segment=u32(map,0),extent=u32(map,16),blocks=u32(map,20);var forward=only(r,scope,14,4,object,segment);var fs=forward.fields();
        if(fs.size()!=2 || fs.get(0).length!=16 || fs.get(1).length!=20 || u32(map,4)!=4 || u32(map,8)!=segment || u32(map,12)!=2
                || (blocks!=8 && blocks!=128) || f.get(3)[0]!=2 || u32(f.get(3),4)!=u32(fs.get(0),12) || u32(f.get(3),8)!=0 || u32(f.get(3),12)!=0
                || u32(fs.get(0),0)!=1 || u32(fs.get(0),4)!=extent || u32(fs.get(0),8)!=blocks
                || f.get(4)[0]!=5 || fs.get(1)[0]!=5 || u32(fs.get(1),4)!=u32(f.get(4),4)+1
                || u32(fs.get(1),8)!=u32(f.get(4),8)+blocks || u32(f.get(4),12)!=0 || u32(f.get(4),16)!=0 || u32(fs.get(1),12)!=0 || u32(fs.get(1),16)!=0)
            throw new RedoException("SECUREFILE_METADATA_EXTENT_PAIR");return new long[]{extent,blocks};
    }
    /** Seq104/native Reserve Chunks for FSG; unused dump high bits are not length or identity authority. */
    static void requireHbbReservationPair(RedoRecords.Record record,RedoRecords.Vector undo,long object)throws RedoException {
        var f=undo.fields();var metadata=f.get(2);var before=f.get(3);var owner=UndoHeader.decode(undo);
        if(f.size()!=4 || metadata.length!=12 || u32(metadata,8)!=8 || u32(metadata,4)==0 || before.length!=40
                || u16(before,0)!=15 || owner.objectId()!=object || owner.dataObjectId()!=object)
            throw new RedoException("SECUREFILE_HBB_RESERVATION_SCOPE");
        var rows=record.vectors().stream().filter(v->v.containerId()==undo.containerId() && v.opcodeMajor()==13 && v.opcodeMinor()==49
                && v.objectId()==object && v.dba()==u32(metadata,0)).toList();
        if(rows.size()!=1)throw new RedoException("SECUREFILE_HBB_RESERVATION_PAIR_COUNT");
        var row=rows.get(0);if(row.blockClass()!=8 || row.fields().size()!=1 || row.fields().get(0).length!=40)
            throw new RedoException("SECUREFILE_HBB_RESERVATION_FORWARD_PROFILE");
        var after=row.fields().get(0);
        if(u32(after,0)!=15)throw new RedoException("SECUREFILE_HBB_RESERVATION_FORWARD_PROFILE");
        for(var bytes:List.of(before,after))for(int at:new int[]{4,12,28,32,36})if(u32(bytes,at)!=0)
            throw new RedoException("SECUREFILE_HBB_RESERVATION_RESERVED");
        boolean reserve=u32(before,8)==0 && u32(before,16)==0 && u32(after,8)>0 && u32(after,16)==4
                && u32(before,20)==0 && u32(before,24)==0 && u32(after,20)==0 && u32(after,24)==0;
        boolean release=u32(before,8)>0 && u32(before,16)==4 && u32(after,8)==0 && u32(after,16)==0
                && u32(before,20)==0 && u32(before,24)==0 && u16(after,20)==owner.xid().usn()
                && u16(after,22)==owner.xid().slot() && u32(after,24)==owner.xid().sequence();
        if(!reserve && !release)throw new RedoException("SECUREFILE_HBB_RESERVATION_INVERSE");
    }
    private static HeapRowPatch.Xid xid(RedoRecords.Vector v)throws RedoException {
        if(v.fields().isEmpty() || v.fields().get(0).length!=20)throw new RedoException("LOB_MAINTENANCE_UNREADABLE_IDENTITY");
        var b=v.fields().get(0);return new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));
    }
    private static void requireScn(At at)throws RedoException {
        if(at.record().scn()<at.source().firstScn() || at.record().scn()>=at.source().nextScn())throw new RedoException("LOB_MAINTENANCE_SCN_OUTSIDE_SOURCE");
    }
    private static void statistics(RedoRecords.Vector undo,RedoRecords.Vector forward,HeapRowPatch.Xid xid,HeapRowPatch.Uba uba,int stored,int column)throws RedoException {
        var u=undo.fields();var f=forward.fields();
        if(u.size()!=6 || f.size()!=4 || u.get(2).length!=32 || u.get(2)[0]!=4 || u.get(2)[1]!=0x0d
                || f.get(0).length!=70 || f.get(0)[0]!=0x11 || f.get(0)[1]!=0x0d || u.get(3).length!=29 || f.get(1).length!=29
                || u.get(4).length!=2 || f.get(2).length!=2 || u16(u.get(4),0)!=column || !Arrays.equals(u.get(4),f.get(2)))
            throw new RedoException("LOB_MAINTENANCE_STATISTICS_PROFILE");
        var ktb=f.get(0);var before=u.get(3);var after=f.get(1);
        if(!new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12)).equals(xid)
                || !new HeapRowPatch.Uba(u32(ktb,16),u16(ktb,20),ktb[22]&255).equals(uba)
                || u32(after,0)!=forward.dba() || after[10]!=5 || after[11]!=1 || after[16]!=0x6c || after[17]!=2 || before[17]!=0
                || (after[22]&255)!=stored || after[23]!=1 || u16(after,24)!=0 || u16(after,26)!=0 || after[28]!=0)
            throw new RedoException("LOB_MAINTENANCE_STATISTICS_IDENTITY");
        byte[] normalized=after.clone();normalized[17]=0;
        if(!Arrays.equals(before,normalized))throw new RedoException("LOB_MAINTENANCE_STATISTICS_ROW_POSITION");
        OracleScalars.number(u.get(5));OracleScalars.number(f.get(3));
    }
    private static RedoRecords.Vector only(RedoRecords.Record r,Scope scope,int major,int minor,long object,long dba)throws RedoException {
        var matches=r.vectors().stream().filter(v->v.containerId()==scope.container() && v.opcodeMajor()==major && v.opcodeMinor()==minor && v.objectId()==object && v.dba()==dba).toList();
        if(matches.size()!=1)throw new RedoException("LOB_MAINTENANCE_FORWARD_COUNT");return matches.get(0);
    }
}
