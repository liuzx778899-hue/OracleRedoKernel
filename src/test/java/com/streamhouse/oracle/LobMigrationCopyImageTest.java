package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobMigrationCopyImageTest {
    private RedoRecords.Vector image()throws Exception {
        var record=new DictionaryObjectUndoPairTest().records("/lob-migration-copy-image-raw120.txt").get(0);var v=record.vectors().get(0);
        return new RedoRecords.Vector(19,1,1,12,v.dba(),record.scn(),v.sequence(),3,v.fields(),81024);
    }
    private RedoRecords.Record control(int at)throws Exception {
        var r=new DictionaryObjectUndoPairTest().records("/lob-migration-copy-controls-raw120.txt").get(at);var vectors=new ArrayList<RedoRecords.Vector>();
        for(var v:r.vectors())vectors.add(v.opcodeMajor()!=13?v:new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.opcodeMinor()==22?8:4,12,v.dba(),r.scn(),v.sequence(),3,v.fields(),81024));
        return new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),vectors);
    }
    @Test void imageHasOneCompleteRowAndAllThreeStorageOriginalsBindToItsOwner()throws Exception {
        var decoded=LobMigrationCopyImage.decode(image(),81024,4);assertEquals(new HeapRowPatch.Xid(10,19,5250),decoded.owner());
        assertEquals(4,decoded.row().size());assertEquals("c102",decoded.row().get(0).hex());
        assertEquals("migration-seed",OracleScalars.varchar2(HexFormat.of().parseHex(decoded.row().get(1).hex()),"AL32UTF8"));
        for(int i=1;i<4;i++){var record=control(i);LobMigrationCopyImage.requireStorage(record,record.vectors().get(0),decoded,81024);}
        var lock=control(0);assertEquals(HeapRowPatch.Kind.LOCK,DictionaryRedefinitionAuxiliary.bind(lock,lock.vectors().get(1),875).physical().forward().kind());
    }
    @Test void wrongObjectOwnerDirectoryAndStorageBytesRefuse()throws Exception {
        for(int at:new int[]{4,32,106,110,122,8056}) {
            var v=image();v.fields().get(0)[at]^=1;
            assertThrows(RedoException.class,()->LobMigrationCopyImage.decode(v,81024,4));
        }
        var decoded=LobMigrationCopyImage.decode(image(),81024,4);
        for(int i=1;i<4;i++) {
            var record=control(i);record.vectors().get(0).fields().get(0)[12]^=1;
            assertThrows(RedoException.class,()->LobMigrationCopyImage.requireStorage(record,record.vectors().get(0),decoded,81024));
            var bytes=control(i);bytes.vectors().get(1).fields().get(0)[0]^=1;
            assertThrows(RedoException.class,()->LobMigrationCopyImage.requireStorage(bytes,bytes.vectors().get(0),decoded,81024));
        }
    }
}
