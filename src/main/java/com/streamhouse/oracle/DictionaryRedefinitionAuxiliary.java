package com.streamhouse.oracle;

import java.util.*;

/** Observed raw120 redefinition controls. Typed SYS metadata is never a business row or event input. */
final class DictionaryRedefinitionAuxiliary {
    private record Spec(String name,int[] types,Set<Integer> required,Set<Integer> operations) {}
    private record IndexSpec(String name,long table,long id,int[] columns) {}
    record Row(long logical,HeapUndoPair physical) {}
    record ClusterRow(long logical,ClusterUndoPair physical) {}
    record Index(long logical,IndexUndoPair physical,List<byte[]> key,long rowDba,int rowSlot) {}
    // Independently checked against the owned 19c SYS column catalog; no catalog read is required at runtime.
    private static final Map<Long,Spec> TABLES=tableSpecs();
    private static Map<Long,Spec> tableSpecs() {
        var tables=new HashMap<Long,Spec>(Map.of(
            256L,new Spec("COM$",new int[]{2,2,1},Set.of(0),Set.of(2,3)),
            867L,new Spec("SNAP_LOADERTIME$",new int[]{2,12,2,12,2},Set.of(),Set.of(2,3,5)),
            871L,new Spec("MLOG$",new int[]{12,12,12,2,12,2,1,1,2,12,1,12,12,12,1,1,12,2,2,2,2,2,2,1},Set.of(6,9),Set.of(2,3)),
            875L,new Spec("SNAP$",new int[]{1,1,1,1,1,1,1,1,12,2,2,12,2,2,1,1,12,2,1,1,2,2,2,2,1,2,2,2,23,12,1,2,2,2,2,2,2,23,23,1,1,23,23,1,1,1,1,112,112,2,2,1,8},Set.of(0,1,2,29),Set.of(2,3,4,5)),
            883L,new Spec("SNAP_REFTIME$",new int[]{1,1,2,12,1,1,2,2,12,2,2,12,23,23,2,1},Set.of(0,1,2),Set.of(2,3,5)),
            885L,new Spec("MLOG_REFCOL$",new int[]{1,1,1,12,2,2},Set.of(0,1,2),Set.of(2,3)),
            887L,new Spec("SNAP_REFOP$",new int[]{1,1,2,2,2,2,23,23,2,8},Set.of(0,1,2,4),Set.of(2,3)),
            889L,new Spec("SNAP_COLMAP$",new int[]{1,1,1,2,1,2,2,2,2},Set.of(0,1,2,3),Set.of(2,3)),
            1316L,new Spec("REDEF$",new int[]{2,1,2,2},Set.of(0,1,2),Set.of(5)),
            1337L,new Spec("EXPACT$",new int[]{1,1,1,1,1,2,2,1,2,1},Set.of(0,1,2,3,4,5,8),Set.of(2,3))));
        tables.put(19L,new Spec("IND$",new int[]{2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,12,2,2,2,2,2,2,2,2,2,2,2,2,1,1,12},Set.of(0,2,3,4,5,6,7,8,9,11,12,13,23),Set.of(2,3,5)));
        tables.put(20L,new Spec("ICOL$",new int[]{2,2,2,2,2,2,2,2,2,2,1,1,12},Set.of(0,1,2,3,4,5,6),Set.of(2,3)));
        tables.put(28L,new Spec("CON$",new int[]{2,1,2,2,2,2,1,1,12},Set.of(0,1,2),Set.of(2,3,5)));
        tables.put(31L,new Spec("CDEF$",new int[]{2,2,2,2,2,1,2,2,2,2,12,2,12,2,2,2,2,1,1,8},Set.of(0,2),Set.of(2,3,5)));
        tables.put(32L,new Spec("CCOL$",new int[]{2,2,2,2,2,2,2,1,1,12},Set.of(0,1,3),Set.of(2,3)));
        tables.put(61L,new Spec("OBJAUTH$",new int[]{2,2,2,2,2,69,2,2},Set.of(0,1,2,3,4),Set.of(2,4)));
        tables.put(109L,new Spec("LOB$",new int[]{2,2,2,2,2,2,2,2,2,2,2,2,2,2},Set.of(0,1,2,4),Set.of(2,3,5)));
        tables.put(14L,new Spec("SEG$",new int[]{2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2},Set.of(),Set.of(5)));
        tables.put(677L,new Spec("WRI$_OPTSTAT_TAB_HISTORY",new int[]{2,181,2,2,2,2,2,12,2,2,2,2,2,2,2,2,2,1,1,181,180},Set.of(0,1),Set.of(2)));
        return Map.copyOf(tables);
    }
    private static final Map<Long,IndexSpec> INDEXES=indexSpecs();
    private static Map<Long,IndexSpec> indexSpecs() {
        var indexes=new HashMap<Long,IndexSpec>(Map.of(
            258L,new IndexSpec("I_COM1",256,0x00400f00L,new int[]{0,1}),
            868L,new IndexSpec("I_SNAP_LOADERTIME1",867,0x00409150L,new int[]{0}),
            886L,new IndexSpec("I_MLOG_REFCOL1",885,0x00409140L,new int[]{0,1,2}),
            880L,new IndexSpec("I_SNAP1",875,0x00409218L,new int[]{1,0,34}),
            881L,new IndexSpec("I_SNAP2",875,0x00409210L,new int[]{46,45,34}),
            884L,new IndexSpec("I_SNAP_REFTIME1",883,0x00409248L,new int[]{1,0,10,2}),
            888L,new IndexSpec("I_SNAP_REFOP1",887,0x00409200L,new int[]{0,1,8,4,2,3}),
            890L,new IndexSpec("I_SNAP_COLMAP1",889,0x00409170L,new int[]{0,1,7,3,2}),
            41L,new IndexSpec("I_IND1",19,0x00400178L,new int[]{0}),
            42L,new IndexSpec("I_ICOL1",20,0x00400180L,new int[]{0})));
        indexes.put(51L,new IndexSpec("I_CON1",28,0x004001c8L,new int[]{0,1}));
        indexes.put(52L,new IndexSpec("I_CON2",28,0x004001d0L,new int[]{2}));
        indexes.put(53L,new IndexSpec("I_CDEF1",31,0x004001d8L,new int[]{0}));
        indexes.put(54L,new IndexSpec("I_CDEF2",31,0x004001e0L,new int[]{-1}));
        indexes.put(56L,new IndexSpec("I_CDEF4",31,0x004001f0L,new int[]{8}));
        indexes.put(57L,new IndexSpec("I_CCOL1",32,0x004001f8L,new int[]{0,1}));
        indexes.put(58L,new IndexSpec("I_CCOL2",32,0x00400200L,new int[]{0,3}));
        indexes.put(62L,new IndexSpec("I_OBJAUTH1",61,0x00400248L,new int[]{0,1,2,3,7}));
        indexes.put(63L,new IndexSpec("I_OBJAUTH2",61,0x00400250L,new int[]{2,0,7}));
        indexes.put(110L,new IndexSpec("I_LOB1",109,0x00400408L,new int[]{-1,0}));
        indexes.put(111L,new IndexSpec("I_LOB2",109,0x00400410L,new int[]{2}));
        indexes.put(678L,new IndexSpec("I_WRI$_OPTSTAT_TAB_OBJ#_ST",677,0x01001042L,new int[]{0,1}));
        indexes.put(679L,new IndexSpec("I_WRI$_OPTSTAT_TAB_ST",677,0x0100103aL,new int[]{1}));
        return Map.copyOf(indexes);
    }
    static List<FrozenHeapBaseline.ObjectRole> roles() {
        var result=new ArrayList<FrozenHeapBaseline.ObjectRole>();
        TABLES.forEach((logical,s)->result.add(new FrozenHeapBaseline.ObjectRole("SYS",s.name(),logical,logical==14?8:logical==871?869:logical==31 || logical==32?29:logical==19 || logical==20 || logical==109?2:logical)));
        INDEXES.forEach((index,s)->result.add(new FrozenHeapBaseline.ObjectRole("SYS",s.name(),index,index)));
        return List.copyOf(result);
    }
    static boolean table(long logical){return TABLES.containsKey(logical) && !cluster(logical);}
    static boolean cluster(long logical){return logical==14 || logical==871 || logical==19 || logical==20 || logical==31 || logical==32 || logical==109;}
    static boolean index(long logical){return INDEXES.containsKey(logical);}
    static Row bind(RedoRecords.Record record,RedoRecords.Vector vector,long logical)throws RedoException {
        var spec=TABLES.get(logical);
        if(spec==null || vector.opcodeMajor()!=11 || !spec.operations().contains(vector.opcodeMinor()))fail("ROW_SCOPE");
        var pair=HeapUndoPair.bindAuxiliary(record,vector,spec.types().length);var owner=pair.undo().identity();
        if(owner.objectId()!=logical || owner.dataObjectId()!=logical || owner.tablespace()!=(logical==677?1:0))fail("ROW_OWNER");
        if(logical==677 && pair.forward().extent().storedColumns()!=3)fail("STATISTICS_HISTORY_EXTENT");
        requireTyped(List.of(pair.forward(),pair.undo().inverse()),spec);
        if(pair.forward().kind()==HeapRowPatch.Kind.UPDATE && logical!=28)for(var index:INDEXES.values())if(index.table()==logical)for(int column:index.columns())
            if(pair.forward().columns().get(column).state()!=HeapRowPatch.State.ABSENT || pair.undo().inverse().columns().get(column).state()!=HeapRowPatch.State.ABSENT)
                fail("INDEX_KEY_UPDATE_NOT_PROVEN");
        return new Row(logical,pair);
    }
    static ClusterRow bindCluster(RedoRecords.Record record,RedoRecords.Vector vector,long logical)throws RedoException {
        if(!cluster(logical) || !TABLES.get(logical).operations().contains(vector.opcodeMinor()))fail("CLUSTER_SCOPE");
        var spec=TABLES.get(logical);
        var pair=ClusterUndoPair.bind(record,vector,new FrozenHeapBaseline.ObjectRole("SYS",spec.name(),logical,logical==14?8:logical==871?869:logical==31 || logical==32?29:2),logical==871 || logical==31?1:logical==14 || logical==32?2:logical==19?3:logical==109?6:4,spec.types().length);
        if(logical==14)for(var patch:List.of(pair.forward().row(),pair.inverse().row())) {
            if(patch.extent().storedColumns()!=16 || patch.extent().changedColumns()!=1)fail("SEGMENT_UPDATE_EXTENT");
            for(int i=0;i<16;i++)if(patch.columns().get(i).state()!=(i==0?HeapRowPatch.State.VALUE:HeapRowPatch.State.ABSENT))fail("SEGMENT_UPDATE_COLUMNS");
        }
        if(logical==109 && vector.opcodeMinor()==5) {
            for(var patch:List.of(pair.forward().row(),pair.inverse().row())) {
                if(patch.extent().storedColumns()!=14 || patch.extent().changedColumns()!=4)fail("LOB_ALLOCATION_EXTENT");
                for(int i=0;i<14;i++)if(patch.columns().get(i).state()!=(Set.of(2,6,7,11).contains(i)?HeapRowPatch.State.VALUE:HeapRowPatch.State.ABSENT))fail("LOB_ALLOCATION_COLUMNS");
            }
            if(!Arrays.equals(pair.forward().row().columns().get(2).bytes(),pair.inverse().row().columns().get(2).bytes()))fail("LOB_ALLOCATION_KEY_CHANGED");
        }
        if(logical==31 && vector.opcodeMinor()==5)for(var patch:List.of(pair.forward().row(),pair.inverse().row())) {
            if(!List.of(17,20).contains(patch.extent().storedColumns()))fail("CONSTRAINT_DEFINITION_EXTENT");
            for(int i=0;i<20;i++) {
                var state=patch.columns().get(i).state();
                if(i==8?state!=HeapRowPatch.State.VALUE:i==13?state==HeapRowPatch.State.ABSENT:state!=HeapRowPatch.State.ABSENT)fail("CONSTRAINT_DEFINITION_COLUMNS");
            }
        }
        requireTyped(List.of(pair.forward().row(),pair.inverse().row()),TABLES.get(logical));
        return new ClusterRow(logical,pair);
    }
    private static void requireTyped(List<HeapRowPatch.Patch> patches,Spec spec)throws RedoException {
        for(var patch:patches)for(int i=0;i<spec.types().length;i++) {
            var cell=patch.columns().get(i);
            if(cell.state()==HeapRowPatch.State.VALUE) {
                if(spec.types()[i]==2)OracleScalars.number(cell.bytes());
                else if(spec.types()[i]==69)fail("METADATA_PARENT_ROWID_NOT_PROVEN");
                else if(spec.types()[i]==12)OracleScalars.date(cell.bytes());
                else if(spec.types()[i]==180)OracleScalars.timestamp(cell.bytes());
                else if(spec.types()[i]==181)OracleScalars.timestampWithOffset(cell.bytes());
                else if(spec.types()[i]==112) {
                    var locator=LobLocator.decode(cell.bytes(),LobLocator.Kind.CLOB,LobLocator.Storage.BASICFILE);
                    if(locator.inlinePayload()==null)fail("EXTERNAL_METADATA_CLOB_NOT_PROVEN");
                    CapturedSecureFileLobValues.logical(LobLocator.Kind.CLOB,locator.inlinePayload());
                } else if(spec.types()[i]!=23)OracleScalars.varchar2(cell.bytes(),"AL32UTF8");
            } else if(patch.kind()!=HeapRowPatch.Kind.DELETE && spec.required().contains(i)
                    && (cell.state()==HeapRowPatch.State.NULL || patch.kind()==HeapRowPatch.Kind.INSERT))fail("REQUIRED_ROW_VALUE");
        }
    }
    static Index bindIndex(RedoRecords.Record record,RedoRecords.Vector vector,long logical)throws RedoException {
        if(!index(logical) || vector.objectId()!=logical || vector.blockClass()!=1
                || !List.of(2,4).contains(vector.opcodeMinor()) && !(List.of(51L,52L).contains(logical) && vector.opcodeMinor()==5))fail("INDEX_SCOPE");
        var pair=IndexUndoPair.bindRedefinitionAuxiliaryIndex(record,vector,logical);var owner=pair.undo().identity();
        long expected=INDEXES.get(logical).id();
        if(owner.objectId()!=logical || owner.dataObjectId()!=logical || owner.tablespace()!=(logical==678 || logical==679?1:0) || pair.undo().indexId()!=expected)fail("INDEX_OWNER");
        boolean forwardKey=pair.forward().kind()==IndexUndoPair.Kind.INSERT || pair.forward().kind()==IndexUndoPair.Kind.RESTORE;
        byte[] key=forwardKey?pair.forward().key():pair.undo().key();
        byte[] data=forwardKey?pair.forward().keydata():pair.undo().keydata();
        validateKey(logical,key,data);var parts=new ArrayList<>(components(key));
        if(data.length==0)data=parts.remove(parts.size()-1);
        long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(data[i]&255);
        if(dba==0 || dba>>>22==0)fail("INDEX_ROW_DBA");
        return new Index(logical,pair,Collections.unmodifiableList(parts),dba,((data[4]&255)<<8)|(data[5]&255));
    }
    static void validateKey(long logical,byte[] key,byte[] data)throws RedoException {
        if(!index(logical) || data!=null && data.length!=6 && data.length!=0)fail("INDEX_KEY_SCOPE");
        var spec=INDEXES.get(logical);var table=TABLES.get(spec.table());var parts=components(key);int count=spec.columns().length;
        if(data!=null && data.length==0 || data==null && parts.size()==count+1) {
            if(!List.of(42L,54L,56L,57L,63L,110L,679L,880L,881L,884L,888L,890L).contains(logical)
                    || parts.size()!=count+1 || parts.get(count)==null || parts.get(count).length!=6)fail("INLINE_INDEX_ROWID");
            parts=parts.subList(0,count);
        }
        if(parts.size()!=count)fail("INDEX_KEY_COUNT");
        for(int i=0;i<count;i++) {
            var value=parts.get(i);
            int column=spec.columns()[i];
            if(value==null) {if(column<0 || table.required().contains(column))fail("INDEX_KEY_NULL");continue;}
            if(column>=0 && table.types()[column]==1) {if(OracleScalars.varchar2(value,"AL32UTF8").isEmpty())fail("INDEX_NAME_EMPTY");}
            else if(spec.table()==677 && column==1) {if(value.length!=11)fail("STATISTICS_HISTORY_TIMESTAMP_KEY");OracleScalars.timestamp(value);}
            else try {long n=OracleScalars.number(value).longValueExact();if(n<0 || n>0xffffffffL || (column<0 || (logical==258 || logical==868) && i==0) && n==0)fail("INDEX_NUMBER_RANGE");}
            catch(ArithmeticException invalid){fail("INDEX_NUMBER_INTEGER");}
        }
    }
    static void requireComplete(List<Row> rows,List<Index> indexes)throws RedoException {
        requireComplete(rows,indexes,List.of());
    }
    static void requireComplete(List<Row> rows,List<Index> indexes,List<ClusterRow> clusters)throws RedoException {
        requireComplete(rows,indexes,clusters,Set.of());
    }
    static void requireComplete(List<Row> rows,List<Index> indexes,List<ClusterRow> clusters,Set<Long> parents)throws RedoException {
        var used=new HashSet<UndoHeader>();
        var exchanges=DictionaryConstraintExchange.require(rows,indexes,used);
        for(var row:rows) {
            var pair=row.physical();
            if(exchanges.contains(pair.undo().identity()))continue;
            if(pair.forward().kind()==HeapRowPatch.Kind.UPDATE || pair.forward().kind()==HeapRowPatch.Kind.LOCK)continue;
            if(row.logical()==677) {
                var object=OracleScalars.number(pair.forward().columns().get(0).bytes());
                try {if(!parents.contains(object.longValueExact()))fail("STATISTICS_HISTORY_UNKNOWN_PARENT");}
                catch(ArithmeticException invalid){fail("STATISTICS_HISTORY_PARENT_INTEGER");}
            }
            var image=pair.forward().kind()==HeapRowPatch.Kind.INSERT?pair.forward():pair.undo().inverse();
            requireReferences(row.logical(),pair.forward().kind(),image,pair.undo().identity(),pair.containerUid(),pair.containerId(),indexes,used,parents);
        }
        for(var row:clusters) {
            var pair=row.physical();var kind=pair.forward().row().kind();
            if(kind==HeapRowPatch.Kind.UPDATE || kind==HeapRowPatch.Kind.LOCK)continue;
            requireReferences(row.logical(),kind,kind==HeapRowPatch.Kind.INSERT?pair.forward().row():pair.inverse().row(),pair.identity(),pair.containerUid(),pair.containerId(),indexes,used,parents);
        }
        if(used.size()!=indexes.size())fail("UNASSOCIATED_INDEX");
    }
    private static void requireReferences(long logical,HeapRowPatch.Kind kind,HeapRowPatch.Patch image,UndoHeader owner,
            long uid,long containerId,List<Index> indexes,Set<UndoHeader> used,Set<Long> parents)throws RedoException {
            for(var entry:INDEXES.entrySet()) {var spec=entry.getValue();if(spec.table()!=logical)continue;
            var refs=indexes.stream().filter(i->i.logical()==entry.getKey() && i.rowDba()==image.dba() && i.rowSlot()==image.slot()).toList();
            if(refs.size()!=1)fail("INDEX_ROW_COVERAGE");var index=refs.get(0);var physical=index.physical();
            if(physical.containerUid()!=uid || physical.containerId()!=containerId
                    || !physical.undo().identity().xid().equals(owner.xid())
                    || physical.forward().kind()!=(kind==HeapRowPatch.Kind.INSERT?IndexUndoPair.Kind.INSERT:IndexUndoPair.Kind.DELETE))fail("INDEX_ROW_OWNER_OR_OPERATION");
            for(int i=0;i<spec.columns().length;i++) {
                if(spec.columns()[i]<0) {
                    try {if(!parents.contains(OracleScalars.number(index.key().get(i)).longValueExact()))fail("UNKNOWN_CLUSTER_PARENT");}
                    catch(ArithmeticException invalid){fail("CLUSTER_PARENT_INTEGER");}
                    continue;
                }
                var cell=image.columns().get(spec.columns()[i]);var key=index.key().get(i);
                if(logical==677 && spec.columns()[i]==1) {
                    if(cell.state()!=HeapRowPatch.State.VALUE || key==null || !Arrays.equals(Arrays.copyOf(cell.bytes(),11),key))fail("STATISTICS_HISTORY_TIMESTAMP_VALUES");
                    continue;
                }
                // Observed OBJAUTH$ IRP stores the five required cells; nullable COL# is a trailing NULL.
                if(logical==61 && spec.columns()[i]==7 && image.kind()==HeapRowPatch.Kind.INSERT
                        && image.extent().storedColumns()==5 && cell.state()==HeapRowPatch.State.ABSENT && key==null)continue;
                if(cell.state()==HeapRowPatch.State.ABSENT || cell.state()==HeapRowPatch.State.NULL && key!=null
                        || cell.state()==HeapRowPatch.State.VALUE && !Arrays.equals(cell.bytes(),key))fail("INDEX_ROW_VALUES");
            }
            if(!used.add(physical.undo().identity()))fail("INDEX_REUSED");
            }
    }
    private static List<byte[]> components(byte[] bytes)throws RedoException {
        var out=new ArrayList<byte[]>();
        for(int at=0;at<bytes.length;) {
            int size=bytes[at++]&255;if(size==255){out.add(null);continue;}
            if(size==0 || size>250 || at+size>bytes.length)fail("KEY_COMPONENT");
            out.add(Arrays.copyOfRange(bytes,at,at+size));at+=size;
        }
        return Collections.unmodifiableList(out);
    }
    private static void fail(String detail)throws RedoException {throw new RedoException("DICTIONARY_REDEFINITION_AUXILIARY_"+detail);}
}
