# Oracle Direct Redo：零侵入解析优先执行方案

日期：2026-10-04。状态：执行中，功能/部署/现场验收/发布未完成。用户附件是研究输入；其中建议、测试矩阵和许可表述不自动成为已验证事实或额外授权。

## 1. 决策与零侵入边界

沿用 SqlserverNoCdc 的原始字节、独立尺子、反例、FAIL CLOSED 与分阶段交付方法。Oracle 内核独立研发；StreamHouse 只消费二进制和公开 API，FlinkETL 只消费事件。

严格路线的源库要求：零触发器、零业务表/索引改动、零 CDC 开关、零 supplemental logging 变更、零 FORCE LOGGING 变更、零 ARCHIVELOG 变更、零数据库重启。允许获授权的只读元数据/一致性快照与 OS 文件读取；代理安装、磁盘/网络开销和文件读取权限必须单独说明，不能称为“零资源成本”。如果用户要求连只读 SQL 也为零，则进入文件/备份字典路线，不能自动沿用元数据方案。

这是一条**待验证的条件性可行路线**，不是“所有 Oracle、任意起点、所有表立即完整 CDC”的承诺。普通恢复日志记录物理变化，不保证每条 UPDATE 都包含完整主键/未改列。没有基线、完整日志链或可证明的 undo 关联时，拒绝完整行事件。

两种支持口径分开：

1. **严格零配置**：当前既有 redo + 既有字典/快照/备份。无 supplemental 样本是主攻对象；缺信息必须停发。
2. **既有补充日志条件**：部署前已启用，记录环境条件，不把此成绩换算为“关闭补充日志也支持”。不得为使测试通过而静默修改源库。

只读查询获取“当前字典”不能证明“过去 SCN 字典”；读取当前行补齐历史 before/after 也不能证明历史事件。字典和行基线必须有 SCN、container、对象世代、来源身份及边界记录。

## 2. 已核实环境和第一选择

仓库 `liuzx778899-hue/OracleRedoKernel` 为 PRIVATE，开工时为空仓库。本地 JDK17 可用，默认 Java 是8，构建显式覆盖。

共享两套 Oracle 21.3 XE：一个 ARCHIVELOG/MIN=YES/ALL=YES，另一个 NOARCHIVELOG/MIN=NO/ALL=NO；均为 Linux x86_64、512字节 redo、CDB/AL32UTF8。两套 DBID 相同，不能用 DBID 做唯一租户身份。共享容器只读取证和 dump，未做业务 DML/配置变更。

本机缓存了 Oracle 19.3.0.0 企业版镜像，专属 `oraredo-19c-lab-20261004` 用于受控实验，不暴露宿主端口。初始无 supplemental；只在该专属容器启用归档以冻结样本。此实验配置不构成生产零配置证明。19c 与21c分开验收；19.3不能代表所有19c RU。

## 3. 证据路线

```text
源库/已存在 standby
  原始 archived / online redo
        ↓ 采集端只抄录，不解析、不查询 LogMiner
  冻结字节 + source binding + capture manifest（后续 .redocap）
        ↓ 内容 SHA / 连续性 / 世代 / 权限核验
OracleRedoKernel
  文件 → 块 → 跨块 record → change vector → undo/row
  SCN 字典 + 行基线 → 事务/回滚 → COMMIT 发布门
        ↓ 完整且已验证的事件
StreamHouse engine-oracle（二进制消费）
        ↓ source gate / 事件账 / 目标幂等 / durable ACK
FlinkETL / Kafka / API / 目标库
```

独立验证链为 SQL 操作真值、DUMP(value) 类型字节、Oracle diagnostic redo dump、可用条件下的 LogMiner。它们只在 `lab/` 使用，不被 `src/main/` 导入或读取。无补充日志时 LogMiner 结果若 UNSUPPORTED/缺键/SQL不完整，保持原文，不将其空输出当作真值成功。

已有21c归档抽取是探索样本，包含未知既有工作负载，原文件和 dump 仅留本地；不上传 Git，不从它推导测试业务已支持。自有19c样本使用固定 ORKLAB 测试 schema。

## 4. 解析层边界与实现顺序

| 层 | 输入/输出 | 判据 | 拒绝情形 |
|---|---|---|---|
| 文件 | 原始归档 → DBID/activation/resetlogs/thread/seq/SCN | dump 与字节身份、长度一致 | 格式/端序/块大小未知、NAB不符 |
| 块 | 512字节 → 校验后的 payload | 块号/sequence完整，checksum核实 | 缺块、重排、错世代、截断、checksum错 |
| record | 去块头重组 → RBA/LEN/VLD/SCN/container | 跨块且与dump选定范围逐条一致 | 长度越界、未知header形态 |
| vector | 字段表 → opcode/DBA/字段raw | 字段长度和对齐闭合 | 越界、未知结构；结构输出不等于语义支持 |
| row/undo | KDO/KTU → 操作候选及列状态 | redo/undo/对象/事务明确绑定 | row migration/chaining/多行/压缩未证明 |
| dictionary | 物理列号 → SCN版列名/类型 | 对象世代、CON_UID与SCN匹配 | DDL漂移、TRUNCATE/对象号复用 |
| transaction | XID+undo链 → 已提交动作 | commit、全/部分rollback逐项对账 | 无begin、缺片、未闭合、回滚无法定位 |
| event | 已提交动作 → envelope | 键、列完整性、顺序与身份全通过 | 缺键/缺列/未知类型/跨来源 |

首版仅接受实测 Linux little-endian、512字节归档、compatibility 19/21 的文件结构；这不是19/21全部功能兼容声明。4096字节、big-endian、online活跃文件、RAC、ASM、TDE必须独立样本与门禁。16MiB 单record上限超出则显式拒绝，后续用真实大record证据调整。

checksum 用于检测偶然损坏，不是安全认证。归档SHA绑定到受控 manifest；攻击者同步改字节和SHA、或合法checksum的错误值不能靠checksum发现。发布仍依赖可信来源、语义对账与完整事务。

## 5. 无补充日志的关键论证实验

| 问题 | 必做实验 | 通过条件/失败处置 |
|---|---|---|
| 未改主键不在UPDATE中 | 只改TXT/长变短/NULL，主键不改 | undo或SCN基线提供确切键；否则 KEY_NOT_PROVABLE |
| BEFORE全行 | 只改单列、多次同txn更新 | 从正确历史基线/undo复原；ABSENT不当NULL |
| DELETE值与行定位 | 删除既有基线行、插后删、slot复用 | object世代+block/slot+txn闭合，禁止凭相邻配对 |
| 逻辑UPDATE vs D/I | 多行UPDATE、显式D/I、MERGE、PK更新 | 区分原操作；不依据最后行相等或唯一配对推断 |
| rollback/savepoint | rollback、rollback to、嵌套多次savepoint | 仅保留真正提交的动作，不能将补偿redo当用户INSERT |
| NOLOGGING | 专属环境 direct-path nologging 与 conventional DML | 已知目标数据不可恢复则拒绝，不默设FORCE LOGGING |
| 初始状态 | 从空表/事务begin前/日志中途/有历史行四种起点 | 缺begin/缺基线停止；不靠当前查询伪造历史 |
| 字典世代 | ADD/DROP、TRUNCATE、drop/recreate、不同PDB同名 | 缺SCN映射时停止，不用当前类型追译旧列 |

列值至少四态：VALUE、NULL、ABSENT、UNSUPPORTED。Oracle 空字符串的 SQL 语义按实际 Oracle 真值验证；不额外制造与 NULL 不同的源值。NUMBER 用精确十进制，禁止 double；日期不偷偷应用宿主时区；VARCHAR2 按数据库字符集严格解码，非法UTF8拒绝。

## 6. 事务、重启与水位设计（未实现）

身份包含 sourceId（部署绑定）、DBID、DB_UNIQUE_NAME/实例绑定、activation、resetlogs SCN/ID、container UID、redo thread。克隆库即使DBID相同也禁止混流。

每thread按sequence/RBA读取，SCN不代替日志连续性。事务键包含来源世代、container、XID USN/slot/seq；缓存动作绑定undo地址与回滚链。COMMIT前只缓存，ROLLBACK丢弃，partial rollback取消确切动作；跨switch保留缓存。没有足够证据时拒绝该窗口所有发布，不能先发前半段再失败。

checkpoint同时保存读取位置、最早未闭合事务LWM、字典/基线版本、capture持久化水位、已发布commit水位、目标ACK。恢复从LWM读取；读取ACK、中央持久化ACK、目标应用ACK是三种状态，不合并。最终应用exactly-once需目标幂等/eventId与持久化事务，文件读成功不证明不重。

RAC需每thread的完整性及merge安全水位；不能简单按当前已看到的COMMIT SCN排序后发布，因为慢thread可能还有更早commit。RAC独立后续gate。

## 7. `.redocap` 与集中控制面契约草案

此为待冻结接口，不先实现与解析无关的控制面。

manifest字段：formatVersion、sourceId、captureId、epoch、DBID、activation、resetlogsId/resetlogsScn、container范围、thread、sequence、first/next SCN（十进制字符串）、redoBlockSize、originPath摘要、fileLength、sha256、chunkIndex/offset/length/checksum、issueSeq、captureTime、frozen、droppedSegments、agentVersion。完整归档与online片段使用不同mode，不把未闭合online尾部当归档。

写入顺序：chunk落盘/fsync→manifest/hash→原子冻结标记→上传→中央校验→持久化ACK。重复同identity同SHA幂等；同identity不同SHA拒绝并报警。缺块/缺sequence不可通过手工ACK跳过；源文件覆盖前未持久化的缺口记为数据风险。

集中端建议登记 source_binding、capture_window、redo_file、decode_run、dictionary_epoch、transaction_checkpoint、event_delivery、apply_ack、quarantine。复用StreamHouse现成注册/任务/审计设施时先核实际表/接口，不能仅按本文名称建重复表。SCN与雪花ID向前端一律字符串。

## 8. StreamHouse/FlinkETL落地界面

当前已只读核实StreamHouse SPI：`CapDecoder.DecodeResult` 非零rc必须空事件，`eventsPath` 未产出为null；`EventEnvelope` 有source/table/operation/transaction/before/after/changedColumns/完整性元数据，transaction位置字段当前为 LSN命名。

Oracle位置编码用明确版本化字符串，不将SCN伪装为SQL Server LSN做数值比较。产品 adapter 必须验证下游对beginLsn/recordLsn/commitLsn是否存在引擎假设，并以公开接口单提出必要契约修改。DDL目前只有预留说明，不通过DML枚举偷发DDL。

交付坐标拟 `com.streamhouse.oracle:oracle-redo-kernel:<immutable-version>`。engine-oracle只引用此jar与API，不复制解析源码/测试样本/实验过程；JNI/ASM等无基线需求暂不引入。FlinkETL保存/试跑/消费/目标应用链另验，不因HTTP200或成功生成事件文件判全链通过。

## 9. 可执行工作包

负责人：本chat Codex（用户授权自主推进）。高风险独立评审必须另有独立证据，会话作者测试不充当独立评审。没有派遣其他agent的授权，不自行多agent派工。

| 包 | 优先级/依赖 | 交付 | 必需门禁 |
|---|---|---|---|
| ORA-W0 | P0，无 | 环境、fixture、truth、hash、矩阵 | 环境差异/0比较拒绝/失败日志 |
| ORA-W1 | P0，W0 | 文件/块scanner | dump身份/截断/篡改/错sequence |
| ORA-W2 | P0，W1 | record/vector skeleton | 跨块RBA/LEN/SCN/opcode逐条对账 |
| ORA-W3 | P0，W2 | heap单行+NUMBER/VARCHAR2/DATE | I/U/D逐列及缺键/缺值拒绝 |
| ORA-W4 | P0，W3 | COMMIT/ROLLBACK/savepoint/跨switch | 双会话、重启、缺日志、补偿不泄漏 |
| ORA-W5 | P0，W3 | SCN版字典+基线 | DDL漂移、对象复用、PDB隔离 |
| ORA-W6 | P1，W3-W5验收 | engine-oracle契约与二进制接入 | 真源→解析→事件账→目标及rc非零零发布 |
| ORA-W7 | P1，前置真实archive闭环 | online capture/C++ agent | switch/reuse/断网/崩溃/迟到archive补链 |
| ORA-W8 | P1，W3-W5 | 标量扩展 | 类型逐值/极值/非法编码拒绝 |
| ORA-W9 | P2，W4/W8 | BasicFile/SecureFile LOB | chunk闭合/byte hash/rollback/缺chunk拒绝 |
| ORA-W10 | P2，W4/W7 | RAC | 多thread完整性、迟到commit、failover |
| ORA-W11 | P2，单项排期 | ASM/TDE/IOT/压缩/分区复杂形态 | 独立兼容矩阵；未知必须拒绝 |

不能以安全拒绝通过升级W3功能支持，也不能以W3测试通过跳过W4/W5。解析瓶颈优先于控制面/GUI研发。

## 10. 验收矩阵和证据格式

T001单行INSERT、T002单列UPDATE、T003DELETE、T004多DML、T005两会话、T006ROLLBACK、T007SAVEPOINT、T008跨switch、T009缺archive、T010篡改、T011DDL漂移、T012中文/NULL/空串。再追加无补充日志缺键、逻辑UPDATE与D/I、NOLOGGING、世代/克隆/PDB冲突、断尾与崩溃恢复，不删原case。

每case：SQL脚本/操作步骤、开始结束SCN、source identity、配置（logging条件）、raw redo、SHA256、dump、LogMiner原文及局限、parser层输出、独立oracle、stdout/stderr/rc、事件数/事务数、字段比较数、verdict。原样本永久冻结；修复对同一输入重跑并追加新目录/文件。

六列：代码交付｜针对性验证｜集成验证｜部署｜现场验收｜发布；每列未开始/进行中/通过/失败/待证/不适用（附理由）。空事件对账/零比较/UNKNOWN不能PASS。GA需真实capture/恢复/负载/告警/安全包门禁；本轮不预报时间或覆盖百分比。

## 11. 资料与来源边界

2026-10-04已查阅以下一手资料。Oracle手册没有公开完整binary redo格式，不能把手册能力描述当作格式实现证明；格式来自本地独立字节/dump实验。

- [Oracle 19c LogMiner](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-logminer-utility.html)：官方要求minimum supplemental，19c continuous_mine取消；本文只用它作条件性验证尺子。
- [Oracle redo管理](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-the-redo-log.html)：redo包含恢复用的change vectors，读取路径与运行模式须分别验证。
- [V$ARCHIVED_LOG](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-ARCHIVED_LOG.html)：sequence/thread/resetlogs/SCN；同一日志可多条登记，不能把RECID连续等同日志连续。
- [V$LOGMNR_CONTENTS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-LOGMNR_CONTENTS.html)：XID、操作、MISSING_SCN、COMMIT_SCN及完整性字段，顶层操作和内部opcode不是同一层。
- [OpenLogReplicator架构文档](https://github.com/bersler/OpenLogReplicator/blob/master/documentation/user-manual/2.architecture-and-components.adoc)：参考架构方向；未复制源码。
- [oracdc RedoMiner](https://oracdc.com/docs/connectors/redominer/)：直接redo的工程参考；其公开能力不证明自研已支持，未复制源码。

来源登记：首轮自研代码来自本地 archive3064 的二进制结构和 Oracle21.3 dump；字段偏移与格式支持是有界观察。附件全流程结构经本次环境与SPI核实调整，后续19c证据追加。
