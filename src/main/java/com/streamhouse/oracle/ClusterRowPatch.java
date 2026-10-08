package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 single-piece clustered KDO bytes. Cluster key index is not a parent object identity. */
public final class ClusterRowPatch {
    public record Patch(int tableNumber,Integer clusterKeyIndex,HeapRowPatch.Patch row) {}

    /** Caller supplies a verified logical layout. This physical patch neither binds a transaction nor authorizes dictionary history. */
    public static Patch decode(List<byte[]> fields,int op,long dba,int tableNumber,int dictionaryColumns) throws RedoException {
        if((tableNumber!=1 && tableNumber!=5) || dictionaryColumns<1 || dictionaryColumns>255
                || fields.size()<2 || (op!=2 && op!=3 && op!=4 && op!=5))
            throw new RedoException("UNSUPPORTED_CLUSTER_ROW_SCOPE_OR_OPCODE");
        byte[] k=fields.get(1);int length=op==2 ? 52 : op==5 ? 29 : 20;
        if(k.length!=length || u32(k,0)!=dba || (k[10]&255)!=op || k[11]!=1
                || (k[12]&255)<1 || k[13]!=0 || u16(k,14)!=0)
            throw new RedoException("UNSUPPORTED_CLUSTER_KDO_LAYOUT_OR_BLOCK");
        int tab=op==2 ? k[44]&255 : op==5 ? k[19]&255 : k[18]&255;
        if(tab!=tableNumber) throw new RedoException("CLUSTER_LOGICAL_TABLE_NUMBER_MISMATCH");
        var columns=new ArrayList<HeapRowPatch.Column>();
        for(int i=0;i<dictionaryColumns;i++)columns.add(new HeapRowPatch.Column(HeapRowPatch.State.ABSENT,new byte[0]));
        HeapRowPatch.Kind kind;HeapRowPatch.RowExtent extent=null;Integer keyIndex=null,lockTo=null;int slot;
        if(op==2) {
            int count=k[18]&255;keyIndex=k[19]&255;
            if(tableNumber!=5 || k[16]!=0x6c || count!=32 || count>dictionaryColumns || fields.size()!=count+2
                    || keyIndex==0 || k[45]!=0)
                throw new RedoException("UNSUPPORTED_CLUSTER_INSERT_ROWPIECE");
            kind=HeapRowPatch.Kind.INSERT;slot=u16(k,42);
            int rowSize=4+count;
            for(int i=0;i<count;i++) {
                byte[] cell=fields.get(i+2);
                if(((k[45+i/8] >>> (i%8))&1)!=(cell.length==0?1:0))
                    throw new RedoException("CLUSTER_INSERT_NULL_BITMAP_MISMATCH");
                rowSize+=cell.length;columns.set(i,value(cell));
            }
            if(rowSize!=u16(k,40))
                throw new RedoException("UNSUPPORTED_CLUSTER_INSERT_COMPLETE_SIZE");
            extent=new HeapRowPatch.RowExtent(count,count,-1,u16(k,40));
        } else if(op==5) {
            int stored=k[22]&255,count=k[23]&255;
            if(k[16]!=0x6c || stored<1 || stored>dictionaryColumns || count<1 || count>stored
                    || fields.size()!=count+3 || fields.get(2).length!=count*2 || u16(k,26)>1 || k[28]!=0)
                throw new RedoException("UNSUPPORTED_CLUSTER_UPDATE_ROWPIECE");
            kind=HeapRowPatch.Kind.UPDATE;slot=u16(k,20);
            for(int i=0;i<count;i++) {
                int index=u16(fields.get(2),i*2);
                if(index>=stored || columns.get(index).state()!=HeapRowPatch.State.ABSENT)
                    throw new RedoException("INVALID_CLUSTER_UPDATE_COLUMN_MAP");
                columns.set(index,value(fields.get(i+3)));
            }
            extent=new HeapRowPatch.RowExtent(stored,count,-1,(int)(short)u16(k,24));
        } else {
            if(tableNumber!=5 || fields.size()!=2)
                throw new RedoException("UNSUPPORTED_CLUSTER_DELETE_OR_LOCK_PROFILE");
            slot=u16(k,16);
            if(op==4) {
                lockTo=k[19]&255;
                if(lockTo!=0 && lockTo!=2)throw new RedoException("UNSUPPORTED_CLUSTER_LOCK_TARGET");
                kind=HeapRowPatch.Kind.LOCK;
            } else {
                // DRP ends at tabn (byte18); byte19 is unused, not a lock/row flag.
                kind=HeapRowPatch.Kind.DELETE;
            }
        }
        return new Patch(tableNumber,keyIndex,new HeapRowPatch.Patch(kind,dba,slot,1,null,null,List.copyOf(columns),extent,lockTo));
    }
    private static HeapRowPatch.Column value(byte[] b) {
        return new HeapRowPatch.Column(b.length==0 ? HeapRowPatch.State.NULL : HeapRowPatch.State.VALUE,b.clone());
    }
}
