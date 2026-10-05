package com.streamhouse.engines.oracle;

import com.streamhouse.oracle.CapturedHeapBatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed-scope consumer state. Requires retained trusted inputs; never authorizes raw deletion. */
public final class OracleConsumerCheckpoint {
    @FunctionalInterface public interface Inputs { List<CapturedHeapBatch> read() throws IOException; }
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    public record Result(OracleConsumptionWindow.Position position,int newApplications,List<String> committedBatches) {
        public Result {committedBatches=List.copyOf(committedBatches);}
    }
    private record Batch(OracleConsumptionWindow window,OracleBatchProjection.Batch projected) {}
    private record State(List<String> ids,String frontier) {}

    public Result consume(Path directory,OracleTargetBatchWriter.Target target,Inputs inputs,String currentBatch,
                          Connections connections) throws IOException,SQLException {
        if (!Files.isDirectory(directory)) throw new IOException("ORACLE_CONSUMER_DIRECTORY_REQUIRED");
        try (var channel=FileChannel.open(directory.resolve("consumer.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock=acquire(channel)) {
            var registry=new LinkedHashMap<String,Batch>();Batch anchor=null;
            for (var input:inputs.read()) {
                var b=new Batch(OracleConsumptionWindow.from(input),OracleBatchProjection.project(input));
                if (anchor!=null) b.window().relativeTo(anchor.window());
                else anchor=b;
                if (registry.putIfAbsent(input.sha256(),b)!=null) throw new IOException("ORACLE_CONSUMER_DUPLICATE_INPUT");
            }
            if (!registry.containsKey(currentBatch)) throw new IOException("ORACLE_CONSUMER_CURRENT_INPUT_MISSING");
            Path checkpoint=directory.resolve("checkpoint"),pending=directory.resolve("checkpoint.tmp");
            State old=readState(checkpoint,target,registry),temp=readState(pending,target,registry);
            try (var files=Files.newDirectoryStream(directory,"*")) {
                for (var file:files) {
                    String name=file.getFileName().toString();
                    int suffix=name.endsWith(".ack.tmp")?8:name.endsWith(".ack")?4:0;
                    if (suffix!=0 && !registry.containsKey(name.substring(0,name.length()-suffix))) throw new IOException("ORACLE_CONSUMER_ACK_INPUT_MISSING");
                }
            }
            // Validate every published ACK before any target operation.
            for (var entry:registry.entrySet()) {
                for (String suffix:List.of(".ack",".ack.tmp")) {
                    var path=directory.resolve(entry.getKey()+suffix);
                    if (Files.exists(path) && !java.util.Arrays.equals(Files.readAllBytes(path),ack(target,entry.getValue())))
                        throw new IOException("ORACLE_CONSUMER_ACK_CONFLICT");
                }
            }
            var committed=new ArrayList<String>();var writer=new OracleTargetBatchWriter();
            for (var entry:registry.entrySet()) {
                String id=entry.getKey();
                boolean published=contains(old,id)||contains(temp,id)||Files.exists(directory.resolve(id+".ack"))||Files.exists(directory.resolve(id+".ack.tmp"));
                try {
                    writer.apply(connections.open(),target,entry.getValue().projected(),true);
                    committed.add(id);
                } catch (SQLException e) {
                    if (published || !"ORACLE_TARGET_ACK_WITHOUT_COMMIT".equals(e.getMessage())) throw e;
                }
            }
            int applied=0;
            if (!committed.contains(currentBatch)) {
                applied=writer.apply(connections.open(),target,registry.get(currentBatch).projected(),false).newApplications();
                committed.add(currentBatch);
            }
            // Include already committed registered windows even if a local checkpoint was lost or stale.
            State next=state(committed,registry);
            preservePending(pending);
            byte[] encoded=encode(next,target,registry);
            if (!Files.exists(checkpoint)||!java.util.Arrays.equals(Files.readAllBytes(checkpoint),encoded)) persist(checkpoint,encoded);
            for (String id:committed) {
                Path path=directory.resolve(id+".ack");preservePending(directory.resolve(id+".ack.tmp"));
                if (!Files.exists(path)) persist(path,ack(target,registry.get(id)));
            }
            return new Result(registry.get(next.frontier()).window().position(),applied,committed);
        }
    }
    private static FileLock acquire(FileChannel channel) throws IOException {
        try {var lock=channel.tryLock();if (lock!=null) return lock;}
        catch (OverlappingFileLockException expected) {}
        throw new IOException("ORACLE_CONSUMER_ALREADY_RUNNING");
    }
    private static boolean contains(State state,String id) {return state!=null&&state.ids().contains(id);}
    private static State state(List<String> ids,Map<String,Batch> registry) throws IOException {
        if (ids.isEmpty()) throw new IOException("ORACLE_CONSUMER_EMPTY_CHECKPOINT");
        String frontier=ids.get(0);
        for (String id:ids) if (registry.get(id).window().relativeTo(registry.get(frontier).window())==OracleConsumptionWindow.Relationship.EXTENDS_PREFIX) frontier=id;
        return new State(List.copyOf(ids),frontier);
    }
    private static State readState(Path path,OracleTargetBatchWriter.Target target,Map<String,Batch> registry) throws IOException {
        if (!Files.exists(path)) return null;
        byte[] bytes=Files.readAllBytes(path);String text;
        try {text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}
        catch (java.nio.charset.CharacterCodingException e) {throw new IOException("ORACLE_CONSUMER_INVALID_CHECKPOINT",e);}
        String[] lines=text.split("\n",-1);var ids=new ArrayList<String>();
        if (lines.length<6 || !lines[0].equals("ORK_ORACLE_CONSUMER_V1") || !lines[1].equals(targetLine(target))
                || !lines[2].startsWith("FRONTIER|") || !lines[lines.length-1].isEmpty()) throw new IOException("ORACLE_CONSUMER_INVALID_CHECKPOINT");
        for (int i=3;i<lines.length-2;i++) {
            String[] fields=lines[i].split("\\|",-1);
            if (fields.length<2 || !fields[0].equals("BATCH") || !registry.containsKey(fields[1]) || ids.contains(fields[1]))
                throw new IOException("ORACLE_CONSUMER_CHECKPOINT_INPUT_MISSING_OR_INVALID");
            ids.add(fields[1]);
        }
        State parsed=state(ids,registry);
        if (!java.util.Arrays.equals(bytes,encode(parsed,target,registry))) throw new IOException("ORACLE_CONSUMER_CHECKPOINT_CONFLICT");
        return parsed;
    }
    private static String targetLine(OracleTargetBatchWriter.Target target) {
        return "TARGET|"+Base64.getUrlEncoder().withoutPadding().encodeToString((target.owner()+"."+target.prefix()+"|"+target.targetId()).getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] encode(State state,OracleTargetBatchWriter.Target target,Map<String,Batch> registry) {
        var text=new StringBuilder("ORK_ORACLE_CONSUMER_V1\n").append(targetLine(target)).append("\nFRONTIER|").append(state.frontier()).append('\n');
        for (String id:state.ids()) {
            var b=registry.get(id);var w=b.window();var pos=w.position();
            text.append("BATCH|").append(id).append('|').append(w.schemaSha256()).append('|').append(w.baselineSha256()).append('|')
                    .append(w.baselineScn()).append('|').append(w.leaf()).append('|').append(OracleTargetBatchWriter.sourceBinding(b.projected()))
                    .append('|').append(pos.closedSequence()).append('|').append(pos.closedBlocks()).append('|').append(pos.endExclusiveScn())
                    .append('|').append(b.projected().entries().size());
            for (var c:w.captures()) text.append('|').append(c.sequence()).append(':').append(c.journalSha256());
            text.append('\n');
        }
        byte[] body=text.toString().getBytes(StandardCharsets.UTF_8);
        return (text+"SHA256|"+digest(body)+"\n").getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] ack(OracleTargetBatchWriter.Target target,Batch batch) {
        return ("ORK_ORACLE_TARGET_ACK_V1|"+target.targetId()+"|"+batch.window().batchSha256()+"|"+batch.window().schemaSha256()+"|"+batch.projected().entries().size()+"\n").getBytes(StandardCharsets.UTF_8);
    }
    private static String digest(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch (NoSuchAlgorithmException e) {throw new AssertionError(e);}
    }
    private static void preservePending(Path temp) throws IOException {
        if (Files.exists(temp)) Files.move(temp,temp.resolveSibling(temp.getFileName()+".retained-"+java.util.UUID.randomUUID()),StandardCopyOption.ATOMIC_MOVE);
    }
    private static void persist(Path destination,byte[] bytes) throws IOException {
        Path temp=destination.resolveSibling(destination.getFileName()+".tmp");
        try (var out=FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            var buffer=ByteBuffer.wrap(bytes);while (buffer.hasRemaining()) out.write(buffer);out.force(true);
        }
        Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        try (var directory=FileChannel.open(destination.getParent(),StandardOpenOption.READ)) {directory.force(true);}
    }
}
