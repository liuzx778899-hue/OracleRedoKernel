# W4 两次 PURGE 顺序及完整图缺口研究

本批新证据将 PURGE 缺口缩小到同一事务的两次 INSERT 后整事务 rollback：B 第二次索引取消跨过第一次 PURGE，现有位置模型拒绝。不是只有处理 A/B 外国 marker 才能复现。生产内核未变，没有放宽门禁。

## 来源

专属19c无supplemental新表，源脚本a5846cd一次执行：基线10/50/90；A DELETE50保持活动；B INSERT20、INSERT30后rollback；最后A rollback。完整研究树 `4249b736780dfda581e468d17cdee410cf4b73fc`，lab新驱动Java17编译rc0。main/test/pom与184测试内核树582fec6差异为空，不重复已通过测试。

源A完整XID10.20.1240、B9.30.584；heap73069/index73070，叶DBA0x0300017b。SCN窗口2277645..2277714，源动作rc0，B结束时A仍活动，最后两事务NONE，最终SQL10/ten、50/fifty、90/ninety，仅作独立truth。

seq23归档35,285,504 bytes，SHA256 `da6a0d7cfc919865a230c1f8d2357e48fedcf8c6334ea20dba0b009eff06313b`。复用jar SHA256 `b842e9c47e1318326dbbdfc476edca7355f37f89ce1768f4502a2eb1580442e0`。原始样本和完整失败堆栈在忽略目录lab/out；[清单](evidence/RESEARCH-double-purge-20261004.json)封存411文件并双次重算SHA，命令、rc、输入、制品、输出与研究树关联。验证阶段没有再执行源端动作。

## 实际顺序和验收

| 操作 | RBA/vector | slot | 直接观察 |
| --- | --- | --- | --- |
| A DELETE50 | 23:68896:148:3 | 1 | ITL2，row size13 |
| B INSERT20 | 23:68897:328:3 | 1 | ITL3，键02c115 |
| B INSERT30 | 23:68897:328:5 | 2 | ITL3，键02c11f |
| B PURGE30 | 23:68899:400:1 | 2 | ITL3，KTB C16字节 |
| B PURGE20 | 23:68900:244:1 | 1 | ITL3，固定短Z8字节 |
| A RESTORE50 | 23:68906:16:1 | 1 | ITL2 |

六项raw/native位置、字段、两键字节零差异。前五record SCN均2277683，必须保留RBA与同record vector顺序。两次PURGE的native count均2，不能据此推断两行删除或通用count语义。

- A/B owned同叶完整清单6/4项，native零差异；A包含B两笔原始索引操作。
- A/B同源完整XID补偿头关联6/4项，native零差异；A包含4外国marker。仅认证关联，不认证物理match、当前head或取消。
- A旧静态单match rc0、matches1、stdout0，不认证完整A图。
- A位置路径rc1、stdout0，`UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE`。
- A单上下文图rc1、stdout0，`MISSING_INDEX_COMPENSATION_ORIGINAL`，没有B原始节点分发。
- **B完整图rc1、stdout0，`UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE`**。代码路径原始INSERT20至其PURGE之间包含INSERT30及其PURGE；模型只支持中间INSERT，不能闭合本批B图。不得补填4原始/4取消/0保留为已验收计数。
- 三项研究比较器派生反例（raw错slot、raw错key、native错slot）分别检出1/1/2差异，rc1；这不是生产安全拒绝验收。
- events rc1、stdout0、`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，CDC发布继续关闭。

## 下一步与判据

先补普通DELETE/RESTORE与多次PURGE实际位置矩阵，随后实现有源归属的有界PURGE位置重放。候选规则必须同时满足原始行身份、完整归属/原始头、物理布局、head取消、完整同叶操作和错误/缺项拒绝；不能仅以slot减一、邻接INSERT/PURGE抵消或最终SQL相等授权取消。本批冻结输入作为修复前真实失败正样本，修复后与seq19—22及已有图做针对性回归。

长期目标仍active；W5字典/基线、持续capture/续接/ACK、StreamHouse真实接入、现场与发布仍未验收。
