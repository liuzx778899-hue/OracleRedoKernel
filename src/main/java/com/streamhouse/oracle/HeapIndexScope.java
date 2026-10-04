package com.streamhouse.oracle;

/** Declared physical roles; this mapping alone is not a historical dictionary. */
public record HeapIndexScope(Generation heap,Generation index) {
    public record Generation(long logicalObject,long dataObject) {}
    public HeapIndexScope {
        if (heap==null || index==null || heap.logicalObject()<=0 || heap.dataObject()<=0 || index.logicalObject()<=0 || index.dataObject()<=0
                || heap.logicalObject()==index.logicalObject() || heap.dataObject()==index.dataObject()
                || heap.logicalObject()==index.dataObject() || heap.dataObject()==index.logicalObject())
            throw new IllegalArgumentException("INVALID_HEAP_INDEX_GENERATION_ROLES");
    }
    public static HeapIndexScope equal(long heap,long index) {return new HeapIndexScope(new Generation(heap,heap),new Generation(index,index));}
    public java.util.Map<Long,Long> generations() {return java.util.Map.of(heap.logicalObject(),heap.dataObject(),index.logicalObject(),index.dataObject());}
}
