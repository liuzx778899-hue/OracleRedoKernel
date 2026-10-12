package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Observed 19.3 single-piece clustered KDO bytes. Cluster key index is not a parent object identity. */
public final class ClusterRowPatch {
    public record Patch(int tableNumber,Integer clusterKeyIndex,HeapRowPatch.Patch row) {}

    /** Caller supplies a verified logical layout. This physical patch neither binds a transaction nor authorizes dictionary history. */
    public static Patch decode(List<byte[]> fields,int op,long dba,int tableNumber,int dictionaryColumns) throws RedoException {
        boolean indexCluster=tableNumber==3 && dictionaryColumns==36 || tableNumber==4 && dictionaryColumns==13;
        boolean constraintCluster=tableNumber==1 && dictionaryColumns==20 || tableNumber==2 && dictionaryColumns==10;
        if((tableNumber!=1 && tableNumber!=5 && tableNumber!=6 && !indexCluster && !constraintCluster && !(op==5 && tableNumber==2))
                || indexCluster && (op==4 || tableNumber==4 && op==5) || dictionaryColumns<1 || dictionaryColumns>255
                || fields.size()<2 || (op!=2 && op!=3 && op!=4 && op!=5))
            throw new RedoException("UNSUPPORTED_CLUSTER_ROW_SCOPE_OR_OPCODE");
        byte[] k=fields.get(1);boolean mlog=tableNumber==1 && dictionaryColumns==24;
        boolean tableBackfill=op==5 && tableNumber==1 && dictionaryColumns==44 && k.length==30 && k[23]==12;
        int length=op==2 ? constraintCluster?tableNumber==1?51:49:indexCluster?tableNumber==3?53:50:mlog?k.length>18 && k[18]==13?50:51:tableNumber==1?54:tableNumber==6?50:52 : op==5 ? tableBackfill?30:29 : 20;
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
            int expected=constraintCluster?tableNumber==1?List.of(17,20).contains(count)?count:-1:5:indexCluster?tableNumber==3?36:10:mlog?List.of(13,19).contains(count)?count:-1:tableNumber==1?44:tableNumber==6?14:32;
            if(k[16]!=0x6c || count!=expected || count>dictionaryColumns || fields.size()!=count+2
                    || tableNumber==5 && k[45]!=0)
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
            boolean generation=tableNumber==1 && stored==44 && count==3 && u16(k,26)==2
                    && fields.get(2).length==6 && u16(fields.get(2),0)==0 && u16(fields.get(2),2)==4 && u16(fields.get(2),4)==35;
            boolean redefinition=tableNumber==1 && dictionaryColumns==44 && stored==44 && count==5 && fields.size()==8
                    && java.util.Arrays.equals(fields.get(2),new byte[]{4,0,28,0,29,0,40,0,41,0}) && u16(k,26)==25
                    && fields.get(3).length==0 && fields.get(4).length>0 && fields.get(5).length>0
                    && fields.get(6).length==0 && fields.get(7).length==0;
            boolean constraintDefinition=tableNumber==1 && dictionaryColumns==20 && List.of(17,20).contains(stored)
                    && count==2 && fields.size()==5 && java.util.Arrays.equals(fields.get(2),new byte[]{8,0,13,0})
                    && fields.get(3).length>0 && u16(k,26)==(fields.get(4).length==0?2:0);
            boolean registration=tableNumber==1 && dictionaryColumns==44 && stored==44 && count==4 && fields.size()==7
                    && java.util.Arrays.equals(fields.get(2),new byte[]{4,0,28,0,40,0,41,0})
                    && fields.get(3).length==0 && fields.get(4).length>0
                    && u16(k,26)==(1 | (fields.get(5).length==0?4:0) | (fields.get(6).length==0?8:0));
            boolean allocation=tableNumber==1 && dictionaryColumns==44 && stored==44 && count==4 && fields.size()==7
                    && java.util.Arrays.equals(fields.get(2),new byte[]{2,0,3,0,4,0,28,0});
            if(allocation) {
                int nulls=0;for(int i=0;i<count;i++)if(fields.get(i+3).length==0)nulls|=1<<i;
                if(u16(k,26)!=nulls)throw new RedoException("CLUSTER_ALLOCATION_NULL_BITMAP_MISMATCH");
            }
            if(tableBackfill) {
                if(stored!=44 || fields.size()!=15 || k[29]!=0
                        || !java.util.Arrays.equals(fields.get(2),new byte[]{4,0,12,0,14,0,15,0,16,0,17,0,18,0,19,0,20,0,21,0,22,0,23,0}))
                    throw new RedoException("UNSUPPORTED_CLUSTER_TABLE_BACKFILL");
                int nulls=0;for(int i=0;i<count;i++)if(fields.get(i+3).length==0)nulls|=1<<i;
                if(u16(k,26)!=nulls)throw new RedoException("CLUSTER_BACKFILL_NULL_BITMAP_MISMATCH");
            }
            if(k[16]!=0x6c || stored<1 || stored>dictionaryColumns || count<1 || count>stored
                    || fields.size()!=count+3 || fields.get(2).length!=count*2 || u16(k,26)>1 && !generation && !redefinition && !constraintDefinition && !registration && !allocation && !tableBackfill || k[28]!=0)
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
            if((tableNumber!=5 && tableNumber!=1 && tableNumber!=6 && !indexCluster && !constraintCluster) || fields.size()!=2)
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
