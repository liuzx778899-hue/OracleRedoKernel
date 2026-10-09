package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.List;

/** Frozen raw input only. SQL and native traces are external comparators. */
public final class LobEventsProbe {
    public static void main(String[] args)throws Exception {
        var captures=List.of(new CaptureJournal().readRawClosed(Path.of(args[0]),args[1]));
        var records=new RedoRecords().readCaptured(captures.get(0));var batches=new java.util.LinkedHashMap<LobLocator.Storage,CapturedLobBatch.Result>();
        for(var storage:LobLocator.Storage.values()) {
            var scope=storage==LobLocator.Storage.BASICFILE
                ?new CapturedLobTransactions.Scope(677737149,3,79334,79339,storage,79335,79336,50365977,79337,79338,50365993)
                :new CapturedLobTransactions.Scope(677737149,3,79340,79345,storage,79341,79342,0,79343,79344,0);
            var batch=CapturedLobBatch.read(captures,scope);
            batches.put(storage,batch);
        }
        for(var entry:batches.entrySet()) {
            var storage=entry.getKey();var batch=entry.getValue();
            System.out.println("BATCH|"+storage+"|"+batch.transactions().size()+"|"+batch.events().size()+"|"+batch.sha256());
            System.out.println("MAINTENANCE|"+storage+"|"+batch.maintenanceTransactions()+"|"+batch.maintenanceOriginals());
            for(var receipt:batch.maintenance()) {
                var t=receipt.identity();var x=t.xid();String xid=x.usn()+"."+x.slot()+"."+x.sequence();
                System.out.println("METADATA|"+storage+"|"+xid+"|"+t.beginRecordScn()+"|"+t.endRecordScn()+"|"+receipt.originals().size());
                for(var p:receipt.originals()) {
                    var r=records.stream().filter(a->a.block()==p.block() && a.offset()==p.offset()).findFirst().orElseThrow();
                    var v=r.vectors().get(p.vector()-1);var m=v.fields().get(1);var b=v.fields().get(0);
                    System.out.println("METADATAORIGINAL|"+p.archiveSequence()+"|"+p.block()+"|"+p.offset()+"|"+p.vector()+"|"+RedoBytes.u32(m,0)+"|"+RedoBytes.u32(m,4)+"|"+xid
                            +"|"+v.dba()+"|"+RedoBytes.u16(b,16)+"|"+(b[18]&255)+"|"+(m[16]&255)+"."+(m[17]&255)+"|"+RedoBytes.u32(m,12)+"|"+(m[19]&255));
                }
            }
            for(var event:batch.events()) {
                System.out.println("EVENT|"+storage+"|"+event.transaction().endRecordScn()+"|"+event.operation()+"|"+event.rowKey().dba()+"|"+event.rowKey().slot()+"|"+event.eventId()+"|"+event.payloadSha256());
                for(var image:List.of(event.before(),event.after())) {
                    System.out.print("ROW|"+(image==event.before()?"BEFORE":"AFTER"));
                    for(var cell:image)System.out.print("|"+cell.state()+"|"+cell.hex());
                    System.out.println();
                }
            }
        }
    }
}
