package com.streamhouse.oracle;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.HexFormat;

public final class RedoCli {
    private RedoCli() {}
    public static void main(String[] args) {
        int rc=run(args);
        if (rc != 0) System.exit(rc);
    }
    static int run(String[] args) {
        try {
            if (args.length != 2 || !(args[0].equals("scan") || args[0].equals("records") || args[0].equals("vectors") || args[0].equals("events"))) {
                System.err.println("usage: java -jar oracle-redo-kernel.jar scan|records|vectors|events <frozen-archive>");
                return 2;
            }
            Path path=Path.of(args[1]);
            ArchivedRedo.Header header=new ArchivedRedo().scan(path);
            System.err.println(header);
            if (args[0].equals("events")) throw new RedoException("DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED");
            if (args[0].equals("records") || args[0].equals("vectors")) {
                long count=new RedoRecords().visit(path,header,r -> {
                    if (args[0].equals("vectors")) {
                        for (int i=0;i<r.vectors().size();i++) {
                            var v=r.vectors().get(i);
                            System.out.printf("%d|%d|%d|%d.%d|%d|%d|%d|%d|%d|%d|%s%n",r.block(),r.offset(),i+1,
                                    v.opcodeMajor(),v.opcodeMinor(),v.blockClass(),v.file(),v.dba(),v.scn(),v.sequence(),v.containerId(),
                                    v.fields().stream().map(f -> HexFormat.of().formatHex(f)).collect(Collectors.joining(",")));
                        }
                    } else System.out.printf(
                        "%d|%d|%d|%d|%d|%d|%d|%s%n",r.block(),r.offset(),r.length(),r.validity(),r.scn(),r.subScn(),r.containerUid(),
                        r.vectors().stream().map(v -> v.opcodeMajor()+"."+v.opcodeMinor()).collect(Collectors.joining(",")));
                });
                System.err.println("structural_records="+count+";cdc_events=0");
            } else System.out.println("SCAN_OK;cdc_events=0");
            return 0;
        } catch (Exception e) {
            System.err.println("REFUSED:"+e.getMessage()+";cdc_events=0");
            return 1;
        }
    }
}
