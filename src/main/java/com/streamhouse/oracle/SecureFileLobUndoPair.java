package com.streamhouse.oracle;

import java.util.HexFormat;
import java.util.Arrays;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Exact same-record 26.2 forward/26.1 undo binding; rollback routing and commit approval are separate. */
public record SecureFileLobUndoPair(long containerUid,long containerId,UndoHeader owner,SecureFileLobFill forward,long inverseHighWater,SecureFileLobFill inverse) {
    public static SecureFileLobUndoPair bind(RedoRecords.Record record,RedoRecords.Vector vector,long expectedObject)throws RedoException {
        if(!record.vectors().contains(vector))throw new RedoException("SECUREFILE_LOB_FILL_NOT_IN_RECORD");
        var forward=SecureFileLobFill.decode(vector);
        if(forward.undo())throw new RedoException("SECUREFILE_LOB_COMPENSATION_IS_NOT_ORIGINAL");
        byte[] ktb=forward.ktb();int offset=ktb[0]==2?8:ktb[0]==1?16:-1;
        if(offset<0)throw new RedoException("SECUREFILE_LOB_FORWARD_CURRENT_KTB");
        var uba=new HeapRowPatch.Uba(u32(ktb,offset),u16(ktb,offset+4),ktb[offset+6]&255);
        if(uba.dba()==0 || uba.record()==0)throw new RedoException("SECUREFILE_LOB_FORWARD_EMPTY_UBA");
        RedoRecords.Vector original=null;UndoHeader owner=null;
        for(var candidate:record.vectors()) {
            if(candidate.containerId()!=vector.containerId() || candidate.opcodeMajor()!=5 || candidate.opcodeMinor()!=1 || candidate.dba()!=uba.dba())continue;
            var header=UndoHeader.decode(candidate);if(!header.uba().equals(uba))continue;
            if(original!=null)throw new RedoException("SECUREFILE_LOB_DUPLICATE_ORIGINAL");
            original=candidate;owner=header;
        }
        if(original==null || owner.objectId()!=expectedObject || owner.dataObjectId()!=expectedObject
                || owner.xid().usn()==0 || owner.xid().sequence()==0)
            throw new RedoException("SECUREFILE_LOB_ORIGINAL_IDENTITY");
        if(ktb[0]==1 && !owner.xid().equals(new HeapRowPatch.Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12))))
            throw new RedoException("SECUREFILE_LOB_CURRENT_XID_MISMATCH");
        byte[] metadata=original.fields().get(1);
        if(metadata[16]!=26 || metadata[17]!=1)throw new RedoException("SECUREFILE_LOB_UNDO_OPCODE");
        var fields=original.fields().subList(2,original.fields().size());
        if(fields.size()==4) {
            var inverse=SecureFileLobFill.decode(new RedoRecords.Vector(26,2,1,vector.file(),vector.dba(),vector.scn(),vector.sequence(),vector.containerId(),fields));
            if(!inverse.undo() || !inverse.lobId().equals(forward.lobId()) || inverse.offset()!=forward.offset()
                    || inverse.payload().length!=forward.payload().length)
                throw new RedoException("SECUREFILE_LOB_INVERSE_EXTENT");
            return new SecureFileLobUndoPair(record.containerUid(),vector.containerId(),owner,forward,-1,inverse);
        }
        if(fields.size()!=3 || fields.get(1).length!=12 || fields.get(2).length!=56)
            throw new RedoException("UNSUPPORTED_SECUREFILE_LOB_APPEND_UNDO");
        byte[] common=fields.get(1),load=fields.get(2);
        if(common[0]!=1 || common[1]!=0x20 || (common[2]!=8 && common[2]!=9) || common[3]!=0 || u16(common,4)!=8060
                || u16(common,6)!=128 || u32(common,8)!=vector.dba() || u32(load,0)!=4 || (load[10]!=0x20 && load[10]!=0x28) || load[11]!=0
                || !HexFormat.of().formatHex(load,12,22).equals(forward.lobId()) || u16(load,54)!=0)
            throw new RedoException("SECUREFILE_LOB_APPEND_UNDO_IDENTITY");
        for(int i=22;i<52;i++)if(load[i]!=0)throw new RedoException("SECUREFILE_LOB_APPEND_UNDO_RESERVED");
        long high=u16(load,52);
        if(high!=forward.offset() || forward.highWater()!=high+forward.payload().length)
            throw new RedoException("SECUREFILE_LOB_APPEND_UNDO_LENGTH");
        return new SecureFileLobUndoPair(record.containerUid(),vector.containerId(),owner,forward,high,null);
    }
    /** Observed partial-fill compensation; source-owned routing and full transaction closure remain caller obligations. */
    public static SecureFileLobUndoPair matchCompensation(RedoRecords.Record record,RedoRecords.Vector marker,
                                                        RedoRecords.Vector vector,List<SecureFileLobUndoPair> history,
                                                        UndoHeader original)throws RedoException {
        if(!record.vectors().contains(marker) || !record.vectors().contains(vector) || marker.containerId()!=vector.containerId())
            throw new RedoException("SECUREFILE_LOB_COMPENSATION_RECORD_OR_CONTAINER");
        var c=UndoCompensation.decode(marker);
        if(c.beginsTransaction() || c.crossesUndoBlock() || c.opcodeMajor()!=26 || c.opcodeMinor()!=1
                || c.dba()!=original.uba().dba() || c.objectId()!=original.objectId() || c.dataObjectId()!=original.dataObjectId()
                || c.tablespace()!=original.tablespace() || c.slot()!=original.xid().slot() || c.wrap()!=(original.xid().sequence()&0xffff))
            throw new RedoException("SECUREFILE_LOB_COMPENSATION_ROUTED_OWNER");
        var rollback=SecureFileLobFill.decode(vector);if(!rollback.undo())throw new RedoException("SECUREFILE_LOB_COMPENSATION_IS_FORWARD");
        SecureFileLobUndoPair match=null;
        for(var pair:history) {
            var inverse=pair.inverse();
            if(pair.containerUid()!=record.containerUid() || pair.containerId()!=vector.containerId() || !pair.owner().equals(original)
                    || inverse==null || !sameInverse(inverse,rollback))continue;
            if(match!=null)throw new RedoException("SECUREFILE_LOB_COMPENSATION_AMBIGUOUS_ORIGINAL");match=pair;
        }
        if(match==null)throw new RedoException("SECUREFILE_LOB_COMPENSATION_MISSING_ORIGINAL");
        int rows=0;
        for(var row:record.vectors())if(row.containerId()==vector.containerId() && row.opcodeMajor()==26 && row.opcodeMinor()==2
                && sameInverse(SecureFileLobFill.decode(row),rollback))rows++;
        if(rows!=1)throw new RedoException("SECUREFILE_LOB_COMPENSATION_AMBIGUOUS_FILL");
        return match;
    }
    private static boolean sameInverse(SecureFileLobFill a,SecureFileLobFill b) {
        return a.undo()==b.undo() && a.lobId().equals(b.lobId()) && a.pageDba()==b.pageDba() && a.offset()==b.offset()
                && a.fillFlags()==b.fillFlags() && a.highWater()==b.highWater() && Arrays.equals(a.payload(),b.payload());
    }
}
