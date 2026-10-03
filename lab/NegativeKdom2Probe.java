import com.streamhouse.oracle.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/** Mutates copies of real vectors only. Audit success is not CDC publication. */
public class NegativeKdom2Probe {
    public static void main(String[] args)throws Exception {
        Path p=Path.of(args[0]);var h=new ArchivedRedo().scan(p);
        RedoRecords.Vector[] sample={null};int[] cross={0};
        new RedoRecords().visit(p,h,r->{
            if(r.containerUid()!=677737149)return;
            for(var v:r.vectors()) {
                if(v.containerId()!=3)continue;
                if(sample[0]==null && v.opcodeMajor()==11 && v.opcodeMinor()==5 && v.dba()==0x03000095 && v.fields().get(1).length==28)sample[0]=v;
                if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1) {
                    byte[] b=v.fields().get(1);
                    if(b.length>=24 && word(b,4)==73043 && word(b,12)!=0) {
                        expect("cross-block",()->UndoLink.decode(v),"CROSS_BLOCK_UNDO_LINK_NOT_PROVEN");cross[0]++;
                    }
                }
            }
        });
        if(sample[0]==null || cross[0]!=15)throw new RedoException("NEGATIVE_FIXTURE_COVERAGE_MISMATCH");
        var v=sample[0];
        for(int test=0;test<6;test++) {
            var f=new ArrayList<byte[]>();for(byte[] b:v.fields())f.add(b.clone());
            switch(test) {
                case 0 -> f.get(3)[1]++;
                case 1 -> f.set(3,Arrays.copyOf(f.get(3),902));
                case 2 -> f.set(3,Arrays.copyOf(f.get(3),904));
                case 3 -> f.get(3)[0]=(byte)0xff;
                case 4 -> f.get(2)[0]=2;
                case 5 -> f.get(1)[23]=2;
            }
            var poison=new RedoRecords.Vector(v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),f);
            String reason=test<4 ? "UNSUPPORTED_KDOM2_VALUE_LENGTH" : test==4 ? "INVALID_UPDATE_COLUMN_MAP" : "UNSUPPORTED_KDOM2_UPDATE_LAYOUT";
            expect("poison-"+test,()->new HeapRowPatch().decode(poison,2),reason);
        }
        System.err.println("audit_cases=21;derived_real_vectors_only;cdc_events=0");
    }
    interface Check { void run() throws Exception; }
    static void expect(String name,Check check,String reason)throws Exception {
        try {check.run();throw new IllegalStateException("NEGATIVE_ACCEPTED:"+name);}
        catch(RedoException e){if(!e.getMessage().equals(reason))throw e;System.out.println(name+"|"+reason);}
    }
    static long word(byte[] b,int o){long n=0;for(int j=0;j<4;j++)n|=(long)(b[o+j]&255)<<(8*j);return n;}
}
