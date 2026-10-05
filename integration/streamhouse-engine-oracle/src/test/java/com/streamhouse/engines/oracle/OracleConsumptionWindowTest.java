package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.CaptureJournal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleConsumptionWindowTest {
    CaptureJournal.Source capture(long seq,long start,long end,String digest) {
        return new CaptureJournal.Source(digest,1000,100,50,1,2,0x13000000,1,seq,3,4,start,end);
    }
    final CaptureJournal.Source a=capture(40,100,110,"old");
    final CaptureJournal.Source b=capture(41,110,120,"b");
    @Test void fullWindowExtendsPrefixAndOldReplayDoesNotAdvance() {
        assertEquals(OracleConsumptionWindow.Relationship.EXTENDS_PREFIX,OracleConsumptionWindow.compareCapturePrefix(List.of(a,b),List.of(a)));
        assertEquals(OracleConsumptionWindow.Relationship.COVERED_PREFIX,OracleConsumptionWindow.compareCapturePrefix(List.of(a),List.of(a,b)));
    }
    @Test void observationHashChangeDoesNotInventProgress() {
        assertEquals(OracleConsumptionWindow.Relationship.SAME_RANGE,OracleConsumptionWindow.compareCapturePrefix(List.of(capture(40,100,110,"new")),List.of(a)));
    }
    @Test void differentStartCannotHideAnUncoveredPrefix() {
        assertEquals("ORACLE_WINDOW_CAPTURE_PREFIX_CHANGED",assertThrows(IllegalArgumentException.class,
                ()->OracleConsumptionWindow.compareCapturePrefix(List.of(b),List.of(a))).getMessage());
    }
    @Test void changedClosedCoverageIsRejected() {
        assertThrows(IllegalArgumentException.class,()->OracleConsumptionWindow.compareCapturePrefix(List.of(capture(40,100,111,"new")),List.of(a)));
    }
    @Test void sequenceHoleAndScnHoleAreRejected() {
        assertEquals("ORACLE_WINDOW_CAPTURE_GAP",assertThrows(IllegalArgumentException.class,
                ()->OracleConsumptionWindow.compareCapturePrefix(List.of(a,capture(42,110,120,"hole")),List.of(a))).getMessage());
        assertThrows(IllegalArgumentException.class,()->OracleConsumptionWindow.compareCapturePrefix(List.of(a,capture(41,111,120,"hole")),List.of(a)));
    }
    @Test void emptyCoverageCannotBePublished() {
        assertThrows(IllegalArgumentException.class,()->OracleConsumptionWindow.compareCapturePrefix(List.of(),List.of(a)));
        assertThrows(IllegalArgumentException.class,()->OracleConsumptionWindow.compareCapturePrefix(List.of(a),List.of()));
    }
}
