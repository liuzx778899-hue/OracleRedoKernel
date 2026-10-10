package com.streamhouse.oracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FrozenHeapBaselineTest {
    @TempDir Path directory;
    @Test void implicitPrimaryKeyNullFlagRetainsNonNullMetadataAndRejectsContradictions()throws Exception {
        String implicit=dictionarySample().replace("DICT_COLUMN|1|1|ID|2|2|0","DICT_COLUMN|1|1|ID|2|1|0");
        assertNotEquals(dictionarySample(),implicit);
        assertFalse(read(implicit).columns().get(0).nullable());
        assertFalse(OracleColumnTypes.nullable(1));assertFalse(OracleColumnTypes.nullable(2));assertTrue(OracleColumnTypes.nullable(0));
        assertThrows(RedoException.class,()->read(implicit.replace("DICT_COLUMN|1|1|ID|2|1|0","DICT_COLUMN|1|1|ID|2|3|0")));
        assertThrows(RedoException.class,()->read(implicit.replace("COLUMN|BEFORE|1|1|ID|NUMBER|N","COLUMN|BEFORE|1|1|ID|NUMBER|Y")));
    }
    String sample() {
        return """
                FORMAT|ORK_HEAP_SNAPSHOT_V1
                SOURCE|1276726821|1276717861|1245693159|1920977|1|19.0.0
                PDB|677737149|3|ORKPDB
                CHARSET|AL32UTF8
                SNAPSHOT_SCN|2341293
                HEAP|BEFORE|ORKLAB|T_BASELINE_20261004|73073|73076
                INDEX|BEFORE|ORKLAB|PK_BASELINE_20261004|73074|73075
                COLUMN|BEFORE|1|1|ID|NUMBER|N|NO|NO
                COLUMN|BEFORE|2|2|TXT|VARCHAR2|Y|NO|NO
                COLUMN|BEFORE|3|3|NOTE|VARCHAR2|Y|YES|NO
                ROW|73076|12|403|0|C10B|6E65772D65706F6368|6E65772D6E6F7465
                ROW_COUNT|1
                HEAP|AFTER|ORKLAB|T_BASELINE_20261004|73073|73076
                INDEX|AFTER|ORKLAB|PK_BASELINE_20261004|73074|73075
                COLUMN|AFTER|1|1|ID|NUMBER|N|NO|NO
                COLUMN|AFTER|2|2|TXT|VARCHAR2|Y|NO|NO
                COLUMN|AFTER|3|3|NOTE|VARCHAR2|Y|YES|NO
                SOURCE_AFTER|1276726821|1276717861|1245693159|1920977|1|19.0.0
                END|ORK_HEAP_SNAPSHOT_V1
                """;
    }
    String digest(String text) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    @Test void rawColumnKeepsBinaryBytesInTrustedSnapshot()throws Exception {
        var snapshot=read(sample().replace("TXT|VARCHAR2","TXT|RAW").replace("6E65772D65706F6368","00FFC328"));
        assertEquals("RAW",snapshot.columns().get(1).type());
        assertEquals("00ffc328",snapshot.rows().values().iterator().next().get(1).hex());
        ArchivedHeapRows.requireCompleteValues(snapshot.rows().values().iterator().next(),snapshot.columns());
    }
    @Test void temporalColumnsRequireCompleteValidValues()throws Exception {
        var snapshot=read(sample().replace("TXT|VARCHAR2","TXT|DATE").replace("NOTE|VARCHAR2","NOTE|TIMESTAMP")
                .replace("6E65772D65706F6368","787C021D183C3B").replace("6E65772D6E6F7465","787C021D183C3B075BCD15"));
        var row=snapshot.rows().values().iterator().next();
        ArchivedHeapRows.requireCompleteValues(row,snapshot.columns());
        var invalid=new java.util.ArrayList<>(row);invalid.set(2,new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"787c021d183c3bffffffff"));
        assertThrows(RedoException.class,()->ArchivedHeapRows.requireCompleteValues(invalid,snapshot.columns()));
        assertEquals(12,OracleColumnTypes.oracleId("DATE"));assertEquals("TIMESTAMP",OracleColumnTypes.name(180));
        assertThrows(RedoException.class,()->OracleColumnTypes.name(181));
    }
    FrozenHeapBaseline.Snapshot read(String text) throws Exception {var p=directory.resolve("snapshot.txt");Files.writeString(p,text);return FrozenHeapBaseline.read(p,digest(text));}
    @Test void keepsPhysicalColumnsAndDistinctHeapIndexGenerations() throws Exception {
        var snapshot=read(sample());assertEquals(73073,snapshot.heap().logicalObject());assertEquals(73076,snapshot.heap().dataObject());
        assertEquals(73074,snapshot.index().logicalObject());assertEquals(73075,snapshot.index().dataObject());assertTrue(snapshot.columns().get(2).hidden());
        assertEquals("c10b",snapshot.rows().get(new ArchivedHeapRows.RowKey(73076,0x03000193L,0)).get(0).hex());
        assertEquals("UNSUPPORTED_SNAPSHOT_LOGICAL_DATA_OBJECT_MAPPING",assertThrows(RedoException.class,snapshot::baselineForEqualObjectScope).getMessage());
        assertThrows(UnsupportedOperationException.class,()->snapshot.rows().clear());
    }
    @Test void missingReceiptAndAlteredUntouchedKeyMetadataScnOrSourceRefuse() throws Exception {
        var p=directory.resolve("snapshot.txt");assertEquals("MISSING_TRUSTED_SNAPSHOT_RECEIPT",assertThrows(RedoException.class,()->FrozenHeapBaseline.read(p,null)).getMessage());
        for (String changed:new String[]{sample().replace("C10B","C10C"),sample().replace("2341293","2282688"),sample().replace("677737149","677737150"),sample().replace("3|3|NOTE","3|2|NOTE"),sample().replace("73076","73073")}) {
            Files.writeString(p,changed);assertEquals("SNAPSHOT_ACQUISITION_DIGEST_MISMATCH",assertThrows(RedoException.class,()->FrozenHeapBaseline.read(p,digest(sample()))).getMessage());
        }
    }
    @Test void trustedButIncompleteDuplicateOrStructurallyWrongOutputStillRefuses() throws Exception {
        String row="ROW|73076|12|403|0|C10B|6E65772D65706F6368|6E65772D6E6F7465\n";
        for (String invalid:new String[]{sample().replace("ROW_COUNT|1","ROW_COUNT|2"),sample().replace(row,row+row),sample().replace("END|ORK_HEAP_SNAPSHOT_V1\n",""),sample()+"unexpected\n",sample().replace("3|3|NOTE","3|2|NOTE"),sample().replace("ROW|73076","ROW|73073"),sample().replace("AL32UTF8","WE8MSWIN1252"),sample().replace("ROW|73076|12|403|0|C10B","ROW|73076|12|403|0|NULL")})
            assertThrows(RedoException.class,()->read(invalid));
    }
    @Test void equalGenerationConvertsOnlyItsRecordedSourceAndScn() throws Exception {
        var snapshot=read(sample().replace("73076","73073").replace("73075","73074"));var baseline=snapshot.baselineForEqualObjectScope();
        assertEquals(snapshot.source(),baseline.source());assertEquals(snapshot.scn(),baseline.scn());assertEquals(snapshot.rows(),baseline.rows());
        var h=new ArchivedRedo.Header("00",512,1,1276717861,0x13000000L,1,26,1245693159,1920977,2282718,2283114,1);
        assertEquals("SNAPSHOT_REDO_SOURCE_MISMATCH",assertThrows(RedoException.class,()->snapshot.requireSource(h)).getMessage());
    }
    String dictionarySample() {
        return sample().replace("ORK_HEAP_SNAPSHOT_V1","ORK_HEAP_SNAPSHOT_V2")
                .replace("ROW|73076", """
                        DICT_ROLE|BEFORE|SYS|OBJ$|18|18
                        DICT_ROLE|BEFORE|SYS|TAB$|4|2
                        DICT_ROLE|BEFORE|SYS|COL$|21|2
                        DICT_ANCHOR|OBJ$|73073|18|1|29640|24
                        DICT_ANCHOR|OBJ$|73074|18|1|29640|25
                        DICT_ANCHOR|TAB$|73073|2|1|35216|7
                        DICT_ANCHOR|COL$|73073|2|1|35216|49
                        DICT_ANCHOR|COL$|73073|2|1|35216|50
                        DICT_ANCHOR|COL$|73073|2|1|35216|51
                        DICT_COLUMN|1|1|ID|2|2|0
                        DICT_COLUMN|2|2|TXT|1|0|0
                        DICT_COLUMN|3|3|NOTE|1|0|17179869216
                        ROW|73076""")
                .replace("SOURCE_AFTER|", """
                        DICT_ROLE|AFTER|SYS|OBJ$|18|18
                        DICT_ROLE|AFTER|SYS|TAB$|4|2
                        DICT_ROLE|AFTER|SYS|COL$|21|2
                        SOURCE_AFTER|""");
    }
    @Test void sameDataObjectStillKeepsDistinctDictionaryRolesAndImmutableAnchors() throws Exception {
        var s=read(dictionarySample());assertEquals(6,s.dictionaryAnchors().size());
        assertEquals(4,s.dictionaryAnchors().get(2).dictionary().logicalObject());
        assertEquals(21,s.dictionaryAnchors().get(3).dictionary().logicalObject());
        assertEquals(2,s.dictionaryAnchors().get(2).key().dataObjectId());
        assertThrows(UnsupportedOperationException.class,()->s.dictionaryAnchors().clear());
        assertTrue(read(sample()).dictionaryAnchors().isEmpty());
    }
    @Test void trustedDictionaryWithMissingDuplicateWrongTargetOrHistoricalColumnsRefuses() throws Exception {
        String v=dictionarySample(),anchor="DICT_ANCHOR|COL$|73073|2|1|35216|51\n";
        for(String invalid:new String[]{v.replace(anchor,""),v.replace(anchor,anchor+anchor),
                v.replace("TAB$|73073|2","TAB$|73074|2"),v.replace("COL$|73073|2","COL$|73073|18"),
                v.replace("DICT_COLUMN|2|2|TXT|1|0|0","DICT_COLUMN|2|2|TXT|1|2|0"),
                v.replace("DICT_ROLE|AFTER|SYS|TAB$|4|2","DICT_ROLE|AFTER|SYS|TAB$|4|3"),
                v.replace("DICT_ROLE|BEFORE|SYS|COL$|21|2","DICT_ROLE|BEFORE|SYS|COL$|4|2")})
            assertThrows(RedoException.class,()->read(invalid));
    }
}
