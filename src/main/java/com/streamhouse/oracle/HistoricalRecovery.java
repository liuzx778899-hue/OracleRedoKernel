package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Unified source-bound checkpoint. Raw prefix remains mandatory for dictionary and physical pair authority. */
public final class HistoricalRecovery {
    private HistoricalRecovery() {}
    public record Prefix(byte[] checkpoint,int pendingTransactions,int completedTransactions) {
        public Prefix { checkpoint=checkpoint.clone(); }
        @Override public byte[] checkpoint(){return checkpoint.clone();}
    }
    public record CommittedPrefix(Prefix state,CapturedHistoricalHeapBatch completed) {}
    /** Publishable complete business transactions; pending business graphs remain bound to the raw prefix. */
    public static CommittedPrefix committedPrefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,
                                                          String trustedBaselineSha256,long leaf)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        if(!history.pending().isEmpty())throw new RedoException("COMMITTED_PREFIX_DICTIONARY_PENDING");
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,leaf);
        var state=new Prefix(encodeDurable(inputs,snapshot,leaf,history,physical),physical.state().pending().size(),physical.completed().transactions().size());
        return new CommittedPrefix(state,CapturedHistoricalHeapBatch.readWithPhysical(List.copyOf(captures),inputs,snapshot,history,leaf,physical.completed()));
    }
    public static byte[] checkpointCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        return prefixCaptured(captures,baseline,trustedBaselineSha256,leaf).checkpoint();
    }
    public static Prefix prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        return prefixCaptured(captures,baseline,trustedBaselineSha256,leaf,false);
    }
    /** Re-derives an enrolled receipt in its authenticated format; new enrollment always emits V2. */
    public static Prefix verifyPrefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf,
                                             byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        byte[] input=checkpoint.clone();
        if(!digest(input).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        String format=new String(input,StandardCharsets.UTF_8).split("\n",-1)[0];
        if(!format.equals("ORK_HISTORICAL_RECOVERY_V1") && !format.equals("ORK_HISTORICAL_RECOVERY_V2"))
            throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");
        var actual=prefixCaptured(captures,baseline,trustedBaselineSha256,leaf,format.equals("ORK_HISTORICAL_RECOVERY_V1"));
        if(!Arrays.equals(input,actual.checkpoint()))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");
        return actual;
    }
    private static Prefix prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf,boolean legacy)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,leaf);
        return new Prefix(legacy?encode(snapshot,leaf,history,physical):encodeDurable(inputs,snapshot,leaf,history,physical),
                physical.state().pending().size()+history.pending().size(),physical.completed().transactions().size());
    }
    public static CapturedHistoricalHeapBatch readCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,
                                                          long leaf,byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        checkpoint=checkpoint.clone();
        if(!digest(checkpoint).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        String[] lines=new String(checkpoint,StandardCharsets.UTF_8).split("\n");
        if(lines.length>0 && lines[0].equals("ORK_HISTORICAL_RECOVERY_V2"))return readDurable(captures,inputs,snapshot,leaf,checkpoint,lines);
        if(lines.length<5 || !lines[0].equals("ORK_HISTORICAL_RECOVERY_V1") || !lines[1].equals("H|"+leaf+"|"+snapshot.sha256()))
            throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");
        int count=0;
        while(count+4<lines.length && lines[count+4].startsWith("S|")) {
            if(count>=inputs.size() || !lines[count+4].equals("S|"+digest(utf8(inputs.get(count).header().toString()))))
                throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
            count++;
        }
        if(count==0)throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        var prefix=inputs.prefix(count);var history=ArchivedDictionaryHistory.readPrefix(prefix,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(prefix,snapshot,history,leaf);
        if(!Arrays.equals(checkpoint,encode(snapshot,leaf,history,physical)))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");
        byte[] dictionary=history.checkpoint();
        var extended=ArchivedDictionaryHistory.restore(inputs,snapshot,dictionary,digest(dictionary));
        var transactions=ArchivedPhysicalTransactions.readHistoryRestored(inputs,snapshot,extended,leaf,physical);
        return CapturedHistoricalHeapBatch.readWithPhysical(List.copyOf(captures),inputs,snapshot,extended,leaf,transactions);
    }
    private static byte[] encodeDurable(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,long leaf,
                                       ArchivedDictionaryHistory.Timeline history,ArchivedPhysicalTransactions.Prefix physical)throws IOException {
        var base64=Base64.getEncoder();
        return utf8("ORK_HISTORICAL_RECOVERY_V2\nH|"+leaf+"|"+snapshot.sha256()+"\nD|"
                +base64.encodeToString(history.durablePendingState().checkpoint())+"\nP|"
                +base64.encodeToString(PhysicalRecoveryState.from(inputs,snapshot,leaf,physical).checkpoint())+"\n");
    }
    private static CapturedHistoricalHeapBatch readDurable(List<CaptureJournal.Verified> captures,RedoBatch inputs,
                                                          FrozenHeapBaseline.Snapshot snapshot,long leaf,byte[] checkpoint,String[] lines)throws IOException {
        if(lines.length!=4 || !lines[1].equals("H|"+leaf+"|"+snapshot.sha256()) || !lines[2].startsWith("D|") || !lines[3].startsWith("P|"))
            throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");
        byte[] dictionary,physical;
        try {dictionary=Base64.getDecoder().decode(lines[2].substring(2));physical=Base64.getDecoder().decode(lines[3].substring(2));}
        catch(IllegalArgumentException e){throw new RedoException("HISTORICAL_RECOVERY_MALFORMED");}
        var base64=Base64.getEncoder();
        byte[] canonical=utf8(lines[0]+"\n"+lines[1]+"\nD|"+base64.encodeToString(dictionary)+"\nP|"+base64.encodeToString(physical)+"\n");
        if(!Arrays.equals(checkpoint,canonical))throw new RedoException("HISTORICAL_RECOVERY_NONCANONICAL");
        var pending=DictionaryPendingState.restore(dictionary,digest(dictionary),snapshot);
        var prefix=PhysicalRecoveryState.restore(physical,digest(physical),snapshot,leaf).prefix();
        var sources=prefix.state().sources();
        if(!sources.equals(pending.decoded().sources()) || sources.size()>inputs.size())
            throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        for(int i=0;i<sources.size();i++)if(!sources.get(i).equals(inputs.get(i).header()))
            throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        var history=ArchivedDictionaryHistory.readRestored(inputs,snapshot,pending);
        var transactions=ArchivedPhysicalTransactions.readHistoryRestored(inputs,snapshot,history,leaf,prefix);
        return CapturedHistoricalHeapBatch.readWithPhysical(List.copyOf(captures),inputs,snapshot,history,leaf,transactions);
    }
    private static byte[] encode(FrozenHeapBaseline.Snapshot snapshot,long leaf,ArchivedDictionaryHistory.Timeline history,
                                 ArchivedPhysicalTransactions.Prefix physical)throws RedoException {
        var out=new StringBuilder("ORK_HISTORICAL_RECOVERY_V1\nH|").append(leaf).append('|').append(snapshot.sha256()).append('\n');
        var base64=Base64.getEncoder();out.append("D|").append(base64.encodeToString(history.checkpoint())).append('\n');
        out.append("I|").append(base64.encodeToString(utf8(physical.state().identityCheckpoint()))).append('\n');
        for(var source:physical.state().sources())out.append("S|").append(digest(utf8(source.toString()))).append('\n');
        for(var p:physical.state().pending())out.append("P|").append(p.xid().usn()).append('|').append(p.xid().slot()).append('|').append(p.xid().sequence())
                .append('|').append(p.segment()).append('|').append(p.next()).append('|').append(p.heaps().size()).append('|').append(p.indexes().size())
                .append('|').append(p.markers().size()).append('|').append(digest(utf8(p.operations().toString()))).append('|')
                .append(base64.encodeToString(utf8(p.graphCheckpoint()))).append('\n');
        for(var t:physical.completed().transactions())out.append("C|").append(digest(utf8(t.toString()))).append('\n');
        return utf8(out.toString());
    }
    private static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static String digest(byte[] bytes)throws RedoException {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new RedoException("SHA256_UNAVAILABLE");}
    }
}
