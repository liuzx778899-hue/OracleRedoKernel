package com.streamhouse.oracle;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeapArrayUpdatePairTest {
 byte[] hex(String s){return HexFormat.of().parseHex(s);}
 RedoRecords.Vector forward(){return new RedoRecords.Vector(11,19,1,12,0x0300b5b5L,0,1,3,new ArrayList<>(List.of(hex("110d0000000000000a000800f5130000b40d4002b0011800000000000000000000000000000000000000000002010d00c77b31000080000001060080a37b3100000000000000"),hex("b5b50003b2b50003fa1213010200200003003439"),hex("b5b50003b2b50003fa121581020020002c0200000000030200000300b5b50003b2b50003fa121581020000002c0200000100030200000300b5b50003b2b50003fa121581020000002c0200000200030200000300"),hex("010001000100"),hex("1a001a001a00000005000000050000000100000000000000"),hex("0d787c031f011f2e075bcd1585f80b787c031f011f2e075bcd150d787c031f021f2e3ade68b185f80b787c031f021f2e3ade68b10d787c031f08012e00000001111e0b787c031f08012e00000001"))),80574);}
 RedoRecords.Vector undo(){return new RedoRecords.Vector(5,1,36,11,0x02400db4L,0,2,3,new ArrayList<>(List.of(hex("3c016415120031000a000800f5130000b0011800"),hex("be3a0100be3a010005000000000000000b010800080c010000000000b40d4002b0011600427b310000000000447b310000000000597031000000000000000000b20d40020000000000000000"),hex("030d0e0011160000"),hex("b5b50003b2b50003fa1213010200200003000000"),hex("b5b50003b2b50003fa121581020020002c0000000000030200000300b5b50003b2b50003fa121581020000002c0000000100030200000300b5b50003b2b50003fa121581020000002c0000000200030200000300"),hex("010001000100"),hex("1a001a001a000000b882b78200000000050124000400ffff"),hex("0d787c031d17012e075bcd15195a0b787c031d17012e075bcd150d787c031d17012e075bcd15195a0b787c031d17012e075bcd150d787c031d17012e075bcd15195a0b787c031d17012e075bcd15"))));}
 @Test void raw111ArrayRetainsAllThreeOwnedUpdatesAndRejectsWrongExtentOrSlot()throws Exception {
  var f=forward();var u=undo();var record=new RedoRecords.Record(1,16,1000,13,1,1,677737149,List.of(f,u));
  var pairs=HeapArrayUndoPair.bind(record,f,3);assertEquals(3,pairs.size());
  for(int i=0;i<3;i++){assertEquals(i,pairs.get(i).forward().slot());assertEquals(HeapRowPatch.Kind.UPDATE,pairs.get(i).forward().kind());assertEquals(HeapRowPatch.State.ABSENT,pairs.get(i).forward().columns().get(0).state());assertEquals(13,pairs.get(i).forward().columns().get(1).bytes().length);assertEquals(11,pairs.get(i).undo().inverse().columns().get(2).bytes().length);}
  f.fields().get(2)[48]=0;assertThrows(RedoException.class,()->HeapArrayUndoPair.bind(record,f,3));
  var bad=forward();bad.fields().get(4)[0]=25;assertThrows(RedoException.class,()->HeapArrayUndoPair.forward(bad,3));
 }
}
