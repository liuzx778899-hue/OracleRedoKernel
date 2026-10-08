package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;

/** Target block cleanout metadata only; no row data and no authorization to skip an operation. */
public class BlockCleanoutProfileProbe {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[1]),args[2]);var blocks=new HashSet<Long>();
        snapshot.rows().keySet().forEach(k->blocks.add(k.dba()));blocks.add(50359491L);
        var raw=Path.of(args[0]);var header=new ArchivedRedo().scan(raw);
        new RedoRecords().visit(raw,header,record->{
            for(var vector:record.vectors())if(vector.opcodeMajor()==4 && vector.opcodeMinor()==1
                    && vector.containerId()==snapshot.source().containerId() && blocks.contains(vector.dba())) {
                try {SingleEntryBlockCleanout.require(record,vector);}catch(RedoException ex) {
                    System.out.println("REFUSED|"+header.sequence()+"|"+record.block()+"|"+record.offset()+"|"+record.scn()+"|"+vector.scn()+"|"+vector.dba());
                    for(var field:vector.fields())System.out.println(HexFormat.of().formatHex(field));
                }
            }
        });
    }
}
