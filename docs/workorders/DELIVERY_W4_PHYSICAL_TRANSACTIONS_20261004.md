# W4 自主发现事务的有界物理分发交付

结论：新内核入口不用预填 A/B XID，也不读取 SQL/dump 作为生产输入；冻结 seq21—24 各自主发现基线、A、B，并关闭全部物理分配/补偿图。12 笔图与独立 native 完整事务发现及有序图重建零差异。只验收已证实布局的有界物理入口，历史字典、完整逻辑 DML、实时恢复、接入与发布仍未验收。

## 交付与绑定

- 运行时完整提交树：`6dd1772e22daea6195709ebf02f1f93fb56c1060`；最终 lab 比较器/派生器树：`eafdb16ff05ace383e6edb52efa75088669b40f8`，二者 main/test/pom 差异为空。
- Java17 独立提交树 `clean verify`：203 项，0 失败/错误/跳过。
- 制品：`lab/out/release-physical-transactions-r2/tree/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar`，SHA256 `5f74b32874c2ad5dd9dbf16bc53265ad927db77eb2d72cca4a73465fce6d997b`。
- [证据清单](evidence/DELIVERY-physical-transactions-20261004.json)：1316 文件双次 SHA 重算，含首次构建失败、native 比较器失败、错误派生器实际成功输出及断言失败、修复后日志与冻结输入；大型字节/dump 保留本地。
- 没有数据库动作，未重放冻结 DDL/DML。新入口实测复用旧原始输入；没有重跑旧 lab 消费者图验收。

## 内核行为

`ArchivedPhysicalTransactions.read(paths,uid,container,heapObject,indexObject,leaf,columns)` 先自主发现完整 XID、原始覆盖与完整窗口，再读取同源叶页位置模型。第二遍按原始位置分发每个节点，核实际 heap/index forward pair、活跃身份及有序 undo head；所有窗口完整消费外国 marker，只有唯一真正 owner 做物理 match 与 head 取消。提交核原始分配尾，整事务回滚核完整身份及无 live head。

原始 inverse 字段建立所选 heap block 范围，index inverse 必须指向声明 leaf。窗口中这些物理块的每个操作必须有实际配对，未知页操作拒绝；所选事务段/undo block 的未知控制、非 begin 5.2 也拒绝。原始项、窗口候选、关联、生命周期与输入 SHA 都闭合后才返回整批不可变结果；内核没有 stdout 或 CDC 发布行为。lab probe 只在静态入口完整返回后打印。

本入口没有可传入的事务/node/marker 子集或可恢复的半失败消费者，配对与历史状态都是每次读取的私有上下文。没有增加数据库/JDBC/Flink/Spring依赖。对象、leaf、列数是调用者声明的物理范围，不能代替 SCN 历史字典；结果不包含逻辑行事件。

## 独立真实对账

| 归档 | 完整事务数 | 原始节点 | 取消 | 保留 | native 差异 |
|---|---:|---:|---:|---:|---:|
| seq21：较小键 INSERT/PURGE | 3 | 10 | 4 | 6 | 0 |
| seq22：较大键 INSERT/PURGE | 3 | 10 | 4 | 6 | 0 |
| seq23：两次 INSERT/PURGE | 3 | 12 | 6 | 6 | 0 |
| seq24：普通 DELETE/RESTORE | 3 | 10 | 4 | 6 | 0 |
| 合计 | 12 | 42 | 18 | 24 | 0 |

每批基线是已提交的 6 节点图，A/B 是独立关闭的完整回滚图。保留是物理节点数，不能称为业务 INSERT/UPDATE/DELETE 数或完整行数。native 比较器先按 dump 对象字段自行发现全部完整 XID，再逐笔独立绑定 begin/end、原始节点、唯一补偿 owner 与当前 head，最终按事务/节点/取消/live 顺序比较 Java；未从 Java 输出选真值事务。

## 拒绝证据

9 项真实原始字节派生输入均 rc1/stdout0：三个既有重封 index slot/ITL/row-size 反例，以及删除 B 全部原始节点、删除 B begin 节点、删除外国 marker、错误 begin marker 段、真实 A heap slot 改写、重放已关闭 B 的 marker。六项新最终派生输入结构 scan 均通过；拒绝发生在完整身份/关联/清单/物理配对门禁，不能称所有反例都到达 head 取消。

真实 A heap IRP 的 native `slot:1` 对应 KDO offset42。正确改写后的入口在 `MISSING_HEAP_COMPENSATION_ORIGINAL` 拒绝，原始遍历此前已关闭基线/B，整批 stdout 仍为空，未泄漏已关闭事务的结果。

四项独立比较器反例：删除整笔 B、漏外国取消、错完整 owner、追加重复取消，分别检出 5/2/1/1 项差异。新增五项合成测试覆盖全部自主身份、不可变结果、缺 B 结束、错 leaf/PDB、无 forward pair 和未知页维护；合成测试不充当实际 Oracle 通用支持。

## 失败原文保全

1. 首轮 `d35617b` 独立构建：203 测试、2 个错误原因预期失败。实际缺叶页在 `TARGET_INDEX_LEAF_NOT_FOUND_IN_WINDOW`，未知页操作在 `UNSUPPORTED_INDEX_FORWARD_OPCODE` 更早拒绝；只改测试预期后新目录构建。
2. 首轮 native 比较器 `086824a` 的通用 `--objects` 被历史并发 fixture 参数逻辑错误消耗，`ValueError: invalid literal for int() with base 10: '--native-only'`；修复实验比较器，未改内核、未重跑已成功 seq24 入口。
3. 首轮 A heap slot 派生器错用 UPDATE offset20 改 IRP 非 slot 字节，实测 rc0/stdout817 bytes，随后断言失败。该输入及实际成功输出保留，不计拒绝通过，也不能声称未知字节已验证。按 opcode 和 native 字段改为 IRP offset42；新目录生成，只补正确 slot 与未执行的重复 marker/比较器反例/events。

## 接续门禁

`events` 同件制品仍 rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，CDC 事件 0。部署、现场验收与发布不变。

本轮推进了 [W4 内核分发派单](DISPATCH_W4_ARCHIVED_ROUTER_20261004.md) 的有限初始实现；不能把整个派单或通用生产分发标为通过。非 begin 5.2、跨块补偿关联、其他控制、页分裂/维护、多叶页/对象迁移、恢复仍限制或拒绝；输入真实性/捕获完整性仍需 W7，历史映射及行完整性仍需 W5。

下一步结合新入口推进 W5 SCN 字典与零侵入基线/完整行可恢复性，明确无 supplemental 时哪些列缺失、ABSENT 与 NULL、基线时间一致性及主键变化能否独立证明。保持 unsupported 情形拒绝；持续 capture/续接/ACK、StreamHouse 接入与现场/发布继续独立推进，不用物理图冒充全目标完成。
