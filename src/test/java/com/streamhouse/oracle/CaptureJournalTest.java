package com.streamhouse.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic framing/corruption checks; real Oracle support is verified by independent lab inputs. */
class CaptureJournalTest {
    @TempDir Path directory;
    private static void put16(byte[] b,int p,int n) { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putShort(p,(short)n); }
    private static void put32(byte[] b,int p,int n) { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(p,n); }
    private static byte[] digest(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(digest(bytes)); }
    private static void checksum(byte[] b) {
        put16(b,14,0);int value=0;for (int p=0;p<512;p+=2) value^=RedoBytes.u16(b,p);put16(b,14,value);
    }
    private static byte[][] blocks(int count) {
        byte[][] result=new byte[count][512];
        put16(result[0],0,0x2200);put32(result[0],20,512);put32(result[0],24,409599);put32(result[0],28,0x7a7b7c7d);
        for (int i=1;i<count;i++) { put16(result[i],0,0x2201);put32(result[i],4,i);put32(result[i],8,41); }
        byte[] header=result[1];put32(header,20,0x13000000);put32(header,24,17);put32(header,52,19);
        put32(header,156,count);put32(header,160,23);put16(header,176,1);put32(header,180,100);put32(header,192,200);
        for (byte[] b:result) checksum(b);
        return result;
    }
    private Path write(String name,byte[][] blocks,int mismatchedSourceBlock) throws Exception {
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        for (int i=0;i<blocks.length;i++) {
            byte[] raw=blocks[i];String metadata="{\"block\":"+i+",\"observed_unix_seconds\":1.0,\"sample\":0,\"sha256\":\""+sha(raw)
                    +"\",\"source\":{\"activation\":19,\"compatibility\":318767104,\"dbid\":"+(i==mismatchedSourceBlock?18:17)
                    +",\"resetlogs\":23,\"sequence\":41,\"thread\":1},\"state\":\"OBSERVED_STABLE\"}";
            byte[] encoded=metadata.getBytes(StandardCharsets.US_ASCII);stream.write("ORACAP01".getBytes(StandardCharsets.US_ASCII));
            stream.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(encoded.length).putInt(512).array());
            stream.write(encoded);stream.write(raw);ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(encoded);body.write(raw);stream.write(digest(body.toByteArray()));
        }
        Path path=directory.resolve(name);Files.write(path,stream.toByteArray());return path;
    }
    private CaptureJournal.Verified read(Path path) throws Exception { return new CaptureJournal().read(path,sha(Files.readAllBytes(path))); }

    @Test void retainsOnlineCapacityAndRawClosedHeader() throws Exception {
        var capture=read(write("closed",blocks(3),-1));
        assertEquals(409600,capture.source().capacityBlocks());assertEquals(3,capture.source().blocks());
        assertEquals(100,capture.source().firstScn());assertEquals(200,capture.source().nextScn());
        assertTrue(new RedoRecords().readCaptured(capture).isEmpty());
    }
    @Test void mixedSourceAndRehashedRawCorruptionAreRefused() throws Exception {
        Path mixed=write("mixed",blocks(3),2);
        assertEquals("CAPTURE_MIXED_SOURCE",assertThrows(RedoException.class,()->read(mixed)).getMessage());
        byte[][] blocks=blocks(3);blocks[2][40]^=1;Path corrupt=write("corrupt",blocks,-1);
        assertEquals("CAPTURE_BLOCK_CHECKSUM_MISMATCH",assertThrows(RedoException.class,()->read(corrupt)).getMessage());
    }
    @Test void receiptAndClosedRangeCannotBeSelfAuthorized() throws Exception {
        Path path=write("receipt",blocks(3),-1);
        assertEquals("CAPTURE_RECEIPT_SHA_MISMATCH",assertThrows(RedoException.class,()->new CaptureJournal().read(path,"0".repeat(64))).getMessage());
        byte[][] active=blocks(3);put32(active[1],156,-1);checksum(active[1]);Path current=write("current",active,-1);
        assertEquals("CAPTURE_CLOSED_RANGE_MISMATCH",assertThrows(RedoException.class,()->read(current)).getMessage());
        byte[][] missing=blocks(3);put32(missing[1],156,4);checksum(missing[1]);Path tail=write("tail",missing,-1);
        assertEquals("CAPTURE_CLOSED_RANGE_MISMATCH",assertThrows(RedoException.class,()->read(tail)).getMessage());
    }
    @Test void lateIncompleteRecordDoesNotReturnEarlierRecords() throws Exception {
        byte[][] raw=blocks(4);put32(raw[2],16,24);raw[2][20]=1;checksum(raw[2]);
        put32(raw[3],16,600);raw[3][20]=1;checksum(raw[3]);var capture=read(write("late",raw,-1));
        assertEquals("TRUNCATED_RECORD_BODY",assertThrows(RedoException.class,()->new RedoRecords().readCaptured(capture)).getMessage());
    }
}
