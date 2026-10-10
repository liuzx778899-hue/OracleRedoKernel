package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;

/** Frozen SYS index refusal positions and KTB bytes only; no events, source actions or publication. */
public class ReadCutIndexProbe {
    public static void main(String[] args)throws Exception {
        var raw=Path.of(args[0]);var input=RedoBatch.captures(List.of(new CaptureJournal().readRawClosed(raw,args[1]))).get(0);var header=input.header();
        var snapshot=FrozenHeapBaseline.read(Path.of(args[2]),args[3]);int[] refused={0};
        input.visit(header,record->{
            if(record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn())return;
            for(var marker:record.vectors())if(marker.opcodeMajor()==24)try {HistoricalSchemaBarrier.requireMarkerProfile(record,marker);}
            catch(RedoException failure) {if(marker.opcodeMinor()==1) {
                System.out.println("SCHEMA_PROFILE|"+record.scn()+"|"+record.block()+"|"+record.offset()+"|fields="+marker.fields().size());
                for(int at:new int[]{0,3,8,9,10,11,12,13})System.out.println("FIELD|"+at+"|"+HexFormat.of().formatHex(marker.fields().get(at)));
            }}
            for(var undo:record.vectors())if(undo.opcodeMajor()==5 && undo.opcodeMinor()==1 && undo.fields().size()>1 && undo.fields().get(0).length==20 && undo.fields().get(1).length>=24) {
                long object=RedoBytes.u32(undo.fields().get(1),0);
                var roles=new ArrayList<FrozenHeapBaseline.ObjectRole>();snapshot.dictionaryBaseline().layouts().values().forEach(x->roles.add(x.role()));snapshot.dictionaryBaseline().indexes().forEach(x->roles.add(x.role()));
                var role=roles.stream().filter(x->x.logicalObject()==object && !x.name().startsWith("I_COL")).findFirst();
                if(role.isPresent() && undo.fields().size()>3 && undo.fields().get(3).length>=20) {
                    byte[] k=undo.fields().get(3);int op=k[10]&255;
                    if(!((op==5 && (k.length==29 || k.length==30)) || ((op==3 || op==4) && k.length==20) || (op==2 && (k.length==49 || k.length==52)))) {
                        System.out.println("DISCOVERY_ROW|"+header.sequence()+"|"+record.block()+"|"+record.offset()+"|scn="+record.scn()+"|role="+role.get().name()+"|op="+op+"|length="+k.length+"|"+HexFormat.of().formatHex(k));
                        for(var v:record.vectors())if(v.opcodeMajor()==11 && v.fields().size()>1)System.out.println("FORWARD_KDO|"+v.opcodeMinor()+"|"+HexFormat.of().formatHex(v.fields().get(1)));
                    }
                }
                for(var scope:snapshot.dictionaryBaseline().indexes())if(scope.role().name().startsWith("I_COL") && object==scope.role().logicalObject()) {
                    var owner=UndoHeader.decode(undo);
                    for(var vector:record.vectors())if(vector.containerId()==undo.containerId() && vector.opcodeMajor()==10 && (vector.opcodeMinor()==2 || vector.opcodeMinor()==4)) {
                        try {DictionaryIndexUndoPair.bind(record,vector,scope.role(),scope.indexDba(),"AL32UTF8");}
                        catch(RedoException failure) {
                            refused[0]++;System.out.println("REFUSED|"+failure.getMessage()+"|"+header.sequence()+"|"+record.block()+"|"+record.offset()+"|"+record.scn()+"|vector="+(record.vectors().indexOf(vector)+1)+"|role="+scope.role().name()+"|owner="+owner+"|dba="+vector.dba());
                            System.out.println("KTB|length="+vector.fields().get(0).length+"|"+HexFormat.of().formatHex(vector.fields().get(0)));
                        }
                    }
                }
            }
        });
        System.out.println("DIAGNOSTIC_ONLY|refused="+refused[0]+"|events=0");
    }
}
