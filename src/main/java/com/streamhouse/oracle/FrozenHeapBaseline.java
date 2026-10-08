package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Import a complete acquisition result against its trusted out-of-band receipt. A self-reported digest is not provenance. */
public final class FrozenHeapBaseline {
    public record ObjectRole(String owner,String name,long logicalObject,long dataObject) {}
    public record PhysicalColumn(int segment,int internal,String name,String type,boolean nullable,boolean hidden) {}
    public record DictionaryAnchor(ObjectRole dictionary,long targetObject,ArchivedHeapRows.RowKey key) {}
    public record Snapshot(String sha256,ArchivedHeapRows.Source source,long scn,ObjectRole heap,ObjectRole index,
                           List<PhysicalColumn> columns,Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,
                           List<DictionaryAnchor> dictionaryAnchors,DictionaryBaseline dictionaryBaseline) {
        public Snapshot { dictionaryAnchors=List.copyOf(dictionaryAnchors); }
        public Snapshot(String sha256,ArchivedHeapRows.Source source,long scn,ObjectRole heap,ObjectRole index,
                        List<PhysicalColumn> columns,Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows,List<DictionaryAnchor> dictionaryAnchors) {
            this(sha256,source,scn,heap,index,columns,rows,dictionaryAnchors,null);
        }
        public Snapshot(String sha256,ArchivedHeapRows.Source source,long scn,ObjectRole heap,ObjectRole index,
                        List<PhysicalColumn> columns,Map<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>> rows) {
            this(sha256,source,scn,heap,index,columns,rows,List.of());
        }
        public void requireSource(ArchivedRedo.Header header) throws RedoException {
            if (!source.equals(ArchivedHeapRows.Source.of(header,source.containerUid(),source.containerId())))
                throw new RedoException("SNAPSHOT_REDO_SOURCE_MISMATCH");
        }
        public ArchivedHeapRows.Baseline baselineForEqualObjectScope() throws RedoException {
            if (heap.logicalObject()!=heap.dataObject() || index.logicalObject()!=index.dataObject())
                throw new RedoException("UNSUPPORTED_SNAPSHOT_LOGICAL_DATA_OBJECT_MAPPING");
            return new ArchivedHeapRows.Baseline(source,scn,heap.logicalObject(),columns.size(),rows);
        }
    }

    public static Snapshot read(Path path,String trustedAcquisitionSha256) throws IOException {
        if (trustedAcquisitionSha256==null || !trustedAcquisitionSha256.matches("[0-9a-f]{64}"))
            throw new RedoException("MISSING_TRUSTED_SNAPSHOT_RECEIPT");
        byte[] bytes=Files.readAllBytes(path);String digest;
        try {digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch (NoSuchAlgorithmException e) {throw new AssertionError(e);}
        if (!digest.equals(trustedAcquisitionSha256)) throw new RedoException("SNAPSHOT_ACQUISITION_DIGEST_MISMATCH");
        for (byte b:bytes) if (b<0) throw new RedoException("UNSUPPORTED_SNAPSHOT_TEXT_ENCODING");
        try {
            var input=new Input(new String(bytes,StandardCharsets.US_ASCII));
            boolean history=!input.lines.isEmpty() && input.lines.get(0).equals("FORMAT|ORK_HEAP_SNAPSHOT_V3");
            boolean dictionary=history || (!input.lines.isEmpty() && input.lines.get(0).equals("FORMAT|ORK_HEAP_SNAPSHOT_V2"));
            String format=history?"ORK_HEAP_SNAPSHOT_V3":dictionary ? "ORK_HEAP_SNAPSHOT_V2" : "ORK_HEAP_SNAPSHOT_V1";
            input.exact("FORMAT|"+format);
            var sourceFields=input.take("SOURCE",7);var pdb=input.take("PDB",4);identifier(pdb[3]);
            input.exact("CHARSET|AL32UTF8");long scn=positive(input.take("SNAPSHOT_SCN",2)[1]);
            if (!sourceFields[6].equals("19.0.0")) throw new RedoException("UNSUPPORTED_SNAPSHOT_COMPATIBILITY");
            var source=new ArchivedHeapRows.Source(positive(sourceFields[1]),positive(sourceFields[2]),0x13000000L,
                    Math.toIntExact(positive(sourceFields[5])),positive(sourceFields[3]),positive(sourceFields[4]),positive(pdb[1]),positive(pdb[2]));
            var heap=role(input.take("HEAP",6),"BEFORE");var index=role(input.take("INDEX",6),"BEFORE");
            if (!heap.owner().equals(index.owner()) || heap.name().equals(index.name()) || heap.logicalObject()==index.logicalObject()
                    || heap.dataObject()==index.dataObject() || heap.logicalObject()==index.dataObject() || heap.dataObject()==index.logicalObject())
                throw new RedoException("SNAPSHOT_OBJECT_ROLE_COLLISION");
            var columns=new ArrayList<PhysicalColumn>();var before=new ArrayList<String>();
            while (input.peek("COLUMN")) {
                var c=input.take("COLUMN",9);if (!c[1].equals("BEFORE")) throw new RedoException("SNAPSHOT_COLUMN_PHASE_MISMATCH");
                int segment=Math.toIntExact(positive(c[2])),internal=Math.toIntExact(positive(c[3]));identifier(c[4]);
                if (segment!=columns.size()+1 || internal!=segment || !List.of("NUMBER","VARCHAR2").contains(c[5])
                        || !List.of("Y","N").contains(c[6]) || !List.of("YES","NO").contains(c[7]) || !c[8].equals("NO"))
                    throw new RedoException("UNSUPPORTED_SNAPSHOT_PHYSICAL_COLUMN_LAYOUT");
                columns.add(new PhysicalColumn(segment,internal,c[4],c[5],c[6].equals("Y"),c[7].equals("YES")));
                before.add(String.join("|",java.util.Arrays.copyOfRange(c,2,c.length)));
            }
            if (columns.isEmpty() || columns.size()>255 || columns.stream().map(PhysicalColumn::name).distinct().count()!=columns.size())
                throw new RedoException("SNAPSHOT_COLUMN_COVERAGE_MISMATCH");
            var dictionaryRoles=new ArrayList<ObjectRole>();var anchors=new ArrayList<DictionaryAnchor>();
            if(dictionary) {
                for(String name:List.of("OBJ$","TAB$","COL$")) {
                    var dict=role(input.take("DICT_ROLE",6),"BEFORE");
                    if(!dict.owner().equals("SYS") || !dict.name().equals(name)
                            || dict.logicalObject()==heap.logicalObject() || dict.logicalObject()==index.logicalObject()
                            || dict.dataObject()==heap.dataObject() || dict.dataObject()==index.dataObject())
                        throw new RedoException("SNAPSHOT_DICTIONARY_ROLE_MISMATCH");
                    dictionaryRoles.add(dict);
                }
                if(dictionaryRoles.stream().map(ObjectRole::logicalObject).distinct().count()!=3
                        || dictionaryRoles.get(0).logicalObject()!=dictionaryRoles.get(0).dataObject()
                        || dictionaryRoles.get(1).dataObject()!=dictionaryRoles.get(2).dataObject()
                        || dictionaryRoles.get(0).dataObject()==dictionaryRoles.get(1).dataObject())
                    throw new RedoException("SNAPSHOT_DICTIONARY_GENERATION_MISMATCH");
                var keys=new java.util.HashSet<String>();
                while(input.peek("DICT_ANCHOR")) {
                    var a=input.take("DICT_ANCHOR",7);var dict=dictionaryRoles.stream().filter(d->d.name().equals(a[1])).findFirst()
                            .orElseThrow(()->new RedoException("SNAPSHOT_DICTIONARY_ANCHOR_ROLE"));
                    long target=positive(a[2]),dataObject=positive(a[3]),file=positive(a[4]),block=positive(a[5]);int slot=Integer.parseInt(a[6]);
                    if(dataObject!=dict.dataObject() || file>1023 || block>=0x400000L || slot<0 || slot>65535
                            || (target!=heap.logicalObject() && (!dict.name().equals("OBJ$") || target!=index.logicalObject()))
                            || !keys.add(file+":"+block+":"+slot))
                        throw new RedoException("SNAPSHOT_DICTIONARY_ANCHOR_SCOPE");
                    anchors.add(new DictionaryAnchor(dict,target,new ArchivedHeapRows.RowKey(dataObject,(file<<22)|block,slot)));
                }
                for(var dict:dictionaryRoles) {
                    var selected=anchors.stream().filter(a->a.dictionary().equals(dict)).toList();
                    int expected=dict.name().equals("OBJ$") ? 2 : dict.name().equals("TAB$") ? 1 : columns.size();
                    if(selected.size()!=expected || (dict.name().equals("OBJ$") && selected.stream().map(DictionaryAnchor::targetObject).distinct().count()!=2))
                        throw new RedoException("SNAPSHOT_DICTIONARY_ANCHOR_COVERAGE");
                }
                for(var c:columns) input.exact("DICT_COLUMN|"+c.internal()+"|"+c.segment()+"|"+c.name()+"|"
                        +(c.type().equals("NUMBER")?2:1)+"|"+(c.nullable()?0:2)+"|"+(c.hidden()?17179869216L:0));
            }
            DictionaryBaseline dictionaryBaseline=null;
            if(history) {
                var frames=new ArrayList<String>();
                while(input.at<input.lines.size()) {
                    String frame=input.lines.get(input.at++);frames.add(frame);
                    if(frame.startsWith("DICT_HISTORY_END|"))break;
                }
                dictionaryBaseline=DictionaryBaseline.read(frames,anchors);
                dictionaryBaseline.requireMetadata(heap,index,columns);
            }
            var rows=new HashMap<ArchivedHeapRows.RowKey,List<ArchivedHeapRows.Cell>>();
            while (input.peek("ROW")) {
                var row=input.take("ROW",columns.size()+5);long dataObject=positive(row[1]),file=positive(row[2]),block=positive(row[3]);int slot=Integer.parseInt(row[4]);
                if (dataObject!=heap.dataObject() || file>1023 || block>=0x400000L || slot<0 || slot>65535)
                    throw new RedoException("SNAPSHOT_ROW_GENERATION_OR_POSITION_MISMATCH");
                var values=new ArrayList<ArchivedHeapRows.Cell>();
                for (int i=0;i<columns.size();i++) {
                    String raw=row[i+5];boolean isNull=raw.equals("NULL");
                    if ((isNull && !columns.get(i).nullable()) || (!isNull && !raw.matches("(?:[0-9A-F]{2})+")))
                        throw new RedoException("SNAPSHOT_ROW_VALUE_OR_NULLABILITY_MISMATCH");
                    values.add(new ArchivedHeapRows.Cell(isNull ? HeapRowPatch.State.NULL : HeapRowPatch.State.VALUE,isNull ? "" : raw));
                }
                if (rows.putIfAbsent(new ArchivedHeapRows.RowKey(dataObject,(file<<22)|block,slot),List.copyOf(values))!=null)
                    throw new RedoException("SNAPSHOT_DUPLICATE_ROW_POSITION");
            }
            if (Long.parseLong(input.take("ROW_COUNT",2)[1])!=rows.size()) throw new RedoException("SNAPSHOT_ROW_COVERAGE_MISMATCH");
            if (!heap.equals(role(input.take("HEAP",6),"AFTER")) || !index.equals(role(input.take("INDEX",6),"AFTER")))
                throw new RedoException("SNAPSHOT_OBJECT_GENERATION_CHANGED_DURING_QUERY");
            for (var c:before) input.exact("COLUMN|AFTER|"+c);
            for(var dict:dictionaryRoles) input.exact("DICT_ROLE|AFTER|SYS|"+dict.name()+"|"+dict.logicalObject()+"|"+dict.dataObject());
            if(history)for(var dict:dictionaryBaseline.indexes())input.exact("DICT_INDEX|AFTER|"+dict.role().name()+"|"+dict.role().logicalObject()+"|"+dict.role().dataObject()+"|"+dict.indexDba());
            if(history && dictionaryBaseline.statistics()!=null)dictionaryBaseline.statistics().requireAfter(input);
            input.exact("SOURCE_AFTER|"+String.join("|",java.util.Arrays.copyOfRange(sourceFields,1,sourceFields.length)));
            input.exact("END|"+format);if (input.at!=input.lines.size()) throw new RedoException("SNAPSHOT_UNEXPECTED_TRAILING_OUTPUT");
            return new Snapshot(digest,source,scn,heap,index,List.copyOf(columns),Map.copyOf(rows),anchors,dictionaryBaseline);
        } catch (IllegalArgumentException | ArithmeticException e) {throw new RedoException("MALFORMED_SNAPSHOT_FIELD");}
    }
    private static ObjectRole role(String[] fields,String phase) throws RedoException {
        if (!fields[1].equals(phase)) throw new RedoException("SNAPSHOT_OBJECT_PHASE_MISMATCH");
        identifier(fields[2]);identifier(fields[3]);return new ObjectRole(fields[2],fields[3],positive(fields[4]),positive(fields[5]));
    }
    private static long positive(String text) throws RedoException {
        if (!text.matches("[0-9]+")) throw new RedoException("MALFORMED_SNAPSHOT_NUMBER");
        long value=Long.parseLong(text);if (value<=0) throw new RedoException("NONPOSITIVE_SNAPSHOT_NUMBER");return value;
    }
    static void identifier(String text) throws RedoException {
        if (!text.matches("[A-Z][A-Z0-9_$#]{0,127}")) throw new RedoException("UNSUPPORTED_SNAPSHOT_IDENTIFIER");
    }
    static final class Input {
        final List<String> lines;int at;
        Input(String text) {lines=text.lines().map(String::strip).filter(s->!s.isEmpty()).toList();}
        boolean peek(String label) {return at<lines.size() && lines.get(at).startsWith(label+"|");}
        String[] take(String label,int count) throws RedoException {
            if (!peek(label)) throw new RedoException("SNAPSHOT_MISSING_OR_OUT_OF_ORDER_FRAME:"+label);
            var parts=lines.get(at++).split("\\|",-1);if (parts.length!=count) throw new RedoException("SNAPSHOT_FRAME_FIELD_COUNT:"+label);return parts;
        }
        void exact(String expected) throws RedoException {
            if (at>=lines.size() || !lines.get(at++).equals(expected)) throw new RedoException("SNAPSHOT_FRAME_MISMATCH");
        }
    }
}
