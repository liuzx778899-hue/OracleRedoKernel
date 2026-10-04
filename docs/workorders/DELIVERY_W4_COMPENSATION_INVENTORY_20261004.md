# W4 归档自主补偿候选覆盖门禁

本轮补上“调用者漏掉补偿却仍关闭 history”的输入选择缺口。`ArchivedCompensationInventory.read`自己完整遍历冻结归档，先取得目标完整XID的5.1清单，再按唯一begin控制及完整segment/slot/sequence结束身份确定活动record窗口。窗口内同一PDB的全部5.6/5.11均纳入候选，未知布局拒绝，不按对象号、低wrap或猜测归属过滤。长事务lab驱动在history.close前要求已匹配marker位置/顺序与此清单完全一致。

源码及完整封存树：952e65d14468bc96100105bdc72fb0647b9a5a57；[证据清单](evidence/DELIVERY-952e65d.json)。Java17从完整Git archive树独立构建，jar SHA256 `be003fdca9c86165caf6c93cf7082445c4453b75b20d9324641eac2e6836a621`。113项测试，失败/错误/跳过均0；构建及完整验证rc0。335个本地证据文件最终SHA256逐件复核。只读取既有seq10..13和旧seq8/9，没有数据库DDL/DML或部署。

| 验证 | 结果 | 判定范围 |
|---|---|---|
| 四批窗口候选的全部位置/DBA/对象/slot/wrap/flags/previous DBA/rci | 每批60，共240；native独立窗口对账零差异 | PASS指定输入的5.6/5.11候选覆盖 |
| 真实候选选择反例 | 每批漏全部、漏一条、反序、重复、外来位置，共20项 | 全部rc1/stdout0，原因`COMPENSATION_INVENTORY_SELECTION_INCOMPLETE_OR_REORDERED` |
| 新门禁接入后的有序history回归 | 490原始节点、240已匹配补偿、250 live heap片段 | native物理及直接字段零差异 |
| rollback后继续写入 | seq13最终两行SQL字节一致 | PASS既有有界场景 |
| seq10完整前驱页佐证 | 15条边继续与独立事后页sequence/CNT一致 | 不证明归档时点快照一致性 |
| 原seq8/9及既有拒绝 | 18pair/4补偿/SQL row7、9history拒绝、10head-update拒绝等同件jar通过 | 回归通过 |
| events | 四批rc1/stdout0 | 继续拒绝发布 |

四个新单测覆盖其他对象候选不被过滤、其他CON_ID及窗口外marker不被纳入、漏/重/乱序/外来选择、不可变结果、缺结束、未知窗口flags和缺begin控制。初版缺begin反例写错字节偏移，113项中1失败；改成实际UBA字段偏移后113项全通过。首轮失败原文和r2日志保留并绑定清单，没有据首轮宣称生产解析错误。

## 范围与下一步

此清单是保守候选超集，**不认证每个候选属于目标事务**。并发事务在同PDB活动窗口内产生标记时也纳入；无法精确归属或处理的候选会使lab关闭检查拒绝。未知候选布局也拒绝，不能为提高通过率按对象过滤。record内部采用begin→undo→markers→end阶段，包含起止record中的marker；不推断vector顺序等于事务阶段顺序。

`UndoHistory` API仍要求调用者传入已匹配补偿，本轮新增核心覆盖清单及lab关闭前强制检查，尚无完整生产事务组装器。覆盖只限给定冻结输入和5.6/5.11已知profile；不证明所有事务opcode、index、恢复、采集完整性或字典。冻结文件扫描前后SHA相同不代替online capture一致性证明。20个选择反例是既有真实清单的内存派生，不宣称做了20次数据库rollback，也不宣称状态锁定测试。

下一门禁：明确并发候选归属的可证伪判据，补完整index/其他相关控制输入审计、真实第二次savepoint与整事务rollback、恢复；随后推进W5 SCN字典/基线。代码交付和有界针对性验证通过；全事务集成、部署、现场验收、发布继续待证。严格零侵入生产边界保持，未改补充日志或源库配置。
