package com.streamhouse.oracle;

import java.io.*;
import java.util.*;

/** Complete committed dictionary values. The trusted state digest must come from durable control state. */
public final class HistoricalDictionaryState {
    private record Version(long scn,ArchivedUndoInventory.Position position,List<DictionaryBaseline.Row> rows) {
        Version { rows=List.copyOf(rows); }
    }
    private final FrozenHeapBaseline.Snapshot baseline;
    private final List<ArchivedRedo.Header> sources;
    private final List<Version> versions;
    private final List<Long> pendingBegins;
    private HistoricalDictionaryState(FrozenHeapBaseline.Snapshot baseline,List<ArchivedRedo.Header> sources,
                                      List<Version> versions,List<Long> pendingBegins) {
        this.baseline=baseline;this.sources=List.copyOf(sources);this.versions=List.copyOf(versions);this.pendingBegins=List.copyOf(pendingBegins);
    }
    static HistoricalDictionaryState from(FrozenHeapBaseline.Snapshot baseline,List<ArchivedRedo.Header> sources,
                                          List<ArchivedDictionaryHistory.Commit> commits,List<ArchivedDictionaryHistory.Pending> pending) {
        return new HistoricalDictionaryState(baseline,sources,commits.stream()
                .map(c->new Version(c.identity().endRecordScn(),c.commitPosition(),c.rows())).toList(),
                pending.stream().map(ArchivedDictionaryHistory.Pending::beginScn).toList());
    }
    public List<ArchivedRedo.Header> sources(){return sources;}
    public List<FrozenHeapBaseline.PhysicalColumn> schemaAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {
        return DictionaryHistoryRows.schema(baseline,rowsAt(scn,position));
    }
    public List<DictionaryBaseline.Row> rowsAt(long scn,ArchivedUndoInventory.Position position)throws RedoException {
        if(sources.isEmpty() || scn<baseline.scn() || scn>=sources.get(sources.size()-1).nextScn())
            throw new RedoException("DICTIONARY_SCHEMA_SCN_OUTSIDE_VERIFIED_WINDOW");
        if(pendingBegins.stream().anyMatch(begin->begin<=scn))throw new RedoException("DICTIONARY_SCHEMA_PENDING_SYS");
        List<DictionaryBaseline.Row> rows=baseline.dictionaryBaseline().rows();
        for(var version:versions) {
            if(version.scn()>scn || version.scn()==scn && position!=null && compare(version.position(),position)>0)break;
            rows=version.rows();
        }
        return rows;
    }
    public byte[] checkpoint()throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeUTF("ORK_HISTORICAL_DICTIONARY_STATE_V1");out.writeUTF(baseline.sha256());out.writeInt(sources.size());
        for(var h:sources) {
            out.writeUTF(h.sha256());out.writeLong(h.bytes());out.writeLong(h.dbid());out.writeLong(h.activationId());
            out.writeLong(h.compatibility());out.writeInt(h.thread());out.writeLong(h.sequence());out.writeLong(h.resetlogsId());
            out.writeLong(h.resetlogsScn());out.writeLong(h.firstScn());out.writeLong(h.nextScn());out.writeLong(h.blocks());
        }
        out.writeInt(versions.size());
        for(var v:versions) {
            out.writeLong(v.scn());out.writeLong(v.position().archiveSequence());out.writeLong(v.position().block());
            out.writeInt(v.position().offset());out.writeInt(v.position().vector());out.writeInt(v.rows().size());
            for(var row:v.rows()) {
                out.writeUTF(row.role());out.writeLong(row.target());out.writeLong(row.key().dataObjectId());
                out.writeLong(row.key().dba());out.writeInt(row.key().slot());out.writeInt(row.values().size());
                for(var cell:row.values()){out.writeUTF(cell.state().name());out.writeUTF(cell.hex());}
            }
        }
        out.writeInt(pendingBegins.size());for(long scn:pendingBegins)out.writeLong(scn);
        out.flush();return bytes.toByteArray();
    }
    /** Restores values without opening any raw member. It does not restore pending transaction graphs or authorize retention. */
    public static HistoricalDictionaryState restore(byte[] bytes,String trustedStateSha256,java.nio.file.Path baseline,
                                                     String trustedBaselineSha256)throws IOException {
        return restore(bytes,trustedStateSha256,FrozenHeapBaseline.read(baseline,trustedBaselineSha256));
    }
    static HistoricalDictionaryState restore(byte[] bytes,String trustedStateSha256,FrozenHeapBaseline.Snapshot baseline)throws IOException {
        byte[] input=bytes.clone();
        if(!digest(input).equals(trustedStateSha256))throw new RedoException("DICTIONARY_STATE_DIGEST_MISMATCH");
        if(baseline.dictionaryBaseline()==null)throw new RedoException("MISSING_V3_COMPLETE_DICTIONARY_BASELINE");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));
            if(!in.readUTF().equals("ORK_HISTORICAL_DICTIONARY_STATE_V1") || !in.readUTF().equals(baseline.sha256()))
                throw new RedoException("DICTIONARY_STATE_BASELINE_OR_FORMAT");
            var sources=new ArrayList<ArchivedRedo.Header>();
            for(int i=0,n=count(in);i<n;i++) {
                var h=new ArchivedRedo.Header(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),
                        in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());
                baseline.requireSource(h);
                if(!h.sha256().matches("[0-9a-f]{64}") || h.bytes()<=0 || h.blocks()<=0 || h.firstScn()>=h.nextScn()
                        || h.sequence()<=0 || !sources.isEmpty() && (h.sequence()!=sources.get(sources.size()-1).sequence()+1
                        || h.firstScn()!=sources.get(sources.size()-1).nextScn()))throw new RedoException("DICTIONARY_STATE_SOURCE_CONTINUITY");
                sources.add(h);
            }
            if(sources.isEmpty() || baseline.scn()<sources.get(0).firstScn() || baseline.scn()>=sources.get(sources.size()-1).nextScn())
                throw new RedoException("DICTIONARY_STATE_BASELINE_COVERAGE");
            var versions=new ArrayList<Version>();
            for(int i=0,n=count(in);i<n;i++) {
                long scn=in.readLong();var at=new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());
                var member=sources.stream().filter(h->h.sequence()==at.archiveSequence()).findFirst().orElseThrow();
                if(scn<=baseline.scn() || scn<member.firstScn() || scn>=member.nextScn() || at.block()<=0 || at.block()>=member.blocks()
                        || at.offset()<0 || at.offset()>=ArchivedRedo.BLOCK_SIZE || at.vector()<0
                        || !versions.isEmpty() && (scn<versions.get(i-1).scn() || scn==versions.get(i-1).scn()
                        && compare(at,versions.get(i-1).position())<=0))throw new RedoException("DICTIONARY_STATE_COMMIT_ORDER_OR_POSITION");
                var rows=new ArrayList<DictionaryBaseline.Row>();var keys=new HashSet<String>();
                for(int j=0,m=count(in);j<m;j++) {
                    String role=in.readUTF();long target=in.readLong();var key=new ArchivedHeapRows.RowKey(in.readLong(),in.readLong(),in.readInt());
                    var layout=baseline.dictionaryBaseline().layouts().get(role);int cells=count(in);
                    if(layout==null || key.dataObjectId()!=layout.role().dataObject() || key.dba()<=0 || key.dba()>0xffffffffL || key.slot()<0 || key.slot()>65535
                            || cells!=layout.columns().size() || !keys.add(role+":"+key))throw new RedoException("DICTIONARY_STATE_ROW_LAYOUT");
                    var values=new ArrayList<ArchivedHeapRows.Cell>();
                    for(int k=0;k<cells;k++)values.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.valueOf(in.readUTF()),in.readUTF()));
                    DictionaryHistoryRows.requireValues(layout,values);
                    var row=new DictionaryBaseline.Row(role,target,key,values);
                    if(baseline.dictionaryBaseline().number(row,"OBJ#")!=target || target!=baseline.heap().logicalObject()
                            && !(role.equals("OBJ$") && target==baseline.index().logicalObject()))throw new RedoException("DICTIONARY_STATE_ROW_PARENT");
                    rows.add(row);
                }
                versions.add(new Version(scn,at,rows));
            }
            var pending=new ArrayList<Long>();
            for(int i=0,n=count(in);i<n;i++) {
                long begin=in.readLong();if(begin<=baseline.scn() || begin>=sources.get(sources.size()-1).nextScn())
                    throw new RedoException("DICTIONARY_STATE_PENDING_RANGE");pending.add(begin);
            }
            var restored=new HistoricalDictionaryState(baseline,sources,versions,pending);
            if(in.available()!=0 || !Arrays.equals(input,restored.checkpoint()))throw new RedoException("DICTIONARY_STATE_NONCANONICAL");
            return restored;
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException|NoSuchElementException|EOFException|UTFDataFormatException e){throw new RedoException("DICTIONARY_STATE_MALFORMED");}
    }
    private static int count(DataInputStream in)throws IOException {
        int n=in.readInt();if(n<0 || n>in.available())throw new RedoException("DICTIONARY_STATE_COUNT");return n;
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());
        if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0?Integer.compare(a.vector(),b.vector()):c;
    }
    private static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
