import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;

/** Real end fields and derived extension rejection. Does not assemble a transaction or publish events. */
public class FullRollbackEndProbe {
    public static void main(String[] args)throws Exception {
        var p=Path.of(args[0]);var header=new ArchivedRedo().scan(p);
        RedoRecords.Record[] found={null};RedoRecords.Vector[] end={null},begin={null};RedoRecords.Record[] beginRecord={null};
        var xid=new HeapRowPatch.Xid(2,13,581);
        var shortRows=new ArrayList<RedoRecords.Vector>();
        new RedoRecords().visit(p,header,r->{
            if(r.containerUid()!=677737149)return;
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==11 && v.opcodeMinor()==5
                    && (v.dba()==50331875 || v.dba()==50331879) && v.fields().get(0).length==8 && v.fields().get(0)[0]==3)shortRows.add(v);
            for(var v:r.vectors())if(v.containerId()==3 && v.opcodeMajor()==5) {
                if(v.opcodeMinor()==2 && v.fields().size()==2 && v.fields().get(0).length==32
                        && UndoAdvanceProbe.word(v.fields().get(0),4)==581 && UndoAdvanceProbe.half(v.fields().get(0),0)==13) {
                    if(TransactionStart.decode(v).bindBegin(r).equals(xid)){begin[0]=v;beginRecord[0]=r;}
                }
                if(v.opcodeMinor()==4 && v.dba()==0x02400090 && v.fields().get(0).length==20
                        && UndoAdvanceProbe.word(v.fields().get(0),4)==581 && UndoAdvanceProbe.half(v.fields().get(0),0)==13){found[0]=r;end[0]=v;}
            }
        });
        if(end[0]==null || begin[0]==null)throw new RedoException("FULL_ROLLBACK_END_NOT_FOUND");
        var decoded=TransactionEnd.decode(end[0]);if(!decoded.rolledBack() || decoded.flags()!=20)throw new RedoException("NOT_OBSERVED_EXTENDED_ROLLBACK");
        if(args.length==1) {
            System.out.println("ROLLBACK|"+found[0].block()+"|"+found[0].offset()+"|"+decoded.segmentDba()+"|"+decoded.slot()+"|"+decoded.sequence()+"|"+decoded.flags());
            for(var b:end[0].fields())System.out.println("FIELD|"+b.length+"|"+HexFormat.of().formatHex(b));return;
        }
        int rejected=0;
        for(int which=0;which<10;which++) {
            var fields=new ArrayList<byte[]>();for(var b:end[0].fields())fields.add(b.clone());
            String reason;
            if(which<6){fields.get(1)[which*4]^=1;reason="UNSUPPORTED_TRANSACTION_END_EXTENSION";}
            else {fields.get(0)[16]=(byte)new int[]{0,2,4,18}[which-6];reason="UNSUPPORTED_TRANSACTION_END_FLAGS";}
            var v=end[0];var bad=new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),List.copyOf(fields));
            var r=found[0];var changed=new RedoRecords.Record(r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),List.of(bad));
            var state=new TransactionLifecycle();state.archive(header);state.begin(beginRecord[0],begin[0]);
            try{state.end(changed,bad);throw new AssertionError("accepted case "+which);}catch(RedoException e){if(!e.getMessage().equals(reason))throw e;}
            try{state.finish();throw new AssertionError("unlocked case "+which);}catch(RedoException e){if(!e.getMessage().equals("TRANSACTION_LIFECYCLE_ALREADY_REFUSED"))throw e;}
            rejected++;
        }
        System.out.println("derived_real_end_rejections="+rejected+";latches="+rejected+";cdc_events=0");
        if(shortRows.size()!=2)throw new AssertionError("short Z fixture coverage");
        int zRejected=0;
        for(var original:shortRows) {
            var patch=new HeapRowPatch().decode(original,2);
            if(patch.xid()!=null || patch.uba()!=null || patch.xtype()!=2)throw new AssertionError("invented Z identity");
            for(int which=0;which<4;which++) {
                var fields=new ArrayList<byte[]>();for(var b:original.fields())fields.add(b.clone());
                if(which==0)fields.set(0,java.util.Arrays.copyOf(fields.get(0),7));
                if(which==1)fields.get(0)[1]=12;
                if(which==2)fields.get(1)[11]=(byte)0x81;
                if(which==3){int last=fields.size()-1;fields.set(last,java.util.Arrays.copyOf(fields.get(last),fields.get(last).length-1));}
                var bad=new RedoRecords.Vector(11,5,original.blockClass(),original.file(),original.dba(),original.scn(),original.sequence(),3,fields);
                try{new HeapRowPatch().decode(bad,2);throw new AssertionError("accepted Z poison "+which);}catch(RedoException e){zRejected++;}
            }
        }
        System.out.println("real_short_Z_rows=2;derived_real_Z_rejections="+zRejected+";cdc_events=0");
    }
}
