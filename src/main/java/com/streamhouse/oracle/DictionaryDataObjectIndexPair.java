package com.streamhouse.oracle;

import java.util.*;

/** Raw119/120 SYS object39: DATAOBJ#, TYPE#, SPARE3 and inline six-byte OBJ$ rowid. */
public record DictionaryDataObjectIndexPair(IndexUndoPair physical,Long dataObject,long type,long spare3,long rowDba,int rowSlot) {
    static void validateKey(byte[] key,byte[] data)throws RedoException {
        components(key);
        if(data!=null && data.length!=0)throw new RedoException("DICTIONARY_DATA_OBJECT_INLINE_ROWID_REQUIRED");
    }
    private static List<byte[]> components(byte[] key)throws RedoException {
        var parts=new ArrayList<byte[]>();
        for(int at=0;at<key.length;) {
            int size=key[at++]&255;
            // Raw120 SYS39: an object without a segment has NULL DATAOBJ#, followed by TYPE#, SPARE3 and ROWID.
            if(size==255 && parts.isEmpty()) { parts.add(null);continue; }
            if(size<1 || size>250 || at+size>key.length)throw new RedoException("DICTIONARY_DATA_OBJECT_KEY_COMPONENT");
            parts.add(Arrays.copyOfRange(key,at,at+size));at+=size;
        }
        if(parts.size()!=4 || parts.get(3).length!=6)throw new RedoException("DICTIONARY_DATA_OBJECT_KEY_LAYOUT");
        for(int i=0;i<3;i++)if(parts.get(i)!=null)number(parts.get(i));
        byte[] rowid=parts.get(3);long dba=0;for(int i=0;i<4;i++)dba=(dba<<8)|(rowid[i]&255);
        if(dba>>>22==0)throw new RedoException("DICTIONARY_DATA_OBJECT_ROWID_DBA");
        return parts;
    }
    private static long number(byte[] value)throws RedoException {
        long n;try{n=OracleScalars.number(value).longValueExact();}
        catch(ArithmeticException failure){throw new RedoException("DICTIONARY_DATA_OBJECT_KEY_INTEGER");}
        if(n<0 || n>0xffffffffL)throw new RedoException("DICTIONARY_DATA_OBJECT_KEY_RANGE");return n;
    }
    public static DictionaryDataObjectIndexPair bind(RedoRecords.Record record,RedoRecords.Vector row)throws RedoException {
        if(row.opcodeMajor()!=10 || !List.of(2,4).contains(row.opcodeMinor()) || row.objectId()!=39 || row.blockClass()!=1)
            throw new RedoException("DICTIONARY_DATA_OBJECT_INDEX_SCOPE");
        var pair=IndexUndoPair.bindDataObjectIndex(record,row);var h=pair.undo().identity();
        if(h.objectId()!=39 || h.dataObjectId()!=39 || h.tablespace()!=0 || pair.undo().indexId()!=0x00400168L)
            throw new RedoException("DICTIONARY_DATA_OBJECT_INDEX_GENERATION");
        var parts=components(pair.undo().key());byte[] rowid=parts.get(3);long dba=0;
        for(int i=0;i<4;i++)dba=(dba<<8)|(rowid[i]&255);
        return new DictionaryDataObjectIndexPair(pair,parts.get(0)==null?null:number(parts.get(0)),number(parts.get(1)),number(parts.get(2)),dba,((rowid[4]&255)<<8)|(rowid[5]&255));
    }
    void requireRow(DictionaryBaseline baseline,DictionaryBaseline.Row row)throws RedoException {
        var columns=baseline.layouts().get("OBJ$").columns();int dataColumn=-1;
        for(int i=0;i<columns.size();i++)if(columns.get(i).name().equals("DATAOBJ#"))dataColumn=i;
        if(dataColumn<0)throw new RedoException("DICTIONARY_METADATA_COLUMN_MISSING");
        if(!row.role().equals("OBJ$") || row.key().dataObjectId()!=baseline.layouts().get("OBJ$").role().dataObject()
                || row.key().dba()!=rowDba || row.key().slot()!=rowSlot || baseline.number(row,"OBJ#")!=row.target()
                || (dataObject==null ? row.values().get(dataColumn).state()!=HeapRowPatch.State.NULL
                        : baseline.number(row,"DATAOBJ#")!=dataObject) || baseline.number(row,"TYPE#")!=type || baseline.number(row,"SPARE3")!=spare3)
            throw new RedoException("DICTIONARY_DATA_OBJECT_INDEX_ROW_MISMATCH");
    }
    /** Physical generation proof only; the complete owning transaction must still close before publication. */
    public static FrozenHeapBaseline.ObjectRole requireGeneration(DictionaryObjectUndoPair object,
            FrozenHeapBaseline.ObjectRole target,long type,long owner,DictionaryDataObjectIndexPair before,DictionaryDataObjectIndexPair after)throws RedoException {
        var row=object.physical();var forward=row.forward();var inverse=row.undo().inverse();
        if(forward.kind()!=HeapRowPatch.Kind.UPDATE || inverse.kind()!=HeapRowPatch.Kind.UPDATE
                || before.physical().forward().kind()!=IndexUndoPair.Kind.DELETE || after.physical().forward().kind()!=IndexUndoPair.Kind.INSERT
                || row.containerUid()!=before.physical().containerUid() || row.containerUid()!=after.physical().containerUid()
                || row.containerId()!=before.physical().containerId() || row.containerId()!=after.physical().containerId()
                || !row.undo().identity().xid().equals(before.physical().undo().identity().xid())
                || !row.undo().identity().xid().equals(after.physical().undo().identity().xid())
                || forward.dba()!=before.rowDba() || forward.dba()!=after.rowDba() || forward.slot()!=before.rowSlot() || forward.slot()!=after.rowSlot()
                || before.dataObject()==null || after.dataObject()==null || before.dataObject()!=target.dataObject()
                || after.dataObject()<=0 || after.dataObject().equals(before.dataObject())
                || before.type()!=type || after.type()!=type || before.spare3()!=owner || after.spare3()!=owner)
            throw new RedoException("DICTIONARY_OBJECT_GENERATION_INDEX_MISMATCH");
        for(int i=0;i<37;i++) {
            var old=inverse.columns().get(i);var next=forward.columns().get(i);
            if(List.of(0,1,6,17).contains(i)) {
                long expectedOld=i==0?target.logicalObject():i==1?target.dataObject():i==6?type:owner;
                long expectedNext=i==1?after.dataObject():expectedOld;
                if(old.state()!=HeapRowPatch.State.VALUE || next.state()!=HeapRowPatch.State.VALUE
                        || number(old.bytes())!=expectedOld || number(next.bytes())!=expectedNext)
                    throw new RedoException("DICTIONARY_OBJECT_GENERATION_ROW_MISMATCH");
            } else if(i==14) {
                if(old.state()!=next.state() || !Arrays.equals(old.bytes(),next.bytes()))
                    throw new RedoException("DICTIONARY_OBJECT_GENERATION_FLAGS_CHANGED");
            } else if(i==8) {
                if(old.state()!=next.state() || old.state()!=HeapRowPatch.State.ABSENT && old.state()!=HeapRowPatch.State.VALUE)
                    throw new RedoException("DICTIONARY_OBJECT_GENERATION_MTIME_LAYOUT");
                if(old.state()==HeapRowPatch.State.VALUE) { OracleScalars.date(old.bytes());OracleScalars.date(next.bytes()); }
            } else if(old.state()!=HeapRowPatch.State.ABSENT || next.state()!=HeapRowPatch.State.ABSENT)
                throw new RedoException("DICTIONARY_OBJECT_GENERATION_UNEXPECTED_COLUMN");
        }
        return new FrozenHeapBaseline.ObjectRole(target.owner(),target.name(),target.logicalObject(),after.dataObject());
    }
}
