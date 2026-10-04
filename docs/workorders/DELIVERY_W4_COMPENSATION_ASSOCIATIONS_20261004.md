# W4 同源完整 XID 补偿头关联清单交付

seq21的A窗口内有B、B、A、A四项回滚marker。旧单事务图遇到B marker会拒绝，不能凭不同slot/wrap随意跳过。本轮新增由内核拥有的关联清单，自主遍历完整归档，给每个marker绑定唯一先前原始undo节点、完整XID及同源完整活动窗口。真实四项绑定与独立native零差异。**该清单只认证source/header关联，不验证物理逆向行或有效head，不授权取消；A图尚未接入，仍拒绝。**

实现提交12910d5，完整封存树 `582fec6280827b8ac8128f2ffbad74d0be11d477`。Java17 Git archive独立重建184项测试、0失败/错误/跳过，jar SHA256 `b842e9c47e1318326dbbdfc476edca7355f37f89ce1768f4502a2eb1580442e0`。[证据清单](evidence/DELIVERY-compensation-associations-20261004.json)543文件双次重算SHA一致。本轮无数据库动作，复用seq19/20/21冻结输入。

## 所证明的门禁

`ArchivedCompensationAssociations.read`先取得目标事务的owned保守补偿窗口，再自主读取全部原始5.1；调用者不能提供或省略候选undo子集。对每个marker按完整object/dataObject/tablespace、opcode、slot、wrap、prev DBA、rci和begin分类匹配先前record的原始metadata。

- 普通marker还需原始undo DBA一致及完整原始XID的低wrap相符；**低wrap只用于候选，不作为最终XID**。
- begin marker的wrap1不等于原始事务sequence；通过原始begin精确UBA、同record唯一5.2、完整XID及segment DBA绑定，不能靠slot+wrap1猜身份。
- 每个候选XID都重新取得同源owned完整begin/end窗口及原始undo清单，核原始位置/身份属于清单，marker位置/头也属于该活动窗口。候选原始节点必须唯一；没有候选、多个候选、其他事务窗口不完整或源变化均拒绝。**即使多个候选属于同一完整XID，也不猜当前head**。
- 结果列表不可变、构造器私有；查询必须按全部保守窗口位置顺序消费，包含外国XID marker。查询绑定完整源头/SHA、uid/CON、record位置/SCN、vector位置及所有原始字段签名；finish拒绝漏项。查询失败锁定本清单。

目前cross-block marker明确拒绝；要求原始节点位于marker先前record，同record的未证phase关系不猜。全归档中不可读5.1头不能安全排除，保守拒绝。本入口不证明采集从未丢块、全部相关控制opcode已收到或所有Oracle布局可解码。

**关联不是补偿授权**：metadata相同不证明物理逆向行相同；原始节点唯一也不证明它正是有效head。调用者仍需各自的source-bound生命周期、heap/index物理match、完整history/head及取消覆盖门禁。不能把关联Entry直接交给`UndoHistory.compensate`后宣称取消通过。

## 独立真实对账

| 冻结源 / 目标 | marker关联数 | 其中外国XID | native差异 |
|---|---:|---:|---:|
| seq21 / A10.19.1233 | 4 | 2：B9.22.584 | 0 |
| seq21 / B9.22.584 | 2 | 0 | 0 |
| seq19 / A10.23.1220 | 2 | 0 | 0 |
| seq19 / B4.2.587 | 0 | 0 | 0 |
| seq20 / A10.29.1230 | 2 | 0 | 0 |
| seq20 / B9.16.584 | 0 | 0 | 0 |
| 合计 | 10 | 2 | 0 |

seq21 A窗口的结果如下，完整原始位置与UBA均从native独立取得，不从Java结果生成期望：

| marker位置 | 完整XID | 原始undo位置 | 原始UBA | begin |
|---|---|---|---|---|
| 21:1286:440:2 | B9.22.584 | 21:1285:328:5 | 37760641:224:62 | false |
| 21:1287:100:2 | B9.22.584 | 21:1285:328:4 | 37760641:224:61 | true |
| 21:1295:140:2 | A10.19.1233 | 21:1284:148:5 | 37759375:245:23 | false |
| 21:1295:320:2 | A10.19.1233 | 21:1284:148:4 | 37759375:245:22 | true |

`compare_compensation_associations.py`遍历独立native原始头、完整XID、5.2/精确UBA及5.4，自己建立各事务窗口，再匹配marker并核完整顺序。actual行包含marker位置、完整XID、原始位置/UBA、object/dataObject与begin属性，全部零差异。native dump是有界实验校验输入，不用于事件生产；清单自身读取完整raw归档。seq19/20 B的零关联结果只证明其已验证窗口没有5.6/5.11，不代表无需验证其他控制。

## 反例与当前状态

八项真实查询派生反例（A漏B、重复、重排、改marker不透明字段、错source、错位置；B重复及改marker）全部rc1/stdout0。具体原文/rc见清单：顺序/覆盖拒绝、源头不符或owned字段改变。派生只修改查询/选择，不物理删除归档，也不是源端DML实验。

三项native比较器反例：漏B、改完整owner XID、重排，均检出；只算验证器自检。九项新增合成单测包括begin wrap1通过segment区分A/B、外国窗口缺结束拒绝、重复候选不猜head、原始字段/源头/顺序篡改以及失败锁定。合成案例不扩大真实支持范围。

仅新增独立运行时类，既有heap/index/位置/lifecycle源码未改；没有重新执行上一轮九笔混合图和六批长heap回归，也不声称新API已在那些旧长事务上验收。原有175测试及新增9项在最终封存树均通过。

最终同件jar的A旧图仍rc1/stdout0，`MISSING_INDEX_COMPENSATION_ORIGINAL`；位置模型仍`UNSUPPORTED_INDEX_POSITION_INTERVENING_OPERATION:PURGE`；events仍`DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`、rc1/stdout0。**没有以关联清单通过填补A图计数，也没有开放发布。**

下一步让分发消费者完整消费这个保守窗口，对B另行保持完整物理match/有效head/取消图验证，再结合其证明处理外国marker；随后补PURGE位置作用的真实矩阵，不能直接把当前净slot变化0推广为减1规则。W5字典/基线、持续capture到解析/ACK、StreamHouse接入、部署/现场/发布仍待证，目标保持active。
