package com.streamhouse.oracle;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureFileHbbReservationTest {
    byte[] words(int size,int... values) {var b=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);for(int value:values)b.putInt(value);return b.array();}
    RedoRecords.Vector vector(int major,int minor,int cls,long dba,long object,List<byte[]> fields) {
        return new RedoRecords.Vector(major,minor,cls,12,dba,3175459,1,3,fields,object);
    }
    RedoRecords.Record record(RedoRecords.Vector... vectors) {return new RedoRecords.Record(38790,192,308,1,3175459,1,677737149,List.of(vectors));}
    @Test void observedReserveAndReleaseRequireOwnedExactForwardInverse()throws Exception {
        for(boolean release:new boolean[]{false,true}) {
            var ktb=words(20,0,0,9|(22<<16),996,1|(2<<16));
            var metadata=words(24,79915,79915,5,0,13|(50<<8)|(22<<16),996<<16);
            var before=words(40,15,0,release?50371401:0,0,release?4:0);
            var after=words(40,15,0,release?0:50371401,0,release?0:4,release?9|(22<<16):0,release?996:0);
            if(release) {before[2]=0x43;before[3]=(byte)0x86;} // Native-matched unused inverse padding.
            var u=vector(5,1,34,37753963,0xffffffffL,List.of(ktb,metadata,words(12,50371180,50371177,8),before));
            var f=vector(13,49,8,50371180,79915,List.of(after));var r=record(u,f);
            CapturedLobMaintenance.requireHbbReservationPair(r,u,79915);
            assertThrows(RedoException.class,()->CapturedLobMaintenance.requireHbbReservationPair(record(u,f,f),u,79915));
            assertThrows(RedoException.class,()->CapturedLobMaintenance.requireHbbReservationPair(r,u,79917));
            for(int offset:new int[]{0,4,12,16,20,24,28,32,36}) {
                after[offset]^=1;
                assertThrows(RedoException.class,()->CapturedLobMaintenance.requireHbbReservationPair(r,u,79915));
                after[offset]^=1;
            }
        }
    }
}
