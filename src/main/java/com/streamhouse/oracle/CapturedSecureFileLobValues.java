package com.streamhouse.oracle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import static com.streamhouse.oracle.CapturedLobTransactions.*;

/** Observed uncompressed SecureFile images plus source-owned live fills. */
final class CapturedSecureFileLobValues {
    static byte[] value(CapturedLobTransactions input,int column,byte[] raw,ArchivedUndoInventory.Position position,UndoHeader locatorOwner)throws IOException {
        var scope=input.scope();var kind=scope.lob(column).kind();
        var locator=LobLocator.decode(raw,kind,LobLocator.Storage.SECUREFILE);
        if(locator.inlinePayload()!=null)return logical(kind,locator.inlinePayload());
        var current=input.transaction(locatorOwner.xid());var parts=new ArrayList<byte[]>();
        for(long dba:locator.pages()) {
            At selected=null;SecureFileLobPageImage image=null;int imageVector=-1;
            for(var at:input.records())for(int i=0;i<at.record().vectors().size();i++) {
                var v=at.record().vectors().get(i);
                if(v.containerId()!=scope.container() || v.opcodeMajor()!=26 || v.opcodeMinor()!=6 || v.dba()!=dba || compare(at.position(i),position)>0)continue;
                var candidate=SecureFileLobPageImage.decode(v,scope.object(column));
                var owner=input.transaction(candidate.xid());
                // Seq104 reuses pages from the earlier, completely verified quota rollback.
                if(owner.identity().rolledBack() && owner.identity().endRecordScn()<current.identity().beginRecordScn()
                        && at.record().scn()>=owner.identity().beginRecordScn() && at.record().scn()<=owner.identity().endRecordScn())continue;
                if(!candidate.lobId().equals(locator.lobId()))throw new RedoException("SECUREFILE_VALUE_IMAGE_LOCATOR_IDENTITY");
                if(owner.identity().rolledBack() || (!candidate.xid().equals(locatorOwner.xid()) && owner.identity().endRecordScn()>current.identity().beginRecordScn()))
                    throw new RedoException("SECUREFILE_VALUE_IMAGE_UNCOMMITTED_OWNER");
                if(image!=null)throw new RedoException("SECUREFILE_VALUE_REUSED_IMAGE_UNSUPPORTED");
                image=candidate;selected=at;imageVector=i;
            }
            if(image==null)throw new RedoException("SECUREFILE_VALUE_IMAGE_MISSING");
            byte[] page=image.payload();var imageAt=selected.position(imageVector);
            for(var at:input.records())for(int i=0;i<at.record().vectors().size();i++) {
                var v=at.record().vectors().get(i);
                if(v.containerId()!=scope.container() || v.opcodeMajor()!=26 || v.opcodeMinor()!=2 || v.dba()!=dba
                        || compare(at.position(i),imageAt)<0 || compare(at.position(i),position)>0)continue;
                var fill=SecureFileLobFill.decode(v);if(!fill.lobId().equals(locator.lobId()))throw new RedoException("SECUREFILE_VALUE_FILL_LOCATOR_IDENTITY");
                if(fill.undo())continue; // Complete physical graphs already verified and cancelled the corresponding original.
                var pair=SecureFileLobUndoPair.bind(at.record(),v,scope.object(column));var owner=input.transaction(pair.owner().xid());
                if(owner.identity().rolledBack() || owner.graph().live().stream().noneMatch(e->e.identity().equals(pair.owner())))continue;
                if(!pair.owner().xid().equals(locatorOwner.xid()) && owner.identity().endRecordScn()>current.identity().beginRecordScn())
                    throw new RedoException("SECUREFILE_VALUE_FOREIGN_UNCOMMITTED_FILL");
                byte[] bytes=fill.payload();int end=fill.offset()+bytes.length;
                if(fill.offset()>page.length)throw new RedoException("SECUREFILE_VALUE_UNPROVEN_GAP");
                if(end>page.length) {
                    if(pair.inverseHighWater()!=page.length)throw new RedoException("SECUREFILE_VALUE_APPEND_HIGH_WATER");
                    page=Arrays.copyOf(page,end);
                } else if(pair.inverse()!=null && !Arrays.equals(pair.inverse().payload(),Arrays.copyOfRange(page,fill.offset(),end)))
                    throw new RedoException("SECUREFILE_VALUE_INVERSE_BYTES_MISMATCH");
                System.arraycopy(bytes,0,page,fill.offset(),bytes.length);
            }
            parts.add(page);
        }
        byte[] value=new byte[Math.toIntExact(locator.byteLength())];int copied=0;
        for(int i=0;i<parts.size();i++) {
            byte[] page=parts.get(i);int count=Math.min(page.length,value.length-copied);
            if(i<parts.size()-1 && count!=8060)throw new RedoException("SECUREFILE_VALUE_SHORT_INTERMEDIATE_PAGE");
            System.arraycopy(page,0,value,copied,count);copied+=count;
        }
        if(copied!=value.length)throw new RedoException("SECUREFILE_VALUE_INCOMPLETE_BYTES");return logical(kind,value);
    }
    static byte[] logical(LobLocator.Kind kind,byte[] bytes)throws RedoException {
        if(kind==LobLocator.Kind.BLOB)return bytes;
        try{return StandardCharsets.UTF_16BE.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().getBytes(StandardCharsets.UTF_8);}
        catch(java.nio.charset.CharacterCodingException e){throw new RedoException("LOB_VALUE_INVALID_CLOB_UTF16");}
    }
}
