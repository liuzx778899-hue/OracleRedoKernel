# W4 真实短 Z RESTORE 解码及 B 物理回滚图交付

真实 seq24 的索引 RESTORE 使用8字节短 KTB Z，此前完整同叶清单及图入口统一拒绝。此次内核能解析已观察到的原文布局，B 两节点heap+index整事务回滚图闭合且与native零差异；A现在到达中间DELETE位置拒绝。仍没有完整A图、通用位置模型或CDC发布验收。

## 实现与来源

- 运行时提交b7bb75f，完整封存树 `bbd2fd634f31561b5ca779f40476a65dbb2c26e7`。Java17独立构建187测试、0失败/错误/跳过，新增三项布局/拒绝测试。
- jar SHA256 `7b35e94a3ac0d51202a6a6ee47709e186bfeffeae0e74b2bd356c44a013bf2f2`；[清单](evidence/DELIVERY-z-restore-20261004.json)1063文件双次SHA一致，关联完整源码树、构建日志、输入、制品、输出和命令rc。
- `IndexCompensation.decodeRollback`只增加OP10.5的已观察 `030db87100000000` 精确短Z布局。未知bytes2/3不解释、不补造XID/UBA，其他短Z变体仍拒绝。没有将该常量称为通用Oracle版本格式；后续仍需扩大真实变体证据。
- 返回仅RESTORE物理叶字段；原有标记metadata、唯一原始节点、物理slot/ITL/row size、活动完整身份和head约束不变。源绑定/查询签名保留全部原始字段字节。
- 没有新数据库动作。复用seq24、21、22、18冻结原始文件，native dump仅独立验证，未作为事件生产输入。

## 本批验证

| 项目 | 结果 | 边界 |
| --- | --- | --- |
| seq24 A/B owned完整同叶清单 | 4/2项，native窗口/字段零差异 | A保留B DELETE/RESTORE |
| seq24 B完整heap+index图 | 2原始/2取消/0保留，native有序重建零差异 | 有界物理图，不等于完整逻辑DML |
| 13项真实RESTORE vector派生 | 8字节逐位、错误PURGE opcode、ITL/undo模式、extra field、短KTB均rc1/stdout0 | 未知布局拒绝 |
| 3项B图派生 | 漏index节点/错leaf slot/重复补偿均rc1/stdout0 | head或同源位置查询拒绝 |
| seq21/22 B图回归 | 各2/2/0，native零差异 | 既有固定短Z PURGE不受影响 |
| seq18两笔RESTORE图回归 | 8/7/1及5/5/0，native零差异 | 既有普通KTB C/L布局不受影响 |
| seq24 A位置路径 | rc1/stdout0，UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:DELETE | 越过short-Z解码，仍未支持中间DELETE |
| seq24 A单上下文图 | rc1/stdout0，MISSING_INDEX_COMPENSATION_ORIGINAL | 仍未分发B原始节点 |
| events | rc1/stdout0，DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED | 发布关闭 |

本批针对改变的index解码路径核旧PURGE/RESTORE图。其余九笔图与六批长heap此前通过证据保留，没有重复它们或新增计数。B图派生拒绝完整错误原文在release-z-restore-r2。

## 失败保全

首次封存构建release-z-restore在编译阶段误选旧Java，`--release`不支持、rc1；完整命令/源码/日志保留，该目录不计通过。补充release-z-restore-r2以进程环境显式绑定Java17，在新目录构建同提交树通过；没有覆盖或删除失败证据。

## 下一门禁

已观察RESTORE的有限布局和B图完成针对性验证。接续实现有源归属的DELETE/RESTORE及PURGE位置重放：seq24 A位置作为DELETE缺口、seq23 B两次取消作为PURGE缺口；必须独立绑定原始身份、完整同叶覆盖、物理match、当前head/取消和原始查询字节，缺项及未知操作继续拒绝。扩大短Z真实变体时不得将未知字段默认为安全。

W5字典/完整行、持续capture/续接/ACK、StreamHouse集成、现场/发布尚未验收，长期目标active。
