# W4 有源归属位置重放及双事务完整物理图

此前seq24中间DELETE、seq21/23中间PURGE位置拒绝。本轮实现经完整原始关联和物理核对的位置重放：seq19—24六批A位置与native原始UBA/前后slot零差异，seq23 B两次取消闭合，seq21并发A/B两图分别完整闭合。只认证冻结样本的物理位置和选定回滚图；逻辑DML、字典、持续capture、现场和发布未通过。

## 实现与证据

- 内核及消费者完整树 `21cd368f78709b393b8df0f79715c0031cd1c967`。Java17独立构建192项测试、0失败/错误/跳过，新增5项算术与边界测试；它们不冒充source ownership或真实图支持。
- jar SHA256 `0642bb7ffd8691dab868b780ea73966659c9b418ff78f6b84b7e0f3858b48500`。
- 独立位置比较器020e496；并发native完整XID比较器修正树 `ce2321122bfce4a54a7b7623579788100dfc5c0e`，main/test/pom与21cd368差异为空，无需重新构建制品。
- [清单](evidence/DELIVERY-owned-positions-20261004.json)1328文件，包括1327主封存文件双次SHA一致及另行追加并双次核对的首轮汇总脚本失败原文。输入、两份完整源码树、jar、构建/测试、命令rc、原始及补充输出关联。样本/重封反例仅lab/out，不上传原始归档。
- 无新数据库动作，全部复用冻结seq18—24输入；dump用于独立验证，生产位置模型仅使用raw归档解析。

## 最小模型与拒绝边界

`IndexLeafPositions.read`自主读取同叶完整操作清单与完整XID补偿头关联，核来源SHA/header及全部marker位置/字段一致。每个PURGE/RESTORE要求同record唯一marker关联、窗口内唯一原始索引头/原始record、相反原始操作类型、ITL/row size和重放后的实际slot一致。重复取消原始节点、原始项在叶窗口外、物理字段不符或无法证明来源均拒绝。

通过上述校验后，INSERT在前方插入时增加slot，PURGE在前方移除时减少slot；PURGE等于跟踪slot直接拒绝，普通DELETE/RESTORE不移动槽位，INSERT溢出拒绝。不是仅根据邻接或净零推断取消。原始pair签名、每次source/query/marker全字节签名、单调顺序与失败锁定保持。旧单参数构造器仍仅支持中间INSERT，不因缺少归属证明而自动升级。

位置模型不授权head取消：seq21有限双上下文消费者分别保持A/B生命周期、原始节点、物理match、完整补偿覆盖和UndoHistory；B marker先归属B并在B图取消，A窗口完整记录外国marker已处理；full模式才匹配A并关闭A自己的图。foreign-only仍只认证B图，不补填A通过。此消费者仍限定seq21对象/来源，不当通用生产分发已完成。

## 针对性验收

| 项目 | 结果 |
| --- | --- |
| seq19/20 A位置回归 | slot1→2、1→3，原始UBA及RBA/vector/native零差异 |
| seq21/22/23/24 A位置 | 各slot1→1，原始UBA及RBA/vector/native零差异，越过PURGE和DELETE拒绝 |
| seq23 B完整图 | 4原始/4取消/0保留，native有序head重建零差异 |
| seq24 B完整图 | 2/2/0，native零差异 |
| seq21并发full | A2/2/0、B2/2/0，独立native完整XID归属及有序图零差异；各自覆盖和head关闭 |
| seq18两笔旧图 | 8/7/1及5/5/0，同件jar回归零差异 |
| 七项分发派生反例 | 漏B节点/漏B取消/重复B取消/错B slot/错marker/错owner/漏外国查询均rc1/stdout0 |
| 七项位置查询反例 | 错slot/错marker/错pair key/重复查询/重复候选/漏外国操作/错source位置均rc1/stdout0 |
| 三重封raw反例 | 改B RESTORE slot/ITL/row size并重封checksum；结构scan均rc0，owned物理检查均INDEX_POSITION_ROLLBACK_PHYSICAL_MISMATCH、rc1/stdout0 |
| 三native图比较器反例 | 漏取消/重排/错原始UBA检出1/2/1差异，rc1；不计生产安全验收 |
| events | rc1/stdout0，DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED |

六批长heap和其它已通过图证据保留；本批核改变的index位置及指定相关回滚路径，没有重复无关回归或改写旧计数。seq22—24完整双事务分发图仍未认证，不能以其A单位置match替代。

## 失败保全与修正

第一轮routed-full确实关闭两图，但旧native比较器在A活动窗口把B补偿也当作A，返回NATIVE_COMPENSATION_HEAD_OR_IDENTITY_MISMATCH；原始stderr、源码、A选择输出保留。修正比较器从完整native原始头、begin控制、结束窗口和唯一元数据匹配独立构造全部完整XID归属，再对目标完整有序图核head；不从Java输出决定marker归属，也不直接按slot跳过外国项。

补充汇总首轮错误glob纳入失败比较器的空stdout，JSONDecodeError原文保留；r2明确只复用成功graph结果，补跑失败/未执行的A/B比较及剩余反例/events。已通过位置和图运行不重复；内核/制品未因验证器修正改变。

## 后续门禁

将有限双上下文机制扩展到seq22—24完整图，核同叶原始项在窗口前、更多控制/页维护及位置绑定缺项拒绝，再收敛为内核事务分发；不能把lab固定常量消费者称为生产能力。随后推进SCN字典/基线与完整逻辑事件。W7持续跟组、覆盖防丢、续接/ACK及StreamHouse真实集成、现场/发布仍各自待证，目标active。
