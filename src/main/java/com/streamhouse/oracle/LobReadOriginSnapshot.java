package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Complete materialized read cut plus the sealed redo records required to reconstruct its LOB origins. */
final class LobReadOriginSnapshot {
    static byte[] write(LobOriginCapsule origin,LobOriginCapsule.Parsed parsed)throws IOException {
        var root=parsed.root();var history=parsed.history();var last=parsed.inputs().get(parsed.inputs().size()-1).header();long scn=last.nextScn()-1;
        var heap=history.heapAt(scn,null);var index=history.indexAt(scn,null);var lobs=history.lobsAt(scn,null);var columns=history.schemaAt(scn,null);
        var dictionary=root.dictionaryBaseline();
        var rows=history.materializedState().rowsAt(scn,null).stream().filter(r->r.target()==heap.logicalObject() || r.role().equals("OBJ$") && r.target()==index.logicalObject()).toList();
        var materialized=parsed.values().rows();
        if(materialized.values().stream().anyMatch(r->r.size()!=columns.size()))throw new RedoException("LOB_READ_CUT_ROW_COLUMNS");
        byte[] capsule=origin.checkpoint();var out=new StringBuilder("FORMAT|ORK_HEAP_SNAPSHOT_V5\n");
        out.append("LOB_READ_ORIGIN|").append(LobOriginCapsule.sha(capsule)).append('|').append(Base64.getEncoder().encodeToString(capsule)).append('\n');
        String source=root.source().dbid()+"|"+root.source().activationId()+"|"+root.source().resetlogsId()+"|"+root.source().resetlogsScn()+"|"+root.source().thread()+"|19.0.0";
        out.append("SOURCE|").append(source).append('\n');
        String pdb=new String(origin.baseline(),StandardCharsets.US_ASCII).lines().filter(l->l.startsWith("PDB|")).findFirst().orElseThrow();out.append(pdb).append("\nCHARSET|AL32UTF8\n");
        if(root.timeZoneContext()!=null) {
            var tz=root.timeZoneContext();out.append("TZ_CONTEXT|BEFORE|").append(tz.databaseZone()).append('|').append(tz.fileVersion()).append('\n');
            for(var e:new TreeMap<>(tz.regions()).entrySet())out.append("TZ_REGION|").append(e.getKey()).append('|').append(e.getValue()).append('\n');
        }
        out.append("SNAPSHOT_SCN|").append(scn).append('\n');roles(out,"BEFORE",heap,index,columns,lobs);
        for(String name:List.of("OBJ$","TAB$","COL$"))role(out,"DICT_ROLE","BEFORE",dictionary.layouts().get(name).role());
        for(var row:rows)out.append("DICT_ANCHOR|").append(row.role()).append('|').append(row.target()).append('|').append(position(row.key())).append('\n');
        for(var column:columns) {
            var row=rows.stream().filter(r->r.role().equals("COL$") && metadataNumber(dictionary,r,"INTCOL#")==column.internal()).findFirst().orElseThrow();
            out.append("DICT_COLUMN|").append(column.internal()).append('|').append(column.segment()).append('|').append(column.name()).append('|').append(OracleColumnTypes.oracleId(column.type()))
                    .append('|').append(dictionary.number(row,"NULL$")).append('|').append(dictionary.number(row,"PROPERTY")).append('\n');
        }
        out.append("DICT_HISTORY_BEGIN|1\n");
        for(String name:List.of("OBJ$","TAB$","COL$")) {
            var layout=dictionary.layouts().get(name);out.append("DICT_LAYOUT|").append(name).append('|').append(layout.tableNumber()).append('|').append(layout.columns().size()).append('\n');
            for(var c:layout.columns())out.append("DICT_LAYOUT_COLUMN|").append(name).append('|').append(c.segment()).append('|').append(c.internal()).append('|').append(c.name()).append('|').append(c.type()).append('|').append(c.required()?1:0).append('\n');
        }
        for(var i:dictionary.indexes())out.append("DICT_INDEX|BEFORE|").append(i.role().name()).append('|').append(i.role().logicalObject()).append('|').append(i.role().dataObject()).append('|').append(i.indexDba()).append('\n');
        for(var row:rows){out.append("DICT_ROW|").append(row.role()).append('|').append(row.target()).append('|').append(position(row.key()));cells(out,row.values());out.append('\n');}
        out.append("DICT_HISTORY_END|").append(rows.size()).append('\n');
        for(var key:materialized.keySet().stream().sorted(Comparator.comparingLong(ArchivedHeapRows.RowKey::dataObjectId).thenComparingLong(ArchivedHeapRows.RowKey::dba).thenComparingInt(ArchivedHeapRows.RowKey::slot)).toList()) {
            if(key.dataObjectId()!=heap.dataObject())throw new RedoException("LOB_READ_CUT_ROW_GENERATION");
            out.append("ROW|").append(position(key));cells(out,materialized.get(key));out.append('\n');
        }
        out.append("ROW_COUNT|").append(materialized.size()).append('\n');roles(out,"AFTER",heap,index,columns,lobs);
        for(String name:List.of("OBJ$","TAB$","COL$"))role(out,"DICT_ROLE","AFTER",dictionary.layouts().get(name).role());
        for(var i:dictionary.indexes())out.append("DICT_INDEX|AFTER|").append(i.role().name()).append('|').append(i.role().logicalObject()).append('|').append(i.role().dataObject()).append('|').append(i.indexDba()).append('\n');
        if(root.timeZoneContext()!=null)out.append("TZ_CONTEXT|AFTER|").append(root.timeZoneContext().databaseZone()).append('|').append(root.timeZoneContext().fileVersion()).append('\n');
        out.append("SOURCE_AFTER|").append(source).append("\nEND|ORK_HEAP_SNAPSHOT_V5\n");return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
    private static long metadataNumber(DictionaryBaseline dictionary,DictionaryBaseline.Row row,String name){try{return dictionary.number(row,name);}catch(RedoException invalid){return -1;}}
    private static String position(ArchivedHeapRows.RowKey key){return key.dataObjectId()+"|"+(key.dba()>>>22)+"|"+(key.dba()&0x3fffffL)+"|"+key.slot();}
    private static void cells(StringBuilder out,List<ArchivedHeapRows.Cell> cells)throws RedoException {
        for(var cell:cells){if(cell.state()!=HeapRowPatch.State.NULL && cell.state()!=HeapRowPatch.State.VALUE)throw new RedoException("LOB_READ_CUT_INCOMPLETE_VALUE");out.append('|').append(cell.state()==HeapRowPatch.State.NULL?"NULL":cell.hex().toUpperCase(Locale.ROOT));}
    }
    private static void role(StringBuilder out,String tag,String phase,FrozenHeapBaseline.ObjectRole role){out.append(tag).append('|').append(phase).append('|').append(role.owner()).append('|').append(role.name()).append('|').append(role.logicalObject()).append('|').append(role.dataObject()).append('\n');}
    private static void roles(StringBuilder out,String phase,FrozenHeapBaseline.ObjectRole heap,FrozenHeapBaseline.ObjectRole index,List<FrozenHeapBaseline.PhysicalColumn> columns,List<FrozenHeapBaseline.LobColumn> lobs) {
        role(out,"HEAP",phase,heap);role(out,"INDEX",phase,index);
        for(var c:columns)out.append("COLUMN|").append(phase).append('|').append(c.segment()).append('|').append(c.internal()).append('|').append(c.name()).append('|').append(c.type()).append('|').append(c.nullable()?"Y":"N").append('|').append(c.hidden()?"YES":"NO").append("|NO\n");
        for(var l:lobs)out.append("LOB|").append(phase).append('|').append(l.column()).append('|').append(l.storage()).append('|').append(l.lob().owner()).append('|').append(l.lob().name()).append('|').append(l.lob().logicalObject()).append('|').append(l.lob().dataObject()).append('|').append(l.index().owner()).append('|').append(l.index().name()).append('|').append(l.index().logicalObject()).append('|').append(l.index().dataObject()).append('\n');
    }
}
