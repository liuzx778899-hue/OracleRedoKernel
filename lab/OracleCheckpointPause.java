import java.nio.file.*;
import java.io.IOException;

/** Lab-only blocking hook; never packaged into the production adapter. */
public class OracleCheckpointPause {
    public static void pause(Path destination,String point) throws IOException {
        if (!destination.getFileName().toString().equals("checkpoint") || !point.equals(System.getProperty("labStage"))) return;
        Files.writeString(Path.of(System.getProperty("labReady")),point);
        while (true) try {Thread.sleep(1000);} catch (InterruptedException e) {throw new IOException(e);}
    }
}
