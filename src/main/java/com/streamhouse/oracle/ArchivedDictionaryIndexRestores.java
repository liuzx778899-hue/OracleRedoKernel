package com.streamhouse.oracle;

import java.io.IOException;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Input-owned split originals and the subsequent raw-slot restoration. Never accepts caller-supplied proofs. */
final class ArchivedDictionaryIndexRestores {
    final Map<ArchivedUndoInventory.Position,UndoHeader> maintenance=new HashMap<>();
    final Map<ArchivedUndoInventory.Position,DictionaryIndexUndoPair> restores=new HashMap<>();
    private ArchivedDictionaryIndexRestores() {}
    static ArchivedDictionaryIndexRestores read(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        var closed=ArchivedDictionaryIndexSplit.read(inputs,snapshot);var result=new ArchivedDictionaryIndexRestores();
        var scope=snapshot.dictionaryBaseline().indexes().stream().filter(x->x.role().name().equals("I_COL3")).findFirst().orElseThrow();
        var restored=new HashSet<ArchivedDictionaryIndexSplit.Closed>();
        for(var c:closed)for(var e:c.original())if(result.maintenance.putIfAbsent(e.position(),e.identity())!=null)
            throw new RedoException("SPLIT_ORIGINAL_OWNER_COLLISION");
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);
            input.visit(header,r->{
                if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var c:closed) {
                    if(restored.contains(c) || compare(header,r,c.endPosition())<=0)continue;
                    var candidates=r.vectors().stream().filter(v->v.containerId()==snapshot.source().containerId() && v.dba()==c.oldLeaf().block()).toList();
                    if(candidates.isEmpty())continue;
                    if(candidates.size()!=1 || r.scn()<c.identity().endRecordScn())throw new RedoException("SPLIT_SLOT_CHANGED_BEFORE_RESTORE");
                    var v=candidates.get(0);var pair=bind(r,v,c,scope.role(),scope.indexDba());
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),r.vectors().indexOf(v)+1);
                    if(result.restores.putIfAbsent(at,pair)!=null)throw new RedoException("SPLIT_RESTORE_OWNER_COLLISION");
                    restored.add(c);
                }
            });
            if(!header.equals(input.header()))throw new RedoException("SPLIT_RESTORE_SOURCE_CHANGED");
        }
        if(restored.size()!=closed.size())throw new RedoException("MISSING_SPLIT_SLOT_RESTORE");
        return result;
    }
    private static int compare(ArchivedRedo.Header h,RedoRecords.Record r,ArchivedUndoInventory.Position p) {
        int value=Long.compare(h.sequence(),p.archiveSequence());if(value!=0)return value;
        value=Long.compare(r.block(),p.block());return value!=0?value:Integer.compare(r.offset(),p.offset());
    }
    private static DictionaryIndexUndoPair bind(RedoRecords.Record r,RedoRecords.Vector v,ArchivedDictionaryIndexSplit.Closed c,
            FrozenHeapBaseline.ObjectRole role,long segment)throws RedoException {
        var fields=v.fields();
        if(!role.owner().equals("SYS") || !role.name().equals("I_COL3") || v.opcodeMajor()!=10 || v.opcodeMinor()!=5
                || fields.size()!=2 || fields.get(0).length!=24 || fields.get(1).length!=6 || v.blockClass()!=1 || v.file()!=c.file())
            throw new RedoException("UNSUPPORTED_DICTIONARY_SPLIT_RESTORE");
        byte[] ktb=fields.get(0),leaf=fields.get(1);
        if(ktb[0]!=1 || ktb[1]!=0x0d || leaf[0]==0 || leaf[1]!=0 || u16(leaf,2)!=c.pendingSlot())
            throw new RedoException("DICTIONARY_SPLIT_RESTORE_SLOT_OR_KTB_CONFLICT");
        var xid=new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
        var uba=new HeapRowPatch.Uba(u32(ktb,16),u16(ktb,20),ktb[22]&255);
        if(uba.dba()==0 || uba.record()==0)throw new RedoException("INVALID_DICTIONARY_RESTORE_UBA");
        IndexUndoPair.Inverse inverse=null;
        if(r.vectors().size()!=2 || r.vectors().get(1)!=v)throw new RedoException("UNCLASSIFIED_DICTIONARY_RESTORE_VECTOR");
        var undo=r.vectors().get(0);
        if(undo.containerId()!=v.containerId() || undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1 || undo.dba()!=uba.dba())
            throw new RedoException("MISSING_DICTIONARY_RESTORE_UNDO");
        inverse=IndexUndoPair.decodeDictionaryUndo(undo);var h=inverse.identity();
        var pending=c.oldLeaf().rows().get(c.pendingSlot());byte[] raw=HexFormat.of().parseHex(pending.rawHex());
        byte[] key=Arrays.copyOfRange(raw,8,raw.length),data=Arrays.copyOfRange(raw,2,8);
        if(!h.xid().equals(xid) || !h.uba().equals(uba) || h.objectId()!=role.logicalObject() || h.dataObjectId()!=role.dataObject()
                || h.tablespace()!=0 || inverse.indexId()!=segment || inverse.block()!=v.dba() || inverse.itl()!=(leaf[0]&255)
                || inverse.kind()!=IndexUndoPair.Kind.PURGE || inverse.flags()!=1 || inverse.sdc()!=0
                || !Arrays.equals(inverse.key(),key) || u16(leaf,4)!=raw.length+2 || pending.flags()!=1
                || !DictionaryIndexKey.decode(role.name(),key,data,"AL32UTF8").equals(pending.key()))
            throw new RedoException("DICTIONARY_SPLIT_RESTORE_IDENTITY_KEY_OR_SIZE_CONFLICT");
        var f=new IndexUndoPair.Forward(IndexUndoPair.Kind.RESTORE,v.dba(),leaf[0]&255,c.pendingSlot(),u16(leaf,4),xid,uba,key,data);
        return new DictionaryIndexUndoPair(role,segment,new IndexUndoPair(r.containerUid(),v.containerId(),f,inverse),pending.key());
    }
}
