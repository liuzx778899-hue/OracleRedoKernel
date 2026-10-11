package com.streamhouse.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Complete source-owned LOB graphs. No baseline/publication authority. */
public final class CapturedLobTransactions {
    public record Lob(int column,LobLocator.Kind kind,long object,long index,long bitmap) {}
    public record Scope(long uid,long container,long heap,long primaryIndex,LobLocator.Storage storage,
                        List<FrozenHeapBaseline.PhysicalColumn> columns,List<Lob> lobs) {
        public Scope {columns=List.copyOf(columns);lobs=List.copyOf(lobs);}
        public Scope(long uid,long container,long heap,long primaryIndex,LobLocator.Storage storage,
                     long blob,long blobIndex,long blobBitmap,long clob,long clobIndex,long clobBitmap) {
            this(uid,container,heap,primaryIndex,storage,List.of(
                    new FrozenHeapBaseline.PhysicalColumn(1,1,"ID","NUMBER",false,false),
                    new FrozenHeapBaseline.PhysicalColumn(2,2,"B","BLOB",true,false),
                    new FrozenHeapBaseline.PhysicalColumn(3,3,"C","CLOB",true,false)),
                    List.of(new Lob(1,LobLocator.Kind.BLOB,blob,blobIndex,blobBitmap),new Lob(2,LobLocator.Kind.CLOB,clob,clobIndex,clobBitmap)));
        }
        Lob lob(int column) {return lobs.stream().filter(l->l.column()==column).findFirst().orElseThrow();}
        long object(int column) {return lob(column).object();}
        long index(int column) {return lob(column).index();}
        long bitmap(int column) {return lob(column).bitmap();}
        boolean isLob(long object) {return lobs.stream().anyMatch(l->l.object()==object);}
        boolean isLobIndex(long object) {return lobs.stream().anyMatch(l->l.index()==object);}
        long bitmapFor(long object) {return lobs.stream().filter(l->l.object()==object).mapToLong(Lob::bitmap).findFirst().orElse(0);}
        List<Long> objects() {var result=new ArrayList<Long>(List.of(heap,primaryIndex));for(var l:lobs){result.add(l.object());result.add(l.index());}return List.copyOf(result);}
        public long blob() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.BLOB).findFirst().orElseThrow().object();}
        public long blobIndex() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.BLOB).findFirst().orElseThrow().index();}
        public long blobBitmap() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.BLOB).findFirst().orElseThrow().bitmap();}
        public long clob() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.CLOB).findFirst().orElseThrow().object();}
        public long clobIndex() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.CLOB).findFirst().orElseThrow().index();}
        public long clobBitmap() {return lobs.stream().filter(l->l.kind()==LobLocator.Kind.CLOB).findFirst().orElseThrow().bitmap();}
    }
    record At(ArchivedRedo.Header source,RedoRecords.Record record) {
        ArchivedUndoInventory.Position position(int vector) {return new ArchivedUndoInventory.Position(source.sequence(),record.block(),record.offset(),vector+1);}
    }
    public record Row(ArchivedUndoInventory.Position position,HeapUndoPair pair) {}
    public record Transaction(TransactionLifecycle.ClosedIdentity identity,UndoHistory.Result graph,
                              ArchivedUndoInventory.Position commit,List<Row> rows) {
        public Transaction {rows=List.copyOf(rows);}
    }
    private final Scope scope;
    private final List<At> records;
    private final List<Transaction> transactions;
    private CapturedLobTransactions(Scope scope,List<At> records,List<Transaction> transactions) {
        this.scope=scope;this.records=List.copyOf(records);this.transactions=List.copyOf(transactions);
    }
    public Scope scope() {return scope;}
    public List<Transaction> transactions() {return transactions;}
    List<At> records() {return records;}
    Transaction transaction(HeapRowPatch.Xid xid)throws RedoException {
        for(var t:transactions)if(t.identity().xid().equals(xid))return t;
        throw new RedoException("LOB_VALUE_OWNER_OUTSIDE_COMPLETE_GRAPHS");
    }
    public static CapturedLobTransactions read(List<CaptureJournal.Verified> captures,Scope scope)throws IOException {
        return read(captures,scope,null,null);
    }
    static CapturedLobTransactions read(List<CaptureJournal.Verified> captures,Scope scope,ArchivedDictionaryHistory.Timeline history,
                                        FrozenHeapBaseline.Snapshot snapshot)throws IOException {
        if(scope==null)throw new RedoException("INVALID_LOB_TRANSACTION_SCOPE");
        var objects=scope.objects();
        if(scope.uid()<=0 || scope.container()<=0 || scope.storage()==null || objects.stream().anyMatch(o->o<=0)
                || objects.stream().distinct().count()!=objects.size()
                || scope.columns().isEmpty() || scope.columns().size()>255 || scope.lobs().isEmpty()
                || java.util.stream.IntStream.range(0,scope.columns().size()).anyMatch(i->scope.columns().get(i).segment()!=i+1
                    || scope.columns().get(i).internal()!=i+1 || scope.columns().get(i).hidden()
                    || !List.of("NUMBER","VARCHAR2","RAW","DATE","TIMESTAMP","BLOB","CLOB").contains(scope.columns().get(i).type()))
                || scope.columns().stream().map(FrozenHeapBaseline.PhysicalColumn::name).distinct().count()!=scope.columns().size()
                || scope.lobs().stream().map(Lob::column).distinct().count()!=scope.lobs().size()
                || scope.columns().stream().filter(c->c.type().equals("BLOB") || c.type().equals("CLOB")).count()!=scope.lobs().size()
                || scope.lobs().stream().anyMatch(l->l.column()<0 || l.column()>=scope.columns().size() || l.kind()==null
                    || !scope.columns().get(l.column()).type().equals(l.kind().name())
                    || l.bitmap()<0))
            throw new RedoException("INVALID_LOB_TRANSACTION_SCOPE");
        var inputs=RedoBatch.captures(captures);var continuity=new TransactionLifecycle();var records=new ArrayList<At>();
        for(var input:inputs) {var h=input.header();continuity.archive(h);input.visit(h,r->{if(r.containerUid()==scope.uid())records.add(new At(h,r));});}
        continuity.finish();var xids=new java.util.LinkedHashSet<HeapRowPatch.Xid>();
        for(var at:records)for(var v:at.record().vectors())if(v.containerId()==scope.container() && v.opcodeMajor()==5 && v.opcodeMinor()==1
                && v.fields().size()>1 && v.fields().get(1).length>=24 && u32(v.fields().get(1),0)==scope.heap()) {
            if((u16(v.fields().get(1),20)&0x10)==0)xids.add(UndoHeader.decode(v).xid());
        }
        if(history!=null)xids.removeIf(xid->history.commits().stream().anyMatch(c->c.identity().xid().equals(xid)));
        if(xids.isEmpty())throw new RedoException("NO_LOB_TRANSACTIONS");
        var transactions=new ArrayList<Transaction>();
        for(var xid:xids) {
            var association=ArchivedCompensationAssociations.read(inputs,scope.uid(),scope.container(),xid);
            var inventory=association.window().undo();var typed=new HashSet<UndoHeader>();var rows=new ArrayList<Row>();
            var heaps=new ArrayList<HeapUndoPair>();var fills=new ArrayList<SecureFileLobUndoPair>();
            var controls=new ArrayList<SecureFileLobControlUndoPair>();
            var bitmaps=new ArrayList<LobBitmapUndoPair>();var indexes=new ArrayList<IndexUndoPair>();
            for(var entry:inventory.entries()) {
                var at=find(records,entry.position());var original=at.record().vectors().get(entry.position().vector()-1);
                var owner=entry.identity();long object=owner.objectId();
                if(object==scope.heap()) {
                    int columns=history==null || at.record().scn()<=snapshot.scn()?scope.columns().size()
                            :history.schemaAt(at.record().scn(),entry.position()).size();
                    var pair=findHeap(at.record(),original,columns);heaps.add(pair);
                    int v=-1;
                    for(int i=0;i<at.record().vectors().size();i++) {
                        var candidate=at.record().vectors().get(i);
                        if(candidate.opcodeMajor()!=11 || candidate.dba()!=pair.forward().dba() || candidate.containerId()!=scope.container())continue;
                        var p=new HeapRowPatch().decodeLob(candidate,columns);
                        if(p.xtype()==1 && owner.uba().equals(p.uba())) {if(v>=0)throw new RedoException("LOB_GRAPH_HEAP_AMBIGUOUS");v=i;}
                    }
                    if(v<0)throw new RedoException("LOB_GRAPH_HEAP_MISSING");
                    rows.add(new Row(at.position(v),pair));typed.add(pair.undo().identity());
                } else if(object==scope.primaryIndex() || (scope.storage()==LobLocator.Storage.BASICFILE && scope.isLobIndex(object))) {
                    IndexUndoPair pair=null;
                    for(var v:at.record().vectors())if(v.containerId()==scope.container() && v.objectId()==object && v.opcodeMajor()==10 && (v.opcodeMinor()==2 || v.opcodeMinor()==4)) {
                        var bound=object==scope.primaryIndex()?IndexUndoPair.bind(at.record(),v):IndexUndoPair.bindLob(at.record(),v,object);
                        if(!bound.undo().identity().equals(owner))continue;
                        if(pair!=null)throw new RedoException("LOB_GRAPH_INDEX_AMBIGUOUS");pair=bound;
                    }
                    if(pair==null)throw new RedoException("LOB_GRAPH_INDEX_MISSING");indexes.add(pair);typed.add(pair.undo().identity());
                } else if(scope.isLob(object)) {
                    if(scope.storage()==LobLocator.Storage.BASICFILE) {
                        var pair=LobBitmapUndoPair.bind(at.record(),original,object,scope.bitmapFor(object));
                        bitmaps.add(pair);typed.add(pair.owner());
                    } else if(entry.opcodeMajor()==26 && entry.opcodeMinor()==1) {
                        SecureFileLobUndoPair pair=null;
                        for(var v:at.record().vectors())if(v.containerId()==scope.container() && v.opcodeMajor()==26 && v.opcodeMinor()==2) {
                            var fill=SecureFileLobFill.decode(v);var ktb=fill.ktb();int offset=ktb[0]==2?8:ktb[0]==1?16:-1;
                            if(fill.undo() || offset<0 || !new HeapRowPatch.Uba(u32(ktb,offset),u16(ktb,offset+4),ktb[offset+6]&255).equals(owner.uba()))continue;
                            var bound=SecureFileLobUndoPair.bind(at.record(),v,object);if(!bound.owner().equals(owner))continue;
                            if(pair!=null)throw new RedoException("LOB_GRAPH_FILL_AMBIGUOUS");pair=bound;
                        }
                        if(pair==null)throw new RedoException("LOB_GRAPH_FILL_MISSING");fills.add(pair);typed.add(pair.owner());
                    } else {
                        SecureFileLobControlUndoPair pair=null;
                        for(var candidate:records)for(var v:candidate.record().vectors())if(v.containerId()==scope.container() && v.objectId()==object
                                && v.opcodeMajor()==13 && v.opcodeMinor()==entry.opcodeMinor()-1) {
                            if(entry.opcodeMinor()!=54 && candidate!=at)continue;
                            if(entry.opcodeMinor()==54) {
                                var b=v.fields().get(0);var inverse=original.fields().get(3);
                                boolean split=b.length==28 && u32(b,0)==3 && u32(b,4)==u32(inverse,36) && u32(b,8)==u32(inverse,40)
                                        && scn(b,20)==scn(inverse,16) && new HeapRowPatch.Xid(u16(b,12),u16(b,14),u32(b,16)).equals(xid);
                                boolean reuse=b.length==68 && u32(b,0)==4 && u32(b,16)==u32(inverse,36) && u32(b,20)==u32(inverse,40)
                                        && scn(b,44)==scn(inverse,16) && new HeapRowPatch.Xid(u16(b,36),u16(b,38),u32(b,40)).equals(xid);
                                if(!split && !reuse)continue;
                            }
                            var bound=SecureFileLobControlUndoPair.bind(at.record(),original,candidate.record(),v,object);
                            if(pair!=null)throw new RedoException("LOB_GRAPH_CONTROL_AMBIGUOUS");pair=bound;
                        }
                        if(pair==null)throw new RedoException("LOB_GRAPH_CONTROL_MISSING");controls.add(pair);typed.add(pair.owner());
                    }
                } else throw new RedoException("LOB_GRAPH_UNKNOWN_OBJECT");
                if(!typed.contains(owner))throw new RedoException("LOB_GRAPH_ORIGINAL_NOT_TYPED");
            }
            var routes=new HashMap<ArchivedUndoInventory.Position,ArchivedCompensationAssociations.Entry>();
            for(var route:association.entries()) {
                var at=find(records,route.position());var marker=at.record().vectors().get(route.position().vector()-1);
                var actual=association.associate(at.source(),at.record(),marker,route.position());
                var owner=actual.original();long object=owner.objectId();int matches=0;
                if(scope.storage()==LobLocator.Storage.BASICFILE && scope.isLob(object)) {
                    LobBitmapUndoPair.matchCompensation(at.record(),marker,bitmaps,owner);matches=1;
                } else for(var v:at.record().vectors())if(v.containerId()==scope.container()) {
                    if(object==scope.heap() && v.opcodeMajor()==11 && v.opcodeMinor()>=2 && v.opcodeMinor()<=5) {
                        int columns=heaps.stream().filter(p->p.undo().identity().equals(owner)).findFirst().orElseThrow().forward().columns().size();
                        HeapCompensation.matchLob(at.record(),marker,v,heaps,owner,columns);matches++;
                    } else if(scope.isLob(object) && v.opcodeMajor()==26 && v.opcodeMinor()==2) {
                        SecureFileLobUndoPair.matchCompensation(at.record(),marker,v,fills,owner);matches++;
                    } else if(scope.isLob(object) && v.opcodeMajor()==13 && v.opcodeMinor()==53) {
                        SecureFileLobControlUndoPair.matchPubCompensation(at.record(),marker,v,controls,owner);matches++;
                    } else if(scope.isLobIndex(object) && v.opcodeMajor()==10 && v.opcodeMinor()==3) {
                        IndexCompensation.matchLob(at.record(),marker,v,indexes,owner);matches++;
                    }
                }
                if(matches!=1)throw new RedoException("LOB_GRAPH_COMPENSATION_COUNT");routes.put(route.position(),actual);
            }
            association.finish();var state=new TransactionLifecycle();var graph=new UndoHistory(inventory);int next=0;Transaction closed=null;
            ArchivedRedo.Header currentSource=null;
            for(var at:records) {
                if(!at.source().equals(currentSource)) {state.archive(at.source());currentSource=at.source();}
                var w=association.window();if(compareRecord(at.position(0),w.firstRecord())<0 || compareRecord(at.position(0),w.lastRecord())>0)continue;
                var r=at.record();
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(next>=inventory.entries().size() || !inventory.entries().get(next).position().equals(at.position(i)))continue;
                    var entry=inventory.entries().get(next++);var owner=entry.identity();
                    if(owner.beginsTransaction()) {
                        int begins=0;for(var control:r.vectors())if(control.containerId()==scope.container() && control.opcodeMajor()==5 && control.opcodeMinor()==2
                                && control.fields().size()==2 && TransactionStart.decode(control).uba().equals(owner.uba())) {state.begin(r,control);begins++;}
                        if(begins!=1)throw new RedoException("LOB_GRAPH_BEGIN_COUNT");
                    }
                    state.undo(r,v);graph.append(entry);
                }
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=scope.container() || v.opcodeMajor()!=5)continue;
                    if(v.opcodeMinor()==2) {
                        state.observeSlotControl(r,v);
                        if(v.dba()==w.segmentDba() && v.fields().size()==1 && v.fields().get(0).length==32 && u16(v.fields().get(0),0)==xid.slot()) {
                            int mode=u16(v.fields().get(0),16);if(mode==10)state.advance(r,v);else if(mode==2)state.headUpdate(r,v);
                            else throw new RedoException("LOB_GRAPH_UNKNOWN_SEGMENT_CONTROL");
                        }
                    }
                    var route=routes.get(at.position(i));if(route!=null)graph.compensate(route.original());
                }
                for(int i=0;i<r.vectors().size();i++) {
                    var v=r.vectors().get(i);if(v.containerId()!=scope.container() || v.opcodeMajor()!=5 || v.opcodeMinor()!=4 || !state.knowsEnd(r,v))continue;
                    var end=state.end(r,v);var result=end.rolledBack()?graph.closeRollback(end):v.fields().size()==2 && TransactionEnd.decode(v).flags()==0
                            ?graph.closeCommitWithoutNativeTail(end):graph.close(TransactionEnd.undoTail(v));
                    if(closed!=null)throw new RedoException("LOB_GRAPH_DUPLICATE_END");closed=new Transaction(end,result,at.position(i),rows);
                }
            }
            state.finish();if(closed==null || next!=inventory.entries().size())throw new RedoException("LOB_GRAPH_NOT_CLOSED");transactions.add(closed);
        }
        transactions.sort((a,b)->compare(a.commit(),b.commit()));return new CapturedLobTransactions(scope,records,transactions);
    }
    private static HeapUndoPair findHeap(RedoRecords.Record r,RedoRecords.Vector original,int columns)throws RedoException {
        var owner=UndoHeader.decode(original);HeapUndoPair result=null;
        for(var v:r.vectors())if(v.containerId()==original.containerId() && v.opcodeMajor()==11 && v.opcodeMinor()>=2 && v.opcodeMinor()<=5) {
            var bound=HeapUndoPair.bindLob(r,v,columns);if(!bound.undo().identity().equals(owner))continue;
            if(result!=null)throw new RedoException("LOB_GRAPH_HEAP_AMBIGUOUS");result=bound;
        }
        if(result==null)throw new RedoException("LOB_GRAPH_HEAP_MISSING");return result;
    }
    static At find(List<At> records,ArchivedUndoInventory.Position p)throws RedoException {
        for(var at:records)if(compareRecord(at.position(0),p)==0)return at;
        throw new RedoException("LOB_POSITION_OUTSIDE_SOURCE");
    }
    static int compareRecord(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {
        int c=Long.compare(a.archiveSequence(),b.archiveSequence());if(c==0)c=Long.compare(a.block(),b.block());return c==0?Integer.compare(a.offset(),b.offset()):c;
    }
    static int compare(ArchivedUndoInventory.Position a,ArchivedUndoInventory.Position b) {int c=compareRecord(a,b);return c==0?Integer.compare(a.vector(),b.vector()):c;}
}
