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

    public record Source(String journalSha256,long journalBytes,long capacityBlocks,long blocks,long dbid,
                         long activationId,long compatibility,int thread,long sequence,
                         long resetlogsId,long resetlogsScn,long firstScn,long nextScn) {}
    public static final class Verified {
        private final Source source;
        private final List<byte[]> blocks;
        private Verified(Source source,List<byte[]> blocks) { this.source=source; this.blocks=List.copyOf(blocks); }
        public Source source() { return source; }
        byte[] block(long index) { return blocks.get(Math.toIntExact(index)).clone(); }
        /** Suppress a repeated closed observation; the first receipt and all redo payload bytes stay authoritative. */
        public boolean sameClosedGeneration(Verified other) {
            var a=source;var b=other.source;
            if (a.journalBytes()!=b.journalBytes() || a.capacityBlocks()!=b.capacityBlocks() || a.blocks()!=b.blocks()
                    || a.dbid()!=b.dbid() || a.activationId()!=b.activationId() || a.compatibility()!=b.compatibility()
                    || a.thread()!=b.thread() || a.sequence()!=b.sequence() || a.resetlogsId()!=b.resetlogsId()
                    || a.resetlogsScn()!=b.resetlogsScn() || a.firstScn()!=b.firstScn() || a.nextScn()!=b.nextScn()) return false;
            if (!Arrays.equals(blocks.get(0),other.blocks.get(0))) return false;
            byte[] header=blocks.get(1),next=other.blocks.get(1);
            // Only these differences were observed in the owned 19c closed-member snapshots.
            for (int i=0;i<512;i++)
                if (i!=14 && i!=15 && i!=36 && i!=172 && header[i]!=next[i]) return false;
            for (int i=2;i<blocks.size();i++)
                if (!Arrays.equals(blocks.get(i),other.blocks.get(i))) return false;
            return true;
        }
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
            validateBlock(raw,blocks.size(),source[4]);
            blocks.add(raw);offset=end+32;
        }
        return closed(actual,data.length,blocks,source);
    }

    /** Whole immutable member digest is transport evidence, not approval of its source or sequence. */
    public Verified readRawClosed(Path path,String expectedSha256) throws IOException {
        var observation=readRawObserved(path,expectedSha256);
        if (observation.closed()==null) throw new RedoException("CAPTURE_CLOSED_RANGE_MISMATCH");
        return observation.closed();
    }

    /** An open online header identifies a generation but never authorizes records or publication. */
    public record RawObservation(Source source,Verified closed) {}
    public RawObservation readRawObserved(Path path,String expectedSha256) throws IOException {
        byte[] data=Files.readAllBytes(path);
        String actual=hexDigest(data);
        if (!actual.equals(expectedSha256)) throw new RedoException("CAPTURE_RECEIPT_SHA_MISMATCH");
        if (data.length<1536 || data.length%512!=0)
            throw new RedoException("RAW_MEMBER_TRUNCATED_OR_UNALIGNED");
        byte[] file=Arrays.copyOfRange(data,0,512),header=Arrays.copyOfRange(data,512,1024);
        if (u32(file,24)+1!=data.length/512L)
            throw new RedoException("RAW_MEMBER_CAPACITY_MISMATCH");
        long nab=u32(header,156),seq=u32(header,8);
        if (nab==0xffffffffL) {
            validateBlock(file,0,seq);validateBlock(header,1,seq);
            if (u16(file,0)!=0x2200 || u32(file,20)!=512 || u32(file,28)!=0x7a7b7c7dL)
                throw new RedoException("CAPTURE_FILE_HEADER_INVALID");
            long compatibility=u32(header,20),first=scn(header,180),next=scn(header,192);
            if (seq==0 || u16(header,176)==0 || (compatibility!=0x13000000L && compatibility!=0x15000000L))
                throw new RedoException("CAPTURE_SOURCE_HEADER_MISMATCH");
            if (first>=next || next!=0x7fffffffffffL)
                throw new RedoException("CAPTURE_CLOSED_RANGE_MISMATCH");
            return new RawObservation(new Source(actual,data.length,data.length/512L,0,u32(header,24),
                    u32(header,52),compatibility,u16(header,176),seq,u32(header,160),scn(header,164),first,next),null);
        }
        if (nab<3 || nab>data.length/512L)
            throw new RedoException("CAPTURE_CLOSED_RANGE_MISMATCH");
        List<byte[]> blocks=new ArrayList<>();
        for (int i=0;i<nab;i++) {
            byte[] raw=Arrays.copyOfRange(data,i*512,(i+1)*512);
            validateBlock(raw,i,seq);blocks.add(raw);
        }
        long[] source={u32(header,52),u32(header,20),u32(header,24),u32(header,160),seq,u16(header,176)};
        var verified=closed(actual,data.length,blocks,source);
        return new RawObservation(verified.source(),verified);
    }

    private static void validateBlock(byte[] raw,int index,long sequence) throws RedoException {
        int checksum=0;
        for (int i=0;i<512;i+=2) checksum^=u16(raw,i);
        if (checksum!=0) throw new RedoException("CAPTURE_BLOCK_CHECKSUM_MISMATCH");
        if (index!=0 && (u16(raw,0)!=0x2201 || u32(raw,4)!=index || u32(raw,8)!=sequence))
            throw new RedoException("CAPTURE_BLOCK_IDENTITY_MISMATCH");
    }

    private static Verified closed(String actual,long bytes,List<byte[]> blocks,long[] source) throws RedoException {
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
        return new Verified(new Source(actual,bytes,capacity,nab,source[2],source[0],source[1],(int)source[5],
                source[4],source[3],scn(header,164),first,next),blocks);
    }

    private static byte[] digest(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String hexDigest(byte[] data) { return HexFormat.of().formatHex(digest(data)); }
}
