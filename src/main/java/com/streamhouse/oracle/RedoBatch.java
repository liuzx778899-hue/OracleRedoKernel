package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/** Same graph traversal over rescanned archives or immutable, already verified capture bytes. */
final class RedoBatch implements Iterable<RedoBatch.Input> {
    static final class Input {
        private final Path archive;
        private final CaptureJournal.Verified capture;
        private final CaptureJournal.Source originSource;
        private final List<RedoRecords.Record> originRecords;
        private Input(Path archive,CaptureJournal.Verified capture) { this.archive=archive;this.capture=capture;originSource=null;originRecords=null; }
        private Input(CaptureJournal.Source source,List<RedoRecords.Record> records) {archive=null;capture=null;originSource=source;originRecords=records.stream().map(RedoBatch::copy).toList();}
        ArchivedRedo.Header header() throws IOException {
            if (archive!=null) return new ArchivedRedo().scan(archive);
            var s=capture==null?originSource:capture.source();
            return new ArchivedRedo.Header(s.journalSha256(),s.journalBytes(),s.dbid(),s.activationId(),s.compatibility(),
                    s.thread(),s.sequence(),s.resetlogsId(),s.resetlogsScn(),s.firstScn(),s.nextScn(),s.blocks());
        }
        void visit(ArchivedRedo.Header header,RedoRecords.Visitor visitor) throws IOException {
            if (archive!=null) new RedoRecords().visit(archive,header,visitor);
            else {
                if (!header.equals(header())) throw new RedoException("CAPTURE_READER_IDENTITY_MISMATCH");
                // Parse anew from private raw bytes: mutable vector field arrays cannot poison a later graph pass.
                for (var record:capture==null?originRecords.stream().map(RedoBatch::copy).toList():new RedoRecords().readCaptured(capture)) visitor.accept(record);
            }
        }
    }
    private final List<Input> inputs;
    private RedoBatch(List<Input> inputs) { this.inputs=List.copyOf(inputs); }
    static RedoBatch archives(List<Path> paths) {
        return new RedoBatch(List.copyOf(paths).stream().map(path->new Input(path,null)).toList());
    }
    static RedoBatch captures(List<CaptureJournal.Verified> captures) {
        return new RedoBatch(List.copyOf(captures).stream().map(capture->new Input(null,capture)).toList());
    }
    static RedoBatch origin(List<LobOriginCapsule.Member> members,RedoBatch suffix)throws IOException {
        var merged=new java.util.TreeMap<Long,Input>();
        for(var member:members)if(merged.put(member.source().sequence(),new Input(member.source(),member.records()))!=null)throw new RedoException("LOB_ORIGIN_SOURCE_DUPLICATE");
        for(var input:suffix) {
            var h=input.header();var old=merged.get(h.sequence());
            if(old!=null && !old.header().equals(h))throw new RedoException("LOB_ORIGIN_RAW_SOURCE_CHANGED");
            merged.put(h.sequence(),input);
        }
        return new RedoBatch(List.copyOf(merged.values()));
    }
    static RedoRecords.Record copy(RedoRecords.Record r) {
        var vectors=r.vectors().stream().map(v->new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),v.fields().stream().map(byte[]::clone).toList(),v.objectId())).toList();
        return new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),vectors);
    }
    CaptureJournal.Source source(int index)throws IOException {
        var input=inputs.get(index);if(input.capture!=null)return input.capture.source();if(input.originSource!=null)return input.originSource;
        var h=input.header();return new CaptureJournal.Source(h.sha256(),h.bytes(),h.blocks(),h.blocks(),h.dbid(),h.activationId(),h.compatibility(),h.thread(),h.sequence(),h.resetlogsId(),h.resetlogsScn(),h.firstScn(),h.nextScn());
    }
    int size() {return inputs.size();}
    List<CaptureJournal.Source> sources() throws IOException {
        var result=new java.util.ArrayList<CaptureJournal.Source>();
        for(int i=0;i<size();i++)result.add(source(i));
        return List.copyOf(result);
    }
    boolean isEmpty() {return inputs.isEmpty();}
    Input get(int index) {return inputs.get(index);}
    RedoBatch prefix(int count) {return new RedoBatch(inputs.subList(0,count));}
    RedoBatch suffix(int count) {return new RedoBatch(inputs.subList(count,inputs.size()));}
    public Iterator<Input> iterator() {return inputs.iterator();}
}
