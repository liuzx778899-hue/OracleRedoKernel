package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Unified LOB producer checkpoint. Publication and reclamation still require the external target/control/ACK commit. */
final class LobProducerRecovery {
    private record Derived(LobOriginCapsule origin,LobOriginCapsule.Parsed parsed,CapturedHistoricalHeapBatch batch) {}
    static HistoricalRecovery.CommittedPrefix enroll(List<CaptureJournal.Verified> captures,Path path,String sha,long leaf)throws IOException {
        byte[] baseline=Files.readAllBytes(path);var snapshot=FrozenHeapBaseline.read(baseline,sha);var inputs=RedoBatch.captures(captures);
        LobOriginCapsule origin;LobOriginCapsule.Parsed parsed;
        if(snapshot.lobOrigin()!=null) {
            origin=embedded(snapshot);origin.requireCut(snapshot,leaf);parsed=origin.parse(inputs);
        } else {
            var history=ArchivedDictionaryHistory.read(inputs,snapshot);
            var values=CapturedLobTrustedBatch.prepare(inputs,snapshot,leaf,history);
            origin=LobOriginCapsule.create(inputs,baseline,leaf,history);parsed=new LobOriginCapsule.Parsed(inputs,snapshot,history,values);
        }
        var batch=batch(snapshot,leaf,parsed);
        origin=LobOriginCapsule.create(parsed.inputs(),origin.baseline(),origin.leaf(),parsed.history());
        return new HistoricalRecovery.CommittedPrefix(prefix(snapshot,leaf,origin,batch),batch);
    }
    static HistoricalRecovery.CommittedPrefix restore(List<CaptureJournal.Verified> captures,Path path,String sha,long leaf,byte[] checkpoint,String trustedSha)throws IOException {
        var snapshot=FrozenHeapBaseline.read(path,sha);var origin=decode(snapshot,leaf,checkpoint,trustedSha);
        var prior=origin.parse(RedoBatch.captures(List.of()));
        var previous=batch(snapshot,leaf,prior);
        if(!Arrays.equals(checkpoint,prefix(snapshot,leaf,origin,previous).checkpoint()))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");
        if(snapshot.lobOrigin()!=null)embedded(snapshot).requireCut(snapshot,leaf);
        var parsed=origin.parse(RedoBatch.captures(captures));var batch=batch(snapshot,leaf,parsed);
        var next=LobOriginCapsule.create(parsed.inputs(),origin.baseline(),origin.leaf(),parsed.history());
        return new HistoricalRecovery.CommittedPrefix(prefix(snapshot,leaf,next,batch),batch);
    }
    static HistoricalRecovery.Prefix verify(List<CaptureJournal.Verified> captures,Path path,String sha,long leaf,byte[] checkpoint,String trustedSha)throws IOException {
        var restored=restore(captures,path,sha,leaf,checkpoint,trustedSha);
        if(!Arrays.equals(checkpoint,restored.state().checkpoint()))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");return restored.state();
    }
    static CapturedHistoricalHeapBatch audit(List<CaptureJournal.Source> sources,Path path,String sha,long leaf,byte[] checkpoint,String trustedSha)throws IOException {
        var snapshot=FrozenHeapBaseline.read(path,sha);var origin=decode(snapshot,leaf,checkpoint,trustedSha);
        if(!sources.equals(origin.members().stream().map(LobOriginCapsule.Member::source).filter(s->s.nextScn()>snapshot.scn()).toList()))throw new RedoException("READ_CUT_AUDIT_SOURCES_CHANGED");
        var parsed=origin.parse(RedoBatch.captures(List.of()));
        var batch=batch(snapshot,leaf,parsed);
        if(!Arrays.equals(checkpoint,prefix(snapshot,leaf,origin,batch).checkpoint()))throw new RedoException("HISTORICAL_RECOVERY_STATE_CHANGED");return batch;
    }
    static HistoricalRecovery.ReadCut cut(List<CaptureJournal.Verified> captures,Path path,String sha,long leaf,byte[] checkpoint,String trustedSha)throws IOException {
        verify(captures,path,sha,leaf,checkpoint,trustedSha);var snapshot=FrozenHeapBaseline.read(path,sha);var origin=decode(snapshot,leaf,checkpoint,trustedSha);
        var parsed=origin.parse(RedoBatch.captures(List.of()));var last=parsed.inputs().get(parsed.inputs().size()-1).header();long scn=last.nextScn()-1;
        if(scn<=snapshot.scn())throw new RedoException("READ_CUT_NOT_ADVANCING");
        byte[] bytes=LobReadOriginSnapshot.write(origin,parsed);var cut=FrozenHeapBaseline.read(bytes,LobOriginCapsule.sha(bytes));
        long nextLeaf=LobOriginCapsule.currentLeaf(parsed.inputs(),cut.index().dataObject());origin.requireCut(cut,nextLeaf);
        return new HistoricalRecovery.ReadCut(bytes,cut.sha256(),scn,last.sequence(),nextLeaf,CapturedHeapBatch.schemaDigest(parsed.root()));
    }
    static CapturedHistoricalHeapBatch readOrigin(List<CaptureJournal.Verified> captures,FrozenHeapBaseline.Snapshot snapshot,long leaf)throws IOException {
        var origin=embedded(snapshot);origin.requireCut(snapshot,leaf);
        return batch(snapshot,leaf,origin.parse(RedoBatch.captures(captures)));
    }
    private static CapturedHistoricalHeapBatch batch(FrozenHeapBaseline.Snapshot snapshot,long leaf,LobOriginCapsule.Parsed parsed)throws IOException {
        return CapturedHistoricalHeapBatch.lob(snapshot,parsed.inputs().sources().stream().filter(s->s.nextScn()>snapshot.scn()).toList(),parsed.values().events().stream().filter(e->e.row().transaction().endRecordScn()>snapshot.scn()).toList(),leaf,parsed.root());
    }
    private static HistoricalRecovery.Prefix prefix(FrozenHeapBaseline.Snapshot snapshot,long leaf,LobOriginCapsule origin,CapturedHistoricalHeapBatch batch)throws IOException {
        byte[] bytes=origin.checkpoint();int transactions=(int)batch.events().stream().map(e->e.row().transaction()).distinct().count();
        String text="ORK_HISTORICAL_RECOVERY_V7\nH|"+leaf+"|"+snapshot.sha256()+"\nO|"+LobOriginCapsule.sha(bytes)+"|"+Base64.getEncoder().encodeToString(bytes)+"\nE|"+transactions+"|"+batch.publicationBoundary(batch.events().size()).sha256()+"\n";
        return new HistoricalRecovery.Prefix(text.getBytes(StandardCharsets.US_ASCII),0,transactions);
    }
    private static LobOriginCapsule decode(FrozenHeapBaseline.Snapshot snapshot,long leaf,byte[] checkpoint,String trustedSha)throws IOException {
        if(!LobOriginCapsule.sha(checkpoint).equals(trustedSha))throw new RedoException("HISTORICAL_RECOVERY_DIGEST_MISMATCH");
        String[] lines=new String(checkpoint,StandardCharsets.US_ASCII).split("\n",-1);
        if(snapshot.lobs().isEmpty() || lines.length!=5 || !lines[0].equals("ORK_HISTORICAL_RECOVERY_V7") || !lines[1].equals("H|"+leaf+"|"+snapshot.sha256()) || !lines[4].isEmpty())throw new RedoException("LOB_PRODUCER_BASELINE_OR_FORMAT");
        try {String[] f=lines[2].split("\\|",-1);if(f.length!=3 || !f[0].equals("O"))throw new RedoException("LOB_PRODUCER_ORIGIN_FRAME");return LobOriginCapsule.restore(Base64.getDecoder().decode(f[2]),f[1]);}
        catch(IllegalArgumentException invalid){throw new RedoException("LOB_PRODUCER_MALFORMED");}
    }
    private static LobOriginCapsule embedded(FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        if(snapshot.lobOrigin()==null)throw new RedoException("LOB_READ_ORIGIN_REQUIRED");
        byte[] bytes=Base64.getDecoder().decode(snapshot.lobOrigin());return LobOriginCapsule.restore(bytes,LobOriginCapsule.sha(bytes));
    }
}
