package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalSchemaBarrierTest {
    final FrozenHeapBaseline.Snapshot snapshot=new FrozenHeapBaseline.Snapshot("sha",
            new ArchivedHeapRows.Source(1276726821,1276717861,318767104,1,1245693159,1920977,677737149,3),2501884,
            new FrozenHeapBaseline.ObjectRole("ORKLAB","T_SCHEMA_BARRIER_20261005",74695,74695),
            new FrozenHeapBaseline.ObjectRole("ORKLAB","PK_SCHEMA_BARRIER_20261005",74696,74696),List.of(),Map.of());
    RedoRecords.Vector marker() {
        var fields=new ArrayList<byte[]>();for(int i=0;i<46;i++)fields.add(new byte[0]);
        fields.set(0,HexFormat.of().parseHex("e02e00000a000d00710800000f0002000000010001000000"));
        fields.set(3,HexFormat.of().parseHex("6a000000c723010002000000"));
        fields.set(8,"ORKLAB".getBytes(StandardCharsets.UTF_8));fields.set(9,"T_SCHEMA_BARRIER_20261005".getBytes(StandardCharsets.UTF_8));
        fields.set(11,HexFormat.of().parseHex("c7230100"));
        return new RedoRecords.Vector(24,1,0,0,0,0,0,3,fields);
    }
    RedoRecords.Record record(long scn,long uid,RedoRecords.Vector vector) {return new RedoRecords.Record(118,96,544,1,scn,1,uid,List.of(vector));}
    void refuse(RedoRecords.Vector vector) {
        var e=assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,vector),snapshot));
        assertTrue(e.getMessage().startsWith("HISTORICAL_SCHEMA_BARRIER"));
    }
    @Test void actualAddAndRestoredSchemaStillRefuse() {
        refuse(marker());var drop=marker();drop.fields().set(3,HexFormat.of().parseHex("6a000000c723010003000000"));refuse(drop);
    }
    @Test void eitherNumericIdentityOrExactNameIsEnoughToRefuse() {
        var v=marker();v.fields().set(8,new byte[0]);v.fields().set(9,new byte[0]);refuse(v);
        v=marker();v.fields().set(3,new byte[12]);v.fields().set(11,new byte[4]);refuse(v);
        v=marker();v.fields().set(3,HexFormat.of().parseHex("6a000000c823010002000000"));v.fields().set(8,new byte[0]);v.fields().set(9,new byte[0]);v.fields().set(11,new byte[4]);refuse(v);
    }
    @Test void foreignRecognizedMarkerDoesNotUseSqlTextForScope() throws Exception {
        var v=marker();v.fields().set(3,new byte[12]);v.fields().set(11,new byte[4]);v.fields().set(8,"OTHER".getBytes(StandardCharsets.UTF_8));
        v.fields().set(7,"alter table ORKLAB.T_SCHEMA_BARRIER_20261005 add EXTRA number".getBytes(StandardCharsets.UTF_8));
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot);
    }
    @Test void preAcquisitionOrDifferentContainerDoesNotChangeThisGeneration() throws Exception {
        HistoricalSchemaBarrier.requireUnchanged(record(2501884,677737149,marker()),snapshot);
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737150,marker()),snapshot);
        var v=marker();v=new RedoRecords.Vector(24,1,0,0,0,0,0,1,v.fields());HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot);
    }
    @Test void unknownMarkerProfileCannotEstablishUnrelatedScope() {
        var v=marker();v.fields().remove(45);
        var e=assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot));
        assertEquals("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER",e.getMessage());
    }
    RedoRecords.Vector observedTriggerMarker() {
        var v=marker();
        v.fields().set(0,HexFormat.of().parseHex("e02e00000a000800c10d00003b0002000000010001000000"));
        v.fields().set(3,HexFormat.of().parseHex("00000000682f010000000000"));
        v.fields().set(8,"SYS".getBytes(StandardCharsets.UTF_8));v.fields().set(9,"ORK_D2_SYS_DELAY_20261008".getBytes(StandardCharsets.UTF_8));
        v.fields().set(10,HexFormat.of().parseHex("000000003b0000000080000000000000ffff0c8c"));
        v.fields().set(11,new byte[0]);return v;
    }
    @Test void observedTriggerMarkerUsesPrimaryIdentityAndNameWithoutSqlAuthority()throws Exception {
        var v=observedTriggerMarker();
        v.fields().set(7,"alter table ORKLAB.T_SCHEMA_BARRIER_20261005 add EXTRA number".getBytes(StandardCharsets.UTF_8));
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot);
        v.fields().set(3,HexFormat.of().parseHex("00000000c723010000000000"));refuse(v);
        v=observedTriggerMarker();v.fields().set(8,marker().fields().get(8));v.fields().set(9,marker().fields().get(9));refuse(v);
    }
    @Test void absentAffectedFieldRequiresObservedTriggerProfile() {
        for(int field:new int[]{0,3,10}) {
            var v=observedTriggerMarker();
            if(field==3)Arrays.fill(v.fields().get(field),4,8,(byte)0);
            else v.fields().get(field)[field==0?12:4]=0;
            assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot));
        }
        var v=marker();v.fields().set(11,new byte[0]);
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),snapshot));
    }
    @Test void unknownSchemaFamilyOpcodeCannotBeSilentlyExcluded() {
        var v=marker();var unknown=new RedoRecords.Vector(24,0,0,0,0,0,0,3,v.fields());
        var e=assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,unknown),snapshot));
        assertEquals("UNSUPPORTED_HISTORICAL_SCHEMA_MARKER_OPCODE",e.getMessage());
    }
    RedoRecords.Vector mediaMarker() {
        return new RedoRecords.Vector(24,4,0,0,0,0,0,3,List.of(
                HexFormat.of().parseHex("28230000000000000000000072000c00"),HexFormat.of().parseHex("6d4a2600008000006e4a260000800000")));
    }
    RedoRecords.Record mediaRecord(RedoRecords.Vector... vectors) {
        return new RedoRecords.Record(144,16,140,5,2509423,1,677737149,List.of(vectors));
    }
    @Test void nativeObservedStandaloneMediaMarkerIsNotAnObjectSchemaMarker() throws Exception {
        HistoricalSchemaBarrier.requireUnchanged(mediaRecord(mediaMarker()),snapshot);
    }
    RedoRecords.Vector mixedMediaMarker() {
        return new RedoRecords.Vector(24,4,0,0,0,0,0,3,List.of(
                HexFormat.of().parseHex("28230000000000000000000072000c00"),HexFormat.of().parseHex("0ddc29000000000017dc290000800000")));
    }
    RedoRecords.Record mixedMediaRecord(RedoRecords.Vector v) {
        return new RedoRecords.Record(3,16,140,5,2743320,1,677737149,List.of(v));
    }
    @Test void nativeMixedRangeRequiresObservedLowerEndpointAfterBaseline() throws Exception {
        var v=mixedMediaMarker();var r=mixedMediaRecord(v);
        HistoricalSchemaBarrier.requireMarkerProfile(r,v,2743256,Set.of(2743309L));
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(r,v));
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(r,v,2743256,Set.of()));
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(r,v,2743309,Set.of(2743309L)));
    }
    @Test void actualSeq83MarkerWithoutOptionalRecordTimeStillRequiresObservedRange()throws Exception {
        var v=new RedoRecords.Vector(24,4,0,0,0,0,0,3,List.of(
                HexFormat.of().parseHex("28230000000000000000000072000c00"),HexFormat.of().parseHex("b0692c0000000000bf692c0000800000")));
        var r=new RedoRecords.Record(9,280,140,1,2910656,1,677737149,List.of(v));
        HistoricalSchemaBarrier.requireMarkerProfile(r,v,2910534,Set.of(2910640L));
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(r,v,2910534,Set.of()));
    }
    @Test void mixedRangeFlagsPaddingAndUpperEndpointCannotBeIgnored() {
        for(int at:new int[]{0,4,6,8,12,14}) {
            var v=mixedMediaMarker();v.fields().get(1)[at]^=1;
            assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(mixedMediaRecord(v),v,2743256,Set.of(2743309L)));
        }
        var v=mixedMediaMarker();v.fields().get(0)[0]^=1;
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireMarkerProfile(mixedMediaRecord(v),v,2743256,Set.of(2743309L)));
    }
    @Test void mediaMarkerCannotHideOtherVectorsOrUnknownHeaderOrScnRange() {
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(mediaRecord(mediaMarker(),marker()),snapshot));
        for(int field:new int[]{0,1}) {
            var v=mediaMarker();v.fields().get(field)[0]^=1;
            assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(mediaRecord(v),snapshot));
        }
        var v=mediaMarker();v.fields().get(1)[4]=1;
        assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(mediaRecord(v),snapshot));
    }
    FrozenHeapBaseline.Snapshot anchored() {
        return new FrozenHeapBaseline.Snapshot(snapshot.sha256(),snapshot.source(),snapshot.scn(),snapshot.heap(),snapshot.index(),
                snapshot.columns(),snapshot.rows(),List.of(new FrozenHeapBaseline.DictionaryAnchor(
                new FrozenHeapBaseline.ObjectRole("SYS","TAB$",4,2),74695,new ArchivedHeapRows.RowKey(2,0x00408990L,7))));
    }
    RedoRecords.Vector dictionaryWrite(int slot,long logical,long data) {
        byte[] metadata=new byte[24],kdo=new byte[29];
        java.nio.ByteBuffer.wrap(metadata).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt((int)logical).putInt((int)data);
        java.nio.ByteBuffer.wrap(kdo).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x00408990);
        kdo[10]=5;kdo[20]=(byte)slot;
        return new RedoRecords.Vector(5,1,0,0,0,0,0,3,new ArrayList<>(List.of(new byte[20],metadata,new byte[0],kdo)));
    }
    @Test void anchoredDictionaryWriteRefusesWithoutAnySchemaMarker() {
        var e=assertThrows(RedoException.class,()->HistoricalSchemaBarrier.requireUnchanged(
                record(2501918,677737149,dictionaryWrite(7,4,2)),anchored()));
        assertEquals("TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2501918:object=74695",e.getMessage());
    }
    @Test void sameClusterDataDoesNotTurnAnotherLogicalRoleOrRowIntoTargetDdl() throws Exception {
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,dictionaryWrite(7,21,2)),anchored());
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,dictionaryWrite(8,4,2)),anchored());
        HistoricalSchemaBarrier.requireUnchanged(record(2501884,677737149,dictionaryWrite(7,4,2)),anchored());
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737150,dictionaryWrite(7,4,2)),anchored());
        HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,dictionaryWrite(7,4,2)),snapshot);
    }
    @Test void trustedDictionaryGenerationConflictAndUnknownRowProfileRefuse() {
        assertEquals("DICTIONARY_UNDO_GENERATION_CONFLICT",assertThrows(RedoException.class,()->
                HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,dictionaryWrite(7,4,3)),anchored())).getMessage());
        var v=dictionaryWrite(8,4,2);v.fields().get(3)[10]=99;
        assertEquals("UNSUPPORTED_DICTIONARY_ROW_PROFILE",assertThrows(RedoException.class,()->
                HistoricalSchemaBarrier.requireUnchanged(record(2501918,677737149,v),anchored())).getMessage());
    }
}
