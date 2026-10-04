import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Explicit fixed-SCN baseline fixture, never current SQL or dump as change input. Buffered bounded row proof, not CDC. */
public class HeapBaselineProbe {
    public static void main(String[] args) throws Exception {
        var path=Path.of(args[0]);String mode=args.length>1 ? args[1] : "positive";
        var h=new ArchivedRedo().scan(path);var source=ArchivedHeapRows.Source.of(h,677737149,3);
        long scn=2282688,object=73073;var key=new ArchivedHeapRows.RowKey(object,0x03000193L,0);
        var rows=Map.of(key,List.of(value("c10b"),value("616c706861"),value("736563726574")));
        switch (mode) {
            case "positive": break;
            case "missing_row": rows=Map.of();break;
            case "wrong_before": rows=Map.of(key,List.of(value("c10b"),value("63757272656e74"),value("736563726574")));break;
            case "false_null": rows=Map.of(key,List.of(value("c10b"),value("616c706861"),new ArchivedHeapRows.Cell(HeapRowPatch.State.NULL,"")));break;
            case "late_baseline": scn=2282705;break;
            case "before_coverage": scn=h.firstScn()-1;break;
            case "wrong_source": source=new ArchivedHeapRows.Source(h.dbid()+1,h.activationId(),h.compatibility(),h.thread(),h.resetlogsId(),h.resetlogsScn(),677737149,3);break;
            case "wrong_pdb": source=ArchivedHeapRows.Source.of(h,677737149,4);break;
            case "wrong_generation": rows=Map.of(new ArchivedHeapRows.RowKey(73076,key.dba(),0),rows.get(key));break;
            case "wrong_slot": rows=Map.of(new ArchivedHeapRows.RowKey(object,key.dba(),1),rows.get(key));break;
            case "wrong_key": rows=Map.of(key,List.of(value("c10c"),value("616c706861"),value("736563726574")));break;
            case "missing_baseline": break;
            default: throw new IllegalArgumentException("UNKNOWN_BASELINE_RESEARCH_MODE");
        }
        var baseline=new ArchivedHeapRows.Baseline(source,scn,object,3,rows);
        var result=ArchivedHeapRows.read(List.of(path),mode.equals("missing_baseline") ? null : baseline,73074,0x0300019bL);
        for (var c:result.changes()) {
            var p=c.position();System.out.println("ROW|"+p.archiveSequence()+":"+p.block()+":"+p.offset()+":"+p.vector()+"|"+c.xid()+"|"+c.key().dataObjectId()+"|"+c.key().dba()+"|"+c.key().slot()+"|"+c.kind()+"|"+values(c.before())+"|"+values(c.after()));
        }
        System.err.println("row_changes="+result.changes().size()+";explicit_baseline_fixed_schema_only;historical_dictionary_and_cdc_unaccepted;cdc_events=0");
    }
    static ArchivedHeapRows.Cell value(String hex) {return new ArchivedHeapRows.Cell(HeapRowPatch.State.VALUE,hex);}
    static String values(List<ArchivedHeapRows.Cell> row) {return String.join(",",row.stream().map(c->c.state()+":"+c.hex()).toList());}
}
