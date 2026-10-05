package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.CaptureJournal;
import com.streamhouse.oracle.CapturedHeapBatch;
import java.util.List;

/** Fixed-baseline, fixed-leaf closed capture coverage. Neither target commit proof nor a retention permit. */
public final class OracleConsumptionWindow {
    public enum Relationship { SAME_RANGE, EXTENDS_PREFIX, COVERED_PREFIX }
    public record Position(long closedSequence,long closedBlocks,long endExclusiveScn) {}
    private final CapturedHeapBatch input;
    private OracleConsumptionWindow(CapturedHeapBatch input) {this.input=input;}
    public static OracleConsumptionWindow from(CapturedHeapBatch input) {
        if (input.captures().isEmpty()) throw new IllegalArgumentException("ORACLE_WINDOW_NO_CAPTURE");
        return new OracleConsumptionWindow(input);
    }
    public String batchSha256() {return input.sha256();}
    public String baselineSha256() {return input.baselineSha256();}
    public long baselineScn() {return input.baselineScn();}
    public long leaf() {return input.leaf();}
    public String schemaSha256() {return input.schema().sha256();}
    public List<CaptureJournal.Source> captures() {return input.captures();}
    public Position position() {
        var last=captures().get(captures().size()-1);
        return new Position(last.sequence(),last.blocks(),last.nextScn());
    }
    public Relationship relativeTo(OracleConsumptionWindow previous) {
        if (!input.source().equals(previous.input.source()) || !baselineSha256().equals(previous.baselineSha256())
                || baselineScn()!=previous.baselineScn() || leaf()!=previous.leaf()
                || !schemaSha256().equals(previous.schemaSha256()))
            throw new IllegalArgumentException("ORACLE_WINDOW_SCOPE_CHANGED");
        return compareCapturePrefix(captures(),previous.captures());
    }
    static Relationship compareCapturePrefix(List<CaptureJournal.Source> current,List<CaptureJournal.Source> previous) {
        validateContiguous(current);validateContiguous(previous);
        for (int i=0;i<Math.min(current.size(),previous.size());i++) {
            var a=current.get(i);var b=previous.get(i);
            // Observation metadata may change journal bytes/hash; closed physical coverage must not.
            if (a.capacityBlocks()!=b.capacityBlocks() || a.blocks()!=b.blocks() || a.dbid()!=b.dbid()
                    || a.activationId()!=b.activationId() || a.compatibility()!=b.compatibility()
                    || a.thread()!=b.thread() || a.sequence()!=b.sequence() || a.resetlogsId()!=b.resetlogsId()
                    || a.resetlogsScn()!=b.resetlogsScn() || a.firstScn()!=b.firstScn() || a.nextScn()!=b.nextScn())
                throw new IllegalArgumentException("ORACLE_WINDOW_CAPTURE_PREFIX_CHANGED");
        }
        return current.size()==previous.size()?Relationship.SAME_RANGE:
                current.size()>previous.size()?Relationship.EXTENDS_PREFIX:Relationship.COVERED_PREFIX;
    }
    private static void validateContiguous(List<CaptureJournal.Source> captures) {
        if (captures.isEmpty()) throw new IllegalArgumentException("ORACLE_WINDOW_NO_CAPTURE");
        for (int i=1;i<captures.size();i++) {
            var a=captures.get(i-1);var b=captures.get(i);
            if (a.sequence()+1!=b.sequence() || a.nextScn()!=b.firstScn())
                throw new IllegalArgumentException("ORACLE_WINDOW_CAPTURE_GAP");
        }
    }
}
