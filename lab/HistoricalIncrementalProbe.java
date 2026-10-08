import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;

/** Frozen raw correctness probe only; a computed boundary is not a target commit receipt. */
public class HistoricalIncrementalProbe {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[4]);Files.createDirectory(output);
        var reader=new CaptureJournal();var captures=List.of(reader.readRawClosed(Path.of(args[2]),"44a08e6ec18c86b6a9601b137f1a37ead8c6797769cd4d3b70d67ace6016986b"),
                reader.readRawClosed(Path.of(args[3]),"e837cd97db88b0212275c1451686911949fb954161cd0d7cc84feecede6b4b2f"));
        var full=CapturedHistoricalHeapBatch.read(captures,Path.of(args[0]),args[1],50359491);
        if(full.events().size()!=40)throw new AssertionError("full event count");
        var boundary=full.publicationBoundary(20);var incremental=full.afterPublished(boundary);
        if(!incremental.events().equals(full.events().subList(20,40)))throw new AssertionError("images/identity changed");
        var duplicate=full.afterPublished(full.publicationBoundary(40));
        if(!duplicate.events().isEmpty())throw new AssertionError("duplicate emission");
        String refused;
        try {full.publicationBoundary(1);throw new AssertionError("transaction split accepted");}
        catch(RedoException expected){refused=expected.getMessage();}
        String json="{\"fullEvents\":40,\"publishedPrefixEvents\":20,\"newEvents\":20,\"duplicateEvents\":0,"
                +"\"completeImagesAndIdentityEqual\":true,\"boundarySha256\":\""+boundary.sha256()+"\","
                +"\"incrementalBatchSha256\":\""+incremental.sha256()+"\",\"splitRefused\":\""+refused+"\","
                +"\"targetCommitted\":false,\"sourceActions\":0}";
        Files.writeString(output.resolve("summary.json"),json);System.out.println(json);
    }
}
