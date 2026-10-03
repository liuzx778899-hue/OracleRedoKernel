package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static com.streamhouse.oracle.RedoBytes.*;

/** Frozen Linux x86_64 archived redo, 512-byte blocks; other layouts are refused. */
public final class ArchivedRedo {
    public static final int BLOCK_SIZE = 512;
    public record Header(String sha256, long bytes, long dbid, long activationId,
                         long compatibility, int thread, long sequence, long resetlogsId,
                         long resetlogsScn, long firstScn, long nextScn, long blocks) {}

    public Header scan(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < BLOCK_SIZE*3L || size % BLOCK_SIZE != 0)
                throw new RedoException("TRUNCATED_OR_UNALIGNED_FILE");
            byte[] first = readBlock(channel, 0);
            if (u16(first,0) != 0x2200 || u32(first,28) != 0x7a7b7c7dL)
                throw new RedoException("UNSUPPORTED_FILE_HEADER_OR_ENDIAN");
            if (u32(first,20) != BLOCK_SIZE)
                throw new RedoException("UNSUPPORTED_REDO_BLOCK_SIZE");
            if (u32(first,24)+1 != size/BLOCK_SIZE)
                throw new RedoException("ARCHIVE_FILE_LENGTH_MISMATCH");
            byte[] header = readBlock(channel, 1);
            long compatibility = u32(header,20);
            if (compatibility != 0x13000000L && compatibility != 0x15000000L)
                throw new RedoException("UNSUPPORTED_COMPATIBILITY");
            long seq = u32(header,8);
            if (u32(header,156) != size/BLOCK_SIZE)
                throw new RedoException("ARCHIVE_NAB_MISMATCH");
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
            digest.update(first);
            for (long index=1; index<size/BLOCK_SIZE; index++) {
                byte[] block = index == 1 ? header : readBlock(channel,index);
                if (u16(block,0) != 0x2201 || u32(block,4) != index || u32(block,8) != seq)
                    throw new RedoException("BLOCK_IDENTITY_MISMATCH:block="+index);
                int checksum=0;
                for (int offset=0; offset<BLOCK_SIZE; offset+=2) checksum ^= u16(block,offset);
                if (checksum != 0) throw new RedoException("BLOCK_CHECKSUM_MISMATCH:block="+index);
                digest.update(block);
            }
            int checksum=0;
            for (int offset=0; offset<BLOCK_SIZE; offset+=2) checksum ^= u16(first,offset);
            if (checksum != 0) throw new RedoException("FILE_HEADER_CHECKSUM_MISMATCH");
            long low=scn(header,180), high=scn(header,192);
            if (low >= high || u16(header,176) == 0)
                throw new RedoException("INVALID_ARCHIVE_SCN_OR_THREAD");
            if (channel.size() != size) throw new RedoException("INPUT_CHANGED_DURING_SCAN");
            return new Header(HexFormat.of().formatHex(digest.digest()),size,u32(header,24),
                    u32(header,52),compatibility,u16(header,176),seq,u32(header,160),
                    scn(header,164),low,high,size/BLOCK_SIZE);
        }
    }

    static byte[] readBlock(FileChannel channel, long index) throws IOException {
        ByteBuffer buffer=ByteBuffer.allocate(BLOCK_SIZE);
        while (buffer.hasRemaining()) {
            int n=channel.read(buffer,index*BLOCK_SIZE+buffer.position());
            if (n < 0) throw new RedoException("TRUNCATED_BLOCK:block="+index);
        }
        return buffer.array();
    }
}
