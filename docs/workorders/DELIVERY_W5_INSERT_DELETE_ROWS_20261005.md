# W5 真实INSERT/DELETE完整行交付

状态：专属19c固定schema、实际获取基线后的两笔完整INSERT/DELETE链路通过。不是通用DML、rollback/并发、完整CDC或发布验收。

## 来源与结果

源端一次性脚本076d183；运行时复用b9320ff，jar SHA256 `9053c01bae7420837c80448cc4d3ca39ae0b28431706a1483a9eda52a5e4fc5d`，该冻结树已通过225测试，本轮内核无修改、不重跑已通过测试。实际只读获取SCN2346182，snapshot SHA256 `e254ee68b3c38646ef23e62b5f16f7c78377e6a92d3a90e5fb8c7b124f1eb454`。控制进程交付digest的信任边界仍在，不把SHA当签发者认证。

专属已有项目表及heap73073→73076/index73074→73075不变：先核单行11/boundary-closed/new-note和无supplemental，再实际获取固定SCN。一次INSERT新增ID12/insert-proof/inserted-note，XID10.22.1639提交；另一次CAS DELETE只删除该新行，XID10.3.1641提交。每步影响1行，原有ID11未改，最终仍一行。无DDL/config/源端重启，不重复已提交动作。

seq30归档207360字节，SHA256 `986241002eeea784ca8ce7540226c0471512ce5bf828256a161ad3a5c6d03907`，first2345442/next2346198覆盖基线及两次提交。原件、SQL/native独立真值在`lab/out/oracle19-insert-delete-rows-20261004`；输出`lab/out/release-insert-delete-rows`不入Git。

| 完整逻辑操作 | before | after |
|---|---|---|
| INSERT，heap DBA03000193/slot1 | 行不存在 | 12/insert-proof/inserted-note |
| DELETE，同data-object/DBA/slot | 12/insert-proof/inserted-note | 行不存在 |

自主物理发现两笔事务，各2原始/0取消/2保留，共4/0/4，包括两heap及两index；完整有序图与native零差异。原始行解析/UNDO配对后两次完整before/after与独立native零差异，并核旧ASOF、INSERT后的两行和最终一行三组SQL快照。native比较器校验逻辑/data角色、同UBA/DBA/slot、相反IRP/DRP及完整列值、完整XID，与Java输入分离；最终行相等不充当操作意图证据。

## 反例与边界

| 验证 | 结果 |
|---|---|
| raw INSERT after键、DELETE inverse键篡改 | 两件重封checksum、scan及物理图仍rc0；完整行在后续DELETE before核对ROW_REPLAY_BEFORE_VALUE_MISMATCH、rc1/stdout0，不泄漏已局部处理的INSERT。物理通过不能当行值验收 |
| raw DELETE inverse slot、INSERT列数篡改 | 重封checksum后scan通过；物理及完整行分别ROW_UNDO_OPERATION_POSITION_OR_XID_MISMATCH/UNSUPPORTED_INSERT_ROWPIECE、rc1/stdout0 |
| 重算digest的语义基线新增slot1行 | ROW_REPLAY_INSERT_OVER_EXISTING_ROW、rc1/stdout0；声明型语义测试，不当原回执认证证据 |
| 同副本使用原获取回执 | SNAPSHOT_ACQUISITION_DIGEST_MISMATCH、rc1/stdout0 |
| 四比较器反例 | 漏INSERT、DELETE before伪值、INSERT伪UPDATE、仅中间SQL伪键均检出；最终SQL未变仍不能掩盖中间差异 |
| public events seq30 | DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED、rc1/stdout0，CDC0 |

raw值反例在本两操作链路由后续逆向值对账拒绝，不宣称所有孤立INSERT都会由index键语义自动认证。当前snapshot元数据尚无通用历史主键列映射，索引/heap全值语义与来源可信交接仍需后续门禁。此处仅固定schema/完整列/单rowpiece有界输入，不泛化尾NULL、迁移/链式行、LOB或多版本。

接续[完整行rollback派单](DISPATCH_W5_ROLLBACK_ROWS_20261005.md)，再做部分回滚/并发、初始恢复/历史字典、capture/ACK、StreamHouse和独立现场/发布。清单：[DELIVERY-insert-delete-rows-20261005.json](evidence/DELIVERY-insert-delete-rows-20261005.json)。
