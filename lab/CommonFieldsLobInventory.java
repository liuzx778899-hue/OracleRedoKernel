package com.streamhouse.oracle;
import java.nio.file.*;
import java.util.*;
/** Frozen-input diagnostic; does not produce events or grant ownership. */
public class CommonFieldsLobInventory {
    public static void main(String[] args)throws Exception {
        var snapshot=FrozenHeapBaseline.read(Path.of(args[0]),args[1]);
        var objects=new HashSet<Long>();snapshot.lobs().forEach(l->objects.add(l.lob().dataObject()));
        System.out.println("OBJECTS|"+objects);
        var path=Path.of(args[2]);var header=new ArchivedRedo().scan(path);
        new RedoRecords().visit(path,header,r->{
            if(r.containerUid()!=snapshot.source().containerUid())return;
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==26 && v.opcodeMinor()==6 && v.fields().size()==4
                    && v.fields().get(1).length>=28 && objects.contains(RedoBytes.u32(v.fields().get(1),24))) {
                var page=SecureFileLobPageImage.decode(v,RedoBytes.u32(v.fields().get(1),24));
                System.out.println("IMAGE|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+page.pageDba()+"|"+page.objectId()+"|"+page.lobId()+"|"+page.xid()+"|"+page.payload().length);
            }
            for(var v:r.vectors())if(v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>=4 && v.fields().get(1).length>=24) {
                var f=v.fields();var m=f.get(1);int minor=m[17]&255;
                if(m[16]==13 && objects.contains(RedoBytes.u32(m,0))) {
                    var metadata=f.get(2);var body=f.get(3);long mode=RedoBytes.u32(body,0);
                    boolean profile=minor==50 && metadata.length==12 && RedoBytes.u32(metadata,8)==8 &&
                            ((mode==1 && body.length==76 && f.size()==4) || (mode==2 && body.length==80 && f.size()==4)
                             || (mode==4 && body.length==20 && ((f.size()==5 && f.get(4).length==8)
                                 || (f.size()==7 && f.get(4).length==8 && f.get(5).length==76 && f.get(6).length==76)))
                             || (mode==8 && body.length==332 && f.size()==4))
                        || minor==54 && metadata.length==8 && RedoBytes.u32(metadata,4)==10 && f.size()==4 &&
                            ((mode==8 && body.length==88) || (mode==14 && body.length==5288))
                        || minor==56 && metadata.length==8 && RedoBytes.u32(metadata,4)==4 && f.size()==4 &&
                            ((mode==3 && body.length==44) || (mode==65543 && body.length==8));
                    if(!profile)System.out.println("PROFILE_UNDO|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+RedoBytes.u32(m,0)+"|13."+minor+"|"+mode+"|"+f.stream().map(b->b.length).toList()+"|"+HexFormat.of().formatHex(metadata)+"|"+HexFormat.of().formatHex(Arrays.copyOf(body,Math.min(100,body.length))));
                }
            }
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==4) {
                try {TransactionEnd.decode(v);}catch(RedoException e) {
                    System.out.println("END_REFUSED|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+e.getMessage());
                    for(int i=0;i<v.fields().size();i++)System.out.println("END_FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
                }
            }
            boolean cross=r.vectors().stream().anyMatch(v->v.opcodeMajor()==5 && v.opcodeMinor()==1 && v.fields().size()>1 && v.fields().get(1).length>=24
                    && objects.contains(RedoBytes.u32(v.fields().get(1),4)) && RedoBytes.u32(v.fields().get(1),12)!=0);
            if(cross)for(var v:r.vectors())if(v.opcodeMajor()==5) {
                System.out.println("CROSS|"+r.block()+"|"+r.offset()+"|5."+v.opcodeMinor()+"|"+v.fields().size());
                for(int i=0;i<Math.min(2,v.fields().size());i++)System.out.println("CROSS_FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
            for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && objects.contains(v.objectId()) && v.opcodeMajor()==13) {
                System.out.println("VECTOR|"+r.block()+"|"+r.offset()+"|"+r.scn()+"|"+v.objectId()+"|"+v.dba()+"|13."+v.opcodeMinor());
                for(int i=0;i<v.fields().size();i++)System.out.println("FIELD|"+i+"|"+HexFormat.of().formatHex(v.fields().get(i)));
            }
        });
    }
}
