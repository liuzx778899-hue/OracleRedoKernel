package com.streamhouse.oracle;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.streamhouse.oracle.ArchivedRedoTest.*;

class UndoControlRootTest {
    @TempDir Path dir;
    byte[] hex(String s) {return HexFormat.of().parseHex(s);}
    RedoRecords.Vector vector(int minor,long dba,String... fields) {
        return new RedoRecords.Vector(5,minor,0,11,dba,101,1,3,Arrays.stream(fields).map(this::hex).toList());
    }
    RedoRecords.Vector start() {return vector(2,37748896,"1e000000c10200003e014002b4012000510050000085410e0000000000000000","bd726528");}
    RedoRecords.Vector begin() {return vector(1,37749054,"5000f0141000400003001e00c1020000b4012000",
            "0000000000000000000000000000000005071e00080c0100000000003e014002b4011e00d272260000000000ef7226000000000000000000ffffffffffffffff390140020000000000000000");}
    RedoRecords.Vector head() {return vector(2,37748896,"1e000000000000003e014002b401210002005400f0fb4a080000000000000000");}
    RedoRecords.Vector root() {return vector(1,37749054,"54009e142200000003001e00c1020000b4012100","150000000200000000000000000000000b011e000000c102");}
    byte[] archive(List<RedoRecords.Vector> first,List<RedoRecords.Vector> second) {
        byte[] b=Arrays.copyOf(new ArchivedUndoInventoryTest().fixture(),2048);Arrays.fill(b,1024,2048,(byte)0);
        put(b,24,3,4);put(b,668,4,4);
        for(int block=2;block<4;block++) {
            int base=block*512;put(b,base,0x2201,2);put(b,base+4,block,4);put(b,base+8,7,4);
            var vectors=block==2?first:second;if(vectors.isEmpty())continue;
            int at=base+40;
            for(var v:vectors) {
                put(b,at,5,1);put(b,at+1,v.opcodeMinor(),1);put(b,at+8,v.dba(),4);put(b,at+24,v.containerId(),4);
                int table=(v.fields().size()+1)*2;put(b,at+32,table,2);
                int data=at+32+(table+3)/4*4;
                for(int i=0;i<v.fields().size();i++) {
                    byte[] field=v.fields().get(i);put(b,at+34+i*2,field.length,2);
                    System.arraycopy(field,0,b,data,field.length);data+=(field.length+3)/4*4;
                }
                at=data;
            }
            put(b,base+16,at-base-16,4);put(b,base+20,1,1);put(b,base+24,99+block,4);put(b,base+32,677737149,4);
        }
        for(int i=0;i<b.length;i+=512)seal(b,i);return b;
    }
    ArchivedUndoInventory read(List<RedoRecords.Vector> first,List<RedoRecords.Vector> second) throws Exception {
        Path p=dir.resolve("raw.dbf");Files.write(p,archive(first,second));var header=new ArchivedRedo().scan(p);
        // All mutations remain structurally readable and checksum-valid.
        new RedoRecords().visit(p,header,r->{});
        return ArchivedUndoInventory.read(List.of(p),677737149,3,new HeapRowPatch.Xid(3,30,705));
    }
    @Test void rowRootHasNoInventedEdgeToAllocationControl() throws Exception {
        var i=read(List.of(start(),begin()),List.of(head(),root()));var h=new UndoHistory(i);
        assertNotNull(i.controlRoot());assertEquals(2,i.entries().size());
        assertNull(h.append(i.entries().get(0)));assertNull(h.append(i.entries().get(1)));
        var result=h.close(i.entries().get(1).identity().uba());
        assertEquals(2,result.original().size());assertEquals(List.of(i.entries().get(1)),result.live());
    }
    @Test void missingDuplicateAndForeignControlsCannotCertifyRoot() throws Exception {
        assertThrows(RedoException.class,()->read(List.of(begin()),List.of(head(),root())));
        assertThrows(RedoException.class,()->read(List.of(start(),start(),begin()),List.of(head(),root())));
        assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(root())));
        assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(head(),head(),root())));
        assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of()));
        for(int offset:new int[]{0,8,12,14,16,18,24}) {
            var bad=head();bad.fields().get(0)[offset]^=1;
            assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(bad,root())),"control offset "+offset);
        }
    }
    @Test void wrongFullIdentityLinkObjectAndContainerStillRefuse() throws Exception {
        for(int offset:new int[]{12,14,16,18}) {
            var bad=root();bad.fields().get(0)[offset]^=1;
            assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(head(),bad)),"identity offset "+offset);
        }
        for(int offset:new int[]{0,4,12,16,18,19,22}) {
            var bad=root();if(offset==0||offset==4)Arrays.fill(bad.fields().get(1),offset,offset+4,(byte)0);
            else bad.fields().get(1)[offset]^=1;
            assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(head(),bad)),"metadata offset "+offset);
        }
        var c=head();var foreign=new RedoRecords.Vector(5,2,0,11,c.dba(),101,1,4,c.fields());
        assertThrows(RedoException.class,()->read(List.of(start(),begin()),List.of(foreign,root())));
    }
    @Test void nonRowStartCannotAuthorizeOrdinaryRowBeginOrIncompleteHistory() throws Exception {
        var bad=begin();bad.fields().get(1)[16]=11;bad.fields().get(1)[17]=1;
        var record=new RedoRecords.Record(2,16,236,1,101,1,677737149,List.of(start(),bad));
        assertThrows(RedoException.class,()->TransactionStart.decode(record.vectors().get(0)).bindBegin(record));
        var i=read(List.of(start(),begin()),List.of(head(),root()));var h=new UndoHistory(i);h.append(i.entries().get(0));
        assertThrows(RedoException.class,()->h.close(i.entries().get(1).identity().uba()));
        var missing=new UndoHistory(i);assertThrows(RedoException.class,()->missing.append(i.entries().get(1)));
    }
}
