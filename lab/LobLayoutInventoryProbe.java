import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Format inventory only: emits no events and makes no LOB ownership or transaction claim. */
public class LobLayoutInventoryProbe {
    public static void main(String[] args)throws Exception {
        long scn=Long.parseLong(args[0]);var counts=new TreeMap<String,Integer>();int records=0;
        var digest=MessageDigest.getInstance("SHA-256");var hex=HexFormat.of();
        for(int a=1;a<args.length;a+=2) {
            var capture=new CaptureJournal().readRawClosed(Path.of(args[a]),args[a+1]);
            for(var record:new RedoRecords().readCaptured(capture)) {
                if(record.containerUid()!=677737149L || record.scn()<=scn)continue;
                records++;
                System.out.println("RECORD|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+record.scn()+"|"+record.vectors().size()+"|"+record.subScn()+"|"+record.validity()+"|"+record.length());
                for(int v=0;v<record.vectors().size();v++) {
                    var vector=record.vectors().get(v);String opcode=vector.opcodeMajor()+"."+vector.opcodeMinor();counts.merge(opcode,1,Integer::sum);
                    if(opcode.equals("19.1")) {
                        byte[] field=vector.fields().get(0);
                        long object=(field[0]&255L)|((field[1]&255L)<<8)|((field[2]&255L)<<16)|((field[3]&255L)<<24);
                        var page=BasicFileLobPage.decode(vector,object);
                        System.out.println("PAGE|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                page.objectId()+"|"+page.lobId()+"|"+page.version()+"|"+page.pageNumber()+"|"+page.parentDba()+"|"+
                                hex.formatHex(digest.digest(page.payload()))+"|"+hex.formatHex(page.payload()));
                    }
                    if(opcode.equals("26.2")) {
                        var fill=SecureFileLobFill.decode(vector);
                        System.out.println("FILL|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                fill.pageDba()+"|"+fill.lobId()+"|"+fill.undo()+"|"+fill.offset()+"|"+fill.highWater()+"|"+
                                hex.formatHex(digest.digest(fill.payload()))+"|"+hex.formatHex(fill.payload())+"|"+hex.formatHex(fill.ktb()));
                        if(!fill.undo()) {
                            long object=switch(fill.lobId()) {
                                case "0000000100000034f6cb"->79341;
                                case "0000000100000034f6cc"->79343;
                                default->throw new RedoException("LOB_PROBE_OUTSIDE_FROZEN_FIXTURE");
                            };
                            var pair=SecureFileLobUndoPair.bind(record,vector,object);var owner=pair.owner();
                            System.out.println("PAIR|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                    owner.objectId()+"|"+owner.xid().usn()+"."+owner.xid().slot()+"."+owner.xid().sequence()+"|"+
                                    owner.uba().dba()+"|"+owner.uba().sequence()+"|"+owner.uba().record()+"|"+fill.lobId()+"|"+
                                    fill.offset()+"|"+pair.inverseHighWater()+"|"+(pair.inverse()==null?"-":hex.formatHex(pair.inverse().payload())));
                        }
                    }
                    if(opcode.equals("26.6")) {
                        byte[] field=vector.fields().get(1);
                        long object=(field[24]&255L)|((field[25]&255L)<<8)|((field[26]&255L)<<16)|((field[27]&255L)<<24);
                        var image=SecureFileLobPageImage.decode(vector,object);
                        System.out.println("IMAGE|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                image.objectId()+"|"+image.lobId()+"|"+image.pageDba()+"|"+image.pageScn()+"|"+
                                image.xid().usn()+"."+image.xid().slot()+"."+image.xid().sequence()+"|"+hex.formatHex(image.payload()));
                    }
                    // Fixture heap DBAs from the frozen metadata/native inventory, for diagnostics only.
                    if(vector.opcodeMajor()==11 && (vector.opcodeMinor()==2 || vector.opcodeMinor()==3 || vector.opcodeMinor()==5)
                            && (vector.dba()==50365967 || vector.dba()==50366015)) {
                        var row=new HeapRowPatch().decode(vector,3);
                        for(int col=0;col<row.columns().size();col++) {
                            var value=row.columns().get(col);if(value.state()==HeapRowPatch.State.ABSENT)continue;
                            System.out.println("LOCATOR|"+capture.source().sequence()+"|"+record.block()+"|"+record.offset()+"|"+(v+1)+"|"+
                                    record.scn()+"|"+row.dba()+"|"+row.slot()+"|"+row.xtype()+"|"+col+"|"+value.state()+"|"+
                                    (value.bytes()==null?"":hex.formatHex(value.bytes())));
                            if(col>0 && value.state()==HeapRowPatch.State.VALUE) {
                                var locator=LobLocator.decode(value.bytes(),col==1?LobLocator.Kind.BLOB:LobLocator.Kind.CLOB,
                                        row.dba()==50365967?LobLocator.Storage.BASICFILE:LobLocator.Storage.SECUREFILE);
                                System.out.println("LOB|"+record.scn()+"|"+row.dba()+"|"+row.slot()+"|"+col+"|"+locator.kind()+"|"+
                                        locator.storage()+"|"+locator.lobId()+"|"+locator.byteLength()+"|"+locator.version()+"|"+
                                        locator.pages().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"|"+
                                        (locator.inlinePayload()==null?"-":hex.formatHex(locator.inlinePayload())));
                            }
                        }
                    }
                    System.out.println("VECTOR|"+v+"|"+opcode+"|"+vector.dba()+"|"+vector.blockClass()+"|"+vector.fields().size());
                    for(int f=0;f<vector.fields().size();f++) {
                        byte[] bytes=vector.fields().get(f);
                        System.out.println("FIELD|"+f+"|"+bytes.length+"|"+hex.formatHex(digest.digest(bytes))+"|"+
                                hex.formatHex(bytes)+"|"+
                                hex.formatHex(Arrays.copyOfRange(bytes,Math.max(0,bytes.length-16),bytes.length)));
                    }
                }
            }
        }
        System.out.println("SUMMARY|records="+records+"|opcodes="+counts+"|events=0|mode=STRUCTURAL_ONLY");
    }
}
