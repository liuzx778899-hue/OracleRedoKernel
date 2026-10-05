package com.streamhouse.oracle;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryObjectUndoPairTest {
    final FrozenHeapBaseline.ObjectRole role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
    List<RedoRecords.Record> records()throws Exception {
        String text;try(var in=getClass().getResourceAsStream("/dictionary-object-row-profiles.txt")){text=new String(in.readAllBytes(),StandardCharsets.US_ASCII);}
        var out=new ArrayList<RedoRecords.Record>();var vectors=new ArrayList<RedoRecords.Vector>();long block=0,scn=0;int offset=0;
        for(String line:text.lines().toList()) {
            var f=line.split("\\|",-1);
            if(f[0].equals("RECORD")) {
                if(!vectors.isEmpty())out.add(new RedoRecords.Record(block,offset,1000,1,scn,0,677737149,List.copyOf(vectors)));
                vectors.clear();block=Long.parseLong(f[2]);offset=Integer.parseInt(f[3]);scn=Long.parseLong(f[4]);
            } else {
                var op=f[1].split("\\.");var fields=new ArrayList<byte[]>();for(int i=3;i<f.length;i++)fields.add(HexFormat.of().parseHex(f[i]));
                vectors.add(new RedoRecords.Vector(Integer.parseInt(op[0]),Integer.parseInt(op[1]),1,9,Long.parseLong(f[2]),0,0,3,fields));
            }
        }
        out.add(new RedoRecords.Record(block,offset,1000,1,scn,0,677737149,List.copyOf(vectors)));return out;
    }
    @Test void exactSysUpdatesBindSixEightAndNineColumnsAndKeepHistoricalItlSeparate()throws Exception {
        var records=records();int[] counts={6,8,9};
        for(int i=0;i<records.size();i++) {
            var r=records.get(i);var row=r.vectors().get(1);var p=DictionaryObjectUndoPair.bind(r,row,role,37).physical();
            assertEquals(counts[i],p.forward().extent().changedColumns());assertEquals(p.undo().identity().xid(),p.forward().xid());
            assertEquals(HeapRowPatch.State.ABSENT,p.forward().columns().get(36).state());
        }
        var r=records.get(2);assertThrows(RedoException.class,()->HeapUndoPair.bind(r,r.vectors().get(1),37));
    }
    @Test void wrongLayoutBitmapRoleGenerationAndFullXidCannotBind()throws Exception {
        for(int at:new int[]{11,12,14,19,23,26,28,29}) {
            var r=records().get(2);r.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,r.vectors().get(1),role,37));
        }
        for(int at:new int[]{8,12,18}) {
            var r=records().get(2);r.vectors().get(1).fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,r.vectors().get(1),role,37));
        }
        var r=records().get(2);var row=r.vectors().get(1);
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,19),37));
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,new FrozenHeapBaseline.ObjectRole("SYS","COL$",18,18),37));
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,role,36));
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),List.of(r.vectors().get(0),row,row)),row,role,37));
    }
}
