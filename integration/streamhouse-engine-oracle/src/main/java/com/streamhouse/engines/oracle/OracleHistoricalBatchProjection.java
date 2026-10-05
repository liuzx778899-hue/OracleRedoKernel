package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Projection of factory-owned history; each event keeps its complete schema. No publication permit. */
public final class OracleHistoricalBatchProjection {
    private OracleHistoricalBatchProjection() {}
    public record Entry(CapturedHeapBatch.Schema schema,OracleBatchProjection.Entry event) {}
    public static final class Batch {
        private final CapturedHistoricalHeapBatch input;
        private final List<Entry> entries;
        private final String schemaSequenceSha256;
        private Batch(CapturedHistoricalHeapBatch input,List<Entry> entries,String schemaSequenceSha256) {
            this.input=input;this.entries=List.copyOf(entries);this.schemaSequenceSha256=schemaSequenceSha256;
        }
        public String inputBatchSha256(){return input.sha256();}
        public ArchivedHeapRows.Source source(){return input.source();}
        public String baselineSha256(){return input.baselineSha256();}
        public long baselineScn(){return input.baselineScn();}
        public long leaf(){return input.leaf();}
        public List<CaptureJournal.Source> captures(){return input.captures();}
        public List<Entry> entries(){return entries;}
        public String schemaSequenceSha256(){return schemaSequenceSha256;}
    }
    public static Batch project(CapturedHistoricalHeapBatch input)throws RedoException {
        var entries=new ArrayList<Entry>();var ordinals=new HashMap<HeapRowPatch.Xid,Long>();
        for(var e:input.events())entries.add(new Entry(e.schema(),OracleBatchProjection.projectEntry(e.schema(),e.row(),input.sha256(),ordinals)));
        return new Batch(input,entries,schemaSequenceDigest(entries));
    }
    static String schemaSequenceDigest(List<Entry> entries) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeUTF("ORK_ORACLE_SCHEMA_SEQUENCE_V1");out.writeInt(entries.size());
            for(var e:entries) {
                out.writeUTF(e.event().envelope().eventId());out.writeUTF(e.schema().sha256());
                out.writeLong(e.event().transaction().endRecordScn());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException | NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
