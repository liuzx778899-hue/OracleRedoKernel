package com.streamhouse.oracle;

import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Discovery only: follow raw OBJ$ identities of already selected SYS work back to their creating owners. */
final class DictionaryObjectOwnerClosure {
    private record RowPosition(long logical,long data,long dba,int slot) {}
    private record Candidate(HeapRowPatch.Xid owner,long scn,Set<Long> objects,Set<RowPosition> rows) {}
    static void extend(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,Set<HeapRowPatch.Xid> owners)throws java.io.IOException {
        extend(inputs,snapshot,owners,Set.of());
    }
    static void extend(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,Set<HeapRowPatch.Xid> owners,Set<HeapRowPatch.Xid> closed)throws java.io.IOException {
        if(snapshot.lobs().isEmpty() || owners.isEmpty())return;
        var baseline=snapshot.dictionaryBaseline();
        var heap=baseline.rows().stream().filter(r->r.role().equals("OBJ$") && r.target()==snapshot.heap().logicalObject()).findFirst().orElseThrow();
        long ownerId=baseline.number(heap,"OWNER#");boolean[] replacement={false};
        var candidates=new ArrayList<Candidate>();
        for(var input:inputs) {
            var header=input.header();
            input.visit(header,r->{
                if(r.scn()<=snapshot.scn() || r.containerUid()!=snapshot.source().containerUid())return;
                for(var undo:r.vectors()) {
                    if(undo.opcodeMajor()!=5 || undo.opcodeMinor()!=1 || undo.containerId()!=snapshot.source().containerId()
                            || undo.fields().size()<4 || undo.fields().get(0).length!=20 || undo.fields().get(1).length<12
                            || !Set.of(4L,18L,21L).contains(u32(undo.fields().get(1),0)) || u32(undo.fields().get(1),8)!=0)continue;
                    byte[] b=undo.fields().get(0);var owner=new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));
                    if(closed.contains(owner))continue;
                    long logical=u32(undo.fields().get(1),0),data=u32(undo.fields().get(1),4);
                    if(data!=(logical==18?18:2))continue;
                    var ids=new HashSet<Long>();var positions=new HashSet<RowPosition>();
                    if(logical==18)collect(undo.fields().subList(2,undo.fields().size()),ids);
                    position(undo.fields().subList(2,undo.fields().size()),logical,data,positions);
                    long block=undo.fields().get(3).length>=4?u32(undo.fields().get(3),0):0;
                    var originalUba=new HeapRowPatch.Uba(undo.dba(),u16(b,16),b[18]&255);
                    for(var row:r.vectors())if(row.containerId()==undo.containerId() && row.dba()==block && row.opcodeMajor()==11 && row.opcodeMinor()>=2 && row.opcodeMinor()<=5
                            && HeapUndoPair.referencesOriginal(row,originalUba)) {
                        if(logical==18)collect(row.fields(),ids);
                        position(row.fields(),logical,data,positions);
                        var f=row.fields();
                        if(logical==18 && row.opcodeMinor()==2 && f.size()>=9 && f.get(1).length==52 && f.get(2).length>0 && f.get(4).length>0 && f.get(5).length>0 && f.get(8).length>0
                                && OracleScalars.number(f.get(2)).compareTo(java.math.BigDecimal.valueOf(snapshot.heap().logicalObject()))!=0
                                && OracleScalars.number(f.get(4)).compareTo(java.math.BigDecimal.valueOf(ownerId))==0
                                && OracleScalars.number(f.get(8)).compareTo(java.math.BigDecimal.valueOf(2))==0
                                && OracleScalars.varchar2(f.get(5),"AL32UTF8").equals(snapshot.heap().name()))replacement[0]=true;
                    }
                    if(!ids.isEmpty() || !positions.isEmpty())candidates.add(new Candidate(owner,r.scn(),Set.copyOf(ids),Set.copyOf(positions)));
                }
            });
            if(!header.equals(input.header()))throw new RedoException("DICTIONARY_OBJECT_DISCOVERY_SOURCE_CHANGED");
        }
        if(!replacement[0])return;
        long ceiling=candidates.stream().filter(c->owners.contains(c.owner())).mapToLong(Candidate::scn).max().orElse(0);
        var objects=new HashSet<Long>();objects.add(snapshot.heap().logicalObject());objects.add(snapshot.index().logicalObject());
        var positions=new HashSet<RowPosition>();
        boolean changed;
        do {
            changed=false;
            for(var candidate:candidates) {
                if(candidate.scn()>ceiling)continue;
                if(owners.contains(candidate.owner())) {changed|=objects.addAll(candidate.objects());changed|=positions.addAll(candidate.rows());}
                if(candidate.objects().stream().anyMatch(objects::contains) || candidate.rows().stream().anyMatch(positions::contains))changed|=owners.add(candidate.owner());
            }
        }while(changed);
    }
    private static void position(List<byte[]> fields,long logical,long data,Set<RowPosition> rows) {
        if(fields.size()<2)return;byte[] k=fields.get(1);if(k.length<20)return;
        int op=k[10]&255,slot;
        if(op==2 && k.length>=49)slot=u16(k,42);
        else if(op==5 && k.length>=29)slot=u16(k,20);
        else if((op==3 || op==4) && k.length==20)slot=u16(k,16);
        else return;
        if(logical!=18) {
            int table=op==2?k[44]&255:op==5?k[19]&255:k[18]&255;
            if(table!=(logical==4?1:5))return;
        }
        rows.add(new RowPosition(logical,data,u32(k,0),slot));
    }
    private static void collect(List<byte[]> fields,Set<Long> objects)throws RedoException {
        if(fields.size()<2 || fields.get(1).length<20)return;
        byte[] k=fields.get(1);int op=k[10]&255;byte[] raw=null;
        if(op==2 && k.length==52 && fields.size()>2)raw=fields.get(2);
        else if(op==5 && (k.length==29 || k.length==30) && fields.size()>2) {
            int count=k[23]&255;byte[] map=fields.get(2);
            if(map.length!=count*2 || fields.size()!=count+3)return;
            for(int i=0;i<count;i++)if(u16(map,i*2)==0)raw=fields.get(i+3);
        }
        if(raw==null || raw.length==0)return;
        try {long object=OracleScalars.number(raw).longValueExact();if(object>1 && object<=0xffffffffL)objects.add(object);}
        catch(ArithmeticException invalid){throw new RedoException("DICTIONARY_OBJECT_DISCOVERY_ID_INVALID");}
    }
}
