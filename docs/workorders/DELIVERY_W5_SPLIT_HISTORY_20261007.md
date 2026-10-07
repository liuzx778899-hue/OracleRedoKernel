# W5 split恢复与完整历史业务事件

同实际产品早V3 SCN2566695/SHA323fd35a及原冻结70/71，Java17 kernel **a84db1a3ff8fddbe49258714c8b8d7945e6648f2caa75eb082cbf36b225e3a64 / 311测试，0失败/0错误/0跳过**。本轮将物理split闭包接入完整历史工厂，产出两条完整UPDATE并经既有adapter投影；不是完整Oracle开发、生产接入或现场发布全部完成。

## 实现与可证伪边界

生产选择不传手选XID/RBA，也不读native/SQL真值。工厂从同RedoBatch/同可信Snapshot自主生成Closed，严格按原位置和完整UndoHeader处置五个物理维护original，其freelist/end重建由已验证物理闭包负责，不伪造SYS逻辑提交。随后读取完整输入，在end之后直到恢复的OLD页拒绝未知写入。

10.5只允许SYS.I_COL3：同源容器/file/segment/页世代、原始槽、完整current XID/UBA、同记录10.22 PURGE/ITL/key/size绑定。rowid来自重建后的删除标记槽，不能从旧前像同槽、其它索引或SQL补齐。物理RESTORE仍保留，在可信SYS事务中明确表示逻辑index INSERT；COL$行与三索引完整状态必须闭合。Closed不接受外部注入，普通IndexUndoPair.bind入口不支持10.5。

真实输入还揭示两处有限格式缺口：SYS.OBJ$六列/stored26正逆ckix144；DROP首record flags004a/size316。前者只在完整37列OBJ$路径允许且绑定正逆ckix；后者绑定同记录start与undo大小、SYS.COL$/21/2/TS0及32列cluster inverse/null bitmap，无parent事务。其它profile继续拒绝，不能当一般Oracle版本支持。

## 实际验证

| 范围 | 结果 |
|---|---|
| 三SYS commit | 2567428 / 2567435 / 2567462；original/row/index/control分别6/3/3/0、10/5/4/1、6/3/3/0 |
| 完整SYS状态 | 从独立核过的早V3基线及冻结native独立重放；native全量XID original集合22项完整相等，267+267+230=764值零差异 |
| 两业务事件 | 70:126642:16:4 / XID10.15.2348 / commit2567441；71:16:16:4 / XID10.12.2321 / commit2567465 |
| 完整像/schema | ID0、TXT前后值、隐藏NOTE=anchor、新增EXTRA_HP=NULL；schema4→3；native与一次性阶段SQL零差异 |
| archive/capture/重复 | SYS历史字节、完整业务行/归属/schema一致；完整capture batch重复字节一致 |
| 完整入口反例 | 23 archive + 3 capture = 26；合法checksum/结构rc0，完整历史/业务入口rc1、stdout0；含恢复slot/size/key/current及undo XID/UBA、缺恢复、pending rowid、早SCN、未知OBJ ckix、未知同页写入及原12 split反例 |
| marker擦除 | 14条24.x全部擦成0.0并重封checksum/帧SHA；完整SYS历史及两事件/schema/ID/payload不变 |
| adapter | 既有65e0fcba / SPI ad75404c与新kernel实际投影；两完整envelope语义、schema序列241d24ac一致。captureInput随batch/journal SHA变化，不能要求整envelope SHA不变 |
| 影响检查 | 旧V2三IUD及早68/69两完整历史事件stdout字节不变；旧V2原/擦marker历史拒绝原因不变。原122 production classes中114字节未变，8个相关class变化、新增1个input-owned restore class |

三次旧SCN SYS ASOF补查均 **ORA-01555**，查询/完整Java stderr/partial results保留，不能写ASOF再次通过。764值验证使用已有独立基线和native尺子；native不进入生产事件路径。阶段SQL的TRUTH_SCN2567443/2567467是后续查询位置，不冒充业务commit2567441/2567465。第一SYS临时列名阶段schema仍拒绝，业务只在可验证后续schema时点产出。

## 证据与失败处置

[1181项双SHA/写后核验清单](evidence/DELIVERY-split-history-20261007.json)，前549项SHA/大小未变。输出在lab/out/split-history-candidate-20261007；secrets不哈希/不上传。输入、source/profile失败候选、最终jar、311 reports、commands/rc/stdout/stderr、正/反例原始派生物与冻结驱动均保留。

首r1完整入口因OBJ profile拒绝；r3因DROP begin拒绝，均stdout0。r2测试误要求既有普通heap拒绝144、r5测试误要求不含USN的start字节单独拒绝USN变化，失败reports/源码保留并修正判据。最终完整恢复XID反例仍拒绝。研究驱动的native长度空格、无真实间隔记录、旧四marker假设、envelope输入绑定变化、class内嵌元数据预期均有冻结失败版本/工具原文及处置；已通过JVM输出未重新执行，修正比较器后复用精确commands/rc/输出。

end OLD重建与10.5实际相邻；未知间隔写入反例插入一条已观察13.6 vector、重排尾部原始records并重封合法checksum，而非假称原现场存在该记录。安全拒绝只证明对应反例，不能当业务功能通过或完整故障矩阵完成。

本轮0源fixture重放、0源配置、0新获取、0目标写入、0产品构建/换装。六状态：代码交付=完整历史候选；针对性验证=上述有限窗口通过；集成验证=raw/capture→历史→完整行→adapter通过，产品HTTP/target/CPACK未过；部署=未开始；现场验收=待证；发布=未开始。

## 下一开发执行包

1. 使用本1181封存制品/原早V3/70—71及projection-r2/actual-projection.json，升级adapter依赖和指定私有StreamHouse worktree的不可变kernel坐标；完整模块/产品构建并核真正嵌入class/SHA。共享StreamHouse只读，禁止Luna/子代理。
2. 专属实验环境接原产品acquisition100747510573780992登记与同窗口批准输入，在新owned目标prefix执行实际历史HTTP→原子target receipt→控制checkpoint→持久ACK；独立核完整两事件、成员账、schema序列、SCN/位置与重复/同件重启。
3. 新owned late-failure prefix验证第二成员约束失败整批回滚；凭证/绑定/序列篡改拒绝、恢复原文及零推进。不得污染已通过目标，不能用晚SCN或新source动作补冻结窗口。
4. 完成开发、针对性验证和专属集成后继续原跨窗恢复/正式采集/类型/故障与扩展能力主线；部署及生产验证按用户最新安排协同，目标范围不缩小。Oracle全部目标完成之后才排查Codex并关闭automation。
