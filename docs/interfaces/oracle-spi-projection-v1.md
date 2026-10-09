# Oracle SPI 有界转换契约 V1

坐标`com.streamhouse:streamhouse-engine-oracle:0.1.0-SNAPSHOT`，独立模块位于`integration/streamhouse-engine-oracle`，Java17。只依赖产品SPI和Oracle内核二进制；根内核没有新增数据库、SPI、Spring或Flink依赖。SNAPSHOT须附完整提交树和三jar SHA，不靠坐标名称认定同件。

## 可用与拒绝入口

`ServiceLoader<EngineConnector>`可发现`ORACLE`。当前`windowScanner()`抛`ORACLE_CAPTURE_WINDOW_CONTRACT_NOT_ACCEPTED`；`decoder().decode(...)`返回非零rc、空iterator、eventsPath=null、`ORACLE_DICTIONARY_PUBLICATION_GATES_NOT_ACCEPTED`。不存在authorize/mode配置绕过。这是待验收能力的明确拒绝，不是公共decoder或产品流水线通过。

可用的限定接入方法：

```java
CapturedHeapBatch input = CapturedHeapBatch.read(captures, baseline, trustedSha256, leaf);
OracleBatchProjection.Batch projection = OracleBatchProjection.project(input);
// 所有事件的完整before/after严格解值都成功后，才返回完整不可变entries。
```

输入必须来自内核完整batch工厂，不接探针文本、native dump或SQL truth。不边遍历边调用目标写入；后面任意非法值必须使整个方法失败，没有部分返回值。

## 事件和值

NUMBER调用内核严格NUMBER解值，返回BigDecimal；AL32UTF8 VARCHAR2调用严格UTF8 decoder。NULL保留为Map中存在且值为null；NOT NULL列的NULL拒绝，缺列或未知类型拒绝。隐藏列仍保留。before/after map和entries不可变，保留physical列顺序，INSERT/DELETE的不存在像为空map。

RAW扩展候选：schema列类型为`RAW`，非NULL值为不可变`OracleRawValue`，JSON形如`{"hex":"00ffc328"}`，使用非空、小写、偶数长度hex保存精确字节；不按字符集解释，不与VARCHAR2混用。`bytes()`每次返回独立数组。JDBC目标以`setBytes`绑定，目标事件像记为`RAW:00ffc328`；NULL仍为显式null。可信基线查询直接`rawtohex(RAW列)`，历史COL$要求TYPE#=23。专属真实冻结95的I/U/D、NULL与回滚完整值已经核等；有限产品目标4/4/1、实际CP/ACK全文与producer状态核等，0差异。早期实际Acquirer记录采用实验导入，不当正式HTTP获取流程通过；同实例目标不当独立目标验收，不据有限样本宣称D4整体验收。详见DELIVERY_D4_RAW_CANDIDATE_20261009.md。

`Entry.envelope`是实际`com.streamhouse.spi.EventEnvelope`。eventId为`oracle1:<kernel eventId>`，不伪装SQLServer v2身份；metadata.schemaVersion绑定内核schema摘要，captureInput为`oracle-batch-sha256:<input sha>`，两个完整性标记为true。changedColumns按完整raw cell差异及physical顺序列出，INSERT/DELETE列出存在像列。transactionId使用`oracle-xid:<usn>.<slot>.<sequence>`，transaction.sequence是这份已核有界scope中该XID的1-based DML序号，不宣称缺省知道源端全库事务序号。

`Entry`另保留原payload摘要、pairedUndoPosition及完整ClosedIdentity；`Batch`另保留Oracle source身份和完整schema。不能丢弃这些sidecar，仅凭通用envelope推进Oracle位置/目标ACK。

现行SPI的beginLsn/recordLsn/commitLsn要求SQLServer语义，因此Oracle转换中三者为null；起止record SCN和配对undo RBA在类型化sidecar，不编造commit SCN/墙钟commitAt。未知source.server/database/databaseGuid/recoveryFork均null，DBID/resetlogs/PDB身份在Batch.source，不将DBID转换成GUID。

## 产品接续

这份转换不是SQLServer V2 durable writer输入：该writer要求v2身份、完整source和SQLServer commitLsn。ODS要求commitAt的路径也不得用当前时间补造后放行。产品输入window/Oracle位置扩展、可信schema授权及Oracle目标writer/同事务commit receipt必须独立冻结和验收，之后才能接公开decoder成功路径。

本轮真实binary互通为：冻结raw capture→同件Oracle内核→独立转换模块→实际SPI类型。SPI contract jar从产品完整提交树只读编译，不是产品完整reactor发布制品；未部署产品连接器，没有目标写入/ACK，也没有现场或发布通过。
