# W4 普通 DELETE/RESTORE 位置及短 Z 缺口

新批真实 ordinary DELETE/RESTORE 显示 A DELETE50、B DELETE10、B RESTORE10、A RESTORE50 的 slot 为1/0/0/1；原始字节和 native 直接字段零差异。但 B 的索引 RESTORE 使用 `030db87100000000` 短 KTB Z，现有制品拒绝。不能将最终行相等、静态slot相等或结构解析当完整图通过，也不能把本批称为 DELETE 位置模型通过。

## 来源与封存

- 专属 `oraredo-19c-lab-20261004`，源脚本4cb3693一次执行，新表基线10/50/90，A DELETE50保持活动，B DELETE10后整事务rollback，最后A rollback。源端DBID1276726821、ARCHIVELOG、MIN/ALL均NO；所有源动作rc0，B结束时A仍活动，两事务最后均NONE。
- A完整XID10.12.1212、B9.5.585，heap73071/index73072，叶DBA0x0300018b。实验SCN2277965..2278025，最终SQL10/ten、50/fifty、90/ninety，仅用于独立truth。
- seq24归档100352 bytes，SHA256 `def82f0b1009a60142150e90726bfa8482d55c0a1a8a617603e4e3cf58395b09`。
- lab驱动完整树6f49a87、修正研究比较器完整树 `6bd7a8724471a9c8929e1263c172b7f8a32c3112`。Java17 lab编译rc0；main/test/pom与184测试内核树582fec6差异为空，不重复已通过内核测试。
- 同件jar SHA256 `b842e9c47e1318326dbbdfc476edca7355f37f89ce1768f4502a2eb1580442e0`。验证阶段没有再做数据库动作。698文件双次SHA核对，见[清单](evidence/RESEARCH-delete-restore-20261004.json)。原始数据、首轮失败树及补充树在忽略目录lab/out，不上传原始样本。

## 判据与结果

| 项目 | 实际结果 | 边界 |
| --- | --- | --- |
| 四叶操作 | 24:180:148:3、24:181:328:3、24:182:456:1、24:185:16:1；slot1/0/0/1，ITL2/3/3/2，row size13，raw/native零差异 | 有界实际位置，不推导通用规则 |
| B RESTORE | OP10.5，native op Z，raw KTB030db87100000000 | bytes2/3未知，原文保留；不能补造XID/UBA |
| A/B补偿头关联 | 4/2项，native零差异；A含2外国marker | 不认证物理match/head取消 |
| A/B完整同叶清单 | 均rc1、stdout0，UNSUPPORTED_INDEX_ROLLBACK_MODE_OR_KTB | 在真实short-Z RESTORE入口拒绝，未通过 |
| A位置路径、A/B完整图 | 三者同上rc1、stdout0 | 尚未到DELETE/RESTORE位置重放门禁，未通过 |
| 三比较器派生反例 | raw错slot、raw错Z尾部、native错slot检出1/1/2差异，rc1 | 研究验证器检查，不算生产安全反例 |
| events | rc1、stdout0，DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED | CDC发布关闭 |

前三record SCN均2278002，第四2278012。会话标记SCN2278001/2278006/2278012独立保留，不能替代RBA/vector操作顺序。

## 首轮失败与修正

首轮研究比较器错误地沿用此前PURGE固定全零短Z字节，返回 `RESTORE_SHORT_Z_PROFILE`、mismatches1、rc1，原文及源码树保留在release-delete-restore。此次真实RESTORE含非零bytes2/3，比较器修正为明确记录和核对本样本原始字节；没有改变内核或宣称未知字段含义。补充树release-delete-restore-supplement只补跑失败比较及未执行的关联/拒绝/events，不重编译、不重新取fields、不重做DML。

研究反例脚本首轮native选择器未限定OP10.5，选择了其他同叶操作，触发断言；失败脚本与原文保留。两项已完成raw反例没有重跑，只在native_slot-r2补跑正确OP选择的未执行反例。最终三项差异均被检出。

## 下一步

直接进入最小解析修复：针对真实 short-Z RESTORE 补明确布局与派生反例，保持未知字节原文绑定及失败锁定；用本冻结源验证owned清单和B完整heap+index图。随后核普通DELETE/RESTORE的位置语义，并结合seq21—23的真实PURGE矩阵实现有源归属的位置重放，要求原始身份、完整同叶覆盖、物理match、当前head/取消分别闭合。不能直接删除slot条件或忽略中间操作。

长期目标active；字典/完整行、持续capture/续接/ACK、StreamHouse接入、现场与发布未验收。
