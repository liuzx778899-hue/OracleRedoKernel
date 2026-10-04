# W4 短 Z index PURGE 与 heap XR DELETE 交付

[seq21研究](RESEARCH_W4_INTERVENING_PURGE_20261004.md)暴露两个真实布局缺口。本轮补充有限字节解码：index 10.3接受观察到的八字节KTB Z，heap 11.3接受短Z与二十字节KDO XR DRP。两种实际vector输出与native物理字段零差异；完整同叶清单能收录B PURGE，B的选定heap/index回滚图通过。**A的中间PURGE位置作用及其他XID marker归属仍拒绝，未开放完整DML/CDC。**

运行时实现提交a6818ea，完整封存源码树 `919e6eeedb034a1b043f518d24c293933c386bce`；Java17从完整Git archive独立重建175项测试、0失败/错误/跳过。jar SHA256 `1531c9bc81dc751c701c419e51de560346ea5c18a830b6f6835be16113ca6cde`。[证据清单](evidence/DELIVERY-z-rollback-20261004.json)绑定源码、制品、测试XML、真实输入、native比较、派生拒绝、回归及历史拒绝日志，文件双次SHA重算一致。本轮无数据库动作，冻结seq21没有重放DDL/DML。

## 最小实现与拒绝边界

- `IndexCompensation.decodeRollback`仅增加 **10.3 PURGE、KTB恰为030d000000000000、既有二字段/六字节leaf**。短Z用于10.5 RESTORE、不同version或非零未证tail仍拒绝。原有C/L路径及ITL、leaf模式、row size检查保留，不自行解释native的`count = 2`。
- `HeapRowPatch`增加 **11.3、八字节op Z/version0x0d、KDO长20且xtype XR=2**，继续用原严格KDO opcode/DBA/field count/slot解码。XA、packed DELETE、额外列或错误块仍拒绝。两个字典列均ABSENT，XID/UBA均null，不从Z不透明字节猜当前身份。
- index marker的metadata/UBA/完整活动身份/唯一候选检查和事务失败锁定没有放宽。合成匹配测试证明短Z仍不能绕过原metadata前驱检查；合成测试不作为新真实事务支持证据。
- 本轮没有改变`IndexLeafPositions`的位置算术，**中间PURGE/DELETE/RESTORE继续拒绝**；也没有按不相同slot随意跳过并发marker。

## 真实输入与结果

seq21 SHA256 `f01a212cf53ce3472376f4e3b7e3d46dd72e557fa92bfb83997f1bcf5373ea80`，原先控制过的源动作、A/B交叉顺序、配置和最终SQL沿用研究。事件路径仅raw redo，native dump用于独立验证。

| 门禁 | 结果 | 实际范围 |
|---|---|---|
| B index PURGE 21:1286:440:1 | PURGE / DBA50331995 / ITL3 / slot1 / row13，native零差异 | 单个已观察short-Z物理叶vector |
| B heap XR DELETE 21:1287:100:1 | DELETE / DBA50331989 / slot0 / XR2；无XID/UBA，两个ABSENT列，native零差异 | 物理DRP，不生成完整before行 |
| A owned同叶清单 | 窗口21:1284:148..21:1296:40；DELETE、B INSERT、B PURGE、RESTORE四项 | 窗口、顺序、原始完整XID/UBA、键/keydata与marker字段native零差异 |
| B owned同叶清单 | INSERT、PURGE两项 | native全输出字段零差异 |
| B / XID9.22.584图 | 2原始 / 2取消 / 0保留，native零差异 | heap INSERT及index INSERT均撤销，begin/end/head闭合 |
| A位置重放 | rc1/stdout0，`UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE` | 现在到达位置门禁，未认证一般PURGE作用 |
| A混合图 | rc1/stdout0，`MISSING_INDEX_COMPENSATION_ORIGINAL` | 现在到达B其他XID marker，尚不能证明归属并完整分类 |

`compare_intervening_purge.py`从native B PURGE实际ITL/slot/size生成期望；heap期望来自指定真实record中的11.3、DRP、XR与slot，不从Java输出计算。`compare_index_leaf_inventory.py`独立求目标完整begin/end窗口及所有同叶操作；`compare_mixed_undo.py`独立核B完整XID分配、取消、head与保留集合。这些验证不代替源头采集连续性、W5字典映射或全部redo控制覆盖。

11项真实vector内存派生反例全部rc1/stdout0且为RedoException，AssertionError不计通过：index version、未证tail、改RESTORE、forward mode、额外字段；heap XA、错DBA、错KDO opcode、额外列、截短KTB、截短KDO。输入归档未修改，不是11次实际Oracle动作。八项新增单测也覆盖零ITL、packed DELETE及已有metadata检查不被短Z绕过。

## 同件制品回归与接续

runtime同时改变heap与index解码，因此对最终封存jar运行相关既有回归，全部native零差异：

- seq8/9/18五笔混合图加seq19/20四笔并发图，共 **34原始/23取消/11保留**；不把seq21 A拒绝填进通过计数。
- 六批长heap seq10—15，共 **756原始/430取消/326保留**。这些是物理patch/选定图回归，不是支持所有长事务或index页维护。
- events rc1/stdout0，`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`，CDC零发布。

之前aa44b0d制品的五处short-Z入口拒绝及静态A单补偿成功全文继续保留在 `lab/out/release-intervening-purge`，纳入本轮证据，不抹去此前功能缺口。本轮从919e6ee完整树另目录 `release-z-rollback` 验证；没有覆盖旧制品或原始样本。

下一步先对其他XID marker做完整来源/全XID/原始UBA/head与活动窗口归属认证，不能用slot过滤代替完整清单分类；再研究PURGE目录移动及更多实际布局，保持未证中间操作拒绝。W5字典/基线、持续capture到解析/ACK、StreamHouse接入、部署/现场/发布继续待证，目标保持active。
