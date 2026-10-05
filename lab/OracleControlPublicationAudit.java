import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Independent SELECT export; no MyBatis, journal or parser invocation. */
public class OracleControlPublicationAudit {
    record Target(String dialect,String stage,String database,String url,String user,String password) {}
    public static void main(String[] args)throws Exception {
        var json=new ObjectMapper();var targets=json.readValue(Files.readString(Path.of(args[0])),Target[].class);var out=new ArrayList<Map<String,Object>>();
        for(var t:targets)try(var c=DriverManager.getConnection(t.url(),t.user(),t.password())){
            int width;
            try(var columns=c.getMetaData().getColumns(c.getCatalog(),c.getSchema(),"SH_EVENT_LEDGER","EVENT_ID")){
                if(!columns.next())throw new AssertionError("EVENT_ID_COLUMN_MISSING");width=columns.getInt("COLUMN_SIZE");
            }
            for(String name:List.of("SNON","SNUL","SMIX","EMPT")){
                String source="oracle-journal-"+name,window="journal-"+name;var row=new LinkedHashMap<String,Object>();row.put("key",t.dialect()+":"+t.stage()+":"+name);row.put("event_id_width",width);
                try(var s=c.prepareStatement("SELECT b.ID,b.STATE,b.INPUT_SHA256,b.ISSUE_SEQ_START,b.ISSUE_SEQ_END,b.EVENT_COUNT,t.CHECKPOINT_JSON,t.STATE,w.STATUS,w.FROZEN,w.DROPPED_SEGMENTS FROM SH_PIPELINE_BATCH b JOIN SH_TASK t ON t.REF_ID=b.SOURCE_ID AND t.REF_TYPE='SOURCE' JOIN SH_CAP_WINDOW w ON w.SOURCE_ID=b.SOURCE_ID AND w.WINDOW_KEY=b.WINDOW_KEY WHERE b.SOURCE_ID=? AND b.WINDOW_KEY=?")){
                    s.setString(1,source);s.setString(2,window);try(var rs=s.executeQuery()){
                        if(!rs.next())throw new AssertionError("CONTROL_ROW_MISSING");long batch=rs.getLong(1);row.put("batch_id",batch);row.put("batch_state",rs.getString(2));row.put("input_sha256",rs.getString(3));row.put("transport",List.of(rs.getLong(4),rs.getLong(5)));row.put("event_count",rs.getInt(6));row.put("checkpoint",json.readTree(rs.getString(7)));row.put("task_state",rs.getString(8));row.put("window_state",rs.getString(9));row.put("frozen",rs.getInt(10));row.put("dropped",rs.getLong(11));if(rs.next())throw new AssertionError("CONTROL_ROW_AMBIGUOUS");
                        var ledger=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT EVENT_ID,SOURCE_ID,TABLE_NAME,OPERATION FROM SH_EVENT_LEDGER WHERE BATCH_ID=? ORDER BY EVENT_ID")){
                            p.setLong(1,batch);try(var r=p.executeQuery()){while(r.next())ledger.add(Map.of("event_id",r.getString(1),"source_id",r.getString(2),"table",r.getString(3),"operation",r.getString(4)));}
                        }row.put("ledger",ledger);var gates=new ArrayList<String>();try(var p=c.prepareStatement("SELECT VERDICT FROM SH_PUBLISH_GATE WHERE BATCH_ID=?")){p.setLong(1,batch);try(var r=p.executeQuery()){while(r.next())gates.add(r.getString(1));}}row.put("gates",gates);
                    }
                }out.add(row);
            }
        }
        Files.writeString(Path.of(args[1]),json.writeValueAsString(out));System.out.println("ORACLE_CONTROL_SELECT_AUDIT_EXPORTED|"+out.size());
    }
}
