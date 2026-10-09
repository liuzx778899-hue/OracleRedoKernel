package com.streamhouse.oracle;

import java.io.*;
import java.util.*;

/** Internal value codec for an authenticated undo graph, not a physical pair or commit certificate. */
final class DurableUndoState {
    static byte[] encode(ArchivedUndoInventory inventory,byte[] actions)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeUTF("ORK_DURABLE_UNDO_V1");out.writeLong(inventory.containerUid());out.writeLong(inventory.containerId());
        out.writeInt(inventory.sources().size());
        for(var h:inventory.sources()) {
            out.writeUTF(h.sha256());out.writeLong(h.bytes());out.writeLong(h.dbid());out.writeLong(h.activationId());
            out.writeLong(h.compatibility());out.writeInt(h.thread());out.writeLong(h.sequence());out.writeLong(h.resetlogsId());
            out.writeLong(h.resetlogsScn());out.writeLong(h.firstScn());out.writeLong(h.nextScn());out.writeLong(h.blocks());
        }
        var root=inventory.controlRoot();out.writeBoolean(root!=null);
        if(root!=null) {
            var s=root.start();out.writeLong(s.segmentDba());out.writeInt(s.slot());out.writeLong(s.sequence());uba(out,s.uba());
            out.writeInt(s.flags());out.writeLong(s.containerUid());out.writeLong(s.containerId());
            undo(out,root.allocationBegin());undo(out,root.rowRoot());
        }
        out.writeInt(inventory.entries().size());
        for(var e:inventory.entries()) {
            var p=e.position();out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());
            undo(out,e.identity());out.writeInt(e.opcodeMajor());out.writeInt(e.opcodeMinor());out.writeLong(e.previousDba());out.writeInt(e.rci());
        }
        out.writeInt(actions.length);out.write(actions);out.flush();return bytes.toByteArray();
    }
    static UndoHistory restore(byte[] bytes,String trustedSha256)throws IOException {
        byte[] input=bytes.clone();if(!sha(input).equals(trustedSha256))throw new RedoException("DURABLE_UNDO_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));
            if(!in.readUTF().equals("ORK_DURABLE_UNDO_V1"))throw new RedoException("DURABLE_UNDO_FORMAT");
            long uid=in.readLong(),container=in.readLong();if(uid<=0 || container<=0)throw new RedoException("DURABLE_UNDO_CONTAINER");
            var sources=new ArrayList<ArchivedRedo.Header>();var continuity=new TransactionLifecycle();
            for(int i=0,n=count(in);i<n;i++) {
                var h=new ArchivedRedo.Header(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),
                        in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());
                if(!h.sha256().matches("[0-9a-f]{64}") || h.bytes()<=0 || h.blocks()<3 || h.thread()<1 || h.sequence()<=0 || h.firstScn()>=h.nextScn())
                    throw new RedoException("DURABLE_UNDO_SOURCE");
                continuity.archive(h);sources.add(h);
            }
            if(sources.isEmpty())throw new RedoException("DURABLE_UNDO_SOURCE");
            UndoControlRoot root=null;
            if(in.readBoolean()) {
                var start=new TransactionStart(in.readLong(),in.readInt(),in.readLong(),uba(in),in.readInt(),in.readLong(),in.readLong());
                root=new UndoControlRoot(start,undo(in),undo(in));
            }
            var entries=new ArrayList<ArchivedUndoInventory.Entry>();HeapRowPatch.Xid owner=null;ArchivedUndoInventory.Position previous=null;
            for(int i=0,n=count(in);i<n;i++) {
                var p=new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());
                var h=undo(in);var e=new ArchivedUndoInventory.Entry(p,h,in.readInt(),in.readInt(),in.readLong(),in.readInt());
                var member=sources.stream().filter(s->s.sequence()==p.archiveSequence()).findFirst().orElseThrow();
                if(p.block()<=0 || p.block()>=member.blocks() || p.offset()<0 || p.offset()>=512 || p.vector()<1
                        || previous!=null && compare(previous,p)>=0 || e.opcodeMajor()<0 || e.opcodeMajor()>255 || e.opcodeMinor()<0 || e.opcodeMinor()>255
                        || e.previousDba()<0 || e.previousDba()>0xffffffffL || e.rci()<0 || e.rci()>255
                        || owner!=null && !owner.equals(h.xid()))throw new RedoException("DURABLE_UNDO_ENTRY_SCOPE_OR_ORDER");
                if(owner==null)owner=h.xid();previous=p;entries.add(e);
            }
            if(entries.isEmpty())throw new RedoException("DURABLE_UNDO_EMPTY_INVENTORY");
            if(root!=null && (entries.size()<2 || !root.allocationBegin().equals(entries.get(0).identity())
                    || !root.rowRoot().equals(entries.get(1).identity()) || root.start().containerUid()!=uid || root.start().containerId()!=container
                    || !root.start().uba().equals(root.allocationBegin().uba()) || root.start().slot()!=owner.slot()
                    || root.start().sequence()!=owner.sequence()))throw new RedoException("DURABLE_UNDO_CONTROL_ROOT");
            byte[] actions=in.readNBytes(count(in));
            var inventory=new ArchivedUndoInventory(sources,entries,uid,container,root);
            var state=UndoHistory.restore(inventory,actions,sha(actions));
            if(in.available()!=0 || !Arrays.equals(input,state.durableCheckpoint()))throw new RedoException("DURABLE_UNDO_NONCANONICAL");
            return state;
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException|NoSuchElementException|EOFException|UTFDataFormatException e){throw new RedoException("DURABLE_UNDO_MALFORMED");}
    }
    private static void uba(DataOutputStream out,HeapRowPatch.Uba u)throws IOException {out.writeLong(u.dba());out.writeInt(u.sequence());out.writeInt(u.record());}
    private static HeapRowPatch.Uba uba(DataInputStream in)throws IOException {
        var u=new HeapRowPatch.Uba(in.readLong(),in.readInt(),in.readInt());
        if(u.dba()<=0 || u.dba()>0xffffffffL || u.sequence()<0 || u.sequence()>65535 || u.record()<1 || u.record()>255)
            throw new RedoException("DURABLE_UNDO_UBA");return u;
    }
    private static void undo(DataOutputStream out,UndoHeader h)throws IOException {
        out.writeInt(h.xid().usn());out.writeInt(h.xid().slot());out.writeLong(h.xid().sequence());uba(out,h.uba());
        out.writeLong(h.objectId());out.writeLong(h.dataObjectId());out.writeLong(h.tablespace());out.writeBoolean(h.beginsTransaction());
    }
    private static UndoHeader undo(DataInputStream in)throws IOException {
        var xid=new HeapRowPatch.Xid(in.readInt(),in.readInt(),in.readLong());var u=uba(in);
        var h=new UndoHeader(xid,u,in.readLong(),in.readLong(),in.readLong(),in.readBoolean());
        if(xid.usn()<1 || xid.usn()>65535 || xid.slot()<0 || xid.slot()>65535 || xid.sequence()<0 || xid.sequence()>0xffffffffL
                || h.objectId()<0 || h.objectId()>0xffffffffL || h.dataObjectId()<0 || h.dataObjectId()>0xffffffffL
                || h.tablespace()<0 || h.tablespace()>0xffffffffL)throw new RedoException("DURABLE_UNDO_IDENTITY");return h;
    }
    private static int count(DataInputStream in)throws IOException {
        int n=in.readInt();if(n<0 || n>in.available())throw new RedoException("DURABLE_UNDO_COUNT");return n;
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());
        if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0?Integer.compare(a.vector(),b.vector()):c;
    }
    private static String sha(byte[] bytes) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
