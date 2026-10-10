package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 raw 24.1 scope barrier. Does not reconstruct a historical dictionary. */
final class HistoricalSchemaBarrier {
    static void requireUnchanged(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot) throws IOException {
        for(var input:inputs) {
            var header=input.header();snapshot.requireSource(header);
            input.visit(header,record->requireUnchanged(record,snapshot));
            if(!header.equals(input.header())) throw new RedoException("SCHEMA_BARRIER_SOURCE_CHANGED");
        }
    }
    static void requireUnchanged(RedoRecords.Record record,FrozenHeapBaseline.Snapshot snapshot) throws RedoException {
        if(record.containerUid()!=snapshot.source().containerUid() || record.scn()<=snapshot.scn()) return;
        for(var vector:record.vectors()) {
            if(vector.containerId()==snapshot.source().containerId() && vector.opcodeMajor()==5 && vector.opcodeMinor()==1
                    && !snapshot.dictionaryAnchors().isEmpty()) requireDictionaryUnchanged(record,vector,snapshot);
            if(vector.containerId()!=snapshot.source().containerId() || vector.opcodeMajor()!=24) continue;
            requireMarkerProfile(record,vector);
            if(vector.opcodeMinor()==4)continue;
            var fields=vector.fields();
            // Independently observed on CREATE/GRANT/ADD/DROP in seq56/57.
            // SQL text in field 7 is deliberately not interpreted or used as authority.
            long object=u32(fields.get(3),4);
            var roles=new java.util.ArrayList<FrozenHeapBaseline.ObjectRole>(java.util.List.of(snapshot.heap(),snapshot.index()));
            for(var lob:snapshot.lobs()) {roles.add(lob.lob());roles.add(lob.index());}
            for(var role:roles)
                if(object==role.logicalObject() || affectsObject(vector,role.logicalObject())
                        || (Arrays.equals(fields.get(8),role.owner().getBytes(StandardCharsets.UTF_8))
                        && Arrays.equals(fields.get(9),role.name().getBytes(StandardCharsets.UTF_8))))
                    throw new RedoException("HISTORICAL_SCHEMA_BARRIER:scn="+record.scn()+":object="+role.logicalObject());
        }
    }
    /** Profile only; callers must separately bind target scope and complete SYS history. */
    static void requireMarkerProfile(RedoRecords.Record record,RedoRecords.Vector vector)throws RedoException {
        requireMarkerProfile(record,vector,Long.MAX_VALUE,java.util.Set.of());
    }
    /** Mixed range profile needs its lower endpoint in the already visited same-container input. */
    static void requireMarkerProfile(RedoRecords.Record record,RedoRecords.Vector vector,long baselineScn,
                                     java.util.Set<Long> observedScns)throws RedoException {
        var fields=vector.fields();
        if(vector.opcodeMajor()!=24)throw new RedoException("NOT_SCHEMA_FAMILY_MARKER");
        if(vector.opcodeMinor()==1) {
            if(fields.size()!=46 || fields.get(0).length!=24 || u32(fields.get(0),0)!=0x2ee0 || fields.get(3).length!=12
                    || (fields.get(11).length!=4 && !observedTriggerMarker(fields) && !observedIndexMarker(fields) && !observedMultipleObjects(fields)
                        && !observedUserQuotaMarker(fields)))
                throw new RedoException("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER");
        } else if(vector.opcodeMinor()==4) {
            if((record.validity()!=1 && record.validity()!=5) || record.vectors().size()!=1 || vector.blockClass()!=0 || vector.file()!=0 || vector.dba()!=0 || vector.scn()!=0 || vector.sequence()!=0
                    || fields.size()!=2 || !Arrays.equals(fields.get(0),java.util.HexFormat.of().parseHex("28230000000000000000000072000c00")) || fields.get(1).length!=16)
                throw new RedoException("UNSUPPORTED_MEDIA_RECOVERY_MARKER_PROFILE");
            byte[] range=fields.get(1);long first=u32(range,0),last=u32(range,8);
            boolean closed=u16(range,4)==0x8000 && last-first<=1;
            // Seq73/74 standalone native-matched marker. This is not a begin/end or schema authority.
            boolean mixed=u16(range,4)==0 && observedScns.contains(first) && last==record.scn()-1;
            // Seq89 native-matched marker has an unspecified zero lower endpoint, not a transaction SCN.
            boolean unspecified=record.validity()==5 && first==0 && u16(range,4)==0 && last==record.scn()-1;
            if((!closed && !mixed && !unspecified) || u16(range,12)!=0x8000 || u16(range,6)!=0 || u16(range,14)!=0 || first>last || last>=record.scn())
                throw new RedoException("UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE");
        } else throw new RedoException("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER_OPCODE");
    }
    /** Seq80 native-matched CREATE TRIGGER marker has no secondary affected-object field. */
    private static boolean observedTriggerMarker(java.util.List<byte[]> fields) {
        return fields.get(11).length==0 && u16(fields.get(0),12)==59 && u16(fields.get(0),14)==2
                && u32(fields.get(3),0)==0 && u32(fields.get(3),4)>0 && u32(fields.get(3),8)==0
                && fields.get(10).length==20 && u32(fields.get(10),4)==59
                && fields.get(4).length==0 && fields.get(12).length==0 && fields.get(13).length==0
                && fields.get(8).length>0 && fields.get(9).length>0;
    }
    /** Seq102 native-matched 24.1 records carry one primary index identity and no secondary arrays. */
    private static boolean observedIndexMarker(java.util.List<byte[]> fields) {
        return u16(fields.get(0),12)==63 && u16(fields.get(0),14)==2
                && u32(fields.get(3),0)==0 && u32(fields.get(3),4)>0 && u32(fields.get(3),8)==0
                && Arrays.equals(fields.get(10),java.util.HexFormat.of().parseHex("020000003f0000000000000000000000ffff0100"))
                && fields.get(11).length==0 && fields.get(12).length==0 && fields.get(13).length==0
                && fields.get(8).length>0 && fields.get(9).length>0;
    }
    /** Seq104 owned quota experiment: user identity only, no primary/secondary table object. SQL text is not authority. */
    private static boolean observedUserQuotaMarker(java.util.List<byte[]> fields) {
        return u16(fields.get(0),12)==43 && u16(fields.get(0),14)==2
                && u32(fields.get(3),0)>0 && u32(fields.get(3),4)==0 && u32(fields.get(3),8)==0
                && Arrays.equals(fields.get(10),java.util.HexFormat.of().parseHex("000000002b0000000000080000000000ffff109d"))
                && fields.get(4).length==0 && fields.get(8).length>0 && fields.get(9).length==0
                && fields.get(11).length==0 && fields.get(12).length==0 && fields.get(13).length==0;
    }
    /** Seq92 native-matched markers carry two object identities and parallel flag arrays. */
    private static boolean observedMultipleObjects(java.util.List<byte[]> fields) {
        if(fields.get(10).length!=20 || fields.get(11).length<=4 || fields.get(11).length%4!=0)return false;
        int count=fields.get(11).length/4,secondary=u16(fields.get(10),14);
        return count==2 && u16(fields.get(10),12)==count && (secondary==0 || secondary==count)
                && fields.get(12).length==count*2 && fields.get(13).length==secondary*2;
    }
    /** Call only after requireMarkerProfile; every affected identity participates in scope. */
    static boolean affectsObject(RedoRecords.Vector vector,long object) {
        byte[] affected=vector.fields().get(11);
        for(int at=0;at<affected.length;at+=4)if(u32(affected,at)==object)return true;
        return false;
    }
    private static void requireDictionaryUnchanged(RedoRecords.Record record,RedoRecords.Vector vector,
                                                    FrozenHeapBaseline.Snapshot snapshot) throws RedoException {
        var fields=vector.fields();
        if(fields.size()<2 || fields.get(0).length!=20 || fields.get(1).length<24)
            throw new RedoException("UNREADABLE_DICTIONARY_UNDO_SCOPE");
        long logical=u32(fields.get(1),0),data=u32(fields.get(1),4);
        var anchors=snapshot.dictionaryAnchors().stream().filter(a->a.dictionary().logicalObject()==logical).toList();
        if(anchors.isEmpty()) return;
        if(data!=anchors.get(0).dictionary().dataObject() || u32(fields.get(1),8)!=0)
            throw new RedoException("DICTIONARY_UNDO_GENERATION_CONFLICT");
        if(fields.size()<4 || fields.get(3).length<12) throw new RedoException("UNSUPPORTED_DICTIONARY_ROW_PROFILE");
        byte[] kdo=fields.get(3);int op=kdo[10]&255,slotAt;
        if(op==5 && (kdo.length==29 || kdo.length==30)) slotAt=20;
        else if(op==3 && kdo.length==20) slotAt=16;
        else if(op==2 && ((kdo.length==49 && (kdo[16]&255)==0x2c) || (kdo.length==52 && (kdo[16]&255)==0x6c))) slotAt=42;
        else throw new RedoException("UNSUPPORTED_DICTIONARY_ROW_PROFILE");
        var key=new ArchivedHeapRows.RowKey(data,u32(kdo,0),u16(kdo,slotAt));
        for(var anchor:anchors) if(anchor.key().equals(key))
            throw new RedoException("TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn="+record.scn()+":object="+anchor.targetObject());
    }
}
