package com.streamhouse.oracle;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CapturedLobTrustedBatchTest {
    @TempDir Path directory;
    @Test void observedSecureFileZeroFlagLockRemainsLobScoped()throws Exception {
        var kdo=java.util.HexFormat.of().parseHex("3f8600033a860003fa1204010100000000000001");
        var fields=List.of(new byte[24],kdo);
        assertEquals(HeapRowPatch.Kind.LOCK,HeapRowPatch.decodeLobKdo(fields,4,50366015,3).kind());
        assertThrows(RedoException.class,()->HeapRowPatch.decodeKdo(fields,4,50366015,3));
        kdo[19]=0;assertEquals(0,HeapRowPatch.decodeLobKdo(fields,4,50366015,3).lockTo());
        kdo[14]=2;assertThrows(RedoException.class,()->HeapRowPatch.decodeLobKdo(fields,4,50366015,3));
    }
    @Test void lobTypesRequireDedicatedCompleteValuePath()throws Exception {
        assertEquals(113,OracleColumnTypes.oracleId("BLOB"));assertEquals("CLOB",OracleColumnTypes.name(112));
        assertEquals(128,OracleColumnTypes.property("BLOB",false));assertEquals(128,OracleColumnTypes.property("CLOB",false));
        var helper=new FrozenHeapBaselineTest();helper.directory=directory;
        var text=helper.sample().replace("TXT|VARCHAR2","TXT|BLOB");
        assertEquals("UNSUPPORTED_SNAPSHOT_LOB_LAYOUT",assertThrows(RedoException.class,()->helper.read(text)).getMessage());
    }
    @Test @EnabledIfSystemProperty(named="ork.lob.fixture",matches=".+")
    void diagnosticLateAnchorCannotRepublishAnyOldEventAndMismatchRefuses()throws Exception {
        Path raw=Path.of(System.getProperty("ork.lob.fixture"));
        var capture=new CaptureJournal().readRawClosed(raw,"9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995");
        var dictHelper=new DictionaryBaselineTest();dictHelper.directory=directory;var dictionary=dictHelper.read(dictHelper.v3()).dictionaryBaseline();
        var records=new RedoRecords().readCaptured(capture);
        var header=RedoBatch.captures(List.of(capture)).iterator().next().header();
        for(var storage:LobLocator.Storage.values()) {
            boolean basic=storage==LobLocator.Storage.BASICFILE;long heap=basic?79334:79340,pk=basic?79339:79345;
            String table=basic?"T_D4_LOBB_20261009":"T_D4_LOBS_20261009";
            var scope=new CapturedLobTransactions.Scope(677737149,3,heap,pk,storage,basic?79335:79341,basic?79336:79342,basic?50365977:0,basic?79337:79343,basic?79338:79344,basic?50365993:0);
            var logical=CapturedLobBatch.read(List.of(capture),scope);var seed=logical.events().get(0);
            var match=java.util.regex.Pattern.compile("\"hex\":\\s*\"([0-9a-f]*)\"").matcher(Files.readString(raw.getParent().resolve(table+"-trim-truth.json")));
            assertTrue(match.find());String blob=match.group(1);assertTrue(match.find());String clob=match.group(1);assertFalse(match.find());
            var rows=Map.of(seed.rowKey(),List.of(new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,"c102"),new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,blob),new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,clob)));
            var heapRole=new FrozenHeapBaseline.ObjectRole("ORKLAB",table,heap,heap);var index=new FrozenHeapBaseline.ObjectRole("ORKLAB","PK_"+table.substring(2),pk,pk);
            var columns=List.of(new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),new FrozenHeapBaseline.PhysicalColumn(2,2,"B","BLOB",true,false),new FrozenHeapBaseline.PhysicalColumn(3,3,"C","CLOB",true,false));
            var lobs=List.of(new FrozenHeapBaseline.LobColumn(2,storage,new FrozenHeapBaseline.ObjectRole("ORKLAB","LB",scope.blob(),scope.blob()),new FrozenHeapBaseline.ObjectRole("ORKLAB","LI",scope.blobIndex(),scope.blobIndex())),
                    new FrozenHeapBaseline.LobColumn(3,storage,new FrozenHeapBaseline.ObjectRole("ORKLAB","LC",scope.clob(),scope.clob()),new FrozenHeapBaseline.ObjectRole("ORKLAB","CI",scope.clobIndex(),scope.clobIndex())));
            long leaf=records.stream().flatMap(r->r.vectors().stream()).filter(v->v.opcodeMajor()==10 && v.objectId()==pk).findFirst().orElseThrow().dba();
            var snapshot=new FrozenHeapBaseline.Snapshot("0".repeat(64),ArchivedHeapRows.Source.of(header,677737149,3),header.nextScn()-1,heapRole,index,columns,rows,List.of(),dictionary,lobs);
            assertEquals(0,CapturedLobTrustedBatch.read(List.of(capture),snapshot,leaf).events().size());
            var bad=new FrozenHeapBaseline.Snapshot(snapshot.sha256(),snapshot.source(),snapshot.scn(),heapRole,index,columns,Map.of(),List.of(),dictionary,lobs);
            assertEquals("LOB_ACQUIRED_ANCHOR_AND_RAW_CONTEXT_MISMATCH",assertThrows(RedoException.class,()->CapturedLobTrustedBatch.read(List.of(capture),bad,leaf)).getMessage());
        }
        // These are synthetic diagnostic arguments, not a real acquisition receipt or approval of archive 97.
    }
}
