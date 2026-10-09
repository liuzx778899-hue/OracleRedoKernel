package com.streamhouse.oracle;

import java.io.*;
import java.util.*;
import static com.streamhouse.oracle.DurableUndoState.*;

/** Complete SYS commit metadata bound to the producer's authenticated dictionary versions. */
final class DictionaryCommittedState {
    static byte[] encode(List<ArchivedDictionaryHistory.Commit> commits)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_DICTIONARY_COMMITTED_V1");out.writeInt(commits.size());
        for(var c:commits) {
            var id=c.identity();var xid=id.xid();out.writeInt(xid.usn());out.writeInt(xid.slot());out.writeLong(xid.sequence());
            out.writeLong(id.containerUid());out.writeLong(id.containerId());out.writeLong(id.beginRecordScn());out.writeLong(id.endRecordScn());out.writeBoolean(id.rolledBack());
            position(out,c.commitPosition());out.writeInt(c.rowPairs());out.writeInt(c.indexPairs());out.writeInt(c.controls());out.writeInt(c.original().size());
            for(var e:c.original()){position(out,e.position());undo(out,e.identity());out.writeInt(e.opcodeMajor());out.writeInt(e.opcodeMinor());out.writeLong(e.previousDba());out.writeInt(e.rci());}
        }
        out.flush();return bytes.toByteArray();
    }
    static List<ArchivedDictionaryHistory.Commit> restore(byte[] bytes,String trustedSha256,FrozenHeapBaseline.Snapshot baseline,
                                                          HistoricalDictionaryState dictionary)throws IOException {
        byte[] input=bytes.clone();if(!sha(input).equals(trustedSha256))throw new RedoException("DICTIONARY_COMMITTED_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(input));if(!in.readUTF().equals("ORK_DICTIONARY_COMMITTED_V1"))throw new RedoException("DICTIONARY_COMMITTED_FORMAT");
            var commits=new ArrayList<ArchivedDictionaryHistory.Commit>();var positions=new HashSet<ArchivedUndoInventory.Position>();var owners=new HashSet<HeapRowPatch.Xid>();
            for(int i=0,n=count(in);i<n;i++) {
                var xid=new HeapRowPatch.Xid(in.readInt(),in.readInt(),in.readLong());
                var id=new TransactionLifecycle.ClosedIdentity(xid,in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readBoolean());
                var at=position(in);int rows=in.readInt(),indexes=in.readInt(),controls=in.readInt();
                var member=member(dictionary,at);
                if(xid.usn()<1 || xid.usn()>65535 || xid.slot()<0 || xid.slot()>65535 || xid.sequence()<0 || xid.sequence()>0xffffffffL
                        || id.containerUid()!=baseline.source().containerUid() || id.containerId()!=baseline.source().containerId() || id.rolledBack()
                        || id.beginRecordScn()<=baseline.scn() || id.endRecordScn()<id.beginRecordScn() || id.endRecordScn()<member.firstScn() || id.endRecordScn()>=member.nextScn()
                        || rows<0 || indexes<0 || controls<0 || !owners.add(xid) || !positions.add(at))throw new RedoException("DICTIONARY_COMMITTED_IDENTITY_OR_POSITION");
                var original=new ArrayList<ArchivedUndoInventory.Entry>();ArchivedUndoInventory.Position previous=null;
                for(int j=0,m=count(in);j<m;j++) {
                    var p=position(in);var h=undo(in);var e=new ArchivedUndoInventory.Entry(p,h,in.readInt(),in.readInt(),in.readLong(),in.readInt());member(dictionary,p);
                    if(!h.xid().equals(xid) || compare(p,at)>0 || previous!=null && compare(previous,p)>=0 || !positions.add(p)
                            || e.opcodeMajor()<0 || e.opcodeMajor()>255 || e.opcodeMinor()<0 || e.opcodeMinor()>255 || e.previousDba()<0 || e.previousDba()>0xffffffffL || e.rci()<0 || e.rci()>255)
                        throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_SCOPE");
                    original.add(e);previous=p;
                }
                if(original.isEmpty() || original.size()!=(long)rows+indexes+controls)throw new RedoException("DICTIONARY_COMMITTED_ORIGINAL_COVERAGE");
                commits.add(new ArchivedDictionaryHistory.Commit(id,original,rows,indexes,controls,List.of(),at));
            }
            var restored=dictionary.withCommitMetadata(commits);
            if(in.available()!=0 || !Arrays.equals(input,encode(restored)))throw new RedoException("DICTIONARY_COMMITTED_NONCANONICAL");return restored;
        } catch(RedoException e){throw e;}
        catch(IllegalArgumentException|NoSuchElementException|EOFException|UTFDataFormatException e){throw new RedoException("DICTIONARY_COMMITTED_MALFORMED");}
    }
    private static void position(DataOutputStream out,ArchivedUndoInventory.Position p)throws IOException {out.writeLong(p.archiveSequence());out.writeLong(p.block());out.writeInt(p.offset());out.writeInt(p.vector());}
    private static ArchivedUndoInventory.Position position(DataInputStream in)throws IOException {return new ArchivedUndoInventory.Position(in.readLong(),in.readLong(),in.readInt(),in.readInt());}
    private static ArchivedRedo.Header member(HistoricalDictionaryState dictionary,ArchivedUndoInventory.Position p)throws RedoException {
        var h=dictionary.sources().stream().filter(s->s.sequence()==p.archiveSequence()).findFirst().orElseThrow();
        if(p.block()<=0 || p.block()>=h.blocks() || p.offset()<0 || p.offset()>=ArchivedRedo.BLOCK_SIZE || p.vector()<1)throw new RedoException("DICTIONARY_COMMITTED_POSITION");return h;
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0?Integer.compare(a.vector(),b.vector()):c;
    }
}
