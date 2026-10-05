package com.streamhouse.engines.oracle;

import com.streamhouse.spi.EventEnvelope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Oracle target transaction port. Owns/closes a fresh connection; never writes source tables or ACK. */
public final class OracleTargetBatchWriter {
    public record Target(String owner,String prefix,String targetId) {
        public Target { identifier(owner);identifier(prefix);if (targetId==null || targetId.isBlank()) throw new IllegalArgumentException("MISSING_TARGET_ID"); }
        String table(String suffix) {return owner+"."+prefix+"_"+suffix;}
    }
    public record CommitReceipt(String targetId,String batchSha256,String schemaSha256,int events) {}
    public record Result(CommitReceipt receipt,int newApplications) {}

    public static String sourceBinding(OracleBatchProjection.Batch batch) {
        var s=batch.source();return s.dbid()+":"+s.activationId()+":"+s.compatibility()+":"+s.thread()+":"+s.resetlogsId()
                +":"+s.resetlogsScn()+":"+s.containerUid()+":"+s.containerId();
    }
    public Result apply(Connection freshConnection,Target target,OracleBatchProjection.Batch batch,boolean requireExistingCommit) throws SQLException {
        // Ownership is explicit: the caller must not supply an existing transaction or pooled shared handle.
        try (var conn=freshConnection) {
            if (!conn.getAutoCommit()) throw new SQLException("ORACLE_TARGET_REQUIRES_FRESH_AUTOCOMMIT_CONNECTION");
            conn.setAutoCommit(false);
            try {
                try (var ps=conn.prepareStatement("select TARGET_ID,SOURCE_BINDING,SCHEMA_SHA from "+target.table("IDENT")+" where ID=1 for update nowait");var rs=ps.executeQuery()) {
                    if (!rs.next() || !target.targetId().equals(rs.getString(1)) || !sourceBinding(batch).equals(rs.getString(2))
                            || !batch.schema().sha256().equals(rs.getString(3)) || rs.next()) throw new SQLException("ORACLE_TARGET_IDENTITY_MISMATCH");
                }
                var receipt=new CommitReceipt(target.targetId(),batch.inputBatchSha256(),batch.schema().sha256(),batch.entries().size());
                boolean committed=false;
                try (var ps=conn.prepareStatement("select TARGET_ID,SCHEMA_SHA,EVENTS_COUNT from "+target.table("COMMITS")+" where BATCH_SHA=?")) {
                    ps.setString(1,receipt.batchSha256());try (var rs=ps.executeQuery()) {
                        if (rs.next()) {
                            if (!receipt.targetId().equals(rs.getString(1)) || !receipt.schemaSha256().equals(rs.getString(2))
                                    || receipt.events()!=rs.getInt(3) || rs.next()) throw new SQLException("ORACLE_TARGET_COMMIT_CONFLICT");
                            committed=true;
                        }
                    }
                }
                if (requireExistingCommit && !committed) throw new SQLException("ORACLE_TARGET_ACK_WITHOUT_COMMIT");
                int applied=0;int ordinal=0;
                for (var entry:batch.entries()) {
                    var e=entry.envelope();String before=image(e.before()),after=image(e.after());boolean present=false;
                    try (var ps=conn.prepareStatement("select PAYLOAD_SHA,BEFORE_IMAGE,AFTER_IMAGE,OPERATION from "+target.table("APPLIED")+" where EVENT_ID=?")) {
                        ps.setString(1,e.eventId());try (var rs=ps.executeQuery()) {
                            if (rs.next()) {
                                if (!entry.payloadSha256().equals(rs.getString(1)) || !before.equals(empty(rs.getString(2)))
                                        || !after.equals(empty(rs.getString(3))) || !e.operation().name().equals(rs.getString(4)) || rs.next())
                                    throw new SQLException("ORACLE_TARGET_EVENT_CONFLICT");
                                present=true;
                            }
                        }
                    }
                    if (!present) {
                        if (committed) throw new SQLException("ORACLE_TARGET_COMMIT_WITHOUT_EVENT");
                        applyRow(conn,target,e);
                        try (var ps=conn.prepareStatement("insert into "+target.table("APPLIED")+"(EVENT_ID,PAYLOAD_SHA,BEFORE_IMAGE,AFTER_IMAGE,OPERATION) values(?,?,?,?,?)")) {
                            ps.setString(1,e.eventId());ps.setString(2,entry.payloadSha256());ps.setString(3,before);ps.setString(4,after);
                            ps.setString(5,e.operation().name());ps.executeUpdate();
                        }
                        applied++;
                    }
                    if (!committed) {
                        try (var ps=conn.prepareStatement("insert into "+target.table("MEMBERS")+"(BATCH_SHA,ORDINAL,EVENT_ID) values(?,?,?)")) {
                            ps.setString(1,receipt.batchSha256());ps.setInt(2,ordinal);ps.setString(3,e.eventId());ps.executeUpdate();
                        }
                    }
                    ordinal++;
                }
                try (var ps=conn.prepareStatement("select ORDINAL,EVENT_ID from "+target.table("MEMBERS")+" where BATCH_SHA=? order by ORDINAL")) {
                    ps.setString(1,receipt.batchSha256());try (var rs=ps.executeQuery()) {
                        int i=0;while (rs.next()) {
                            if (i>=batch.entries().size() || rs.getInt(1)!=i || !batch.entries().get(i++).envelope().eventId().equals(rs.getString(2)))
                                throw new SQLException("ORACLE_TARGET_BATCH_MEMBERS_CONFLICT");
                        }
                        if (i!=batch.entries().size()) throw new SQLException("ORACLE_TARGET_BATCH_MEMBERS_INCOMPLETE");
                    }
                }
                if (!committed) try (var ps=conn.prepareStatement("insert into "+target.table("COMMITS")+"(BATCH_SHA,TARGET_ID,SCHEMA_SHA,EVENTS_COUNT) values(?,?,?,?)")) {
                    ps.setString(1,receipt.batchSha256());ps.setString(2,receipt.targetId());ps.setString(3,receipt.schemaSha256());ps.setInt(4,receipt.events());ps.executeUpdate();
                }
                conn.commit();return new Result(receipt,applied);
            } catch (SQLException | RuntimeException e) {
                try {conn.rollback();} catch (SQLException rollback) {e.addSuppressed(rollback);}
                throw e;
            }
        }
    }
    private static void applyRow(Connection conn,Target target,EventEnvelope event) throws SQLException {
        Map<String,Object> before=event.before()==null ? Map.of() : event.before();
        Map<String,Object> after=event.after()==null ? Map.of() : event.after();var args=new ArrayList<Object>();String sql;
        for (var name:before.keySet()) identifier(name);for (var name:after.keySet()) identifier(name);
        if (event.operation()==EventEnvelope.Operation.INSERT) {
            sql="insert into "+target.table("ROWS")+"("+String.join(",",after.keySet())+") values("
                    +after.keySet().stream().map(k->"?").collect(Collectors.joining(","))+")";args.addAll(after.values());
        } else {
            String where=before.entrySet().stream().map(e->e.getKey()+(e.getValue()==null ? " is null" : "=?")).collect(Collectors.joining(" and "));
            if (where.isEmpty()) throw new SQLException("ORACLE_TARGET_MISSING_BEFORE");
            if (event.operation()==EventEnvelope.Operation.UPDATE) {
                sql="update "+target.table("ROWS")+" set "+after.keySet().stream().map(k->k+"=?").collect(Collectors.joining(","))+" where "+where;args.addAll(after.values());
            } else sql="delete from "+target.table("ROWS")+" where "+where;
            before.values().stream().filter(v->v!=null).forEach(args::add);
        }
        try (var ps=conn.prepareStatement(sql)) {
            for (int i=0;i<args.size();i++) ps.setObject(i+1,args.get(i));
            if (ps.executeUpdate()!=1) throw new SQLException("ORACLE_TARGET_ROW_MATCH_NOT_ONE");
        }
    }
    private static String image(Map<String,Object> values) {
        if (values==null) return "";
        var fields=new ArrayList<String>();for (var e:values.entrySet()) {
            Object v=e.getValue();String value=v==null ? "NULL" : v instanceof BigDecimal n ? "NUMBER:"+n.toPlainString()
                    : "VARCHAR2:"+Base64.getUrlEncoder().withoutPadding().encodeToString(((String)v).getBytes(StandardCharsets.UTF_8));
            fields.add(e.getKey()+"="+value);
        }
        return String.join(";",fields);
    }
    private static String empty(String value) {return value==null ? "" : value;}
    private static void identifier(String value) {
        if (value==null || !value.matches("[A-Z][A-Z0-9_]{0,23}")) throw new IllegalArgumentException("ORACLE_TARGET_IDENTIFIER_REJECTED");
    }
}
