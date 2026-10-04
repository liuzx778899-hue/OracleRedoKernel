# W4 较大键 INSERT/PURGE 真实位置研究

当前仍在 W4 事务/回滚解析验收。专属无 supplemental 19c 新表一次执行：基线10/50/90，A DELETE50，A仍活动时B INSERT70后整事务rollback，最后A rollback。本批只证明有界真实布局与B选定物理回滚图；A完整图、通用PURGE位置规则、逻辑DML和发布未通过。

## 来源和复核

- 源脚本提交 f332264；完整研究树 `3d05413ae247cb39b0d00498a15ab8287f86f9f8`。Java17编译lab新驱动通过；与184测试内核树582fec6的main/test/pom差异为空，复用既有同件制品，不重复已通过测试。
- jar SHA256 `b842e9c47e1318326dbbdfc476edca7355f37f89ce1768f4502a2eb1580442e0`。
- A完整XID10.3.1236、B7.11.587，heap73067/index73068，叶DBA0x0300016b。实验SCN2268704..2268799；source动作均rc0，B结束时A仍活动，最终两事务均NONE。SQL最终10/ten、50/fifty、90/ninety，70不存在；只作独立truth。
- seq22归档472064 bytes，SHA256 `b1fcd87aeba90d31d6f281f3ea099f3c2f4643566756bf62c891d2eb4ced57b8`。输入、native dump、源码树、命令rc/stdout/stderr和制品共390文件双次SHA一致，见[清单](evidence/RESEARCH-higher-purge-20261004.json)。原始样本仅保留忽略目录lab/out，不上传。
- 本批核对没有再执行数据库动作。`lab/out/release-higher-purge/commands.json`记录全部只读验证命令，summary.json关联结果。

## 实际结果

| 项目 | 结果 | 认证边界 |
| --- | --- | --- |
| raw/native四叶操作 | DELETE/INSERT/PURGE/RESTORE，slot1/2/2/1，ITL2/3/3/2，零差异 | 仅本批实际位置 |
| B INSERT70键 | raw/native均02c147 | 直接字节对账 |
| B PURGE | 固定短Z030d000000000000，native count=2 | 不推导count含义 |
| A/B完整同叶清单 | 4/2项，native零差异 | A清单保留B操作 |
| A/B完整XID补偿头关联 | 4/2项；A含2外国marker，零差异 | 关联不等于物理match/head取消 |
| B完整选定heap+index图 | 2原始/2取消/0保留，native零差异 | 有界物理图，不等于逻辑CDC |
| A旧静态单match | rc0、matches1、stdout0 | 不认证A完整图 |
| A位置模型 | rc1、stdout0、UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE | 功能缺口，未通过 |
| A单上下文图 | rc1、stdout0、MISSING_INDEX_COMPENSATION_ORIGINAL | 尚未分发B原始节点，未通过 |
| B三项派生反例 | 漏index节点/错leaf slot/重复补偿均rc1、stdout0 | 保留各原始错误堆栈 |
| events | rc1、stdout0、DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED | 发布继续拒绝 |

四叶RBA/vector依次22:859:16:3、22:860:240:3、22:861:352:1、22:911:16:1；前三record SCN同为2268747，不能仅按SCN排序。源会话标记SCN2268744/2268751/2268786与record SCN分开保存，不能混当操作精确位置。

此前seq21较小键20的slot1/1/1/1和本批较大键70的slot1/2/2/1，均观察到最终目标slot净变化0。两笔均为单键INSERT再rollback的净零实例，不能据此引入通用PURGE减一、忽略PURGE、邻接取消或SQL相等即通过的规则。本批未复跑seq21双上下文驱动，也未扩大其生产能力声明。

## 下一门禁

1. 新专属fixture扩展多次PURGE、普通DELETE及目标相关取消，保留同叶完整raw/native顺序与最终SQL；不能重跑本批已提交动作。
2. 仅对有真实证据和可证伪边界的PURGE位置模型实现最小支持，独立核全部事务归属、物理匹配、当前head和清单覆盖，缺操作及未知布局继续拒绝。
3. W5 SCN字典/基线、W7持续跟组与覆盖/续接/ACK、StreamHouse真实接入及现场发布分别验收。当前目标保持active。
