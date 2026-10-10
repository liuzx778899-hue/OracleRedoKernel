package com.streamhouse.oracle;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FrozenTimeZoneBaselineTest {
    @TempDir Path directory;
    FrozenHeapBaseline.Snapshot read(String value)throws Exception {var helper=new FrozenHeapBaselineTest();helper.directory=directory;return helper.read(value);}
    String fixture()throws Exception {
        String old=new DictionaryBaselineTest().v3();
        var lines=new java.util.ArrayList<String>();
        for(String line:old.lines().toList()) {
            if(line.startsWith("DICT_ROW|COL$") && line.contains("|545854|")) {
                var fields=line.split("\\|",-1);assertEquals("C102",fields[13]);fields[13]="C20252";line=String.join("|",fields);
            }
            lines.add(line);
        }
        return String.join("\n",lines)+"\n";
    }
    String zoned()throws Exception {
        return fixture().replace("ORK_HEAP_SNAPSHOT_V3","ORK_HEAP_SNAPSHOT_V4")
                .replace("CHARSET|AL32UTF8\n","CHARSET|AL32UTF8\nTZ_CONTEXT|BEFORE|+00:00|32\nTZ_REGION|382|Europe/Paris\n")
                .replace("TXT|VARCHAR2","TXT|TIMESTAMP WITH TIME ZONE")
                .replace("DICT_COLUMN|2|2|TXT|1|","DICT_COLUMN|2|2|TXT|181|")
                .replace("64696374696F6E6172792D73656564","787C031F011F2E075BCA0085F8")
                .replace("SOURCE_AFTER|","TZ_CONTEXT|AFTER|+00:00|32\nSOURCE_AFTER|");
    }
    @Test void trustedContextIsBoundIntoSchemaAndLegacySnapshotsRemainWithoutIt()throws Exception {
        var first=read(zoned());assertEquals("Europe/Paris",first.timeZoneContext().regions().get(382));
        var changed=read(zoned().replace("+00:00|32","+00:00|33"));
        assertNotEquals(CapturedHeapBatch.schemaDigest(first),CapturedHeapBatch.schemaDigest(changed));
        assertNull(read(new DictionaryBaselineTest().v3()).timeZoneContext());
    }
    @Test void changedMissingOrUnknownContextCannotAuthorizeTimezoneBytes()throws Exception {
        String v=zoned();
        for(String bad:new String[]{v.replace("TZ_CONTEXT|AFTER|+00:00|32","TZ_CONTEXT|AFTER|+01:00|32"),
                v.replace("TZ_REGION|382|Europe/Paris\n",""),v.replace("TZ_REGION|382|Europe/Paris\n","TZ_REGION|382|Europe/Paris\nTZ_REGION|382|Europe/Paris\n"),
                v.replace("ORK_HEAP_SNAPSHOT_V4","ORK_HEAP_SNAPSHOT_V3"),v.replace("0085F8","0085F9")})
            assertThrows(RedoException.class,()->read(bad));
    }
}
