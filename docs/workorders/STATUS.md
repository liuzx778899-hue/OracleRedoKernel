# OracleRedoKernel 当前状态

更新：2026-10-04（Asia/Shanghai）。用户授权自主论证、实现、专属实验验证，主攻解析；禁止 Luna。目标仍进行中，不是全流程验收完成。

## 六列状态

| 工作包 | 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|---|
| W0 环境/样本/真值 | 通过：首批fa61476，矩阵继续 | 通过：环境/冻结归档；矩阵待证 | 待证 | 不适用：实验脚本 | 待证 | 未开始 |
| W1 文件/块 scanner | 通过：首批fa61476 | 通过：19c/21c有界样本/5个真实派生反例 | 待证：真实capture | 未开始 | 待证 | 未开始 |
| W2 record/vector | 通过：首批fa61476 | 通过：19c seq8/9完整dump、21c选定范围 | 待证：事务/字典 | 未开始 | 待证 | 未开始 |
| W3 基础行/类型 | 进行中：scalar + pair + KDOM2单列扩展值 | 通过：18pair/302长值patch；完整行待证 | 进行中：物理配对；字典/基线待证 | 未开始 | 待证 | 未开始 |
| W4 事务/回滚 | 进行中：有序history/覆盖/slot排除/heap rollback/index普通purge补偿 | 通过：六批heap756/430/326；14index pair；三笔混合图9/7/2及6派生拒绝；完整事务待证 | 进行中：并发归属/其他控制/index restore及扩展取消图/恢复待证 | 未开始 | 待证 | 未开始 |
| W5 SCN字典/基线 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
| W6 StreamHouse接入 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
| W7 online采集 | 进行中：固定member有界观察journal实验 | 通过：429归档数据块字节覆盖、6派生拒绝；完整持续捕获/续接/Windows/ASM待证 | 未开始：journal到解析/ACK | 未开始 | 待证 | 未开始 |
| W8-W11 扩展类型/LOB/RAC/ASM/TDE | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |

## 当前证据

- 私有空仓库已拉取，Java17构建；生产运行时无数据库/Flink/Spring依赖。
- 本地两套21.3 XE只读盘点：ARCHIVELOG且MIN/ALL已开启 vs NOARCHIVELOG且MIN/ALL关闭；DBID相同，需来源绑定防混流。
- `lab/out/environment-20261004/archive3064.dbf`：10,352,128 bytes，SHA256 `259286dbce264815ac582152a75670a3c7572cbe71fb2af8dbeba62164a8e34f`，20,219 blocks，20,105 structural records。DBID3062128985、thread1、seq3064、resetlogs1181008281、SCN[41434081,41443847)。
- Oracle dump选择范围：490条record、920个vector位置/LEN/VLD/SCN/container/opcode零差异。首轮comparator漏掉一条不在行首的CHANGE，失败原文 `dump-parity.json` 保留；修复后 `dump-parity-r2.json`。这是验证器修复，不是parser修复。
- fa61476完整提交树独立重建：14项测试、0失败、0跳过。研发jar SHA256 `3c7e87e590afded83d468cb677e4403d5a978f9dfa555577fefd2f682f835421`。合成测试不冒充真实Oracle支持。
- 同件jar对19c真实归档副本5项反例（截断1字节/末块缺失/中间块缺失/checksum篡改/重封sequence错）均rc1、stdout空，结构拒绝通过；T009事务级缺archive仍待证。
- 已推送main首批源码fa61476；[交付清单](evidence/DELIVERY-fa61476.json)绑定jar、输入、对账、反例与日志SHA，私有真实raw样本留本地。
- `events` 返回rc1、CDC事件0，原因 `DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED`。未开放事件发布。
- 专属19.3容器 `oraredo-19c-lab-20261004` 已建好。第一次初始化口令长度超限失败，原容器改名带bootstrap-failed、原日志保留；成功实例不暴露宿主端口。
- 19c无supplemental fixture已冻结seq7/8/9：SHA分别d53b6ee8…、e9ec1daf…、99ea74e7…。seq8对账53,078 records/104,545 vectors，seq9对账14/28，均零差异。尚未判DML/事务通过。
- 首轮PDB切日志ORA-65040、CDB VLD9/13/marker结构缺口、SQLPlus中文显示与LogMiner status3均保留原文；两会话交叉事务/跨switch动作已执行。细节见[首批反馈](DELIVERY_W0_W2_20261004.md)。

## 接续与限制

最新：[索引补偿与混合undo图交付](DELIVERY_W4_MIXED_UNDO_20261004.md)，最终完整封存树9e80b0f，[清单](evidence/DELIVERY-mixed-undo-20261004.json)。138项测试通过；三笔完整XID的heap+index原始9/取消7/保留2与native有序重建零差异（index取消3、heap取消4）；6项漏index节点/错误purge slot派生输入rc1/stdout0。同件jar14index pair/12拒绝及既有heap补偿回归，events拒绝。无数据库动作；下一步index RESTORE及主键更新回滚专属fixture，其他控制/并发/恢复/W5/持续capture仍待证。

最新：[索引undo配对交付](DELIVERY_W4_INDEX_PAIR_20261004.md)，完整封存树b0771f7，[清单](evidence/DELIVERY-index-pair-20261004.json)。133项测试通过；seq8/9真实14index pair按同record唯一完整UBA与native零差异，12记录派生拒绝；同件jar既有18heap pair及4补偿/1保留回归，events rc1/stdout0。首次lambda编译失败保全；最终XID反例直接篡改forward当前身份。无数据库动作；下一步3条索引补偿归属及heap+index取消图，SCN字典/逻辑DML/发布未通过。

最新：[索引字段与完整输入研究](RESEARCH_W4_INDEX_FIELDS_20261004.md)，源码0880e03，[清单](evidence/RESEARCH-index-fields-20261004.json)。既有seq8/9主键索引35个相关vector与native位置/直接字段零差异（14原始undo、12insert、2delete、3purge、3补偿、1维护）；6项研究对账反例拒绝。首轮漏3补偿与验证器8处差异保全。无数据库动作，生产内核076865c不变；下一步完整XID/UBA索引配对及heap+index取消图，尚不证明完整DML或发布。

最新：[W7原始块观察交付](DELIVERY_W7_OBSERVATION_20261004.md)，源码7669d7c，[清单](evidence/DELIVERY-observation-20261004.json)。实际固定member采样journal与新fixture DML/seq17→18切换时间交叠，432帧验证、429归档数据块字节零差异；6真实journal派生反例拒绝。第一轮结束后才DML的编排错误保留，seq16相对journal missing284、rc1。Python3.6时间API和CRLF/LF源码封存失败保留。`capture_complete=false`；未自动跟组/续接/完成record/ACK，W7实验不算捕获验收，CDC拒绝；Java内核076865c不变。

最新：[整事务rollback交付](DELIVERY_W4_FULL_ROLLBACK_20261004.md)，封存源码076865c，[清单](evidence/DELIVERY-076865c.json)。124项测试通过；新seq15/XID2.13.581的120原始/120补偿/0 live与native零差异，20行恢复基线，有限0x14结束与两条短KTB Z完成；10真实结束反例及锁定、8真实Z反例拒绝。同件jar六批长事务756/430/326回归通过，首轮字段索引错误及实际KTB拒绝保留。并发/index/恢复/W5继续待证，CDC拒绝。

捕获问题：[W7执行包](DISPATCH_W7_ONLINE_CAPTURE_20261004.md)，[CURRENT只读探测](evidence/READ-online-open-20261004.json)。专属Linux Oracle运行中、LGWR存在、seq16 CURRENT只读1024字节rc0且读取前后未切换。只证明该次访问，不证明并发写一致性/持续流/Windows共享或ASM；当前归档copy不是ldfcap，W7捕获持久化未实现。

最新：[真实第二次savepoint回滚研究](RESEARCH_W4_SECOND_ROLLBACK_20261004.md)，研究脚本/驱动cfdcdc0，[清单](evidence/RESEARCH-second-rollback-20261004.json)。专属无supplemental新表一次性执行并冻结seq14/XID3.11.671，146原始/70补偿含9跨块/76保留与native零差异，最终两行SQL通过；真实记录阶段120节点→60补偿→20节点→10补偿→6节点。五批合计636/310/326同件既有jar回归通过，生产内核9a2344b不变。首次研究编译依赖漏项和native未来候选误纳失败均保全，验证器按native RBA修正。整个rollback/index/恢复/W5待证，CDC继续拒绝。

最新：[活动窗口slot冲突拒绝反馈](DELIVERY_W4_WINDOW_SLOT_20261004.md)，完整封存树9a2344b，[清单](evidence/DELIVERY-9a2344b.json)。117项测试通过；四批各3份原始归档副本改写完整sequence并重封checksum，12个结构scan均rc0，旧覆盖读取器均rc0，新读取器均`TRANSACTION_SLOT_REASSIGNED_WHILE_ACTIVE`、rc1/stdout0。活动窗口现在审计同segment/slot的完整sequence冲突，合成测试验证失败锁定；不是实际slot复用支持。四批原始240候选/490节点/240补偿/250 live及SQL同件jar回归通过。未做数据库动作；并发归属/其他控制/index/恢复/W5仍待证，CDC拒绝。

最新：[补偿候选覆盖门禁反馈](DELIVERY_W4_COMPENSATION_INVENTORY_20261004.md)，完整封存树952e65d，[清单](evidence/DELIVERY-952e65d.json)。113项测试全通过；内核自主遍历同PDB目标活动record窗口，四批240个5.6/5.11候选与native位置/字段零差异，20项真实清单派生选择反例rc1/stdout0；同件jar490节点/240补偿/250 live及最终SQL回归通过。lab关闭history前强制核对候选覆盖。候选为保守超集，不认证并发归属；其他控制/index/恢复/W5继续待证，CDC拒绝。首轮单测反例偏移错误及失败日志已保全。

最新：[有序分配历史与生效head反馈](DELIVERY_W4_ORDERED_HISTORY_20261004.md)，封存源码8a18e3d，[清单](evidence/DELIVERY-8a18e3d.json)。109项测试全通过；四批490原始节点、240补偿/250 live heap片段与native零差异，seq13最终SQL两行通过；seq10的15条完整前驱继续得到独立页CNT佐证。9项真实派生拒绝与锁定。history只覆盖5.1及调用者已匹配的补偿，不能证明补偿全部收到；下一步主攻完整相关控制/补偿输入门禁，index/恢复/W5仍待证，CDC继续拒绝。

最新：[已分配块head-update反馈](DELIVERY_W4_HEAD_UPDATE_20261004.md)，源码6d30626，[清单](evidence/DELIVERY-6d30626.json)。104项测试全通过；5.2/0x0002新UBA控制与完整活跃身份native零差异，10项真实派生拒绝及锁定。四批490节点、240补偿/250保留patch含31跨块，同件jar回归零差异；seq13最终两行SQL通过。完整生效图/index/恢复/W5仍待证，CDC继续拒绝。上轮网络推送超时，本地交付不代替远端同步证明。

最新研究：[rollback后继续写入](RESEARCH_W4_RESUMED_WRITE_20261004.md)，fixture e322007、研究driver ea0e0d8。[清单](evidence/RESEARCH-resume-fixture-20261004.json)封存seq13/XID10.2.1163。130原始节点、60补偿含8跨块、70保留patch与native零差异，最终两行SQL字节通过；旧三批补偿回归零差异。首次resume使用5.2/0x0002及原分配块record3，prev DBA指向保留head，旧cursor保守拒绝。生产内核未改，完整图/index/新控制/恢复继续待证，CDC零发布。

最新：[原始undo选择覆盖反馈](DELIVERY_W4_UNDO_INVENTORY_20261004.md)，源码9ffc19b，[封存清单](evidence/DELIVERY-9ffc19b.json)。98项测试全通过；内核自己遍历归档生成360个目标5.1节点清单，与native零差异；三个真实漏项选择均rc1、stdout空，三个完整选择通过。同件jar180补偿及旧18pair/4补偿回归通过。覆盖仅限给定输入中的目标5.1，不证明事务图/所有相关opcode/块generation或恢复；CDC继续拒绝。

最新研究：[独立前驱页CNT佐证](RESEARCH_W4_CURSOR_SNAPSHOT_20261004.md)，源码140711e。既有seq10的15个跨块前驱页sequence/CNT与完整cursor零差异，缺节点cursor的首条边被独立页值拒绝。第16页不被跨块previous DBA引用且身份不同，明确保留而不认证。无新数据库动作；事后页面时间一致性/通用raw块覆盖仍待证，生产制品与CDC拒绝门禁不变。

最新研究：[跨块cursor缺节点反例](RESEARCH_W4_CURSOR_HOLE_20261004.md)，研究源码b64a090，生产制品仍0445b43。三个冻结正样本各120节点零差异；各省略一个跨块前驱节点后，119节点仍通过15次局部切块及提交尾检查，但native全量比较均rc1。研究模式刻意调整fixture计数，未修改生产拒绝门禁；证明局部cursor约束不足，完整输入与跨块前驱仍需独立证据。无新数据库动作、CDC零发布。

最新：[普通跨块5.11与多heap block反馈](DELIVERY_W4_CROSS_COMPENSATION_20261004.md)，源码0445b43，[封存清单](evidence/DELIVERY-0445b43.json)。93项测试全通过；三批360pair/180补偿/180保留物理patch与native零差异，23个普通跨块5.11匹配；9项真实派生拒绝与锁定。946物理patch字节及新两批最终SQL值通过。不同heap block的118个历史KTB引用反驳通用前驱替代规则。实验cursor的完整跨块前驱仍未独立认证，index/恢复/W5仍待证，CDC零发布。

最新：[跨块控制身份反馈](DELIVERY_W4_UNDO_ADVANCE_20261004.md)，源码b99828e，[封存清单](evidence/DELIVERY-b99828e.json)。87项测试全通过；长事务120 undo、15显式UBA切换、1扩展提交身份闭合，17个控制/起止字段与native零差异；9项真实派生拒绝、8生命周期锁定。302patch和旧18pair同件jar回归通过。完整跨块前驱/60撤销保留集合仍待证，CDC零发布。

最新：[KDOM2与跨块样本反馈](DELIVERY_W3_KDOM2_20261004.md)，源码6985d9c，[封存清单](evidence/DELIVERY-6985d9c.json)。82项测试0失败0跳过；302个长值正向/逆向物理patch与native dump零差异，末XR与SQL keep-060值一致；6项真实派生反例+15真实跨块入口拒绝。长事务1.1.604跨16块，跨块链接/完整保留集合尚未验收。原seq8/9的18pair同件jar回归通过。此前11原始同块链/32节点与四heap补偿证据继续有效。W5及集成仍待证，CDC零发布。

补充：[事后undo快照拒绝研究及更正](RESEARCH_W4_UNDO_SNAPSHOT_20261004.md)。只读16块dump中，0x02400089页头XID与归档长事务不符，研究器按未证页头身份假设保守拒绝，rc1、stdout空，不能据此证明页面复用。0x02400088的raw原始节点已为sequence0xe7，上一版称序号变化错误已更正。正式制品不变，继续从raw归档论证跨块链。

最新：[5.11 begin补偿反馈](DELIVERY_W4_BEGIN_COMPENSATION_20261004.md)，源码d3f07b3，[封存清单](evidence/DELIVERY-d3f07b3.json)。72项测试0失败0跳过；四次5.6/5.11补偿原操作与dump零差异，保留row7四列与SQL字节一致，缺归档反例stdout空。index/完整undo链、恢复及W5仍待证，CDC零发布。

最新：[5.6补偿关联反馈](DELIVERY_W4_COMPENSATION_20261004.md)，源码0c5ff4c，[封存清单](evidence/DELIVERY-0c5ff4c.json)。66项测试0失败0跳过；T007撤销原undo36 INSERT8/35 UPDATE，保留undo33 INSERT7，与dump及SQL row7四列字节零差异。两个缺archive反例均rc1且stdout空。5.11、index/完整undo链、恢复及W5仍待证，CDC零发布。

最新：[精确行/undo反馈](DELIVERY_W3_PAIR_20261004.md)，源码3e1b5bc，[封存清单](evidence/DELIVERY-3e1b5bc.json)。57项测试0失败0跳过；18对、48正向/10逆向出现列与dump零差异；6项真实record内存派生反例全部拒绝。完整行、活跃事务集成、savepoint撤销与W5仍待证。

最新：[逆向行片段反馈](DELIVERY_W3_UNDO_ROW_20261004.md)，源码3418fb9，[封存清单](evidence/DELIVERY-3418fb9.json)。51项测试0失败0跳过；18个5.1逆向patch/10出现列与dump零差异；同件jar回归21个旧物理patch/45出现列零差异。完整before行、XA/undo配对、补偿链与W5仍待证。

最新：[有界身份生命周期反馈](DELIVERY_W4_LIFECYCLE_20261004.md)，源码8859c7d，[封存清单](evidence/DELIVERY-8859c7d.json)。46项测试0失败0跳过；11个身份（9commit/2rollback/1跨archive）与dump零差异；缺开始、缺结束、重复archive均rc1且stdout空。行变化、savepoint撤销、undo链与W5仍未验收，CDC零发布。

最新：[起点身份绑定反馈](DELIVERY_W4_BEGIN_20261004.md)，源码0b36846，[封存清单](evidence/DELIVERY-0b36846.json)。36项测试0失败0跳过；11个目标表5.2与唯一同record5.1 begin精确UBA绑定，dump零差异。结束/补偿链、跨归档连续性及W5仍待证。

最新W4：[结束/补偿字段反馈](DELIVERY_W4_MARKERS_20261004.md)，源码b188709，[封存清单](evidence/DELIVERY-b188709.json)。30项测试0失败0跳过；同件jar对86条5.4、5条5.6、2条5.11与dump零差异。5.11 wrap1不等于事务sequence；commit vector可能位于undo vector之前。没有事务关联或CDC验收。

最新进展：[W3首轮反馈](DELIVERY_W3_PATCH_20261004.md)，已推送ea709ae；[同提交树清单](evidence/DELIVERY-ea709ae.json)。24项测试0失败0跳过；同19c seq8/9的21个物理patch/45个出现列、18个undo header与dump零差异。KTB L为旧ITL、XR补偿可恢复旧UBA，不能据此猜当前事务。完整CDC仍零发布，W4/W5继续。

1. 先读19c fixture stdout/stderr/commands/manifest，失败不得重跑已提交DML；修复只读export或用新fixture。
2. 冻结同一输入做19c scan/record/vector + dump独立对账，保留失败目录。
3. 按[W3-W5 DISPATCH](DISPATCH_W3_W5_NOSUPP_20261004.md)攻row/undo/事务关联，优先证明缺主键/ABSENT与NULL区别及rollback补偿，不先做GUI。
4. 有源码与针对性证据后立即提交、推送反馈；后续HIGH门禁不被初始代码交付替代。
5. 长目标保持active；恢复接续automation id=`oracle-redo`，每小时绑定本chat检查。额度未恢复/无行动变化保持安静；用户暂停/取消时停止接续。当前模型要求保持，禁止Luna。

技术方案：[Oracle Direct Redo](../Oracle_Direct_Redo_技术方案_v0.1_20261004.md)。长期目标、真实DML、真实capture、目标写入、独立评审、GA均未完成。
