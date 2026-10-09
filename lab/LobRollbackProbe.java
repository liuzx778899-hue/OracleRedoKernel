package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;

/** Frozen fixture transaction discovery only; no baseline or event authorization. */
public class LobRollbackProbe {
    public static void main(String[] args)throws Exception {
        var captures=new ArrayList<CaptureJournal.Verified>();
        for(int i=1;i<args.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(args[i]),args[i+1]));
        var xids=new LinkedHashSet<HeapRowPatch.Xid>();var heaps=new ArrayList<HeapUndoPair>();var fills=new ArrayList<SecureFileLobUndoPair>();
        for(var capture:captures)for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.containerUid()!=677737149L)continue;
            for(var vector:record.vectors()) {
                if(vector.containerId()==3 && vector.opcodeMajor()==26 && vector.opcodeMinor()==2) {
                    var fill=SecureFileLobFill.decode(vector);
                    if(!fill.undo()) {
                        long object=switch(fill.lobId()) {
                            case "0000000100000034f6cb"->79341;
                            case "0000000100000034f6cc"->79343;
                            default->throw new RedoException("LOB_PROBE_OUTSIDE_SECUREFILE_FIXTURE");
                        };
                        fills.add(SecureFileLobUndoPair.bind(record,vector,object));
                    }
                }
                if(vector.containerId()==3 && vector.opcodeMajor()==11 && vector.opcodeMinor()>=2 && vector.opcodeMinor()<=5
                        && (vector.dba()==50365967 || vector.dba()==50365966 || vector.dba()==50366015 || vector.dba()==50366011)) {
                    var row=new HeapRowPatch().decodeLob(vector,3);
                    if(row.xtype()==1) {
                        heaps.add(HeapUndoPair.bindLob(record,vector,3));
                        if(record.scn()<=Long.parseLong(args[0]))System.out.println("EARLY_HEAP_ORIGINAL|"+record.block()+"|"+record.offset()+"|"+record.scn()+"|"+row.kind());
                    }
                }
                if(vector.containerId()!=3 || vector.opcodeMajor()!=5 || vector.opcodeMinor()!=1)continue;
                var fields=vector.fields();if(fields.size()<2 || fields.get(1).length<24)throw new RedoException("LOB_PROBE_UNREADABLE_UNDO");
                byte[] b=fields.get(1);long object=RedoBytes.u32(b,0);
                if((object==79334 || object==79340) && (RedoBytes.u16(b,20)&0x10)==0)xids.add(UndoHeader.decode(vector).xid());
            }
        }
        var bitmaps=new ArrayList<LobBitmapUndoPair>();var indexes=new ArrayList<IndexUndoPair>();
        for(var capture:captures)for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.containerUid()!=677737149L)continue;
            for(var vector:record.vectors()) {
                if(vector.containerId()!=3)continue;
                if(vector.opcodeMajor()==5 && vector.opcodeMinor()==1) {
                    var m=vector.fields().get(1);long object=RedoBytes.u32(m,0);
                    if(object==79335 || object==79337) {
                        var owner=UndoHeader.decode(vector);if(!xids.contains(owner.xid()))continue;
                        if(m[16]!=13 || m[17]!=23)throw new RedoException("LOB_PROBE_UNSUPPORTED_BASICFILE_UNDO");
                        bitmaps.add(LobBitmapUndoPair.bind(record,vector,owner.objectId(),owner.objectId()==79335 ? 50365977 : 50365993));
                    }
                }
                if(vector.opcodeMajor()==10 && vector.opcodeMinor()==2 && (vector.objectId()==79336 || vector.objectId()==79338)) {
                    var pair=IndexUndoPair.bindLob(record,vector,vector.objectId());
                    if(xids.contains(pair.undo().identity().xid()))indexes.add(pair);
                }
            }
        }
        int markers=0;var associations=new ArrayList<ArchivedCompensationAssociations>();
        var typedOwners=new java.util.HashSet<UndoHeader>();
        for(var pair:heaps)typedOwners.add(pair.undo().identity());
        for(var pair:fills)typedOwners.add(pair.owner());
        for(var pair:bitmaps)typedOwners.add(pair.owner());
        for(var pair:indexes)typedOwners.add(pair.undo().identity());
        int unboundOriginals=0;
        for(var xid:xids) {
            System.out.println("DISCOVERED|"+xid);
            var association=ArchivedCompensationAssociations.read(RedoBatch.captures(captures),677737149L,3,xid);markers+=association.entries().size();
            associations.add(association);
            System.out.println("TRANSACTION|"+xid+"|"+association.window().undo().entries().size()+"|"+association.entries().size());
            for(var entry:association.window().undo().entries())if(!typedOwners.contains(entry.identity())) {
                unboundOriginals++;
                System.out.println("UNTYPED_ORIGINAL|"+entry.position()+"|"+entry.identity().objectId()+"|"+entry.opcodeMajor()+"."+entry.opcodeMinor()+"|"+entry.identity().xid());
            }
        }
        int cancelled=0,fillCancelled=0,bitmapCancelled=0,indexCancelled=0,pending=0;var routes=new HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
        for(var capture:captures)for(var record:new RedoRecords().readCaptured(capture)) {
            if(record.containerUid()!=677737149L)continue;
            var source=RedoBatch.captures(java.util.List.of(capture)).get(0).header();
            for(int v=0;v<record.vectors().size();v++) {
                var marker=record.vectors().get(v);if(marker.containerId()!=3 || marker.opcodeMajor()!=5 || (marker.opcodeMinor()!=6 && marker.opcodeMinor()!=11))continue;
                var at=new ArchivedUndoInventory.Position(source.sequence(),record.block(),record.offset(),v+1);
                ArchivedCompensationAssociations.Entry route=null;
                for(var association:associations)if(association.entries().stream().anyMatch(e->e.position().equals(at))) {
                    var current=association.associate(source,record,marker,at);
                    if(route!=null && !route.equals(current))throw new RedoException("LOB_PROBE_ROUTE_CONFLICT");route=current;
                }
                if(route==null)continue;
                routes.put(at,route);var original=route.original();
                if(original.objectId()==79335 || original.objectId()==79337) {
                    var pair=LobBitmapUndoPair.matchCompensation(record,marker,bitmaps,original);bitmapCancelled++;
                    System.out.println(physicalRoute("BITMAPROLLBACK",at,route)+"|"+pair.bitmapDba()+"|"+pair.parentCompensation()+"|"+pair.parentDba()+"|"+java.util.HexFormat.of().formatHex(pair.inverse()));
                    continue;
                }
                if(original.objectId()==79336 || original.objectId()==79338) {
                    int matches=0;
                    for(var row:record.vectors())if(row.containerId()==3 && row.opcodeMajor()==10 && row.opcodeMinor()==3) {
                        var pair=IndexCompensation.matchLob(record,marker,row,indexes,original);matches++;indexCancelled++;
                        System.out.println(physicalRoute("INDEXROLLBACK",at,route)+"|"+pair.forward().block()+"|"+pair.forward().slot());
                    }
                    if(matches!=1)throw new RedoException("LOB_PROBE_INDEX_COMPENSATION_ROW_COUNT");continue;
                }
                if(original.objectId()==79341 || original.objectId()==79343) {
                    int matches=0;
                    for(var row:record.vectors())if(row.containerId()==3 && row.opcodeMajor()==26 && row.opcodeMinor()==2) {
                        var pair=SecureFileLobUndoPair.matchCompensation(record,marker,row,fills,original);matches++;fillCancelled++;
                        var owner=pair.owner();var inverse=pair.inverse();
                        System.out.println("SECUREROLLBACK|"+source.sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                owner.objectId()+"|"+owner.xid().usn()+"."+owner.xid().slot()+"."+owner.xid().sequence()+"|"+
                                owner.uba().dba()+"|"+owner.uba().sequence()+"|"+owner.uba().record()+"|FILL|"+
                                route.originalPosition().archiveSequence()+"|"+route.originalPosition().block()+"|"+route.originalPosition().offset()+"|"+route.originalPosition().vector()+"|"+
                                inverse.lobId()+"|"+inverse.fillFlags()+"|"+inverse.offset()+"|"+java.util.HexFormat.of().formatHex(inverse.payload()));
                    }
                    if(matches!=1)throw new RedoException("LOB_PROBE_SECURE_COMPENSATION_ROW_COUNT");continue;
                }
                if(original.objectId()!=79334 && original.objectId()!=79340) {
                    var c=UndoCompensation.decode(marker);
                    System.out.println("PENDING_PHYSICAL_ROUTE|"+at+"|"+original.objectId()+"|"+c.opcodeMajor()+"."+c.opcodeMinor());pending++;continue;
                }
                int matches=0;
                for(var row:record.vectors()) {
                    if(row.containerId()!=3 || row.opcodeMajor()!=11 || row.opcodeMinor()<2 || row.opcodeMinor()>5)continue;
                    var pair=HeapCompensation.matchLob(record,marker,row,heaps,original);matches++;cancelled++;
                    var owner=pair.undo().identity();
                    System.out.println("HEAPROLLBACK|"+source.sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                            owner.objectId()+"|"+owner.xid().usn()+"."+owner.xid().slot()+"."+owner.xid().sequence()+"|"+
                            owner.uba().dba()+"|"+owner.uba().sequence()+"|"+owner.uba().record()+"|"+pair.forward().kind()+"|"+
                            route.originalPosition().archiveSequence()+"|"+route.originalPosition().block()+"|"+route.originalPosition().offset()+"|"+route.originalPosition().vector());
                }
                if(matches!=1)throw new RedoException("LOB_PROBE_COMPENSATION_ROW_COUNT");
            }
        }
        for(var association:associations)association.finish();
        System.out.println("SUMMARY|transactions="+xids.size()+"|markers="+markers+"|uniqueRoutes="+routes.size()+"|heapOriginals="+heaps.size()+"|heapCancelled="+cancelled+"|secureOriginals="+fills.size()+"|secureCancelled="+fillCancelled+"|bitmapOriginals="+bitmaps.size()+"|bitmapCancelled="+bitmapCancelled+"|indexOriginals="+indexes.size()+"|indexCancelled="+indexCancelled+"|pendingPhysicalRoutes="+pending+"|untypedOriginals="+unboundOriginals+"|events=0|mode=PHYSICAL_LOB_COMPENSATION_ONLY");
    }
    private static String physicalRoute(String kind,ArchivedUndoInventory.Position at,ArchivedCompensationAssociations.Entry route) {
        var owner=route.original();var from=route.originalPosition();
        return kind+"|"+at.archiveSequence()+"|"+at.block()+"|"+at.offset()+"|"+at.vector()+"|"+owner.objectId()+"|"+
                owner.xid().usn()+"."+owner.xid().slot()+"."+owner.xid().sequence()+"|"+owner.uba().dba()+"|"+owner.uba().sequence()+"|"+owner.uba().record()+"|"+kind+"|"+
                from.archiveSequence()+"|"+from.block()+"|"+from.offset()+"|"+from.vector();
    }
}
