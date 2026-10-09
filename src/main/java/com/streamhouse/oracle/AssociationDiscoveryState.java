package com.streamhouse.oracle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authenticated source records for object discovery and header routing, not physical cancellation authority. */
final class AssociationDiscoveryState {
    private final List<ArchivedRedo.Header> sources;
    private final long uid,container;
    private final List<List<String>> records;
    private final Map<HeapRowPatch.Xid,ArchivedCompensationInventory> owners;
    private AssociationDiscoveryState(List<ArchivedRedo.Header> sources,long uid,long container,List<List<String>> records,
                                      Map<HeapRowPatch.Xid,ArchivedCompensationInventory> owners) {
        this.sources=List.copyOf(sources);this.uid=uid;this.container=container;this.records=records.stream().map(List::copyOf).toList();this.owners=new HashMap<>(owners);
    }
    static AssociationDiscoveryState read(RedoBatch inputs,long uid,long container)throws IOException {
        return new AssociationDiscoveryState(List.of(),uid,container,List.of(),Map.of()).extend(inputs);
    }
    AssociationDiscoveryState extend(RedoBatch inputs)throws IOException {
        require(inputs,uid,container);return append(inputs.suffix(sources.size()));
    }
    AssociationDiscoveryState append(RedoBatch suffix)throws IOException {
        var nextSources=new ArrayList<>(sources);var nextRecords=new ArrayList<>(records);
        var continuity=new TransactionLifecycle();for(var h:sources)continuity.archive(h);
        for(var path:suffix) {
            var header=path.header();continuity.archive(header);var selected=new ArrayList<String>();
            path.visit(header,r->{if(relevant(r,uid,container))selected.add(Base64.getEncoder().encodeToString(record(r)));});
            if(!header.equals(path.header()))throw new RedoException("ASSOCIATION_DISCOVERY_INPUT_CHANGED");
            nextSources.add(header);nextRecords.add(selected);
        }
        return new AssociationDiscoveryState(nextSources,uid,container,nextRecords,owners);
    }
    void require(RedoBatch inputs,long expectedUid,long expectedContainer)throws IOException {
        if(uid!=expectedUid || container!=expectedContainer || sources.size()>inputs.size())throw new RedoException("ASSOCIATION_DISCOVERY_SOURCE_OR_CONTAINER");
        for(int i=0;i<sources.size();i++)if(!sources.get(i).equals(inputs.get(i).header()))throw new RedoException("ASSOCIATION_DISCOVERY_SOURCE_OR_CONTAINER");
    }
    void visit(int source,RedoRecords.Visitor visitor)throws IOException {
        for(var value:records.get(source))visitor.accept(record(Base64.getDecoder().decode(value)));
    }
    ArchivedRedo.Header source(int index) {return sources.get(index);}
    void remember(ArchivedCompensationInventory window)throws RedoException {
        if(!window.undo().sources().equals(sources) || window.undo().containerUid()!=uid || window.undo().containerId()!=container)
            throw new RedoException("ASSOCIATION_DISCOVERY_OWNER_SCOPE");
        owners.put(window.undo().entries().get(0).identity().xid(),window);
    }
    void requireWindow(ArchivedCompensationInventory window)throws IOException {
        var saved=owners.get(window.undo().entries().get(0).identity().xid());
        if(saved==null || !Arrays.equals(saved.durableCheckpoint(),window.durableCheckpoint()))throw new RedoException("ASSOCIATION_DISCOVERY_SAVED_WINDOW_CHANGED");
    }
    ArchivedCompensationInventory owner(RedoBatch inputs,HeapRowPatch.Xid xid,boolean allowPending)throws IOException {
        var window=owners.get(xid);
        if(window==null)window=ArchivedCompensationInventory.read(inputs,uid,container,xid,allowPending);
        else {
            int count=window.undo().sources().size();if(count>sources.size() || !window.undo().sources().equals(sources.subList(0,count)))throw new RedoException("ASSOCIATION_DISCOVERY_OWNER_SCOPE");
            if(count<sources.size())window=window.complete()?window.advanceClosed(inputs.suffix(count)):window.extend(inputs.suffix(count),allowPending);
            if(!allowPending && !window.complete())throw new RedoException("COMPENSATION_INVENTORY_TRANSACTION_WINDOW_INCOMPLETE");
        }
        remember(window);return window;
    }
    byte[] checkpoint()throws IOException {
        var out=new StringBuilder("ORK_ASSOCIATION_DISCOVERY_V1\nH|"+uid+"|"+container+"\n");
        for(int i=0;i<sources.size();i++) {
            out.append("S|").append(DurableUndoState.sha(sources.get(i).toString().getBytes(StandardCharsets.UTF_8))).append('\n');
            for(var value:records.get(i))out.append("R|").append(value).append('\n');
        }
        var sorted=new TreeMap<String,ArchivedCompensationInventory>();
        for(var entry:owners.entrySet())sorted.put(entry.getKey().toString(),entry.getValue());
        for(var window:sorted.values())out.append("O|").append(Base64.getEncoder().encodeToString(window.durableCheckpoint())).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    static AssociationDiscoveryState restore(byte[] bytes,String trustedSha,List<ArchivedRedo.Header> sources,long uid,long container)throws IOException {
        byte[] input=bytes.clone();if(!DurableUndoState.sha(input).equals(trustedSha))throw new RedoException("ASSOCIATION_DISCOVERY_DIGEST");
        try {
            String[] lines=new String(input,StandardCharsets.UTF_8).split("\n");
            if(lines.length<2 || !lines[0].equals("ORK_ASSOCIATION_DISCOVERY_V1") || !lines[1].equals("H|"+uid+"|"+container))throw new RedoException("ASSOCIATION_DISCOVERY_FORMAT");
            var records=new ArrayList<List<String>>();var owners=new HashMap<HeapRowPatch.Xid,ArchivedCompensationInventory>();int at=2;
            for(var h:sources) {
                if(at>=lines.length || !lines[at++].equals("S|"+DurableUndoState.sha(h.toString().getBytes(StandardCharsets.UTF_8))))throw new RedoException("ASSOCIATION_DISCOVERY_SOURCE_OR_CONTAINER");
                var selected=new ArrayList<String>();long block=0;int offset=-1;
                while(at<lines.length && lines[at].startsWith("R|")) {
                    String value=lines[at++].substring(2);byte[] raw=Base64.getDecoder().decode(value);var r=record(raw);
                    if(!relevant(r,uid,container) || r.block()<1 || r.block()>=h.blocks() || r.offset()<0 || r.offset()>=512
                            || r.block()<block || r.block()==block && r.offset()<=offset || r.scn()<h.firstScn() || r.scn()>=h.nextScn()
                            || !Arrays.equals(raw,record(r)))throw new RedoException("ASSOCIATION_DISCOVERY_RECORD_SCOPE_OR_ORDER");
                    block=r.block();offset=r.offset();selected.add(Base64.getEncoder().encodeToString(raw));
                }
                records.add(selected);
            }
            while(at<lines.length && lines[at].startsWith("O|")) {
                byte[] raw=Base64.getDecoder().decode(lines[at++].substring(2));var window=ArchivedCompensationInventory.restoreDurable(raw,DurableUndoState.sha(raw));
                var old=window.undo().sources();
                if(old.size()>sources.size() || !old.equals(sources.subList(0,old.size())) || window.undo().containerUid()!=uid || window.undo().containerId()!=container
                        || owners.putIfAbsent(window.undo().entries().get(0).identity().xid(),window)!=null)throw new RedoException("ASSOCIATION_DISCOVERY_OWNER_SCOPE");
            }
            var result=new AssociationDiscoveryState(sources,uid,container,records,owners);
            if(at!=lines.length || !Arrays.equals(input,result.checkpoint()))throw new RedoException("ASSOCIATION_DISCOVERY_NONCANONICAL");return result;
        }catch(IllegalArgumentException|EOFException e){throw new RedoException("ASSOCIATION_DISCOVERY_MALFORMED");}
    }
    private static boolean relevant(RedoRecords.Record r,long uid,long container) {
        return r.containerUid()==uid && r.vectors().stream().anyMatch(v->v.containerId()==container && v.opcodeMajor()==5
                && (v.opcodeMinor()==1 || v.opcodeMinor()==2 || v.opcodeMinor()==6 || v.opcodeMinor()==11));
    }
    private static byte[] record(RedoRecords.Record r)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeLong(r.block());out.writeInt(r.offset());out.writeInt(r.length());out.writeInt(r.validity());out.writeLong(r.scn());out.writeInt(r.subScn());out.writeLong(r.containerUid());out.writeInt(r.vectors().size());
        for(var v:r.vectors()) {
            out.writeInt(v.opcodeMajor());out.writeInt(v.opcodeMinor());out.writeInt(v.blockClass());out.writeInt(v.file());out.writeLong(v.dba());out.writeLong(v.scn());out.writeInt(v.sequence());out.writeLong(v.containerId());out.writeInt(v.fields().size());
            for(byte[] f:v.fields()){out.writeInt(f.length);out.write(f);}
        }
        out.flush();return bytes.toByteArray();
    }
    private static RedoRecords.Record record(byte[] bytes)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));long block=in.readLong();int offset=in.readInt(),length=in.readInt(),validity=in.readInt();long scn=in.readLong();int subScn=in.readInt();long uid=in.readLong();
        var vectors=new ArrayList<RedoRecords.Vector>();
        for(int i=0,n=DurableUndoState.count(in);i<n;i++) {
            int major=in.readInt(),minor=in.readInt(),clazz=in.readInt(),file=in.readInt();long dba=in.readLong(),vectorScn=in.readLong();int sequence=in.readInt();long container=in.readLong();var fields=new ArrayList<byte[]>();
            for(int j=0,m=DurableUndoState.count(in);j<m;j++)fields.add(in.readNBytes(DurableUndoState.count(in)));
            vectors.add(new RedoRecords.Vector(major,minor,clazz,file,dba,vectorScn,sequence,container,List.copyOf(fields)));
        }
        if(in.available()!=0)throw new RedoException("ASSOCIATION_DISCOVERY_RECORD_TRAILING");return new RedoRecords.Record(block,offset,length,validity,scn,subScn,uid,List.copyOf(vectors));
    }
}
