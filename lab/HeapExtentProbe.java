import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Typed physical patches and exact pairs; tail markers remain separate from logical NULL. */
public class HeapExtentProbe {
    public static void main(String[] args) throws Exception {
        String mode=args[0];long object=Long.parseLong(args[1]),block=Long.decode(args[2]);int columns=Integer.parseInt(args[3]);var path=Path.of(args[4]);
        var scanner=new ArchivedRedo();var header=scanner.scan(path);var output=new ArrayList<String>();
        new RedoRecords().visit(path,header,r->{
            if (r.containerUid()!=677737149)return;
            for (int i=0;i<r.vectors().size();i++) {
                var v=r.vectors().get(i);if (v.containerId()!=3)continue;
                HeapRowPatch.Patch p;String side;
                if (v.opcodeMajor()==11 && v.dba()==block) {
                    p=new HeapRowPatch().decode(v,columns);side="FORWARD";
                    if (mode.equals("pairs")) {
                        var pair=HeapUndoPair.bind(r,v,columns);var h=pair.undo().identity();var e=p.extent();var prior=pair.undo().inverse().extent();
                        output.add("PAIR|"+header.sequence()+":"+r.block()+":"+r.offset()+":"+(i+1)+"|"+h.xid()+"|"+h.uba()+"|"+h.dataObjectId()+"|"+(prior==null ? -1 : prior.storedColumns())+"|"+e.storedColumns()+"|"+e.changedColumns()+"|"+e.removedColumn()+"|"+e.sizeDelta()+"|"+states(pair.undo().inverse())+"|"+states(p));
                        continue;
                    }
                } else if (v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>=4 && ColumnLayoutProbe.word(v.fields().get(1),4)==object) {
                    if (mode.equals("pairs"))continue;p=UndoRowPatch.decode(v,columns).inverse();side="INVERSE";
                } else continue;
                var e=p.extent();var values=new ArrayList<String>();
                for (int c=0;c<p.columns().size();c++) if (p.columns().get(c).state()!=HeapRowPatch.State.ABSENT)values.add(c+":"+value(p.columns().get(c)));
                int op=p.kind()==HeapRowPatch.Kind.INSERT ? 2 : p.kind()==HeapRowPatch.Kind.UPDATE ? 5 : 3;
                output.add(header.sequence()+"|"+r.block()+"|"+r.offset()+"|"+(i+1)+"|"+side+"|"+op+"|"+(e==null ? -1 : e.storedColumns())+"|"+(e==null ? 0 : e.changedColumns())+"|"+p.slot()+"|"+String.join(",",values));
            }
        });
        if (!header.equals(scanner.scan(path)))throw new RedoException("EXTENT_PROBE_INPUT_CHANGED");
        output.forEach(System.out::println);System.err.println("typed_results="+output.size()+";physical_extent_only;baseline_dictionary_and_dml_unaccepted;cdc_events=0");
    }
    static String value(HeapRowPatch.Column c) {return c.state()+":"+HexFormat.of().formatHex(c.bytes());}
    static String states(HeapRowPatch.Patch p) {return String.join(",",p.columns().stream().map(HeapExtentProbe::value).toList());}
}
