package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.streamhouse.oracle.RedoBytes.*;

/** Structural records and vectors only. Opcode meaning and CDC publication are separate gates. */
public final class RedoRecords {
    public record Vector(int opcodeMajor, int opcodeMinor, int blockClass, int file,
                         long dba, long scn, int sequence, long containerId, List<byte[]> fields) {}
    public record Record(long block, int offset, int length, int validity, long scn,
                         int subScn, long containerUid, List<Vector> vectors) {}
    @FunctionalInterface public interface Visitor { void accept(Record record) throws IOException; }

    public long visit(Path path, ArchivedRedo.Header header, Visitor visitor) throws IOException {
        try (FileChannel channel=FileChannel.open(path, StandardOpenOption.READ)) {
            return visit(index -> ArchivedRedo.readBlock(channel,index),header.blocks(),visitor);
        }
    }

    /** Buffered publication: a late invalid record never returns a partial capture batch. */
    public List<Record> readCaptured(CaptureJournal.Verified capture) throws IOException {
        List<Record> result=new ArrayList<>();
        visit(capture::block,capture.source().blocks(),result::add);
        return List.copyOf(result);
    }

    @FunctionalInterface private interface BlockReader { byte[] read(long index) throws IOException; }
    private long visit(BlockReader reader,long blocks,Visitor visitor) throws IOException {
        long count=0;
            Cursor cursor=new Cursor(reader,blocks);
            while (cursor.block < blocks) {
                if (512-cursor.offset < 24) { cursor.nextBlock(); continue; }
                long block=cursor.block;
                int offset=cursor.offset;
                long length=u32(cursor.bytes,offset);
                if (length == 0) { cursor.nextBlock(); continue; }
                if (length < 24 || length > 16*1024*1024 || length % 4 != 0)
                    throw new RedoException("INVALID_RECORD_LENGTH:block="+block+":offset="+offset);
                byte[] bytes=cursor.read((int)length);
                int validity=bytes[4]&255;
                // 0x09/0x0d observed and dump-matched on dedicated 19.3 CDB fixtures.
                if (validity != 1 && validity != 2 && validity != 4 && validity != 5
                        && validity != 6 && validity != 9 && validity != 13)
                    throw new RedoException("UNSUPPORTED_RECORD_VALIDITY:"+validity);
                int recordHeader=(validity & 4) != 0 ? 68 : 24;
                if (length < recordHeader) throw new RedoException("TRUNCATED_RECORD_HEADER");
                List<Vector> vectors=new ArrayList<>();
                if ((validity & 3) != 0) {
                    int pos=recordHeader;
                    while (pos < length) {
                        if (length-pos < 36) throw new RedoException("TRUNCATED_VECTOR_HEADER");
                        int fieldBytes=u16(bytes,pos+32);
                        if (fieldBytes < 2 || fieldBytes % 2 != 0 || fieldBytes > length-pos-32)
                            throw new RedoException("INVALID_VECTOR_FIELD_TABLE");
                        int data=pos+32+aligned(fieldBytes);
                        List<byte[]> fields=new ArrayList<>();
                        for (int f=pos+34; f<pos+32+fieldBytes; f+=2) {
                            int fieldLength=u16(bytes,f);
                            if (data > length-fieldLength || data > length-aligned(fieldLength))
                                throw new RedoException("TRUNCATED_VECTOR_FIELD");
                            fields.add(Arrays.copyOfRange(bytes,data,data+fieldLength));
                            data+=aligned(fieldLength);
                        }
                        vectors.add(new Vector(bytes[pos]&255,bytes[pos+1]&255,u16(bytes,pos+2),
                                u16(bytes,pos+4),u32(bytes,pos+8),scn(bytes,pos+12),bytes[pos+20]&255,
                                u32(bytes,pos+24),List.copyOf(fields)));
                        pos=data;
                    }
                }
                long scn=u32(bytes,8) | ((long)u16(bytes,6)<<32);
                visitor.accept(new Record(block,offset,(int)length,validity,scn,u16(bytes,12),
                        u32(bytes,16),List.copyOf(vectors)));
                count++;
            }
        return count;
    }

    private static final class Cursor {
        final BlockReader reader;
        final long blocks;
        long block=2;
        int offset=16;
        byte[] bytes;
        Cursor(BlockReader reader,long blocks) throws IOException {
            this.reader=reader; this.blocks=blocks; bytes=reader.read(block);
        }
        void nextBlock() throws IOException {
            block++; offset=16;
            if (block < blocks) bytes=reader.read(block);
        }
        byte[] read(int length) throws IOException {
            byte[] result=new byte[length];
            int copied=0;
            while (copied<length) {
                if (block >= blocks) throw new RedoException("TRUNCATED_RECORD_BODY");
                int n=Math.min(length-copied,512-offset);
                System.arraycopy(bytes,offset,result,copied,n);
                offset+=n; copied+=n;
                if (offset == 512) nextBlock();
            }
            return result;
        }
    }
}
