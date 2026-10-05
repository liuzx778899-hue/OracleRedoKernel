# Oracle 目标提交凭证 v1（有界接口）

入口 OracleTargetBatchWriter.apply(freshConnection, Target, OracleBatchProjection.Batch, requireExistingCommit)。Batch 仅由已验证 capture 的严格整批投影创建。调用者提供全新、autoCommit=true 的专属目标连接；writer 拥有并关闭连接，不支持共享连接或调用者既有事务。Target 的 owner/prefix/targetId 受明确校验，SQL 标识符仅支持大写 ASCII，最多24字符。

目标命名空间包含五表：IDENT（ID=1、TARGET_ID、SOURCE_BINDING、SCHEMA_SHA），ROWS（业务列），APPLIED（EVENT_ID 主键、PAYLOAD_SHA、BEFORE_IMAGE、AFTER_IMAGE、OPERATION），MEMBERS（batch/ordinal 主键、batch/event 唯一、event 外键），COMMITS（batch 主键、target/schema/eventCount）。本轮实验审计像使用 VARCHAR2(4000)，不截断、不由 writer 自动建表。

SOURCE_BINDING 绑定 DBID、activation、compatibility、thread、resetlogs time/SCN、PDB UID/container ID 八项十进制身份。IDENT 用 FOR UPDATE NOWAIT 独占；目标/source/schema 必须一致。该协议不认证恶意管理员复制的控制表。

新事件执行完整 before CAS，匹配数必须为1。DML、全局事件审计、批内有序成员及提交凭证同一事务提交后返回 CommitReceipt；newApplications 是本次统计，不参与 ACK 身份。旧批次须核对凭证全部字段、每个事件内容及每个成员次序，不能只看最终行。代码区分全局事件与批成员；同redo更换观察元数据的不同批次，以及原始40—41/40—42两旧一新闭合窗口重叠已有限实证（见 W6 overlap 交付）；任意窗口活跃状态未验收。当前实际验证三条 UPDATE；通用 INSERT/DELETE、扩展类型和大值待证。

消费 ACK 只在提交凭证返回后写入：UTF-8、LF 单行 ORK_ORACLE_TARGET_ACK_V1|targetID|batchSHA|schemaSHA|events，末尾换行。实验消费者使用临时文件 force、原子替换、目录 force。已有 ACK 必须逐字节一致；requireExistingCommit=true 而目标无凭证时拒绝 ORACLE_TARGET_ACK_WITHOUT_COMMIT，不执行 DML 掩盖超前 ACK。

提交调用异常不得写 ACK；恢复需重新查询目标凭证。当前证据为客户端 SIGKILL，不包括提交回包丢失、数据库进程崩溃或断电。消费 ACK 不是 capture next/原始偏移或允许删除原始日志的凭据。公开 decoder/events 继续拒绝；不伪造 SQL Server LSN、GUID 或 commitAt。完整产品窗口、可信历史字典及 checkpoint/保留策略待验收。
