# W4 原始归档自主事务发现与补偿头路由交付

结论：对象范围由调用者明确给定，事务身份由内核从原始 redo 自主枚举；冻结 seq22、23、24 各发现基线/A/B 三笔完整 XID，原始节点与窗口补偿头关联独立 native 对账零差异。只验收这一有界入口，不验收完整物理分发、逻辑 DML、SCN 字典、接入或发布。

## 源码、构建与证据

- 内核/Java probe 封存源码：`e7f231476888722297fa42885632fac3ffddd901`；独立 native 比较器源码：`0d216bc9bea12024d390fb796a8e9aa9032ba088`。
- Java17 独立提交树 `clean verify`：198 项、0 失败、0 错误、0 跳过。没有为本轮重复已通过的旧 fixture 运行。
- 制品：`lab/out/release-object-discovery-r2/tree/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar`；SHA256 `39b202bd58111fa00ff26180268391f0e51d2bc9c42adc3e9a1ca7d78863e546`。
- [证据清单](evidence/DELIVERY-object-discovery-20261004.json)：1118 文件双次 SHA 重算，绑定首次失败构建/实际拒绝、最终构建、三批 raw/dump、源码、命令、rc 与输出；大型原始制品留本地。
- 本轮没有数据库动作；复用冻结输入，没有重放源 DDL/DML。

## 实现与实际验证

`ArchivedObjectTransactions.read` 自主枚举所选对象原始 undo 的完整 XID，取得同源完整窗口及补偿头关联，审计所选事务的全部原始 undo 都在声明范围，且目标补偿都有已发现的完整事务归属；读取前后重新检查源摘要。没有调用者事务号列表、SQL truth 或 dump 作为生产输入。

| 输入 | 自主发现完整事务 | 原始 undo | 窗口关联项 | native 差异 |
|---|---:|---:|---:|---:|
| seq22：较大键 INSERT/PURGE | 3 | 10 | 6 | 0 |
| seq23：两次 INSERT/PURGE | 3 | 12 | 10 | 0 |
| seq24：普通 DELETE/RESTORE | 3 | 10 | 6 | 0 |

窗口关联包含 A 窗口内 B 的外国 marker，所以表中关联项不能当独立取消数；合计 9 笔事务、32 原始 undo、22 窗口关联项，只表示头层验证。比较器先从 native 对象字段独立发现所有事务，再读完整 native 身份/起止/原始头和关联，最后与 Java 输出比较，未以 Java 事务列表选择真值。

六项单测覆盖自主枚举/不可变结果、缺事务结束、无原始节点补偿、object/data-object 不一致、空范围，以及可读取对象字段的范围外未知 undo profile。seq24 派生漏基线、错误完整 XID、漏关联三项比较器反例均 rc1；缺 heap 的索引范围实测 rc1/stdout0，原因 `OBJECT_TRANSACTION_UNDO_OUTSIDE_SCOPE`。这证明有限反例拒绝，不替代实际物理分发。

## 保全的失败与修复

第一件源码 `4f85fc3` 的 seq24 入口 rc1/stdout0：`UNSUPPORTED_UNDO_HEADER_FLAGS`。当时在选择对象前完整解码同 PDB 所有 undo。修复为先检查直接 object/data-object 字段可读、再对范围内 undo 完整解码；所选事务包含范围外原始 undo 仍拒绝，没有扩充未知 flags 支持。

另一个含 object1 的拒绝探针遇到 `UNSUPPORTED_UNDO_HEADER_FLAGS`，只能证明未知所选 profile 拒绝，不计 object 映射反例。后续缺 heap 探针实际上得到 `OBJECT_TRANSACTION_UNDO_OUTSIDE_SCOPE`；首轮封存脚本错误预期 `OBJECT_TRANSACTION_MAPPING_MISMATCH` 而断言失败，原始 rc/stdout/stderr 与脚本保留，仅核对已有结果并补执行未执行的 events/封存，不重复探针。

## 未验收与下一步

- `events` 同件制品仍 rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，CDC 事件 0。
- 对象参数不是 SCN 字典证明；unknown profile、跨范围事务、来源中缺起止仍保守拒绝；只有三个冻结归档对象组的实际验证。
- 当前返回 header 层结果，不驱动物理 pair/head/cancel，不证明所有相关 opcode/页维护/恢复，也没有完整行与业务语义事件输出。
- 接续 [通用归档分发派单](DISPATCH_W4_ARCHIVED_ROUTER_20261004.md)：在本入口完整事务集合上接入同源物理操作流、基线与 A/B 完整有序图，缺分支/错 owner/失败批次无部分输出；再推进 W5 字典/基线。持续 capture/续接/ACK、StreamHouse 与现场发布仍独立待证。
