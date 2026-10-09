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
        private Input(Path archive,CaptureJournal.Verified capture) { this.archive=archive;this.capture=capture; }
        ArchivedRedo.Header header() throws IOException {
            if (archive!=null) return new ArchivedRedo().scan(archive);
            var s=capture.source();
            return new ArchivedRedo.Header(s.journalSha256(),s.journalBytes(),s.dbid(),s.activationId(),s.compatibility(),
                    s.thread(),s.sequence(),s.resetlogsId(),s.resetlogsScn(),s.firstScn(),s.nextScn(),s.blocks());
        }
        void visit(ArchivedRedo.Header header,RedoRecords.Visitor visitor) throws IOException {
            if (archive!=null) new RedoRecords().visit(archive,header,visitor);
            else {
                if (!header.equals(header())) throw new RedoException("CAPTURE_READER_IDENTITY_MISMATCH");
                // Parse anew from private raw bytes: mutable vector field arrays cannot poison a later graph pass.
                for (var record:new RedoRecords().readCaptured(capture)) visitor.accept(record);
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
    int size() {return inputs.size();}
    boolean isEmpty() {return inputs.isEmpty();}
    Input get(int index) {return inputs.get(index);}
    RedoBatch prefix(int count) {return new RedoBatch(inputs.subList(0,count));}
    RedoBatch suffix(int count) {return new RedoBatch(inputs.subList(count,inputs.size()));}
    public Iterator<Input> iterator() {return inputs.iterator();}
}
