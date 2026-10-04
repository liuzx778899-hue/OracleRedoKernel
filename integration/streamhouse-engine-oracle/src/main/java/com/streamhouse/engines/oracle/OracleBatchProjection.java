package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.ArchivedHeapRows;
import com.streamhouse.oracle.ArchivedUndoInventory;
import com.streamhouse.oracle.CapturedHeapBatch;
import com.streamhouse.oracle.FrozenHeapBaseline;
import com.streamhouse.oracle.HeapRowPatch;
import com.streamhouse.oracle.OracleScalars;
import com.streamhouse.oracle.RedoException;
import com.streamhouse.oracle.TransactionLifecycle;
import com.streamhouse.spi.EventEnvelope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed bounded projection only. No public decoder success, target write or ACK. */
public final class OracleBatchProjection {
    private OracleBatchProjection() {}
    public record Entry(EventEnvelope envelope,String payloadSha256,
                        ArchivedUndoInventory.Position pairedUndoPosition,
                        TransactionLifecycle.ClosedIdentity transaction) {}
    public record Batch(String inputBatchSha256,ArchivedHeapRows.Source source,
                        CapturedHeapBatch.Schema schema,List<Entry> entries) {
        public Batch { entries=List.copyOf(entries); }
    }
    public static Batch project(CapturedHeapBatch input) throws RedoException {
        var entries=new ArrayList<Entry>();var ordinals=new HashMap<HeapRowPatch.Xid,Long>();
        var columns=input.schema().columns();
        for (var event:input.events()) {
            var before=image(columns,event.before());var after=image(columns,event.after());
            var changed=new ArrayList<String>();
            for (int i=0;i<columns.size();i++) if (event.before().isEmpty() || event.after().isEmpty()
                    || !event.before().get(i).equals(event.after().get(i))) changed.add(columns.get(i).name());
            var x=event.transaction().xid();long sequence=ordinals.merge(x,1L,Long::sum);
            EventEnvelope.Operation operation=switch(event.operation()) {
                case INSERT->EventEnvelope.Operation.INSERT;
                case UPDATE->EventEnvelope.Operation.UPDATE;
                case DELETE->EventEnvelope.Operation.DELETE;
                default->throw new RedoException("ORACLE_NON_DML_PROJECTION");
            };
            var envelope=new EventEnvelope("oracle1:"+event.eventId(),
                    new EventEnvelope.Source(null,null,null,null),
                    new EventEnvelope.TableRef(input.schema().heap().owner(),input.schema().heap().name()),operation,
                    // These legacy fields require SQL Server LSNs. Oracle positions remain typed below.
                    new EventEnvelope.TransactionRef("oracle-xid:"+x.usn()+"."+x.slot()+"."+x.sequence(),null,null,null,sequence),
                    before,after,List.copyOf(changed),
                    new EventEnvelope.Metadata("oracle-batch-sha256:"+input.sha256(),input.schema().sha256(),true,true));
            entries.add(new Entry(envelope,event.payloadSha256(),event.pairedUndoPosition(),event.transaction()));
        }
        return new Batch(input.sha256(),input.source(),input.schema(),entries);
    }
    static Map<String,Object> image(List<FrozenHeapBaseline.PhysicalColumn> columns,List<ArchivedHeapRows.Cell> cells) throws RedoException {
        if (cells.isEmpty()) return Map.of();
        if (cells.size()!=columns.size()) throw new RedoException("ORACLE_IMAGE_COLUMN_COVERAGE");
        var image=new LinkedHashMap<String,Object>();
        for (int i=0;i<columns.size();i++) {
            var column=columns.get(i);var cell=cells.get(i);Object value;
            if (cell.state()==HeapRowPatch.State.NULL) {
                if (!column.nullable()) throw new RedoException("ORACLE_NULL_IN_NOT_NULL_COLUMN");
                value=null;
            } else {
                byte[] bytes=HexFormat.of().parseHex(cell.hex());
                value=switch(column.type()) {
                    case "NUMBER"->OracleScalars.number(bytes);
                    case "VARCHAR2"->OracleScalars.varchar2(bytes,"AL32UTF8");
                    default->throw new RedoException("ORACLE_UNSUPPORTED_PROJECTION_TYPE");
                };
            }
            image.put(column.name(),value);
        }
        // Map.copyOf rejects SQL NULL; preserve it together with physical column order.
        return Collections.unmodifiableMap(image);
    }
}
