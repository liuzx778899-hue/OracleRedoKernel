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
    public static byte[] checkpointCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        return prefixCaptured(captures,baseline,trustedBaselineSha256,leaf).checkpoint();
    }
    public static Prefix prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,leaf);
        return new Prefix(encode(snapshot,leaf,history,physical),physical.state().pending().size()+history.pending().size(),physical.completed().transactions().size());
    }
    public static CapturedHistoricalHeapBatch readCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,
                                                          long leaf,byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        if(!digest(checkpoint).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        String[] lines=new String(checkpoint,StandardCharsets.UTF_8).split("\n");
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
