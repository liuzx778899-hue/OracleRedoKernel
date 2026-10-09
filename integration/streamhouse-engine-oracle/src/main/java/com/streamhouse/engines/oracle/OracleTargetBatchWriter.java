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
import java.util.LinkedHashMap;
import com.streamhouse.spi.OracleBatchEvidence;
import java.util.stream.Collectors;

/** Oracle target transaction port. Owns/closes a fresh connection; never writes source tables or ACK. */
public final class OracleTargetBatchWriter {
    public record Target(String owner,String prefix,String targetId) {
        public Target { identifier(owner);identifier(prefix);if (targetId==null || targetId.isBlank()) throw new IllegalArgumentException("MISSING_TARGET_ID"); }
        String table(String suffix) {return owner+"."+prefix+"_"+suffix;}
    }
    public record CommitReceipt(String targetId,String batchSha256,String schemaSha256,int events,String schemaSequenceSha256) {
        public CommitReceipt(String targetId,String batchSha256,String schemaSha256,int events){this(targetId,batchSha256,schemaSha256,events,null);}
    }
    public record Result(CommitReceipt receipt,int newApplications) {}

    public static String sourceBinding(OracleBatchProjection.Batch batch) {
        var s=batch.source();return s.dbid()+":"+s.activationId()+":"+s.compatibility()+":"+s.thread()+":"+s.resetlogsId()
                +":"+s.resetlogsScn()+":"+s.containerUid()+":"+s.containerId();
    }
    /** One Oracle statement snapshot on an independent connection; no DML, locks, commit or ACK. */
    public CommitReceipt verifyCommitted(Connection freshConnection,Target target,OracleBatchProjection.Batch batch) throws SQLException {
        try (var conn=freshConnection) {
            if (!conn.getAutoCommit()) throw new SQLException("ORACLE_TARGET_REQUIRES_FRESH_AUTOCOMMIT_CONNECTION");
            var expected=receipt(target,batch);
            String sql="select i.TARGET_ID,i.SOURCE_BINDING,i.SCHEMA_SHA,c.TARGET_ID,c.SCHEMA_SHA,c.EVENTS_COUNT,"
                    +"m.ORDINAL,m.EVENT_ID,a.PAYLOAD_SHA,a.BEFORE_IMAGE,a.AFTER_IMAGE,a.OPERATION from "+target.table("IDENT")+" i"
                    +" left join "+target.table("COMMITS")+" c on c.BATCH_SHA=?"
                    +" left join "+target.table("MEMBERS")+" m on m.BATCH_SHA=c.BATCH_SHA"
                    +" left join "+target.table("APPLIED")+" a on a.EVENT_ID=m.EVENT_ID where i.ID=1 order by m.ORDINAL";
            try (var ps=conn.prepareStatement(sql)) {
                ps.setString(1,expected.batchSha256());
                try (var rs=ps.executeQuery()) {
                    int count=0;
                    while (rs.next()) {
                        if (!target.targetId().equals(rs.getString(1)) || !sourceBinding(batch).equals(rs.getString(2))
                                || !expected.schemaSha256().equals(rs.getString(3))) throw new SQLException("ORACLE_TARGET_IDENTITY_MISMATCH");
                        if (rs.getString(4)==null) throw new SQLException("ORACLE_TARGET_ACK_WITHOUT_COMMIT");
                        if (!target.targetId().equals(rs.getString(4)) || !commitSchemaBinding(expected).equals(rs.getString(5))
                                || rs.getInt(6)!=expected.events()) throw new SQLException("ORACLE_TARGET_COMMIT_CONFLICT");
                        if (expected.events()==0) {
                            if (count!=0 || rs.getObject(7)!=null || rs.getString(8)!=null) throw new SQLException("ORACLE_TARGET_BATCH_MEMBERS_CONFLICT");
                            count++;
                        } else {
                            if (count>=expected.events() || rs.getObject(7)==null || rs.getInt(7)!=count
                                    || !batch.entries().get(count).envelope().eventId().equals(rs.getString(8)))
                                throw new SQLException("ORACLE_TARGET_BATCH_MEMBERS_CONFLICT");
                            var entry=batch.entries().get(count++);var event=entry.envelope();
                            if (!entry.payloadSha256().equals(rs.getString(9)) || !image(event.before()).equals(empty(rs.getString(10)))
                                    || !image(event.after()).equals(empty(rs.getString(11))) || !event.operation().name().equals(rs.getString(12)))
                                throw new SQLException("ORACLE_TARGET_EVENT_CONFLICT");
                        }
                    }
                    if (count!=Math.max(1,expected.events())) throw new SQLException("ORACLE_TARGET_BATCH_MEMBERS_INCOMPLETE");
                }
            }
            return expected;
        }
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
                var receipt=receipt(target,batch);
                boolean committed=false;
                try (var ps=conn.prepareStatement("select TARGET_ID,SCHEMA_SHA,EVENTS_COUNT from "+target.table("COMMITS")+" where BATCH_SHA=?")) {
                    ps.setString(1,receipt.batchSha256());try (var rs=ps.executeQuery()) {
                        if (rs.next()) {
                            if (!receipt.targetId().equals(rs.getString(1)) || !commitSchemaBinding(receipt).equals(rs.getString(2))
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
                        applyRow(conn,target,e,targetColumns(batch));
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
                    ps.setString(1,receipt.batchSha256());ps.setString(2,receipt.targetId());ps.setString(3,commitSchemaBinding(receipt));ps.setInt(4,receipt.events());ps.executeUpdate();
                }
                conn.commit();return new Result(receipt,applied);
            } catch (SQLException | RuntimeException e) {
                try {conn.rollback();} catch (SQLException rollback) {e.addSuppressed(rollback);}
                throw e;
            }
        }
    }
    private static CommitReceipt receipt(Target target,OracleBatchProjection.Batch batch) {
        return new CommitReceipt(target.targetId(),batch.inputBatchSha256(),batch.schema().sha256(),batch.entries().size(),batch.evidence().schemaSequenceSha256());
    }
    // COMMITS.SCHEMA_SHA stores the commit's schema binding; IDENT retains the acquisition baseline SHA.
    private static String commitSchemaBinding(CommitReceipt receipt) {
        return receipt.schemaSequenceSha256()==null?receipt.schemaSha256():receipt.schemaSequenceSha256();
    }
    static List<OracleBatchEvidence.Column> targetColumns(OracleBatchProjection.Batch batch)throws SQLException {
        if(batch.evidence().schemaSequenceSha256()==null)return List.of();
        var columns=new LinkedHashMap<String,OracleBatchEvidence.Column>();
        for(var c:batch.evidence().columns())columns.put(c.name(),c);
        for(var schema:batch.evidence().eventSchemas())for(var c:schema.columns()) {
            var old=columns.putIfAbsent(c.name(),c);
            if(old!=null && !old.equals(c))throw new SQLException("ORACLE_TARGET_HISTORICAL_COLUMN_IDENTITY_CHANGED");
        }
        return List.copyOf(columns.values());
    }
    static Map<String,Object> targetImage(Map<String,Object> logical,List<OracleBatchEvidence.Column> columns)throws SQLException {
        if(logical==null)return Map.of();
        if(columns.isEmpty())return logical;
        var mapped=new LinkedHashMap<String,Object>();
        for(var c:columns) {
            if(!logical.containsKey(c.name()) && !c.nullable())throw new SQLException("ORACLE_TARGET_HISTORICAL_REQUIRED_COLUMN_ABSENT");
            mapped.put(c.name(),logical.get(c.name()));
        }
        if(!mapped.keySet().containsAll(logical.keySet()))throw new SQLException("ORACLE_TARGET_HISTORICAL_COLUMN_UNMAPPED");
        return mapped;
    }
    private static void applyRow(Connection conn,Target target,EventEnvelope event,List<OracleBatchEvidence.Column> columns) throws SQLException {
        Map<String,Object> before=targetImage(event.before(),columns);
        Map<String,Object> after=targetImage(event.after(),columns);var args=new ArrayList<Object>();String sql;
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
            for (int i=0;i<args.size();i++) {
                if(args.get(i) instanceof OracleRawValue raw)ps.setBytes(i+1,raw.bytes());
                else ps.setObject(i+1,args.get(i));
            }
            if (ps.executeUpdate()!=1) throw new SQLException("ORACLE_TARGET_ROW_MATCH_NOT_ONE");
        }
    }
    private static String image(Map<String,Object> values) {
        if (values==null) return "";
        var fields=new ArrayList<String>();for (var e:values.entrySet()) {
            Object v=e.getValue();String value=v==null ? "NULL" : v instanceof BigDecimal n ? "NUMBER:"+n.toPlainString()
                    : v instanceof OracleRawValue raw ? "RAW:"+raw.hex()
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
