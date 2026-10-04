# W5 真实初始事务跨快照拒绝门禁

状态：专属19c固定schema/typed世代下，一次真实跨归档初始活动事务完整物理图和跨snapshot拒绝通过。不是初始事务恢复、完整CDC或发布通过。

## 来源和运行时

一次性源脚本4d41b8a，最终运行时b9320ff，独立比较/派生器5a2344b。225测试0失败/错误/跳过；jar SHA256 `9053c01bae7420837c80448cc4d3ca39ae0b28431706a1483a9eda52a5e4fc5d`。原始样本与输出在`lab/out/oracle19-snapshot-boundary-20261004`，三轮读取/构建在`lab/out/release-snapshot-boundary*`，不上传原始数据。

只对专属现有项目表一次操作：自有持久SQLPlus会话CAS将ID11的TXT typed-epoch→boundary-open，READY握手后保持未提交；独立连接归档并实际只读获取固定SCN2345429，读到的仍是11/typed-epoch/new-note。原会话再CAS boundary-open→boundary-closed，提交一次后再次归档和dump。没有DDL、supplemental/config变更或重启；没有重放已提交DML。最终项目行11/boundary-closed/new-note。

| 输入 | 字节数 | SHA256 |
|---|---:|---|
| seq28 | 5095424 | 9a5ea9c591eec0d5547b5da21fbb6a5ee87eced19f201fd33a126157a7768d01 |
| seq29 | 3584 | 9fe4e3eb83a3f0d8c7b9fe7506691a7473887d3fa184fb735cf5d6cf41c56934 |
| 实际获取snapshot | 见清单 | 181438b85923d001d61ff72d7548c99b0b9bf192cc26a92d0ad729db95500df9 |

两归档first/next SCN相接2345427，覆盖首个修改到提交；取证dump和SQL只用于独立核验，生产解析输入仍为原始字节。

## 实测与修复

完整自主物理入口先验收：XID6.2.594，2原始/0取消/2保留；与native独立完整图零差异。native begin Record SCN2345424、snapshot2345429、end Record SCN2345432，明确满足begin<=snapshot<end，并与独立字节入口全部一致。完整行入口`BASELINE_CROSSES_TRANSACTION_BOUNDARY`、rc1/stdout0，未把未提交修改错当snapshot后新事务。拒绝本fixture，不意味着已有事务初始恢复策略。

最初完整输入因窗口内真实OP4.1 heap cleanout拒绝：`UNBOUND_PHYSICAL_WINDOW_OPERATION:4.1`、rc1/stdout0，三入口原文保全。当时不能计为快照拒绝。原始29:2:16仅一个16字节字段`01000d00d5c923000080000000000000`，native是ver1/opt1/bigscn/compact、无ITL条目。新增`EmptyHeapCleanout`只认可该有限空条目布局：class1、DBA/file、SCN相符、空spare；只在已完整物理undo证明的heap块活动窗口使用，不把4.1普遍跳过，不支持有条目/index/其他flags或布局。不生成行事件或改变undo图。

| 验证 | 结果 |
|---|---|
| seq28单件缺end | INCOMPLETE_TRANSACTION_IDENTITIES:1、rc1/stdout0 |
| seq29单件缺begin | UNDO_WITHOUT_VERIFIED_TRANSACTION_BEGIN、rc1/stdout0 |
| 4重封checksum raw派生 | 条目数非零、未知flags、SCN错、spare非零；scan均通过，物理及完整行入口均UNSUPPORTED_EMPTY_HEAP_CLEANOUT、rc1/stdout0 |
| late SCN语义测试 | 重算digest的声明snapshot设为end SCN2345432，NO_ROW_CHANGES_AFTER_BASELINE、rc1/stdout0；非新的实际获取/认证证据 |
| 原获取回执SCN篡改 | SNAPSHOT_ACQUISITION_DIGEST_MISMATCH、rc1/stdout0 |
| native时序比较器错begin | rc1，检出差异；不代替内核拒绝 |
| 同件jar旧seq27 | TXT/PK两次完整行与native及两SQL快照零差异 |
| public events | seq28/29分别rc1/stdout0、DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED；CDC0 |

首轮驱动补多archive时误删仍用于格式化的List import，编译rc1和stderr保全；日志展示UTF-8解码又失败，修复import及展示后只在新目录读取，未重放DML。临时字段探针初次误用不存在的absoluteFile accessor，工具原文保留，改file后记录实际字段。public events误给两个archive返回usage rc2，不当安全门禁通过；保全后只补每个合法单件调用。

## 接续与限制

本证据验证一次真实初始活动事务安全拒绝。通用初始边界/长事务恢复、历史schema/DDL/ABA、生产可信基线获取、rollback/并发完整行、INSERT/DELETE及扩展类型仍待证。按[INSERT/DELETE派单](DISPATCH_W5_INSERT_DELETE_ROWS_20261004.md)继续真实完整行覆盖，再推进capture/ACK、StreamHouse和独立现场/发布。

清单：[DELIVERY-snapshot-boundary-20261004.json](evidence/DELIVERY-snapshot-boundary-20261004.json)。
