package com.streamhouse.oracle;

import java.io.*;
import java.util.*;
import static com.streamhouse.oracle.DurableUndoState.*;

/** Typed conservative marker window and identity cursor. Does not certify marker ownership or cancellation. */
final class CompensationWindowState {
    static byte[] encode(ArchivedCompensationInventory window,byte[] identity)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_COMPENSATION_WINDOW_V1");
        byte[] inventory=new UndoHistory(window.undo()).durableCheckpoint();out.writeInt(inventory.length);out.write(inventory);
        out.writeInt(identity.length);out.write(identity);position(out,window.firstRecord());position(out,window.lastRecord());out.writeBoolean(window.complete());out.writeLong(window.segmentDba());
        out.writeInt(window.entries().size());for(var e:window.entries()) {
            position(out,e.position());var m=e.marker();out.writeLong(m.dba());out.writeLong(m.objectId());out.writeLong(m.dataObjectId());out.writeLong(m.tablespace());out.writeLong(m.previousDba());
            out.writeInt(m.opcodeMajor());out.writeInt(m.opcodeMinor());out.writeInt(m.slot());out.writeInt(m.predecessorRecord());out.writeInt(m.flags());out.writeInt(m.wrap());out.writeBoolean(m.beginsTransaction());
        }
        out.flush();return bytes.toByteArray();
    }
    static ArchivedCompensationInventory restore(byte[] bytes,String trustedSha256)throws IOException {
        byte[] input=bytes.clone();if(!sha(input).equals(trustedSha256))throw new RedoException("COMPENSATION_WINDOW_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));if(!in.readUTF().equals("ORK_COMPENSATION_WINDOW_V1"))throw new RedoException("COMPENSATION_WINDOW_FORMAT");
            byte[] graph=in.readNBytes(count(in));var undo=UndoHistory.restoreDurable(graph,sha(graph)).inventory();byte[] identity=in.readNBytes(count(in));
            var lifecycle=TransactionLifecycle.restore(identity,sha(identity));var sources=undo.sources();lifecycle.requireRecoveryArchive(sources.get(sources.size()-1));
            var first=position(in);var last=position(in);boolean complete=in.readBoolean();long segment=in.readLong();
            requirePosition(first,sources,false);requirePosition(last,sources,!complete);
            var original=undo.entries().get(0).position();
            if(first.vector()!=0 || last.vector()!=0 || compare(first,last)>0 || first.archiveSequence()!=original.archiveSequence() || first.block()!=original.block() || first.offset()!=original.offset()
                    || segment<=0 || segment>0xffffffffL)throw new RedoException("COMPENSATION_WINDOW_BOUNDARY");
            if(!complete) {
                var member=sources.get(sources.size()-1);
                if(last.archiveSequence()!=member.sequence() || last.block()!=member.blocks()-1 || last.offset()!=Integer.MAX_VALUE)throw new RedoException("COMPENSATION_WINDOW_PENDING_BOUNDARY");
                lifecycle.requireRecoveryPending(undo.entries().get(0).identity().xid(),segment,undo.containerUid(),undo.containerId());
            }else {
                lifecycle.requireRecoveryClosed(undo.entries().get(0).identity().xid(),segment,undo.containerUid(),undo.containerId());lifecycle.finish();
            }
            var entries=new ArrayList<ArchivedCompensationInventory.Entry>();ArchivedUndoInventory.Position previous=null;
            for(int i=0,n=count(in);i<n;i++) {
                var at=position(in);requirePosition(at,sources,false);
                var m=new UndoCompensation(in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readBoolean());
                if(at.vector()<1 || compareRecord(at,first)<0 || compareRecord(at,last)>0 || previous!=null && compare(previous,at)>=0
                        || !u32(m.dba()) || m.dba()==0 || !u32(m.objectId()) || !u32(m.dataObjectId()) || !u32(m.tablespace()) || !u32(m.previousDba())
                        || m.opcodeMajor()<0 || m.opcodeMajor()>255 || m.opcodeMinor()<0 || m.opcodeMinor()>255 || m.slot()<0 || m.slot()>255
                        || m.predecessorRecord()<0 || m.predecessorRecord()>255 || m.wrap()<0 || m.wrap()>65535
                        || m.beginsTransaction() && (m.flags()!=0x0c18 || m.predecessorRecord()!=0 || m.wrap()!=1)
                        || !m.beginsTransaction() && m.flags()!=0x10)throw new RedoException("COMPENSATION_WINDOW_MARKER");
                previous=at;entries.add(new ArchivedCompensationInventory.Entry(at,m));
            }
            var window=new ArchivedCompensationInventory(undo,entries,first,last,complete,segment,identity);
            if(in.available()!=0 || !Arrays.equals(input,window.durableCheckpoint()))throw new RedoException("COMPENSATION_WINDOW_NONCANONICAL");return window;
        }catch(RedoException e){throw e;}
        catch(IllegalArgumentException|EOFException|UTFDataFormatException e){throw new RedoException("COMPENSATION_WINDOW_MALFORMED");}
    }
    private static boolean u32(long n){return n>=0 && n<=0xffffffffL;}
    private static void position(DataOutputStream out,ArchivedUndoInventory.Position p)throws IOException {out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());}
    private static ArchivedUndoInventory.Position position(DataInputStream in)throws IOException {return new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());}
    private static void requirePosition(ArchivedUndoInventory.Position p,List<ArchivedRedo.Header> sources,boolean pending)throws RedoException {
        if(p.block()<1 || p.offset()<0 || p.vector()<0 || p.offset()>=512 && !(pending && p.offset()==Integer.MAX_VALUE)
                || sources.stream().noneMatch(h->h.sequence()==p.archiveSequence() && p.block()<h.blocks()))throw new RedoException("COMPENSATION_WINDOW_POSITION");
    }
    private static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());return c==0?Integer.compare(a.offset(),b.offset()):c;
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b){int c=compareRecord(a,b);return c==0?Integer.compare(a.vector(),b.vector()):c;}
}
