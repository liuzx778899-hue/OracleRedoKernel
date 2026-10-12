package com.streamhouse.oracle;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryObjectIndexPairTest {
    private List<RedoRecords.Record> records()throws Exception {return new DictionaryObjectUndoPairTest().records("/dictionary-object-index-raw120.txt");}
    private DictionaryObjectIndexPair.Pair bind(RedoRecords.Record record,long logical)throws Exception {
        var old=record.vectors().get(1);var row=new RedoRecords.Vector(old.opcodeMajor(),old.opcodeMinor(),1,old.file(),old.dba(),old.scn(),old.sequence(),old.containerId(),old.fields(),logical);
        var owned=new RedoRecords.Record(record.block(),record.offset(),record.length(),record.validity(),record.scn(),record.subScn(),record.containerUid(),List.of(record.vectors().get(0),row));
        return DictionaryObjectIndexPair.bind(owned,row,logical);
    }
    @Test void raw120ObjectIndexesNameTheSameNewObjectRow()throws Exception {
        var records=records();var object=DictionaryObjectUndoPair.bind(records.get(0),records.get(0).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37).physical();
        for(int i=1;i<4;i++) {
            var record=records.get(i);var index=bind(record,List.of(36L,37L,40L).get(i-1));
            DictionaryObjectIndexPair.requireObject(index,object.forward(),object.undo().identity());
            assertEquals(0x00408e20L,index.rowDba());assertEquals(39,index.rowSlot());
            assertThrows(RedoException.class,()->IndexUndoPair.bindDictionary(record,record.vectors().get(1)));
        }
    }
    @Test void wrongIndexOwnerKeyOrReferencedObjectRefuses()throws Exception {
        var records=records();var object=DictionaryObjectUndoPair.bind(records.get(0),records.get(0).vectors().get(1),new FrozenHeapBaseline.ObjectRole("SYS","OBJ$",18,18),37).physical();
        for(int at:new int[]{0,4}) {
            var bad=records().get(1);bad.vectors().get(0).fields().get(1)[at]^=1;assertThrows(RedoException.class,()->bind(bad,36));
        }
        var bad=records().get(1);bad.vectors().get(0).fields().get(3)[4]^=1;assertThrows(RedoException.class,()->bind(bad,36));
        var wrongRow=records().get(1);wrongRow.vectors().get(1).fields().get(3)[5]^=1;
        var index=bind(wrongRow,36);assertThrows(RedoException.class,()->DictionaryObjectIndexPair.requireObject(index,object.forward(),object.undo().identity()));
        var wrongKey=records().get(1);wrongKey.vectors().get(1).fields().get(2)[4]^=1;wrongKey.vectors().get(0).fields().get(4)[4]^=1;
        var changed=bind(wrongKey,36);assertThrows(RedoException.class,()->DictionaryObjectIndexPair.requireObject(changed,object.forward(),object.undo().identity()));
        assertThrows(RedoException.class,()->DictionaryObjectIndexPair.validateKey(37,HexFormat.of().parseHex("ff"),new byte[6]));
    }
}
