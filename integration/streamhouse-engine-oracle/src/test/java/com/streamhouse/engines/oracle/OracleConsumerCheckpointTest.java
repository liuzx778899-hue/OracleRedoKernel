package com.streamhouse.engines.oracle;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OracleConsumerCheckpointTest {
    @TempDir Path dir;
    final OracleTargetBatchWriter.Target target=new OracleTargetBatchWriter.Target("OWNER","FIXTURE","target");
    @Test void secondConsumerIsRejectedBeforeReadingInputsOrConnecting() throws Exception {
        try (var c=FileChannel.open(dir.resolve("consumer.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=c.lock()) {
            var e=assertThrows(IOException.class,()->new OracleConsumerCheckpoint().consume(dir,target,
                    ()->{fail("input read under competing lock");return List.of();},"missing",()->{fail("target connection");return null;}));
            assertEquals("ORACLE_CONSUMER_ALREADY_RUNNING",e.getMessage());
        }
    }
    @Test void inputFailureReleasesTheLifecycleLockAndPreservesFiles() throws Exception {
        var old=dir.resolve("checkpoint");Files.writeString(old,"old-state");
        assertThrows(IOException.class,()->new OracleConsumerCheckpoint().consume(dir,target,()->{throw new IOException("source unavailable");},"missing",()->{fail("target connection");return null;}));
        assertEquals("old-state",Files.readString(old));
        try (var c=FileChannel.open(dir.resolve("consumer.lock"),StandardOpenOption.WRITE);var lock=c.tryLock()) {assertNotNull(lock);}
    }
    @Test void emptyInputRegistryCannotApplyOrPublish() {
        assertEquals("ORACLE_CONSUMER_CURRENT_INPUT_MISSING",assertThrows(IOException.class,
                ()->new OracleConsumerCheckpoint().consume(dir,target,List::of,"missing",()->{fail("target connection");return null;})).getMessage());
        assertFalse(Files.exists(dir.resolve("checkpoint")));
    }
    @Test void missingConsumerDirectoryIsNotCreated() {
        Path absent=dir.resolve("absent");
        assertThrows(IOException.class,()->new OracleConsumerCheckpoint().consume(absent,target,List::of,"missing",()->{fail("target connection");return null;}));
        assertFalse(Files.exists(absent));
    }
}
