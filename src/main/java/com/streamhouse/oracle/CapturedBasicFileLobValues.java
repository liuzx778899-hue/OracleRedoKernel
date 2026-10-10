package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Source-owned BasicFile values at forward heap locators. Declared scope is not publication or baseline authority. */
public final class CapturedBasicFileLobValues {
    public record Scope(long uid,long container,long heapObject,long lobObject,long lobIndexObject,long bitmapParent,int column,
                        int columns,LobLocator.Kind kind) {
        public Scope(long uid,long container,long heapObject,long lobObject,long lobIndexObject,long bitmapParent,int column) {
            this(uid,container,heapObject,lobObject,lobIndexObject,bitmapParent,column,3,column==1?LobLocator.Kind.BLOB:column==2?LobLocator.Kind.CLOB:null);
        }
    }
    public record PageSource(ArchivedUndoInventory.Position image,ArchivedUndoInventory.Position allocation,UndoHeader owner) {}
    /** CLOB bytes are strict UTF-8; BLOB bytes retain every binary byte. NULL differs from an empty LOB. */
    public record Value(UndoHeader locatorOwner,HeapRowPatch.State state,LobLocator.Kind kind,byte[] bytes,List<PageSource> pages) {
        public Value {bytes=bytes==null?null:bytes.clone();pages=List.copyOf(pages);}
        @Override public byte[] bytes() {return bytes==null?null:bytes.clone();}
    }
    private record At(long sequence,RedoRecords.Record record) {
        ArchivedUndoInventory.Position position(int vector) {return new ArchivedUndoInventory.Position(sequence,record.block(),record.offset(),vector+1);}
    }
    private record Window(ArchivedCompensationAssociations association,long begin,long end,boolean rollback) {}
    private final RedoBatch inputs;
    private final Scope scope;
    private final List<At> records;
    private final HashMap<HeapRowPatch.Xid,Window> windows=new HashMap<>();
    private CapturedBasicFileLobValues(RedoBatch inputs,Scope scope,List<At> records) {this.inputs=inputs;this.scope=scope;this.records=List.copyOf(records);}
    public static CapturedBasicFileLobValues read(List<CaptureJournal.Verified> captures,Scope scope)throws IOException {
        if(scope==null || scope.uid()<=0 || scope.container()<=0 || scope.heapObject()<=0 || scope.lobObject()<=0 || scope.lobIndexObject()<=0
                || scope.heapObject()==scope.lobObject() || scope.heapObject()==scope.lobIndexObject() || scope.lobObject()==scope.lobIndexObject()
                || scope.bitmapParent()<0 || scope.kind()==null || scope.columns()<1 || scope.columns()>255
                || scope.column()<0 || scope.column()>=scope.columns())throw new RedoException("INVALID_BASICFILE_VALUE_SCOPE");
        var inputs=RedoBatch.captures(captures);var continuity=new TransactionLifecycle();var records=new ArrayList<At>();
        for(var input:inputs) {
            var header=input.header();continuity.archive(header);
            input.visit(header,r->{if(r.containerUid()==scope.uid())records.add(new At(header.sequence(),r));});
        }
        continuity.finish();return new CapturedBasicFileLobValues(inputs,scope,records);
    }
    public Value valueAt(ArchivedUndoInventory.Position position)throws IOException {
        At at=recordAt(position);var row=vectorAt(at,position);
        if(row.containerId()!=scope.container() || row.objectId()!=scope.heapObject())throw new RedoException("BASICFILE_VALUE_HEAP_SCOPE");
        var pair=HeapUndoPair.bindLob(at.record(),row,scope.columns());var owner=pair.undo().identity();var current=window(owner.xid());
        if(owner.objectId()!=scope.heapObject() || owner.dataObjectId()!=scope.heapObject())throw new RedoException("BASICFILE_VALUE_LOCATOR_OWNER");
        var cell=pair.forward().columns().get(scope.column());var kind=scope.kind();
        if(cell.state()==HeapRowPatch.State.ABSENT)throw new RedoException("BASICFILE_VALUE_LOCATOR_ABSENT");
        if(cell.state()==HeapRowPatch.State.NULL)return new Value(owner,cell.state(),kind,null,List.of());
        var locator=LobLocator.decode(cell.bytes(),kind,LobLocator.Storage.BASICFILE);
        if(locator.inlinePayload()!=null)return new Value(owner,cell.state(),kind,logical(kind,locator.inlinePayload()),List.of());
        byte[] value=new byte[Math.toIntExact(locator.byteLength())];int copied=0;var sources=new ArrayList<PageSource>();
        for(int pageNumber=0;pageNumber<locator.pages().size();pageNumber++) {
            long dba=locator.pages().get(pageNumber);BasicFileLobPage image=null;ArchivedUndoInventory.Position imageAt=null;
            for(var candidate:records)for(int v=0;v<candidate.record().vectors().size();v++) {
                var vector=candidate.record().vectors().get(v);
                if(vector.containerId()!=scope.container() || vector.opcodeMajor()!=19 || vector.opcodeMinor()!=1 || vector.dba()!=dba
                        || vector.objectId()!=scope.lobObject() || compare(candidate.position(v),position)>0)continue;
                var page=BasicFileLobPage.decode(vector,scope.lobObject());
                if(!page.lobId().equals(locator.lobId()) || page.pageNumber()!=pageNumber || page.version()>=locator.version())
                    throw new RedoException("BASICFILE_VALUE_PAGE_IDENTITY_OR_VERSION");
                if(image!=null)throw new RedoException("BASICFILE_VALUE_REUSED_PAGE_IMAGE_UNSUPPORTED");
                image=page;imageAt=candidate.position(v);
            }
            if(image==null)throw new RedoException("BASICFILE_VALUE_PAGE_MISSING");
            LobBitmapUndoPair allocation=null;ArchivedUndoInventory.Position allocationAt=null;
            for(var candidate:records)for(int v=0;v<candidate.record().vectors().size();v++) {
                var vector=candidate.record().vectors().get(v);var f=vector.fields();
                if(vector.containerId()!=scope.container() || vector.opcodeMajor()!=5 || vector.opcodeMinor()!=1 || f.size()!=4
                        || f.get(1).length!=24 || u32(f.get(1),0)!=scope.lobObject() || f.get(1)[16]!=13 || f.get(1)[17]!=23
                        || f.get(2).length!=20 || f.get(3).length!=16 || u32(f.get(2),0)!=image.parentDba()
                        || u32(f.get(2),0)+u32(f.get(3),12)!=dba || compare(candidate.position(v),position)>0)continue;
                var bound=LobBitmapUndoPair.bind(candidate.record(),vector,scope.lobObject(),scope.bitmapParent());
                var w=window(bound.owner().xid());
                if(!bound.owner().xid().equals(owner.xid()) && (w.rollback() || w.end()>current.begin()))
                    throw new RedoException("BASICFILE_VALUE_FOREIGN_UNCOMMITTED_ALLOCATION");
                long imageScn=recordAt(imageAt).record().scn();
                if(imageScn<w.begin() || imageScn>w.end() || candidate.record().scn()<w.begin() || candidate.record().scn()>w.end())
                    throw new RedoException("BASICFILE_VALUE_ALLOCATION_WINDOW");
                if(allocation!=null)throw new RedoException("BASICFILE_VALUE_ALLOCATION_AMBIGUOUS");
                allocation=bound;allocationAt=candidate.position(v);
            }
            if(allocation==null)throw new RedoException("BASICFILE_VALUE_ALLOCATION_MISSING");
            requireNotRetired(dba,position,owner,current);
            byte[] payload=image.payload();int length=Math.min(payload.length,value.length-copied);
            System.arraycopy(payload,0,value,copied,length);copied+=length;sources.add(new PageSource(imageAt,allocationAt,allocation.owner()));
        }
        if(copied!=value.length)throw new RedoException("BASICFILE_VALUE_INCOMPLETE_BYTES");
        return new Value(owner,cell.state(),kind,logical(kind,value),sources);
    }
    private void requireNotRetired(long dba,ArchivedUndoInventory.Position locator,UndoHeader locatorOwner,Window current)throws IOException {
        for(var at:records)for(int v=0;v<at.record().vectors().size();v++) {
            var row=at.record().vectors().get(v);
            if(row.containerId()!=scope.container() || row.objectId()!=scope.lobIndexObject() || row.opcodeMajor()!=10 || row.opcodeMinor()!=2
                    || compare(at.position(v),locator)>=0)continue;
            var pair=IndexUndoPair.bindLob(at.record(),row,scope.lobIndexObject());byte[] data=pair.forward().keydata();
            if(((data[0]&255L)<<24|((data[1]&255L)<<16)|((data[2]&255L)<<8)|(data[3]&255L))!=dba)continue;
            var owner=pair.undo().identity();var w=window(owner.xid());boolean cancelled=false;
            for(var route:w.association().entries())if(route.original().equals(owner) && compare(route.position(),locator)<0) {
                var original=recordAt(route.position());var marker=vectorAt(original,route.position());int rows=0;
                for(var purge:original.record().vectors())if(purge.containerId()==scope.container() && purge.opcodeMajor()==10 && purge.opcodeMinor()==3
                        && purge.objectId()==scope.lobIndexObject()) {IndexCompensation.matchLob(original.record(),marker,purge,List.of(pair),owner);rows++;}
                if(rows!=1 || cancelled)throw new RedoException("BASICFILE_VALUE_RETIREMENT_COMPENSATION_COUNT");cancelled=true;
            }
            if(!cancelled)throw new RedoException("BASICFILE_VALUE_REFERENCES_RETIRED_PAGE");
        }
    }
    private Window window(HeapRowPatch.Xid xid)throws IOException {
        var cached=windows.get(xid);if(cached!=null)return cached;
        var association=ArchivedCompensationAssociations.read(inputs,scope.uid(),scope.container(),xid);var w=association.window();
        var begin=recordAt(w.firstRecord()).record();var end=recordAt(w.lastRecord()).record();TransactionEnd marker=null;
        for(var vector:end.vectors())if(vector.containerId()==scope.container() && vector.opcodeMajor()==5 && vector.opcodeMinor()==4 && vector.dba()==w.segmentDba()) {
            var decoded=TransactionEnd.decode(vector);
            if(decoded.slot()!=xid.slot() || decoded.sequence()!=xid.sequence())continue;
            if(marker!=null)throw new RedoException("BASICFILE_VALUE_END_AMBIGUOUS");marker=decoded;
        }
        if(marker==null)throw new RedoException("BASICFILE_VALUE_END_MISSING");
        var result=new Window(association,begin.scn(),end.scn(),marker.rolledBack());windows.put(xid,result);return result;
    }
    private At recordAt(ArchivedUndoInventory.Position position)throws RedoException {
        for(var at:records)if(at.sequence()==position.archiveSequence() && at.record().block()==position.block() && at.record().offset()==position.offset())return at;
        throw new RedoException("BASICFILE_VALUE_POSITION_OUTSIDE_SOURCE");
    }
    private static RedoRecords.Vector vectorAt(At at,ArchivedUndoInventory.Position p)throws RedoException {
        if(p.vector()<1 || p.vector()>at.record().vectors().size())throw new RedoException("BASICFILE_VALUE_VECTOR_OUTSIDE_RECORD");
        return at.record().vectors().get(p.vector()-1);
    }
    private static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());if(c==0)c=Integer.compare(a.offset(),b.offset());return c==0 ? Integer.compare(a.vector(),b.vector()) : c;
    }
    private static byte[] logical(LobLocator.Kind kind,byte[] bytes)throws RedoException {
        if(kind==LobLocator.Kind.BLOB)return bytes;
        try{return StandardCharsets.UTF_16BE.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().getBytes(StandardCharsets.UTF_8);}
        catch(java.nio.charset.CharacterCodingException e){throw new RedoException("BASICFILE_VALUE_INVALID_CLOB_UTF16");}
    }
}
