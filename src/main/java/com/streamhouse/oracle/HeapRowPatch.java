package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Single-piece heap changes observed on 19.3. Patches include rollback redo, not CDC events. */
public final class HeapRowPatch {
    public enum Kind { INSERT, UPDATE, DELETE, LOCK }
    public enum State { VALUE, NULL, ABSENT }
    public record Column(State state, byte[] bytes) {}
    public record Xid(int usn, int slot, long sequence) {}
    public record Uba(long dba, int sequence, int record) {}
    /** Raw physical extent. removedColumn is an observed tail marker, not an inferred SQL NULL. */
    public record RowExtent(int storedColumns,int changedColumns,int removedColumn,Integer sizeDelta) {}
    /** uba is a physical row ITL reference; XR rollback can restore an earlier undo pointer. */
    public record Patch(Kind kind, long dba, int slot, int xtype, Xid xid, Uba uba,
                        List<Column> columns,RowExtent extent,Integer lockTo) {
        public Patch(Kind kind,long dba,int slot,int xtype,Xid xid,Uba uba,List<Column> columns,RowExtent extent) {
            this(kind,dba,slot,xtype,xid,uba,columns,extent,null);
        }
        public Patch(Kind kind,long dba,int slot,int xtype,Xid xid,Uba uba,List<Column> columns) {
            this(kind,dba,slot,xtype,xid,uba,columns,null);
        }
    }

    public Patch decode(RedoRecords.Vector vector, int dictionaryColumns) throws RedoException {
        return decode(vector,dictionaryColumns,false);
    }
    /** Observed three-column LOB heap profile; ordinary heap locks retain their existing gate. */
    public Patch decodeLob(RedoRecords.Vector vector,int columns)throws RedoException {
        if(columns!=3)throw new RedoException("UNSUPPORTED_LOB_HEAP_COLUMN_PROFILE");
        return decode(vector,columns,false,true);
    }
    Patch decodeDictionaryObject(RedoRecords.Vector vector,int columns) throws RedoException {
        return decode(vector,columns,true);
    }
    private Patch decode(RedoRecords.Vector vector,int dictionaryColumns,boolean dictionaryObject) throws RedoException {
        return decode(vector,dictionaryColumns,dictionaryObject,false);
    }
    private Patch decode(RedoRecords.Vector vector,int dictionaryColumns,boolean dictionaryObject,boolean lobHeap) throws RedoException {
        if (vector.opcodeMajor() != 11 || (vector.opcodeMinor() != 2
                && vector.opcodeMinor() != 3 && vector.opcodeMinor() != 4 && vector.opcodeMinor() != 5))
            throw new RedoException("UNSUPPORTED_HEAP_OPCODE");
        if (dictionaryColumns < 1 || dictionaryColumns > 255)
            throw new RedoException("UNSUPPORTED_HEAP_COLUMN_COUNT");
        List<byte[]> fields=vector.fields();
        if (fields.size() < 2) throw new RedoException("MISSING_KTB_KDO_FIELDS");
        byte[] ktb=fields.get(0);
        // Observed short KTB Z carries no current XID/UBA. Only proven XR row profiles are accepted.
        if (ktb.length==8 && (ktb[0]&255)==3 && (ktb[1]&255)==0x0d) {
            boolean packedUpdate=vector.opcodeMinor()==5 && fields.get(1).length==28 && (fields.get(1)[11]&255)==0x82;
            boolean restoredInsert=vector.opcodeMinor()==2 && fields.get(1).length==49 && (fields.get(1)[11]&255)==2;
            boolean rollbackDelete=vector.opcodeMinor()==3 && fields.get(1).length==20 && (fields.get(1)[11]&255)==2;
            boolean lobUnlock=lobHeap && vector.opcodeMinor()==4 && fields.get(1).length==20 && fields.get(1)[11]==2 && fields.get(1)[19]==0;
            if (!packedUpdate && !restoredInsert && !rollbackDelete && !lobUnlock)
                throw new RedoException("UNSUPPORTED_KTB_Z_ROW_PROFILE");
            return decodeKdo(fields,vector.opcodeMinor(),vector.dba(),dictionaryColumns,false,lobHeap);
        }
        if (ktb.length < 16) throw new RedoException("TRUNCATED_KTB");
        Xid xid=null;
        int ubaOffset=-1;
        if ((ktb[0]&255) == 1 || (ktb[0]&255) == 17) {
            if (ktb.length < 24) throw new RedoException("TRUNCATED_KTB_XID");
            xid=new Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
            ubaOffset=16;
        } else if ((ktb[0]&255) == 2) ubaOffset=8;
        else if ((ktb[0]&255)==18 && ktb.length==70 && (ktb[1]&255)==0x0d
                && ((vector.opcodeMinor()==3 && fields.get(1).length==20)
                    || (vector.opcodeMinor()==5 && fields.get(1).length==29)) && (fields.get(1)[11]&255)==1) {
            // Observed cleanout-C DELETE and seq89 UPDATE: only the C prefix owns the current UBA.
            ubaOffset=8;
        }
        else if ((ktb[0]&255) == 4) {
            // KTB L on rollback references an older row ITL, not the current rollback XID/undo.
            if (ktb.length < 24) throw new RedoException("TRUNCATED_KTB_ITL");
        }
        else throw new RedoException("UNSUPPORTED_KTB_OPERATION");
        Uba uba=ubaOffset < 0 ? null : new Uba(u32(ktb,ubaOffset),u16(ktb,ubaOffset+4),ktb[ubaOffset+6]&255);
        var row=decodeKdo(fields,vector.opcodeMinor(),vector.dba(),dictionaryColumns,dictionaryObject,lobHeap);
        return new Patch(row.kind(),row.dba(),row.slot(),row.xtype(),xid,uba,row.columns(),row.extent(),row.lockTo());
    }

    /** Shared physical KDO decoding. Undo KTB does not describe the current transaction identity. */
    static Patch decodeKdo(List<byte[]> fields,int op,long dba,int dictionaryColumns) throws RedoException {
        return decodeKdo(fields,op,dba,dictionaryColumns,false);
    }
    static Patch decodeKdo(List<byte[]> fields,int op,long dba,int dictionaryColumns,boolean dictionaryObject) throws RedoException {
        return decodeKdo(fields,op,dba,dictionaryColumns,dictionaryObject,false);
    }
    static Patch decodeLobKdo(List<byte[]> fields,int op,long dba,int columns)throws RedoException {
        if(columns!=3)throw new RedoException("UNSUPPORTED_LOB_HEAP_COLUMN_PROFILE");
        return decodeKdo(fields,op,dba,columns,false,true);
    }
    private static Patch decodeKdo(List<byte[]> fields,int op,long dba,int dictionaryColumns,boolean dictionaryObject,boolean lobHeap) throws RedoException {
        if (dictionaryColumns < 1 || dictionaryColumns > 255)
            throw new RedoException("UNSUPPORTED_HEAP_COLUMN_COUNT");
        if (fields.size()<2 || (op!=2 && op!=3 && op!=4 && op!=5))
            throw new RedoException("UNSUPPORTED_HEAP_OPCODE_OR_FIELDS");
        byte[] kdo=fields.get(1);
        boolean packed=op==5 && kdo.length==28 && ((kdo[11]&255)==0x81 || (kdo[11]&255)==0x82);
        boolean tail=op==5 && kdo.length==28 && (kdo[11]&255)==1 && (kdo[23]&255)==0;
        boolean sysNine=dictionaryObject && dictionaryColumns==37 && op==5 && kdo.length==30 && (kdo[23]&255)==9;
        // Observed HIST_HEAD$ IRP stores 18 columns and a longer null bitmap; offsets of row identity and values are unchanged.
        boolean statisticsInsert=op==2 && kdo.length==51 && (kdo[18]&255)==18 && kdo[11]==1;
        // Observed COL_USAGE$ DELETE inverse stores ten columns and a two-byte null bitmap.
        boolean usageInsert=op==2 && dictionaryColumns==10 && kdo.length==50 && (kdo[18]&255)==10 && kdo[11]==1;
        int expectedKdo=sysNine?30:op == 2 ? statisticsInsert?51:usageInsert?50:49 : op == 3 || op == 4 ? 20 : packed || tail ? 28 : 29;
        if (kdo.length != expectedKdo || (kdo[10]&255) != op || u32(kdo,0) != dba)
            throw new RedoException("UNSUPPORTED_KDO_LAYOUT_OR_BLOCK");
        if(dictionaryObject && (dictionaryColumns!=37 || op!=5 || packed || tail || kdo[11]!=1 || kdo[12]!=1 || kdo[13]!=0
                || u16(kdo,14)!=0 || kdo[16]!=0x2c || (kdo[17]!=0 && kdo[17]!=1)
                || (kdo[18]!=0 && !((kdo[23]&255)==6 && (kdo[22]&255)==26)) || kdo[19]!=0
                || !java.util.List.of(6,8,9).contains(kdo[23]&255) || u16(kdo,26)!=(sysNine?0x20:0x10)
                || kdo[28]!=0 || (sysNine && kdo[29]!=0)))
            throw new RedoException("UNSUPPORTED_DICTIONARY_OBJECT_UPDATE_PROFILE");
        int xtype=(kdo[11]&255)&(packed ? 0x7f : 0xff);
        if (xtype != 1 && xtype != 2) throw new RedoException("UNSUPPORTED_KDO_XTYPE");
        List<Column> columns=new ArrayList<>();
        for (int i=0;i<dictionaryColumns;i++) columns.add(new Column(State.ABSENT,new byte[0]));
        if (op==4) {
            boolean lobLock=lobHeap && (kdo[12]==1 || kdo[12]==2) && kdo[13]==0
                    && (u16(kdo,14)==0xffff || u16(kdo,14)==64) && kdo[18]==0 && (kdo[19]==0 || kdo[19]==kdo[12]);
            if (fields.size()!=2 || (!lobLock && (xtype!=1 || kdo[12]!=1 || kdo[13]!=0 || u16(kdo,14)!=1
                    || kdo[18]!=0 || (kdo[19]&255)>1)))
                throw new RedoException("UNSUPPORTED_HEAP_LOCK_LAYOUT");
            return new Patch(Kind.LOCK,dba,u16(kdo,16),xtype,null,null,List.copyOf(columns),null,kdo[19]&255);
        }
        int slot;
        Kind kind;
        RowExtent extent=null;
        if (op == 2) {
            kind=Kind.INSERT; slot=u16(kdo,42);
            int count=kdo[18]&255;
            if ((kdo[16]&255) != 0x2c || kdo[44] != 0 || count > dictionaryColumns
                    || fields.size() != count+2)
                throw new RedoException("UNSUPPORTED_INSERT_ROWPIECE");
            for (int i=0;i<count;i++) columns.set(i,value(fields.get(i+2)));
            extent=new RowExtent(count,count,-1,u16(kdo,40));
            // Trailing omitted columns remain ABSENT until schema/row completeness is proven.
        } else if (op == 5) {
            kind=Kind.UPDATE; slot=u16(kdo,20);
            int count=kdo[23]&255;
            int stored=kdo[22]&255;int delta=(short)u16(kdo,24);
            boolean shortColumnTwo=packed && u16(kdo,26)==0x10 && xtype==1 && fields.size()==4
                    && fields.get(2).length==2 && u16(fields.get(2),0)==2;
            // Actual product-acquired seq75/76: marker2 XA, column1 of3, one-byte value length.
            boolean shortMarkerTwo=packed && u16(kdo,26)==2 && xtype==1 && stored==3 && dictionaryColumns==3
                    && fields.size()==4 && fields.get(2).length==2 && u16(fields.get(2),0)==1;
            // Seq90 XA column2: vector content has a self-sized short value; the KDO vector offset is 103/104.
            boolean shortXaColumnTwo=packed && xtype==1 && stored==3 && dictionaryColumns==3 && fields.size()==4
                    && (u16(kdo,26)==103 || u16(kdo,26)==104)
                    && fields.get(2).length==2 && u16(fields.get(2),0)==2 && fields.get(3).length>=2
                    && (fields.get(3)[0]&255)>=1 && (fields.get(3)[0]&255)<=250
                    && (fields.get(3)[0]&255)==fields.get(3).length-1;
            // Seq96: two contiguous suffix values share one self-sized XA/XR value stream.
            boolean packedSuffixTwo=packed && stored==3 && dictionaryColumns==3 && count==2 && delta==0
                    && u16(kdo,26)==3 && fields.size()==4 && fields.get(2).length==2 && u16(fields.get(2),0)==1;
            if(packedSuffixTwo) {
                if((kdo[16]&255)!=0x2c)throw new RedoException("UNSUPPORTED_UPDATE_ROWPIECE");
                byte[] values=fields.get(3);int at=0;
                for(int index=1;index<=2;index++) {
                    if(at>=values.length)throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
                    int length=values[at++]&255;
                    if(length<1 || length>250 || at+length>values.length)throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
                    columns.set(index,value(java.util.Arrays.copyOfRange(values,at,at+length)));at+=length;
                }
                if(at!=values.length)throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
                return new Patch(kind,dba,slot,xtype,null,null,List.copyOf(columns),new RowExtent(stored,count,-1,null),null);
            }
            if (packed && (count!=1 || u16(kdo,24)!=0 || (!shortColumnTwo && !shortMarkerTwo && !shortXaColumnTwo && u16(kdo,26)!=3 && (u16(kdo,26)!=0 || (kdo[11]&0x7f)!=1))))
                throw new RedoException("UNSUPPORTED_KDOM2_UPDATE_LAYOUT");
            if ((kdo[16]&255) != 0x2c || stored<1 || stored>dictionaryColumns)
                throw new RedoException("UNSUPPORTED_UPDATE_ROWPIECE");
            if (tail) {
                boolean mapped=fields.size()==4 && fields.get(2).length==2
                        && u16(fields.get(2),0)==stored && fields.get(3).length==0 && u16(kdo,26)==1;
                // Actual seq62 inverse URP: ncol1/nnew0, empty map, marker0; pairing proves the removed column.
                boolean unmapped=dictionaryColumns==3 && stored==1 && fields.size()==3
                        && fields.get(2).length==0 && u16(kdo,26)==0;
                boolean mappedTwo=dictionaryColumns==3 && stored==1 && fields.size()==5 && fields.get(2).length==4
                        && u16(fields.get(2),0)==1 && u16(fields.get(2),2)==2 && u16(kdo,26)==3
                        && fields.get(3).length==0 && fields.get(4).length==0;
                if (stored>=dictionaryColumns || (!mapped && !unmapped && !mappedTwo) || delta>=0)
                    throw new RedoException("UNSUPPORTED_UPDATE_TAIL_MARKER");
                if(mappedTwo) {columns.set(1,value(new byte[0]));columns.set(2,value(new byte[0]));}
                extent=new RowExtent(stored,0,stored,delta);
            } else {
                if (fields.size()!=count+3 || fields.get(2).length!=count*2)
                    throw new RedoException("UNSUPPORTED_UPDATE_ROWPIECE");
                extent=new RowExtent(stored,count,-1,packed ? null : delta);
            }
            for (int i=0;i<count;i++) {
                int index=u16(fields.get(2),i*2);
                if (index >= stored || columns.get(index).state() != State.ABSENT)
                    throw new RedoException("INVALID_UPDATE_COLUMN_MAP");
                byte[] bytes=fields.get(i+3);
                // Seq41 value16/three columns and RAW seq95 value4/two columns: marker3 has a self-sized short value.
                boolean shortMarkerThree=packed && u16(kdo,26)==3 && (xtype==1 || xtype==2 && stored==2) && stored==dictionaryColumns
                        && (stored==2 || stored==3) && index==1 && bytes.length>=2 && bytes.length<=251
                        && (bytes[0]&255)==bytes.length-1;
                columns.set(index,value(packed ? packedValue(bytes,u16(kdo,26),shortMarkerThree || shortMarkerTwo || shortXaColumnTwo) : bytes));
            }
        } else {
            kind=Kind.DELETE; slot=u16(kdo,16);
            if (fields.size() != 2) throw new RedoException("UNSUPPORTED_DELETE_ROWPIECE");
        }
        return new Patch(kind,dba,slot,xtype,null,null,List.copyOf(columns),extent);
    }

    private static Column value(byte[] bytes) {
        return new Column(bytes.length == 0 ? State.NULL : State.VALUE,bytes.clone());
    }

    /** Observed single-column KDOM2 XA short or extended value; NULL and short XR remain unproven. */
    private static byte[] packedValue(byte[] bytes,int layoutMarker,boolean shortMarkerThree) throws RedoException {
        if (layoutMarker==0 || layoutMarker==0x10 || shortMarkerThree) {
            if (bytes.length<2 || (bytes[0]&255)<1 || (bytes[0]&255)>250 || (bytes[0]&255)!=bytes.length-1)
                throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
            return java.util.Arrays.copyOfRange(bytes,1,bytes.length);
        }
        if (bytes.length<3 || (bytes[0]&255)!=0xfe || u16(bytes,1)<254 || u16(bytes,1)!=bytes.length-3)
            throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
        return java.util.Arrays.copyOfRange(bytes,3,bytes.length);
    }
}
