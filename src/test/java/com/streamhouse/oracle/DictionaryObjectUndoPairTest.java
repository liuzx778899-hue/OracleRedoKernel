package com.streamhouse.oracle;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryObjectUndoPairTest {
    final FrozenHeapBaseline.ObjectRole role=new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18);
    List<RedoRecords.Record> records()throws Exception {
        return records("/dictionary-object-row-profiles.txt");
    }
    List<RedoRecords.Record> records(String resource)throws Exception {
        String text;try(var in=getClass().getResourceAsStream(resource)){text=new String(in.readAllBytes(),StandardCharsets.US_ASCII);}
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
    @Test void observedSixColumnCkix239BindsBothImagesWithoutWideningOtherProfiles()throws Exception {
        var r=records().get(0);var row=r.vectors().get(1);var inverse=r.vectors().get(0).fields().get(3);
        row.fields().get(1)[18]=(byte)239;inverse[18]=(byte)239;
        assertEquals(6,DictionaryObjectUndoPair.bind(r,row,role,37).physical().forward().extent().changedColumns());
        inverse[18]=0;
        assertEquals("DICTIONARY_OBJECT_CKIX_PAIR_MISMATCH",assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,role,37)).getMessage());
        row.fields().get(1)[18]=(byte)238;inverse[18]=(byte)238;
        assertEquals(6,DictionaryObjectUndoPair.bind(r,row,role,37).physical().forward().extent().changedColumns());
        var nine=records().get(2);nine.vectors().get(1).fields().get(1)[18]=(byte)239;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(nine,nine.vectors().get(1),role,37));
    }
    @Test void observedCkix144RemainsPairedScopedAndSixColumnOnly()throws Exception {
        var r=records().get(0);var row=r.vectors().get(1);var inverse=r.vectors().get(0).fields().get(3);
        row.fields().get(1)[18]=(byte)144;inverse[18]=(byte)144;
        assertEquals(6,DictionaryObjectUndoPair.bind(r,row,role,37).physical().forward().extent().changedColumns());
        inverse[18]=(byte)239;
        assertEquals("DICTIONARY_OBJECT_CKIX_PAIR_MISMATCH",assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,role,37)).getMessage());
        var eight=records().get(1);eight.vectors().get(1).fields().get(1)[18]=(byte)144;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(eight,eight.vectors().get(1),role,37));
    }
    @Test void sixColumnCkixIsAByteReferenceRatherThanAnEnumeratedParentIdentity()throws Exception {
        for(int ckix:new int[]{0,87,144,238,239,255}) {
            var r=records().get(0);var row=r.vectors().get(1);
            row.fields().get(1)[18]=(byte)ckix;r.vectors().get(0).fields().get(3)[18]=(byte)ckix;
            assertEquals(6,DictionaryObjectUndoPair.bind(r,row,role,37).physical().forward().extent().changedColumns());
        }
    }
    @Test void raw118NineColumnCkixRequiresMatchingImagesAndStored26()throws Exception {
        var r=records().get(2);var row=r.vectors().get(1);var inverse=r.vectors().get(0).fields().get(3);
        row.fields().get(1)[18]=(byte)251;inverse[18]=(byte)251;
        row.fields().get(1)[22]=26;inverse[22]=26;
        assertEquals(9,DictionaryObjectUndoPair.bind(r,row,role,37).physical().forward().extent().changedColumns());
        inverse[18]=(byte)127;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(r,row,role,37));
    }
    @Test void raw120FiveColumnUpdatesRemainStored26AndUseTheirOwnBitmapMarker()throws Exception {
        var r=records().get(0);var row=r.vectors().get(1);var undo=r.vectors().get(0);
        var forward=new ArrayList<>(row.fields());var inverse=new ArrayList<>(undo.fields());
        forward.get(1)[23]=5;inverse.get(3)[23]=5;forward.get(1)[26]=8;inverse.get(3)[26]=8;
        forward.set(2,Arrays.copyOf(forward.get(2),10));inverse.set(4,Arrays.copyOf(inverse.get(4),10));
        forward.remove(forward.size()-1);inverse.remove(inverse.size()-1);
        var f=new RedoRecords.Vector(11,5,row.blockClass(),row.file(),row.dba(),row.scn(),row.sequence(),row.containerId(),forward);
        var u=new RedoRecords.Vector(5,1,undo.blockClass(),undo.file(),undo.dba(),undo.scn(),undo.sequence(),undo.containerId(),inverse);
        var record=new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),List.of(u,f));
        assertEquals(5,DictionaryObjectUndoPair.bind(record,f,role,37).physical().forward().extent().changedColumns());
        forward.get(1)[26]=16;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(record,f,role,37));
    }
    @Test void raw120ObjectInsertRequiresItsCurrentOwnerAndInverseDelete()throws Exception {
        var record=records("/dictionary-object-insert-raw120.txt").get(0);var row=record.vectors().get(1);
        var pair=DictionaryObjectUndoPair.bind(record,row,role,37).physical();
        assertEquals(HeapRowPatch.Kind.INSERT,pair.forward().kind());
        assertEquals(HeapRowPatch.Kind.DELETE,pair.undo().inverse().kind());
        assertEquals(26,pair.forward().extent().storedColumns());assertEquals(34,pair.forward().slot());
        assertEquals(81024,OracleScalars.number(pair.forward().columns().get(0).bytes()).longValueExact());
        assertEquals("T_D4_MIG_INT_20261011",OracleScalars.varchar2(pair.forward().columns().get(3).bytes(),"AL32UTF8"));
        assertThrows(RedoException.class,()->HeapUndoPair.bind(record,row,37));
        for(int at:new int[]{11,12,18,42,44}) {
            var bad=records("/dictionary-object-insert-raw120.txt").get(0);bad.vectors().get(1).fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(bad,bad.vectors().get(1),role,37));
        }
        for(int at:new int[]{8,12,18}) {
            var bad=records("/dictionary-object-insert-raw120.txt").get(0);bad.vectors().get(1).fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(bad,bad.vectors().get(1),role,37));
        }
    }
    @Test void raw120LobIndexObjectInsertStores25ColumnsAndBindsCurrentUba()throws Exception {
        var record=records("/dictionary-object-insert-raw120.txt").get(1);var row=record.vectors().get(1);
        var pair=DictionaryObjectUndoPair.bind(record,row,role,37).physical();
        assertEquals(25,pair.forward().extent().storedColumns());assertEquals(35,pair.forward().slot());
        assertEquals(81028,OracleScalars.number(pair.forward().columns().get(0).bytes()).longValueExact());
        assertEquals("SYS_IL0000081024C00004$$",OracleScalars.varchar2(pair.forward().columns().get(3).bytes(),"AL32UTF8"));
        row.fields().get(0)[8]^=1;
        assertThrows(RedoException.class,()->DictionaryObjectUndoPair.bind(record,row,role,37));
    }
}
