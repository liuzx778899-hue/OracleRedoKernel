package com.streamhouse.oracle;

import java.io.*;
import java.util.*;
import static com.streamhouse.oracle.RedoBytes.*;

/** Selected original redo records, sealed by the producer checkpoint. Never a replacement for its trusted digest. */
final class LobOriginCapsule {
    record Member(CaptureJournal.Source source,List<RedoRecords.Record> records) {Member {records=records.stream().map(RedoBatch::copy).toList();}}
    record Parsed(RedoBatch inputs,FrozenHeapBaseline.Snapshot root,ArchivedDictionaryHistory.Timeline history,CapturedLobTrustedBatch.Prepared values) {}
    private final byte[] baseline;
    private final long leaf;
    private final List<Member> members;
    private LobOriginCapsule(byte[] baseline,long leaf,List<Member> members){this.baseline=baseline.clone();this.leaf=leaf;this.members=List.copyOf(members);}
    byte[] baseline(){return baseline.clone();}
    long leaf(){return leaf;}
    List<Member> members(){return members;}
    FrozenHeapBaseline.Snapshot root()throws IOException {
        var root=FrozenHeapBaseline.read(baseline,sha(baseline));
        if(root.lobs().isEmpty() || root.lobOrigin()!=null)throw new RedoException("LOB_ORIGIN_ROOT_BASELINE");return root;
    }
    static LobOriginCapsule create(RedoBatch inputs,byte[] baseline,long leaf,ArchivedDictionaryHistory.Timeline history)throws IOException {
        var snapshot=FrozenHeapBaseline.read(baseline,sha(baseline));var objects=new HashSet<Long>();
        objects.add(snapshot.heap().dataObject());objects.add(snapshot.index().dataObject());
        for(var lob:snapshot.lobs()){objects.add(lob.lob().dataObject());objects.add(lob.index().dataObject());}
        var owners=new HashSet<HeapRowPatch.Xid>();
        for(var commit:history.commits()){owners.add(commit.identity().xid());for(var role:commit.generations())objects.add(role.dataObject());}
        for(var input:inputs) {
            var header=input.header();input.visit(header,r->{if(r.containerUid()!=snapshot.source().containerUid())return;
                for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId() && v.opcodeMajor()==5 && v.opcodeMinor()==1) {
                    if(v.fields().size()<2 || v.fields().get(0).length!=20 || v.fields().get(1).length<12)throw new RedoException("LOB_ORIGIN_UNREADABLE_OWNER");
                    if(objects.contains(u32(v.fields().get(1),4)))owners.add(xid(v));
                }
            });if(!header.equals(input.header()))throw new RedoException("LOB_ORIGIN_SOURCE_CHANGED");
        }
        var members=new ArrayList<Member>();
        for(int i=0;i<inputs.size();i++) {
            var input=inputs.get(i);var header=input.header();var records=new ArrayList<RedoRecords.Record>();
            input.visit(header,r->{if(r.containerUid()!=snapshot.source().containerUid())return;boolean retain=false;
                for(var v:r.vectors())if(v.containerId()==snapshot.source().containerId()) {
                    retain|=objects.contains(v.objectId()) || v.opcodeMajor()==24 || v.objectId()==18 && v.opcodeMajor()==11;
                    if(v.opcodeMajor()==5)retain|=v.opcodeMinor()!=1 || owners.contains(xid(v));
                }
                if(retain)records.add(RedoBatch.copy(r));
            });if(!header.equals(input.header()))throw new RedoException("LOB_ORIGIN_SOURCE_CHANGED");
            members.add(new Member(inputs.source(i),records));
        }
        return new LobOriginCapsule(baseline,leaf,members);
    }
    Parsed parse(RedoBatch suffix)throws IOException {
        var snapshot=root();var inputs=RedoBatch.origin(members,suffix);
        var history=ArchivedDictionaryHistory.read(inputs,snapshot);
        return new Parsed(inputs,snapshot,history,CapturedLobTrustedBatch.prepare(inputs,snapshot,leaf,history));
    }
    void requireCut(FrozenHeapBaseline.Snapshot cut,long currentLeaf)throws IOException {
        var parsed=parse(RedoBatch.captures(List.of()));long scn=members.get(members.size()-1).source().nextScn()-1;
        var history=parsed.history();
        if(cut.scn()!=scn || !cut.source().equals(parsed.root().source()) || !cut.heap().equals(history.heapAt(scn,null))
                || !cut.index().equals(history.indexAt(scn,null)) || !cut.lobs().equals(history.lobsAt(scn,null))
                || !cut.columns().equals(history.schemaAt(scn,null)) || !cut.rows().equals(parsed.values().rows())
                || currentLeaf!=currentLeaf(parsed.inputs(),cut.index().dataObject()))throw new RedoException("LOB_ORIGIN_CUT_VALUES_CHANGED");
        var selected=history.materializedState().rowsAt(scn,null).stream().filter(r->r.target()==cut.heap().logicalObject()
                || r.role().equals("OBJ$") && r.target()==cut.index().logicalObject()).toList();
        if(!selected.equals(cut.dictionaryBaseline().rows()))throw new RedoException("LOB_ORIGIN_CUT_DICTIONARY_CHANGED");
    }
    static long currentLeaf(RedoBatch inputs,long index)throws IOException {
        var leaves=new HashSet<Long>();
        for(var input:inputs)input.visit(input.header(),r->{for(var v:r.vectors())if(v.opcodeMajor()==10 && v.objectId()==index)leaves.add(v.dba());});
        if(leaves.size()!=1)throw new RedoException("LOB_ORIGIN_PRIMARY_LEAF_COVERAGE");return leaves.iterator().next();
    }
    byte[] checkpoint()throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("ORK_LOB_ORIGIN_V1");out.writeLong(leaf);blob(out,baseline);out.writeInt(members.size());
        for(var member:members) {
            var s=member.source();out.writeUTF(s.journalSha256());out.writeLong(s.journalBytes());out.writeLong(s.capacityBlocks());out.writeLong(s.blocks());out.writeLong(s.dbid());out.writeLong(s.activationId());
            out.writeLong(s.compatibility());out.writeInt(s.thread());out.writeLong(s.sequence());out.writeLong(s.resetlogsId());out.writeLong(s.resetlogsScn());out.writeLong(s.firstScn());out.writeLong(s.nextScn());out.writeInt(member.records().size());
            for(var r:member.records()) {
                out.writeLong(r.block());out.writeInt(r.offset());out.writeInt(r.length());out.writeInt(r.validity());out.writeLong(r.scn());out.writeInt(r.subScn());out.writeLong(r.containerUid());out.writeInt(r.vectors().size());
                for(var v:r.vectors()) {
                    out.writeInt(v.opcodeMajor());out.writeInt(v.opcodeMinor());out.writeInt(v.blockClass());out.writeInt(v.file());out.writeLong(v.dba());out.writeLong(v.scn());out.writeInt(v.sequence());out.writeLong(v.containerId());out.writeLong(v.objectId());out.writeInt(v.fields().size());
                    for(var f:v.fields())blob(out,f);
                }
            }
        }
        out.flush();return bytes.toByteArray();
    }
    static LobOriginCapsule restore(byte[] supplied,String trustedSha)throws IOException {
        byte[] bytes=supplied.clone();if(!sha(bytes).equals(trustedSha))throw new RedoException("LOB_ORIGIN_DIGEST_MISMATCH");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));if(!in.readUTF().equals("ORK_LOB_ORIGIN_V1"))throw new RedoException("LOB_ORIGIN_FORMAT");
            long leaf=in.readLong();byte[] baseline=blob(in);var snapshot=FrozenHeapBaseline.read(baseline,sha(baseline));
            if(leaf<=0 || leaf>0xffffffffL || snapshot.lobOrigin()!=null || snapshot.lobs().isEmpty())throw new RedoException("LOB_ORIGIN_ROOT_SCOPE");
            var members=new ArrayList<Member>();var continuity=new TransactionLifecycle();
            for(int i=0,n=count(in);i<n;i++) {
                var source=new CaptureJournal.Source(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());
                var h=header(source);snapshot.requireSource(h);continuity.archive(h);
                if(!source.journalSha256().matches("[0-9a-f]{64}") || source.journalBytes()<=0 || source.capacityBlocks()<source.blocks() || source.blocks()<3)throw new RedoException("LOB_ORIGIN_SOURCE");
                var records=new ArrayList<RedoRecords.Record>();long block=-1;int offset=-1;
                for(int j=0,m=count(in);j<m;j++) {
                    long nextBlock=in.readLong();int nextOffset=in.readInt(),length=in.readInt(),validity=in.readInt();long scn=in.readLong();int subScn=in.readInt();long uid=in.readLong();
                    if(nextBlock<2 || nextBlock>=source.blocks() || nextOffset<0 || nextOffset>=512 || length<=0 || uid!=snapshot.source().containerUid()
                            || scn<source.firstScn() || scn>=source.nextScn() || nextBlock<block || nextBlock==block && nextOffset<=offset)throw new RedoException("LOB_ORIGIN_RECORD_POSITION");
                    block=nextBlock;offset=nextOffset;var vectors=new ArrayList<RedoRecords.Vector>();
                    for(int k=0,l=count(in);k<l;k++) {
                        int major=in.readInt(),minor=in.readInt(),cls=in.readInt(),file=in.readInt();long dba=in.readLong(),vectorScn=in.readLong();int seq=in.readInt();long container=in.readLong(),object=in.readLong();var fields=new ArrayList<byte[]>();
                        if(major<0 || major>255 || minor<0 || minor>255 || dba<0 || dba>0xffffffffL || object< -1 || object>0xffffffffL)throw new RedoException("LOB_ORIGIN_VECTOR_SCOPE");
                        for(int f=0,c=count(in);f<c;f++)fields.add(blob(in));vectors.add(new RedoRecords.Vector(major,minor,cls,file,dba,vectorScn,seq,container,List.copyOf(fields),object));
                    }
                    records.add(new RedoRecords.Record(block,offset,length,validity,scn,subScn,uid,List.copyOf(vectors)));
                }
                members.add(new Member(source,records));
            }
            continuity.finish();if(members.isEmpty())throw new RedoException("LOB_ORIGIN_SOURCE_REQUIRED");
            var result=new LobOriginCapsule(baseline,leaf,members);
            if(in.available()!=0 || !Arrays.equals(bytes,result.checkpoint()))throw new RedoException("LOB_ORIGIN_NONCANONICAL");return result;
        } catch(RedoException failure){throw failure;}
        catch(IllegalArgumentException|EOFException|UTFDataFormatException failure){throw new RedoException("LOB_ORIGIN_MALFORMED");}
    }
    static ArchivedRedo.Header header(CaptureJournal.Source s){return new ArchivedRedo.Header(s.journalSha256(),s.journalBytes(),s.dbid(),s.activationId(),s.compatibility(),s.thread(),s.sequence(),s.resetlogsId(),s.resetlogsScn(),s.firstScn(),s.nextScn(),s.blocks());}
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static HeapRowPatch.Xid xid(RedoRecords.Vector v){byte[] b=v.fields().get(0);return new HeapRowPatch.Xid(u16(b,8),u16(b,10),u32(b,12));}
    private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] blob(DataInputStream in)throws IOException{return in.readNBytes(count(in));}
    private static int count(DataInputStream in)throws IOException{int n=in.readInt();if(n<0 || n>in.available())throw new RedoException("LOB_ORIGIN_COUNT");return n;}
}
