package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static com.streamhouse.oracle.RedoBytes.*;

/** Source-owned unique header associations. Does not verify physical compensation or authorize cancellation. */
public final class ArchivedCompensationAssociations {
    public record Entry(ArchivedUndoInventory.Position position,HeapRowPatch.Xid xid,
                        ArchivedUndoInventory.Position originalPosition,UndoHeader original) {}
    private record Candidate(ArchivedUndoInventory.Position position,UndoHeader header) {}
    private record Marker(ArchivedUndoInventory.Position position,long recordScn,String signature,UndoCompensation header) {}
    private final ArchivedCompensationInventory window;
    private final List<Entry> entries;
    private final List<Marker> markers;
    private int next;
    private boolean failed;

    private ArchivedCompensationAssociations(ArchivedCompensationInventory window,List<Entry> entries,List<Marker> markers) {
        this.window=window;this.entries=List.copyOf(entries);this.markers=List.copyOf(markers);
    }
    public static ArchivedCompensationAssociations read(List<Path> paths,long uid,long container,HeapRowPatch.Xid target) throws IOException {
        return read(RedoBatch.archives(paths),uid,container,target);
    }
    static ArchivedCompensationAssociations read(RedoBatch paths,long uid,long container,HeapRowPatch.Xid target) throws IOException {
        return read(paths,uid,container,target,false);
    }
    static ArchivedCompensationAssociations read(RedoBatch paths,long uid,long container,HeapRowPatch.Xid target,boolean allowPending) throws IOException {
        var window=ArchivedCompensationInventory.read(paths,uid,container,target,allowPending);
        var candidates=new ArrayList<List<Candidate>>();var markers=new ArrayList<Marker>();
        for (var e:window.entries()) {
            if (e.marker().crossesUndoBlock()) throw new RedoException("UNSUPPORTED_ASSOCIATION_CROSS_BLOCK_MARKER");
            candidates.add(new ArrayList<>());
        }

        for (int source=0;source<paths.size();source++) {
            var path=paths.get(source);var header=path.header();
            if (!header.equals(window.undo().sources().get(source))) throw new RedoException("ASSOCIATION_INPUT_CHANGED");
            path.visit(header,r->{
                if (r.containerUid()!=uid) return;
                for (int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if (v.containerId()!=container) continue;
                    var at=new ArchivedUndoInventory.Position(header.sequence(),r.block(),r.offset(),i+1);
                    for (int j=0;j<window.entries().size();j++) {
                        var expected=window.entries().get(j);
                        if (expected.position().equals(at)) {
                            var c=UndoCompensation.decode(v);
                            if (!expected.marker().equals(c)) throw new RedoException("ASSOCIATION_MARKER_CHANGED");
                            markers.add(new Marker(at,r.scn(),signature(v),c));
                        }
                    }
                    if (v.opcodeMajor()!=5 || v.opcodeMinor()!=1) continue;
                    if (v.fields().size()<2 || v.fields().get(0).length!=20 || v.fields().get(1).length<24)
                        throw new RedoException("UNREADABLE_ASSOCIATION_ORIGINAL");
                    byte[] m=v.fields().get(1);
                    for (int j=0;j<window.entries().size();j++) {
                        var expected=window.entries().get(j);var c=expected.marker();
                        if (u32(m,0)!=c.objectId() || u32(m,4)!=c.dataObjectId() || u32(m,8)!=c.tablespace()
                                || u32(m,12)!=c.previousDba() || (m[16]&255)!=c.opcodeMajor() || (m[17]&255)!=c.opcodeMinor()
                                || (m[18]&255)!=c.slot() || (m[19]&255)!=c.predecessorRecord() || u16(m,22)!=c.wrap()) continue;
                        if (compareRecord(at,expected.position())>=0) continue;
                        var h=UndoHeader.decode(v);
                        if (h.beginsTransaction()!=c.beginsTransaction()) continue;
                        long segment=0;
                        if (h.beginsTransaction()) {
                            int count=0;
                            for (var control:r.vectors()) if (control.containerId()==container && control.opcodeMajor()==5 && control.opcodeMinor()==2) {
                                var start=TransactionStart.decode(control);
                                if (start.uba().equals(h.uba()) && start.bindBegin(r).equals(h.xid())) {segment=start.segmentDba();count++;}
                            }
                            if (count!=1) throw new RedoException("ASSOCIATION_BEGIN_CONTROL_COUNT");
                            if (segment!=c.dba()) continue;
                        } else if (h.uba().dba()!=c.dba() || (h.xid().sequence()&0xffff)!=c.wrap()) continue;
                        candidates.get(j).add(new Candidate(at,h));
                    }
                }
            });
            if (!header.equals(path.header())) throw new RedoException("ASSOCIATION_INPUT_CHANGED");
        }
        if (!markers.stream().map(Marker::position).toList().equals(window.entries().stream().map(ArchivedCompensationInventory.Entry::position).toList()))
            throw new RedoException("ASSOCIATION_MARKER_COVERAGE_CHANGED");
        Map<HeapRowPatch.Xid,ArchivedCompensationInventory> owners=new HashMap<>();owners.put(target,window);
        var entries=new ArrayList<Entry>();
        for (int i=0;i<markers.size();i++) {
            var marker=markers.get(i);Candidate chosen=null;
            for (var candidate:candidates.get(i)) {
                var xid=candidate.header().xid();var owner=owners.get(xid);
                if (owner==null) {owner=ArchivedCompensationInventory.read(paths,uid,container,xid,allowPending);owners.put(xid,owner);}
                if (!owner.undo().sources().equals(window.undo().sources())) throw new RedoException("ASSOCIATION_OWNER_SOURCE_MISMATCH");
                if (owner.undo().entries().stream().noneMatch(e->e.position().equals(candidate.position()) && e.identity().equals(candidate.header())))
                    throw new RedoException("ASSOCIATION_ORIGINAL_NOT_OWNED");
                if (owner.entries().stream().noneMatch(e->e.position().equals(marker.position()) && e.marker().equals(marker.header()))) continue;
                if (chosen!=null) throw new RedoException("AMBIGUOUS_COMPENSATION_HEADER_ASSOCIATION");
                chosen=candidate;
            }
            if (chosen==null) throw new RedoException("MISSING_COMPENSATION_HEADER_ASSOCIATION");
            entries.add(new Entry(marker.position(),chosen.header().xid(),chosen.position(),chosen.header()));
        }
        return new ArchivedCompensationAssociations(window,entries,markers);
    }
    public List<Entry> entries() {return entries;}
    public ArchivedCompensationInventory window() {return window;}
    /** Consumes the entire conservative window, including foreign markers, in source order. */
    public Entry associate(ArchivedRedo.Header source,RedoRecords.Record record,RedoRecords.Vector marker,ArchivedUndoInventory.Position at) throws RedoException {
        healthy();
        try {
            if (next>=entries.size() || !entries.get(next).position().equals(at)) throw new RedoException("ASSOCIATION_QUERY_INCOMPLETE_OR_REORDERED");
            if (window.undo().sources().stream().noneMatch(h->h.sequence()==at.archiveSequence() && h.equals(source))) throw new RedoException("ASSOCIATION_QUERY_SOURCE_MISMATCH");
            var expected=markers.get(next);
            if (record.containerUid()!=window.undo().containerUid() || marker.containerId()!=window.undo().containerId()
                    || record.block()!=at.block() || record.offset()!=at.offset() || record.scn()!=expected.recordScn()
                    || !record.vectors().contains(marker) || record.vectors().indexOf(marker)+1!=at.vector() || !expected.signature().equals(signature(marker)))
                throw new RedoException("ASSOCIATION_QUERY_CHANGED_FROM_OWNED_SOURCE");
            return entries.get(next++);
        } catch (RedoException e) {failed=true;throw e;}
    }
    public void finish() throws RedoException {
        healthy();if (next!=entries.size()) {failed=true;throw new RedoException("ASSOCIATION_QUERY_INCOMPLETE_OR_REORDERED");}
    }
    private void healthy() throws RedoException {if (failed) throw new RedoException("COMPENSATION_ASSOCIATIONS_ALREADY_REFUSED");}
    private static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if (c==0)c=Long.compare(a.block(),b.block());return c==0 ? Integer.compare(a.offset(),b.offset()) : c;
    }
    private static String signature(RedoRecords.Vector v) {
        var s=new StringBuilder(v.opcodeMajor()+":"+v.opcodeMinor()+":"+v.blockClass()+":"+v.file()+":"+v.dba()+":"+v.scn()+":"+v.sequence()+":"+v.containerId());
        for (byte[] f:v.fields()) s.append('|').append(f.length).append(':').append(HexFormat.of().formatHex(f));return s.toString();
    }
}
