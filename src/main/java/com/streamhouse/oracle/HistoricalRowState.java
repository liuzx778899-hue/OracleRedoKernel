package com.streamhouse.oracle;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Committed full row images and replay order. Does not certify target publication or pending physical pairs. */
public final class HistoricalRowState {
    private static final Comparator<ArchivedHeapRows.RowKey> KEYS=Comparator.comparingLong(ArchivedHeapRows.RowKey::dataObjectId)
            .thenComparingLong(ArchivedHeapRows.RowKey::dba).thenComparingInt(ArchivedHeapRows.RowKey::slot);
    private final FrozenHeapBaseline.Snapshot baseline;
    private final HistoricalDictionaryState dictionary;
    private final long leaf,commitScn;
    private final ArchivedUndoInventory.Position commit;
    private final Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows;
    private final Map<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position> lastPositions;
    private HistoricalRowState(FrozenHeapBaseline.Snapshot baseline,HistoricalDictionaryState dictionary,long leaf,long commitScn,
                               ArchivedUndoInventory.Position commit,Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,
                               Map<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position> positions) {
        this.baseline=baseline;this.dictionary=dictionary;this.leaf=leaf;this.commitScn=commitScn;this.commit=commit;
        var copy=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();rows.forEach((k,v)->copy.put(k,List.copyOf(v)));
        this.rows=Map.copyOf(copy);this.lastPositions=Map.copyOf(positions);
    }
    static HistoricalRowState from(FrozenHeapBaseline.Snapshot baseline,long leaf,ArchivedDictionaryHistory.Timeline history,
                                   ArchivedPhysicalTransactions.Result physical,ArchivedHeapRows.Result rows)throws RedoException {
        var ordered=ArchivedHeapRows.commitOrdered(physical.transactions().stream().filter(t->t.identity().endRecordScn()>baseline.scn()).toList());
        var last=ordered.isEmpty()?null:ordered.get(ordered.size()-1);
        return new HistoricalRowState(baseline,history.materializedState(),leaf,last==null?baseline.scn():last.identity().endRecordScn(),
                last==null?null:last.commitPosition(),rows.rows(),rows.lastPositions());
    }
    public Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows(){return rows;}
    public Map<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position> lastPositions(){return lastPositions;}
    HistoricalDictionaryState dictionary(){return dictionary;}
    public List<FrozenHeapBaseline.PhysicalColumn> columns()throws RedoException {return dictionary.schemaAt(commitScn,commit);}
    boolean after(ArchivedPhysicalTransactions.Transaction transaction) {
        long scn=transaction.identity().endRecordScn();return scn>commitScn || scn==commitScn && commit!=null && compare(transaction.commitPosition(),commit)>0;
    }
    void requirePrefix(FrozenHeapBaseline.Snapshot snapshot,long expectedLeaf,ArchivedPhysicalTransactions.Result physical)throws RedoException {
        var sources=physical.sources();
        if(!baseline.sha256().equals(snapshot.sha256()) || leaf!=expectedLeaf || dictionary.sources().size()>sources.size()
                || !dictionary.sources().equals(sources.subList(0,dictionary.sources().size())) || commit!=null
                && physical.transactions().stream().noneMatch(t->t.identity().endRecordScn()==commitScn && t.commitPosition().equals(commit)))
            throw new RedoException("ROW_STATE_BASELINE_LEAF_OR_SOURCE_PREFIX");
    }
    public byte[] checkpoint()throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_HISTORICAL_ROW_STATE_V1");
        byte[] dict=dictionary.checkpoint();out.writeInt(dict.length);out.write(dict);out.writeLong(leaf);out.writeLong(commitScn);
        out.writeBoolean(commit!=null);if(commit!=null)position(out,commit);
        out.writeInt(rows.size());
        for(var key:rows.keySet().stream().sorted(KEYS).toList()) {
            key(out,key);out.writeInt(rows.get(key).size());for(var cell:rows.get(key)){out.writeUTF(cell.state().name());out.writeUTF(cell.hex());}
        }
        out.writeInt(lastPositions.size());
        for(var key:lastPositions.keySet().stream().sorted(KEYS).toList()){key(out,key);position(out,lastPositions.get(key));}
        out.flush();return bytes.toByteArray();
    }
    /** State SHA must be authenticated by durable control state, not supplied by the same untrusted file. */
    public static HistoricalRowState restore(byte[] bytes,String trustedStateSha256,Path baseline,String trustedBaselineSha256)throws IOException {
        return restore(bytes,trustedStateSha256,FrozenHeapBaseline.read(baseline,trustedBaselineSha256));
    }
    static HistoricalRowState restore(byte[] bytes,String trustedStateSha256,FrozenHeapBaseline.Snapshot baseline)throws IOException {
        byte[] input=bytes.clone();if(!sha(input).equals(trustedStateSha256))throw new RedoException("ROW_STATE_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));
            if(!in.readUTF().equals("ORK_HISTORICAL_ROW_STATE_V1"))throw new RedoException("ROW_STATE_FORMAT");
            byte[] dict=in.readNBytes(count(in));var dictionary=HistoricalDictionaryState.restore(dict,sha(dict),baseline);
            long leaf=in.readLong(),scn=in.readLong();var commit=in.readBoolean()?position(in):null;
            if(leaf<=0 || leaf>0xffffffffL || scn<baseline.scn() || (commit==null)!=(scn==baseline.scn()))throw new RedoException("ROW_STATE_COMMIT_BOUNDARY");
            if(commit!=null) {
                requirePosition(commit,dictionary);var member=dictionary.sources().stream().filter(h->h.sequence()==commit.archiveSequence()).findFirst().orElseThrow();
                if(scn<member.firstScn() || scn>=member.nextScn())throw new RedoException("ROW_STATE_COMMIT_BOUNDARY");
            }
            var columns=dictionary.schemaAt(scn,commit);var rows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
            for(int i=0,n=count(in);i<n;i++) {
                var key=key(in,baseline);int cells=count(in);if(cells!=columns.size())throw new RedoException("ROW_STATE_COLUMNS");
                var values=new ArrayList<ArchivedHeapRows.Cell>();for(int j=0;j<cells;j++)values.add(new ArchivedHeapRows.Cell(HeapRowPatch.State.valueOf(in.readUTF()),in.readUTF()));
                ArchivedHeapRows.requireCompleteValues(values,columns);
                if(rows.putIfAbsent(key,List.copyOf(values))!=null)throw new RedoException("ROW_STATE_DUPLICATE_ROW");
            }
            var positions=new HashMap<ArchivedHeapRows.RowKey,ArchivedUndoInventory.Position>();
            for(int i=0,n=count(in);i<n;i++) {
                var key=key(in,baseline);var p=position(in);requirePosition(p,dictionary);
                // A verified record can contain end before row vectors; vector ordinal is not its commit boundary.
                if(commit==null || compareRecord(p,commit)>0 || positions.putIfAbsent(key,p)!=null)throw new RedoException("ROW_STATE_ROW_ORDER");
            }
            var restored=new HistoricalRowState(baseline,dictionary,leaf,scn,commit,rows,positions);
            if(in.available()!=0 || !Arrays.equals(input,restored.checkpoint()))throw new RedoException("ROW_STATE_NONCANONICAL");return restored;
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException|NoSuchElementException|EOFException|UTFDataFormatException e){throw new RedoException("ROW_STATE_MALFORMED");}
    }
    private static void requirePosition(ArchivedUndoInventory.Position p,HistoricalDictionaryState dictionary)throws RedoException {
        var source=dictionary.sources().stream().filter(h->h.sequence()==p.archiveSequence()).findFirst().orElseThrow();
        if(p.block()<=0 || p.block()>=source.blocks() || p.offset()<0 || p.offset()>=512 || p.vector()<1)throw new RedoException("ROW_STATE_POSITION");
    }
    private static void key(DataOutputStream out,ArchivedHeapRows.RowKey k)throws IOException {out.writeLong(k.dataObjectId());out.writeLong(k.dba());out.writeInt(k.slot());}
    private static ArchivedHeapRows.RowKey key(DataInputStream in,FrozenHeapBaseline.Snapshot baseline)throws IOException {
        var k=new ArchivedHeapRows.RowKey(in.readLong(),in.readLong(),in.readInt());
        if(k.dataObjectId()!=baseline.heap().dataObject() || k.dba()<=0 || k.dba()>0xffffffffL || k.slot()<0 || k.slot()>65535)throw new RedoException("ROW_STATE_KEY");return k;
    }
    private static void position(DataOutputStream out,ArchivedUndoInventory.Position p)throws IOException {
        out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());
    }
    private static ArchivedUndoInventory.Position position(DataInputStream in)throws IOException {return new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());}
    private static int count(DataInputStream in)throws IOException {int n=in.readInt();if(n<0 || n>in.available())throw new RedoException("ROW_STATE_COUNT");return n;}
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());
        if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0?Integer.compare(a.vector(),b.vector()):c;
    }
    private static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());
        return c==0?Integer.compare(a.offset(),b.offset()):c;
    }
    private static String sha(byte[] bytes) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
