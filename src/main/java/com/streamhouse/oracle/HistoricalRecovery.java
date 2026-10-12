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
    /** Reconstructs sealed historical audit values without raw; never authorizes a new input or reclamation itself. */
    public static CapturedHistoricalHeapBatch auditState(List<CaptureJournal.Source> sources,Path baseline,String trustedBaselineSha256,
                                                         long leaf,byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        sources=List.copyOf(sources);checkpoint=checkpoint.clone();
        if(!digest(checkpoint).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        String[] lines=new String(checkpoint,StandardCharsets.UTF_8).split("\n");
        if(lines[0].equals("ORK_HISTORICAL_RECOVERY_V7"))return LobProducerRecovery.audit(sources,baseline,trustedBaselineSha256,leaf,checkpoint,trustedCheckpointSha256);
        if(lines.length==0 || !lines[0].equals("ORK_HISTORICAL_RECOVERY_V6"))throw new RedoException("READ_CUT_V6_REQUIRED");
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var values=decodeDurable(snapshot,leaf,checkpoint,lines,6);
        var physical=PhysicalRecoveryState.restore(values[1],digest(values[1]),snapshot,leaf).prefix();
        var pending=DictionaryPendingState.restore(values[0],digest(values[0]),snapshot).decoded();
        if(!pending.sources().equals(physical.state().sources()) || !pending.contexts().isEmpty())throw new RedoException("READ_CUT_DICTIONARY_PENDING");
        if(sources.size()!=pending.sources().size())throw new RedoException("READ_CUT_AUDIT_SOURCES_CHANGED");
        for(int i=0;i<sources.size();i++) {
            var s=sources.get(i);var h=pending.sources().get(i);
            if(s.capacityBlocks()<s.blocks() || !new ArchivedRedo.Header(s.journalSha256(),s.journalBytes(),s.dbid(),s.activationId(),s.compatibility(),
                    s.thread(),s.sequence(),s.resetlogsId(),s.resetlogsScn(),s.firstScn(),s.nextScn(),s.blocks()).equals(h))
                throw new RedoException("READ_CUT_AUDIT_SOURCES_CHANGED");
        }
        var producer=HistoricalProducerState.restore(values[2],digest(values[2]),snapshot,leaf,physical.completed());
        var commits=DictionaryCommittedState.restore(values[3],digest(values[3]),snapshot,producer.rows().dictionary());
        var history=ArchivedDictionaryHistory.committedState(snapshot,pending.sources(),commits);
        var rows=new ArchivedHeapRows.Result(pending.sources(),producer.changes(),producer.rows().rows(),producer.rows().lastPositions());
        return CapturedHistoricalHeapBatch.readWithSourceRows(sources,snapshot,history,leaf,physical.completed(),rows);
    }
    /** Materialized read origin only. The caller must separately prove target/control/ACK closure before reclamation. */
    public record ReadCut(byte[] baseline,String baselineSha256,long scn,long requiredRawFirstSequence,long leaf,String schemaSha256) {
        public ReadCut(byte[] baseline,String baselineSha256,long scn,long requiredRawFirstSequence) {
            this(baseline,baselineSha256,scn,requiredRawFirstSequence,0,null);
        }
        public ReadCut { baseline=baseline.clone(); }
        @Override public byte[] baseline(){return baseline.clone();}
    }
    /** Conservative static-schema cut; retain the final member for baseline coverage and boundary transactions. */
    public static ReadCut readCutCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,
                                         long leaf,byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        captures=List.copyOf(captures);checkpoint=checkpoint.clone();
        if(new String(checkpoint,StandardCharsets.UTF_8).startsWith("ORK_HISTORICAL_RECOVERY_V7\n"))
            return LobProducerRecovery.cut(captures,baseline,trustedBaselineSha256,leaf,checkpoint,trustedCheckpointSha256);
        var state=verifyPrefixCaptured(captures,baseline,trustedBaselineSha256,leaf,checkpoint,trustedCheckpointSha256);
        if(state.pendingTransactions()!=0)throw new RedoException("READ_CUT_PENDING_TRANSACTIONS");
        String[] lines=new String(checkpoint,StandardCharsets.UTF_8).split("\n");
        if(!lines[0].equals("ORK_HISTORICAL_RECOVERY_V6"))throw new RedoException("READ_CUT_V6_REQUIRED");
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);
        if(!snapshot.lobs().isEmpty())throw new RedoException("READ_CUT_LOB_NOT_IMPLEMENTED");
        var values=decodeDurable(snapshot,leaf,checkpoint,lines,6);
        var physical=PhysicalRecoveryState.restore(values[1],digest(values[1]),snapshot,leaf).prefix();
        var producer=HistoricalProducerState.restore(values[2],digest(values[2]),snapshot,leaf,physical.completed());
        var last=physical.state().sources().get(physical.state().sources().size()-1);long scn=last.nextScn()-1;
        if(scn<=snapshot.scn())throw new RedoException("READ_CUT_NOT_ADVANCING");
        var dictionary=producer.rows().dictionary();
        if(!dictionary.rowsAt(scn,null).equals(snapshot.dictionaryBaseline().rows())
                || !dictionary.schemaAt(scn,null).equals(snapshot.columns()))throw new RedoException("READ_CUT_SCHEMA_CHANGE_NOT_IMPLEMENTED");
        // Preserve the complete authenticated SYS baseline; only parsed business rows and SCN advance.
        byte[] template=java.nio.file.Files.readAllBytes(baseline);
        if(!digest(template).equals(trustedBaselineSha256))throw new RedoException("READ_CUT_BASELINE_CHANGED");
        var output=new StringBuilder();boolean emitted=false;
        for(String line:new String(template,StandardCharsets.US_ASCII).split("\n")) {
            line=line.stripTrailing();
            if(line.startsWith("SNAPSHOT_SCN|")){output.append("SNAPSHOT_SCN|").append(scn).append('\n');continue;}
            if(line.startsWith("ROW|"))continue;
            if(line.startsWith("ROW_COUNT|")) {
                var rows=producer.rows().rows();
                for(var key:rows.keySet().stream().sorted(Comparator.comparingLong(ArchivedHeapRows.RowKey::dataObjectId)
                        .thenComparingLong(ArchivedHeapRows.RowKey::dba).thenComparingInt(ArchivedHeapRows.RowKey::slot)).toList()) {
                    output.append("ROW|").append(key.dataObjectId()).append('|').append(key.dba()>>>22).append('|')
                            .append(key.dba()&0x3fffffL).append('|').append(key.slot());
                    for(var cell:rows.get(key)) {
                        if(cell.state()!=HeapRowPatch.State.NULL && cell.state()!=HeapRowPatch.State.VALUE)throw new RedoException("READ_CUT_INCOMPLETE_ROW");
                        output.append('|').append(cell.state()==HeapRowPatch.State.NULL?"NULL":cell.hex().toUpperCase(Locale.ROOT));
                    }
                    output.append('\n');
                }
                output.append("ROW_COUNT|").append(rows.size()).append('\n');emitted=true;
            }else output.append(line).append('\n');
        }
        if(!emitted)throw new RedoException("READ_CUT_BASELINE_ROWS_MISSING");
        byte[] bytes=output.toString().getBytes(StandardCharsets.US_ASCII);
        return new ReadCut(bytes,digest(bytes),scn,last.sequence());
    }
    /** Publishable complete business transactions; pending business graphs remain bound to the raw prefix. */
    public static CommittedPrefix committedPrefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,
                                                          String trustedBaselineSha256,long leaf)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        if(!snapshot.lobs().isEmpty())return LobProducerRecovery.enroll(captures,baseline,trustedBaselineSha256,leaf);
        var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        if(!history.pending().isEmpty())throw new RedoException("COMMITTED_PREFIX_DICTIONARY_PENDING");
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,leaf);
        var rows=ArchivedHeapRows.readHistory(inputs,snapshot,leaf,history,physical.completed());
        var state=new Prefix(encodeProducer(inputs,snapshot,leaf,history,physical,rows,6),physical.state().pending().size(),physical.completed().transactions().size());
        return new CommittedPrefix(state,CapturedHistoricalHeapBatch.readWithRows(List.copyOf(captures),snapshot,history,leaf,physical.completed(),rows));
    }
    public static byte[] checkpointCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        return prefixCaptured(captures,baseline,trustedBaselineSha256,leaf).checkpoint();
    }
    public static Prefix prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf)throws IOException {
        if(!FrozenHeapBaseline.read(baseline,trustedBaselineSha256).lobs().isEmpty())return LobProducerRecovery.enroll(captures,baseline,trustedBaselineSha256,leaf).state();
        return prefixCaptured(captures,baseline,trustedBaselineSha256,leaf,6);
    }
    /** Re-derives an enrolled receipt in its authenticated format; new enrollment emits V6. */
    public static Prefix verifyPrefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf,
                                             byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        byte[] input=checkpoint.clone();
        if(!digest(input).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        String format=new String(input,StandardCharsets.UTF_8).split("\n",-1)[0];
        if(format.equals("ORK_HISTORICAL_RECOVERY_V7"))return LobProducerRecovery.verify(captures,baseline,trustedBaselineSha256,leaf,input,trustedCheckpointSha256);
        int version=switch(format){case "ORK_HISTORICAL_RECOVERY_V1"->1;case "ORK_HISTORICAL_RECOVERY_V2"->2;case "ORK_HISTORICAL_RECOVERY_V3"->3;case "ORK_HISTORICAL_RECOVERY_V4"->4;case "ORK_HISTORICAL_RECOVERY_V5"->5;case "ORK_HISTORICAL_RECOVERY_V6"->6;default->throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");};
        var actual=prefixCaptured(captures,baseline,trustedBaselineSha256,leaf,version);
        if(!Arrays.equals(input,actual.checkpoint()))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");
        return actual;
    }
    private static Prefix prefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf,int version)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        var history=ArchivedDictionaryHistory.readPrefix(inputs,snapshot);
        var physical=ArchivedPhysicalTransactions.readHistoryPrefix(inputs,snapshot,history,leaf);
        byte[] checkpoint=version==1?encode(snapshot,leaf,history,physical):version==2?encodeDurable(inputs,snapshot,leaf,history,physical)
                :encodeProducer(inputs,snapshot,leaf,history,physical,ArchivedHeapRows.readHistory(inputs,snapshot,leaf,history,physical.completed()),version);
        return new Prefix(checkpoint,
                physical.state().pending().size()+history.pending().size(),physical.completed().transactions().size());
    }
    public static CapturedHistoricalHeapBatch readCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,
                                                          long leaf,byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        checkpoint=checkpoint.clone();
        if(!digest(checkpoint).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        var snapshot=FrozenHeapBaseline.read(baseline,trustedBaselineSha256);var inputs=RedoBatch.captures(captures);
        String[] lines=new String(checkpoint,StandardCharsets.UTF_8).split("\n");
        if(lines[0].equals("ORK_HISTORICAL_RECOVERY_V7"))return LobProducerRecovery.restore(captures,baseline,trustedBaselineSha256,leaf,checkpoint,trustedCheckpointSha256).completed();
        if(lines.length>0 && (lines[0].equals("ORK_HISTORICAL_RECOVERY_V3") || lines[0].equals("ORK_HISTORICAL_RECOVERY_V4") || lines[0].equals("ORK_HISTORICAL_RECOVERY_V5") || lines[0].equals("ORK_HISTORICAL_RECOVERY_V6"))) {
            var recovered=recoverProducer(captures,inputs,snapshot,leaf,checkpoint,lines);
            if(recovered.state().pendingTransactions()!=0)throw new RedoException("INCOMPLETE_TRANSACTION_IDENTITIES:"+recovered.state().pendingTransactions());
            return recovered.completed();
        }
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
    private static byte[] encodeProducer(RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,long leaf,
                                        ArchivedDictionaryHistory.Timeline history,ArchivedPhysicalTransactions.Prefix physical,ArchivedHeapRows.Result rows,int version)throws IOException {
        byte[] state=HistoricalProducerState.encode(HistoricalRowState.from(snapshot,leaf,history,physical.completed(),rows),rows.changes());
        String prefix=new String(encodeDurable(inputs,snapshot,leaf,history,physical),StandardCharsets.UTF_8);
        String value=prefix.replace("ORK_HISTORICAL_RECOVERY_V2\n","ORK_HISTORICAL_RECOVERY_V"+version+"\n")+"R|"+Base64.getEncoder().encodeToString(state)+"\n";
        if(version>=4)value+="K|"+Base64.getEncoder().encodeToString(DictionaryCommittedState.encode(history.commits()))+"\n";
        if(version>=5)value+="W|"+Base64.getEncoder().encodeToString(PhysicalDiscoveryState.encode(physical.state().windows()))+"\n";
        if(version==6) {
            if(physical.state().discovery()==null)throw new RedoException("PRODUCER_ASSOCIATION_DISCOVERY_MISSING");
            value+="A|"+physical.state().discovery()+"\n";
        }
        return utf8(value);
    }
    /** Advances authenticated producer state; computing events never certifies target commit or ACK. */
    public static CommittedPrefix extendPrefixCaptured(List<CaptureJournal.Verified> captures,Path baseline,String trustedBaselineSha256,long leaf,
                                                      byte[] checkpoint,String trustedCheckpointSha256)throws IOException {
        byte[] input=checkpoint.clone();if(!digest(input).equals(trustedCheckpointSha256))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        String[] lines=new String(input,StandardCharsets.UTF_8).split("\n");
        if(lines[0].equals("ORK_HISTORICAL_RECOVERY_V7"))return LobProducerRecovery.restore(captures,baseline,trustedBaselineSha256,leaf,input,trustedCheckpointSha256);
        if(lines.length==0 || !lines[0].equals("ORK_HISTORICAL_RECOVERY_V3") && !lines[0].equals("ORK_HISTORICAL_RECOVERY_V4") && !lines[0].equals("ORK_HISTORICAL_RECOVERY_V5") && !lines[0].equals("ORK_HISTORICAL_RECOVERY_V6"))throw new RedoException("PRODUCER_RECOVERY_V3_REQUIRED");
        return recoverProducer(captures,RedoBatch.captures(captures),FrozenHeapBaseline.read(baseline,trustedBaselineSha256),leaf,input,lines);
    }
    private static CommittedPrefix recoverProducer(List<CaptureJournal.Verified> captures,RedoBatch inputs,FrozenHeapBaseline.Snapshot snapshot,
                                                  long leaf,byte[] checkpoint,String[] lines)throws IOException {
        int version=lines[0].equals("ORK_HISTORICAL_RECOVERY_V6")?6:lines[0].equals("ORK_HISTORICAL_RECOVERY_V5")?5:lines[0].equals("ORK_HISTORICAL_RECOVERY_V4")?4:3;
        byte[][] values=decodeDurable(snapshot,leaf,checkpoint,lines,version);
        var pending=DictionaryPendingState.restore(values[0],digest(values[0]),snapshot);
        var physical=PhysicalRecoveryState.restore(values[1],digest(values[1]),snapshot,leaf).prefix();
        if(version>=5)physical=PhysicalDiscoveryState.restore(values[4],snapshot,physical);
        if(version==6) {
            var discovery=AssociationDiscoveryState.restore(values[5],digest(values[5]),physical.state().sources(),snapshot.source().containerUid(),snapshot.source().containerId());
            for(var value:physical.state().windows()) {
                byte[] bytes=Base64.getDecoder().decode(value);discovery.requireWindow(ArchivedCompensationInventory.restoreDurable(bytes,digest(bytes)));
            }
            physical=new ArchivedPhysicalTransactions.Prefix(physical.completed(),new ArchivedPhysicalTransactions.PendingState(physical.state().sources(),physical.state().pending(),physical.state().identityCheckpoint(),physical.state().windows(),Base64.getEncoder().encodeToString(values[5])));
        }
        requireSources(inputs,pending,physical);
        var producer=HistoricalProducerState.restore(values[2],digest(values[2]),snapshot,leaf,physical.completed());
        var history=version>=4?ArchivedDictionaryHistory.readRestoredIncremental(inputs,snapshot,pending,
                DictionaryCommittedState.restore(values[3],digest(values[3]),snapshot,producer.rows().dictionary()))
                :ArchivedDictionaryHistory.readRestored(inputs,snapshot,pending);
        var extended=ArchivedPhysicalTransactions.readHistoryRestoredPrefix(inputs,snapshot,history,leaf,physical);
        var delta=ArchivedHeapRows.readHistoryRestored(inputs,snapshot,leaf,history,extended.completed(),producer.rows());
        var changes=new ArrayList<>(producer.changes());changes.addAll(delta.changes());
        var rows=new ArchivedHeapRows.Result(delta.sources(),List.copyOf(changes),delta.rows(),delta.lastPositions());
        var batch=CapturedHistoricalHeapBatch.readWithRows(List.copyOf(captures),snapshot,history,leaf,extended.completed(),rows);
        var next=new Prefix(encodeProducer(inputs,snapshot,leaf,history,extended,rows,version),extended.state().pending().size()+history.pending().size(),extended.completed().transactions().size());
        return new CommittedPrefix(next,batch);
    }
    private static CapturedHistoricalHeapBatch readDurable(List<CaptureJournal.Verified> captures,RedoBatch inputs,
                                                          FrozenHeapBaseline.Snapshot snapshot,long leaf,byte[] checkpoint,String[] lines)throws IOException {
        byte[][] values=decodeDurable(snapshot,leaf,checkpoint,lines,2);
        var pending=DictionaryPendingState.restore(values[0],digest(values[0]),snapshot);
        var prefix=PhysicalRecoveryState.restore(values[1],digest(values[1]),snapshot,leaf).prefix();
        requireSources(inputs,pending,prefix);
        var history=ArchivedDictionaryHistory.readRestored(inputs,snapshot,pending);
        var transactions=ArchivedPhysicalTransactions.readHistoryRestored(inputs,snapshot,history,leaf,prefix);
        return CapturedHistoricalHeapBatch.readWithPhysical(List.copyOf(captures),inputs,snapshot,history,leaf,transactions);
    }
    private static void requireSources(RedoBatch inputs,DictionaryPendingState pending,ArchivedPhysicalTransactions.Prefix prefix)throws IOException {
        var sources=prefix.state().sources();
        if(!sources.equals(pending.decoded().sources()) || sources.size()>inputs.size())
            throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
        for(int i=0;i<sources.size();i++)if(!sources.get(i).equals(inputs.get(i).header()))
            throw new RedoException("HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED");
    }
    private static byte[][] decodeDurable(FrozenHeapBaseline.Snapshot snapshot,long leaf,byte[] checkpoint,String[] lines,int parts)throws IOException {
        if(lines.length!=parts+2 || !lines[1].equals("H|"+leaf+"|"+snapshot.sha256()))throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");
        String[] names={"D|","P|","R|","K|","W|","A|"};byte[][] values=new byte[parts][];var canonical=new StringBuilder(lines[0]+"\n"+lines[1]+"\n");
        for(int i=0;i<parts;i++) {
            if(!lines[i+2].startsWith(names[i]))throw new RedoException("HISTORICAL_RECOVERY_BASELINE_OR_LEAF");
            try{values[i]=Base64.getDecoder().decode(lines[i+2].substring(2));}
            catch(IllegalArgumentException e){throw new RedoException("HISTORICAL_RECOVERY_MALFORMED");}
            canonical.append(names[i]).append(Base64.getEncoder().encodeToString(values[i])).append('\n');
        }
        if(!Arrays.equals(checkpoint,utf8(canonical.toString())))throw new RedoException("HISTORICAL_RECOVERY_NONCANONICAL");return values;
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
