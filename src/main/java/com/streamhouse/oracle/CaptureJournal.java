package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import static com.streamhouse.oracle.RedoBytes.*;

/** ORACAP01 canonical closed-generation profile; raw online headers stay unchanged. */
public final class CaptureJournal {
    private static final byte[] MAGIC="ORACAP01".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern METADATA=Pattern.compile(
            "\\{\"block\":([0-9]+),\"observed_unix_seconds\":([0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?),"
            +"\"sample\":([0-9]+),\"sha256\":\"([a-f0-9]{64})\",\"source\":\\{"
            +"\"activation\":([0-9]+),\"compatibility\":([0-9]+),\"dbid\":([0-9]+),"
            +"\"resetlogs\":([0-9]+),\"sequence\":([0-9]+),\"thread\":([0-9]+)\\},"
            +"\"state\":\"OBSERVED_STABLE\"\\}");

    public record Source(String journalSha256,long capacityBlocks,long blocks,long dbid,
                         long activationId,long compatibility,int thread,long sequence,
                         long resetlogsId,long resetlogsScn,long firstScn,long nextScn) {}
    public static final class Verified {
        private final Source source;
        private final List<byte[]> blocks;
        private Verified(Source source,List<byte[]> blocks) { this.source=source; this.blocks=List.copyOf(blocks); }
        public Source source() { return source; }
        byte[] block(long index) { return blocks.get(Math.toIntExact(index)).clone(); }
    }

    /** expectedSha256 comes from the trusted acquisition receipt, never from this journal itself. */
    public Verified read(Path path,String expectedSha256) throws IOException {
        byte[] data=Files.readAllBytes(path);
        String actual=hexDigest(data);
        if (!actual.equals(expectedSha256)) throw new RedoException("CAPTURE_RECEIPT_SHA_MISMATCH");
        List<byte[]> blocks=new ArrayList<>();
        long[] source=null;
        long sample=-1;
        int offset=0;
        while (offset<data.length) {
            if (data.length-offset<16 || !Arrays.equals(MAGIC,Arrays.copyOfRange(data,offset,offset+8)))
                throw new RedoException("CAPTURE_TRUNCATED_OR_INVALID_HEADER");
            long metadataLength=u32(data,offset+8),payloadLength=u32(data,offset+12);
            offset+=16;
            if (metadataLength<2 || metadataLength>4096 || payloadLength!=512 || data.length-offset<metadataLength+544)
                throw new RedoException("CAPTURE_TRUNCATED_OR_INVALID_FRAME");
            int end=offset+(int)metadataLength+512;
            if (!Arrays.equals(digest(Arrays.copyOfRange(data,offset,end)),Arrays.copyOfRange(data,end,end+32)))
                throw new RedoException("CAPTURE_ENVELOPE_DIGEST_MISMATCH");
            var metadata=METADATA.matcher(new String(data,offset,(int)metadataLength,StandardCharsets.US_ASCII));
            if (!metadata.matches()) throw new RedoException("CAPTURE_UNSUPPORTED_METADATA_PROFILE");
            if (Long.parseLong(metadata.group(1))!=blocks.size() || !Double.isFinite(Double.parseDouble(metadata.group(2))))
                throw new RedoException("CAPTURE_BLOCK_ORDER_OR_TIME_INVALID");
            long currentSample=Long.parseLong(metadata.group(3));
            if (currentSample<sample) throw new RedoException("CAPTURE_SAMPLE_ORDER_INVALID");
            sample=currentSample;
            long[] identity=new long[6];
            for (int i=0;i<identity.length;i++) identity[i]=Long.parseLong(metadata.group(i+5));
            if (source==null) source=identity;
            else if (!Arrays.equals(source,identity)) throw new RedoException("CAPTURE_MIXED_SOURCE");
            byte[] raw=Arrays.copyOfRange(data,offset+(int)metadataLength,end);
            if (!hexDigest(raw).equals(metadata.group(4))) throw new RedoException("CAPTURE_BLOCK_DIGEST_MISMATCH");
            int checksum=0;
            for (int i=0;i<512;i+=2) checksum^=u16(raw,i);
            if (checksum!=0) throw new RedoException("CAPTURE_BLOCK_CHECKSUM_MISMATCH");
            if (!blocks.isEmpty() && (u16(raw,0)!=0x2201 || u32(raw,4)!=blocks.size() || u32(raw,8)!=source[4]))
                throw new RedoException("CAPTURE_BLOCK_IDENTITY_MISMATCH");
            blocks.add(raw);offset=end+32;
        }
        if (blocks.size()<3) throw new RedoException("CAPTURE_MISSING_CLOSED_RANGE");
        byte[] file=blocks.get(0),header=blocks.get(1);
        if (u16(file,0)!=0x2200 || u32(file,20)!=512 || u32(file,28)!=0x7a7b7c7dL)
            throw new RedoException("CAPTURE_FILE_HEADER_INVALID");
        long capacity=u32(file,24)+1,nab=u32(header,156),first=scn(header,180),next=scn(header,192);
        if (nab!=blocks.size() || nab>capacity || first>=next || next==0x7fffffffffffL)
            throw new RedoException("CAPTURE_CLOSED_RANGE_MISMATCH");
        long[] actualSource={u32(header,52),u32(header,20),u32(header,24),u32(header,160),u32(header,8),u16(header,176)};
        if (!Arrays.equals(source,actualSource) || actualSource[5]==0 || actualSource[4]==0
                || (actualSource[1]!=0x13000000L && actualSource[1]!=0x15000000L))
            throw new RedoException("CAPTURE_SOURCE_HEADER_MISMATCH");
        return new Verified(new Source(actual,capacity,nab,source[2],source[0],source[1],(int)source[5],
                source[4],source[3],scn(header,164),first,next),blocks);
    }

    private static byte[] digest(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String hexDigest(byte[] data) { return HexFormat.of().formatHex(digest(data)); }
}
