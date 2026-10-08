import com.streamhouse.oracle.*;
import java.nio.file.*;
import java.util.*;
public class D2InitialRawProbe {
 public static void main(String[] a)throws Exception {
  var captures=new ArrayList<CaptureJournal.Verified>();for(int i=5;i<a.length;i+=2)captures.add(new CaptureJournal().readRawClosed(Path.of(a[i]),a[i+1]));
  var base=Path.of(a[1]);long leaf=Long.parseLong(a[3]);var state=Path.of(a[4]);
  if(a[0].equals("prefix")) {var p=HistoricalRecovery.prefixCaptured(captures,base,a[2],leaf);if(p.pendingTransactions()!=1 || p.completedTransactions()!=0)throw new IllegalStateException("INITIAL_PENDING_SCOPE");Files.write(state,p.checkpoint(),StandardOpenOption.CREATE_NEW);System.out.println("WAITING|1|0|events=0");return;}
  var batch=a[0].equals("restored")?HistoricalRecovery.readCaptured(captures,base,a[2],leaf,Files.readAllBytes(state),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(state)))):CapturedHistoricalHeapBatch.read(captures,base,a[2],leaf);
  if(batch.events().size()!=3)throw new IllegalStateException("INITIAL_EVENT_COUNT");
  var first=batch.events().get(0).row().transaction();if(first.beginRecordScn()>batch.baselineScn() || first.endRecordScn()<=batch.baselineScn())throw new IllegalStateException("NOT_CROSSING_INITIAL_SCN");
  for(var event:batch.events()){var row=event.row();System.out.println("EVENT|"+row.eventId()+"|"+row.payloadSha256()+"|"+row.transaction().xid()+"|"+row.before()+"|"+row.after());}
  System.out.println("COUNT|3");
 }
}
