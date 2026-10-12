package com.streamhouse.oracle;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="ork.lob.future",matches=".+")
class InvalidatedRecordFixtureTest {
    @Test void observedRootInvalidatedRecordIsNotAChangeAndChangedScopeIsRefused()throws Exception {
        Path source=Path.of(System.getProperty("ork.lob.future"));byte[] raw=Files.readAllBytes(source.resolve("inputs/121.raw"));
        String nativeDump=Files.readString(source.getParent().resolve("record-diagnostic-r3/record.trc"));
        assertTrue(nativeDump.contains("RBA: 0x000079.0000e4be.0030"));
        assertFalse(nativeDump.contains("RBA: 0x000079.0000e4be.0134"));
        var verified=new CaptureJournal().readRawClosed(source.resolve("inputs/121.raw"),LobOriginCapsule.sha(raw));
        assertTrue(new RedoRecords().readCaptured(verified).stream().noneMatch(r->r.block()==58558 && r.offset()==308));
        Path file=Files.createTempFile("ork-invalidated-",".raw");
        try {
            for(int offset:new int[]{308+16,308+148+24}) {
                byte[] poison=raw.clone();int start=58558*512;poison[start+offset]=3;
                poison[start+14]=0;poison[start+15]=0;int checksum=0;
                for(int at=start;at<start+512;at+=2)checksum^=RedoBytes.u16(poison,at);
                poison[start+14]=(byte)checksum;poison[start+15]=(byte)(checksum>>>8);Files.write(file,poison);
                var changed=new CaptureJournal().readRawClosed(file,LobOriginCapsule.sha(poison));
                assertTrue(assertThrows(RedoException.class,()->new RedoRecords().readCaptured(changed)).getMessage().startsWith("UNSUPPORTED_INVALIDATED_RECORD_PROFILE"));
            }
        } finally {Files.delete(file);}
    }
}
