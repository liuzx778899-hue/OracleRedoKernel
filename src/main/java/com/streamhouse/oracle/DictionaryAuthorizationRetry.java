package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Raw120 OBJAUTH$ failed insert is physically cancelled before the same owner locks the existing row. */
final class DictionaryAuthorizationRetry {
    static void headUpdate(RedoRecords.Record record,RedoRecords.Vector control,UndoHeader root,
            UndoHistory graph,TransactionLifecycle lifecycle)throws RedoException {
        var update=UndoHeadUpdate.decode(control);
        if(!record.vectors().contains(control) || record.vectors().stream().filter(v->v.containerId()==control.containerId()
                && v.opcodeMajor()==5 && v.opcodeMinor()==2 && v.dba()==control.dba() && v.fields().size()==1
                && v.fields().get(0).length==32 && u16(v.fields().get(0),16)==2
                && u16(v.fields().get(0),0)==update.slot() && u32(v.fields().get(0),8)==update.uba().dba()
                && u16(v.fields().get(0),12)==update.uba().sequence() && (v.fields().get(0)[14]&255)==update.uba().record()).count()!=1)
            fail("HEAD_CONTROL_COVERAGE");
        if(root.objectId()!=61 || root.dataObjectId()!=61 || root.tablespace()!=0 || root.beginsTransaction()
                || !update.uba().equals(root.uba()) || update.slot()!=root.xid().slot()
                || !graph.awaitsCompensatedBeginRestart(root))fail("HEAD_RESTART_SCOPE");
        var originals=record.vectors().stream().filter(v->v.containerId()==control.containerId() && v.opcodeMajor()==5
                && v.opcodeMinor()==1 && v.dba()==root.uba().dba() && v.fields().get(0).length==20
                && u16(v.fields().get(0),16)==root.uba().sequence() && (v.fields().get(0)[18]&255)==root.uba().record()).toList();
        var rows=record.vectors().stream().filter(v->v.containerId()==control.containerId() && v.opcodeMajor()==11
                && v.opcodeMinor()==4 && HeapUndoPair.referencesOriginal(v,root.uba())).toList();
        if(originals.size()!=1 || !UndoHeader.decode(originals.get(0)).equals(root) || rows.size()!=1)fail("HEAD_RESTART_PAIR");
        var pair=DictionaryRedefinitionAuxiliary.bind(record,rows.get(0),61).physical();
        if(!pair.undo().identity().equals(root))fail("HEAD_RESTART_IDENTITY");
        lifecycle.requireActive(record,pair);
    }
    static Set<HeapRowPatch.Uba> roots(RedoBatch inputs,long uid,long container,HeapRowPatch.Xid xid)throws java.io.IOException {
        var lifecycle=new TransactionLifecycle();HeapUndoPair[] inserted={null};boolean[] cancelled={false};
        long[] segment={0};
        var roots=new HashSet<HeapRowPatch.Uba>();
        for(var input:inputs) {
            var header=input.header();lifecycle.archive(header);
            input.visit(header,r->{
                if(r.containerUid()!=uid)return;
                for(var v:r.vectors()) {
                    if(v.containerId()!=container)continue;
                    if(v.opcodeMajor()==5 && v.opcodeMinor()==11 && inserted[0]!=null && !cancelled[0]) {
                        var marker=UndoCompensation.decode(v);
                        if(marker.objectId()!=61 || marker.dataObjectId()!=61 || marker.tablespace()!=0 || marker.slot()!=xid.slot() || marker.dba()!=segment[0])continue;
                        var rollback=r.vectors().stream().filter(row->row.containerId()==container && row.opcodeMajor()==11 && row.opcodeMinor()==3).toList();
                        if(rollback.size()!=1)fail("COMPENSATION_ROW_COUNT");
                        var matched=HeapCompensation.matchBegin(r,v,rollback.get(0),List.of(inserted[0]),8,lifecycle);
                        if(!matched.undo().identity().equals(inserted[0].undo().identity()))fail("COMPENSATION_OWNER");
                        cancelled[0]=true;
                    }
                    if(v.opcodeMajor()!=5 || v.opcodeMinor()!=1 || v.fields().get(0).length!=20)continue;
                    byte[] b=v.fields().get(0);
                    if(!new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12)).equals(xid))continue;
                    var h=UndoHeader.decode(v);if(h.objectId()!=61 || h.dataObjectId()!=61 || h.tablespace()!=0)continue;
                    var rows=r.vectors().stream().filter(row->row.containerId()==container && row.opcodeMajor()==11
                            && (row.opcodeMinor()==2 || row.opcodeMinor()==4)).toList();
                    if(rows.size()!=1)fail("FORWARD_ROW_COUNT");
                    var pair=DictionaryRedefinitionAuxiliary.bind(r,rows.get(0),61).physical();
                    if(!pair.undo().identity().equals(h))fail("UNDO_IDENTITY");
                    if(h.beginsTransaction()) {
                        if(inserted[0]!=null || pair.forward().kind()!=HeapRowPatch.Kind.INSERT || pair.forward().extent().storedColumns()!=5)fail("INSERT_ROOT");
                        var starts=r.vectors().stream().filter(start->start.containerId()==container && start.opcodeMajor()==5 && start.opcodeMinor()==2).toList();
                        if(starts.size()!=1)fail("START_COUNT");lifecycle.begin(r,starts.get(0));lifecycle.requireActive(r,pair);segment[0]=starts.get(0).dba();inserted[0]=pair;
                    } else if(u32(v.fields().get(1),12)==0 && v.fields().get(1)[19]==0) {
                        if(!cancelled[0] || pair.forward().kind()!=HeapRowPatch.Kind.LOCK
                                || pair.forward().dba()!=inserted[0].forward().dba() || pair.forward().slot()==inserted[0].forward().slot()
                                || !roots.add(h.uba()))fail("RESTART_ROOT");
                        lifecycle.requireActive(r,pair);
                    }
                }
            });
            if(!header.equals(input.header()))fail("SOURCE_CHANGED");
        }
        if(!cancelled[0] || roots.size()!=1)fail("INCOMPLETE");return Set.copyOf(roots);
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_AUTHORIZATION_RETRY_"+detail);}
}
