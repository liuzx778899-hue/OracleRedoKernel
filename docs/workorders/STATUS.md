# OracleRedoKernel 当前状态

2026-10-12 边界解释错误已纠正：用户要求降低开发验证强度至场景覆盖，未授权替换功能范围；同一undo节点内部分行撤销仍需对应场景，不能用raw123整节点重建/业务部分成功关闭。受控同SCN场景可用于开发覆盖，不再追特殊实日志。负责人不得自行增加封版门、替换功能或反复要求用户降低门；已通过项冻结复用。当前内核候选针对性打包成功仅是构建进展，不标缺失功能已通过或整体可交付，第一版仍按原功能范围、有限场景覆盖推进。

2026-10-12 最新封版要求覆盖此前两项实样本硬门：用户明确“两项做到场景覆盖验证就行”，开发不追求100%验证，优先让第一版整体测试开始。停止节点内部任意物理格式和特殊真实同SCN样本追逐；保留ARRAY部分失败有效行/恢复、受控同SCN不同事务排序/恢复/分窗发布的固定场景覆盖，证据真实/构造类型如实记录，未证格式不宣称支持。当前转入固定源码/内核/适配器/产品候选与启动交付，不再追加验证矩阵。此前两项必须真实证明再合并的状态为历史时点，当前以本条及执行单第一版边界为准；D5/RAC/ASM/TDE继续搁置。

2026-10-12 第一版管理边界纠正：用户强调框架、功能主链和正常场景覆盖优先，开发不追求100%验证；已在原执行单固定起点、开发终点和取舍，不再另加封版门/长报告。用户再次保留节点内部部分撤销、真实同SCN两门，未通过不合并main，不再提议降低门。已有通过证据复用；剩余仅围绕两项实现/真实定向场景及正式固定制品/一次正常E2E/合并交付推进。长期负载、容量性能、现场平台差异、极端并发和组合故障进入部署后/模拟生产；实际漏数、重复、错误提交/恢复仍须修。D5/RAC/ASM/TDE搁置。此前4–8天条件估计不作为有依据的封版排期。[第一版执行边界](DISPATCH_COMMON_FUNCTIONS_D5_20261010.md)。

2026-10-12 LOB 已闭合事务的正常产品恢复/回收链通过：冻结 native acquisition 复用，raw120迁移→raw121→raw122三窗口各PUBLISHED/rc0/1完整UPDATE，目标APPLIED/COMMITS/事件账本均3，最终完整BLOB/CLOB值一致；产品服务校验目标提交、producer、checkpoint与ACK，实际删除raw120/raw121运行时副本2份/53,785,600字节，仅保留raw122。最后独立进程启动恢复/再次回收rc0，新增发布0、删除0，切点不变；源业务DDL/DML0，自有源停止rc0，原始冻结样本保留。首轮metadata序列化拒绝rc2/0事件完整保留，登记器改用产品Long序列化配置后走原失败任务正常resume。证据在 `lab/out/lob-unified-20261012/product-runtime-r1/proof.json`、四次resume发布/恢复rc、actual-1/2及最终开发制品清单。此为明确标识的开发覆盖包和已闭合LOB链，不是正式固定依赖/部署/发布验收，也不宣称pending LOB或无限context压缩支持。ARRAY节点内部部分撤销、真实同SCN及正式统一交付仍待证；用户两项通过后才合并main的门保持，未合并，D5/RAC/ASM/TDE搁置。

2026-10-12 用户确认 main 合并门保持原要求：必须先通过“同一 undo 节点内任意部分行撤销”和“真实两笔业务事务相同 commit SCN”。raw123 的 18 行来自整节点撤销后重建，仅证明 ARRAY 重试及独立 JVM 恢复（rc0/18事件），不能代替节点内部部分撤销；raw124 两会话32次业务提交、SQL共33行，未出现相同提交 SCN，仍待证，不重跑同一实验。LOB BasicFile/SecureFile/迁移后三条冻结未来链及 acquisition 身份断言已通过，相关失败原文保留。产品专属验收库正执行冻结 native acquisition 复用→实际目标/CPACK→readCut→运行时副本删除→重启链；采用明确标识的开发覆盖包，尚非正式固定依赖交付。未合并 main，四项总目标继续，D5/RAC/ASM/TDE搁置。证据：`lab/out/array-subnode-20261012/`、`lab/out/same-scn-20261012/`、`lab/out/lob-unified-20261012/product-runtime-r1/`。

2026-10-12 单作者续接开发：两处接口编译错误已修复，LOB已接统一producer登记/恢复/审计/readCut；冻结raw120迁移的无raw完整恢复与新切点再次恢复通过，独立JVM恢复rc0/1事件，state与batch一致（batch 21818e2c84f8a1b7d634895ee511568d3a3c269752476183bf58fbaa4ba23562），新切点leaf50380779。现有非LOB协议14项针对性回归通过。仅现有专属fixture新增6次UPDATE，raw121/122已冻结，独立SQL完整值一致；源停止rc0。未来链首轮在raw121 validity0结构门拒绝，native限定SCN只读取证确认紧邻CDB-root失效记录未列入dump，新增已观察root 5.1+11.2有限结构门，三种存储/代际未来链复验进行中，失败原文保留。StreamHouse仅接V7分派与切点新leaf/schema，未构建/部署/验收；实际目标/ACK/回收、ARRAY节点内部分行、真实同SCN及main统一交付仍未完成。证据统一在lab/out/lob-unified-20261012及迁移目录unified-producer-r1/r2，不另产局部交付包，D5/RAC/ASM/TDE仍搁置。

2026-10-12 进度与方向审计：迁移正常完整事件/独立JVM恢复已经通过，但统一LOB恢复尝试尚未接入口，最新工作树的一次编译检查rc1（ReadCut构造参数未接、调用私有lob方法），不是可交付候选。确认有逐个首报错修补及过度局部拆分，新增capsule/V5/V7方案尚未证明回收后的来源完整性与产品兼容，先停止扩展并评估最小主链。剩余仍为LOB统一恢复回收、ARRAY节点内部分行、真实同SCN、产品及main交付四块；建议换到新单作者窗口，不重跑已过迁移/源脚本。审计交接见 [HANDOFF_PROGRESS_AUDIT_20261012.md](HANDOFF_PROGRESS_AUDIT_20261012.md)。本次只做检查/交接，未提交、部署或启动实库，D5/TDE搁置，未创建新chat。

2026-10-12 用户指出收尾过慢、过度局部验证：确认整体仍剩LOB统一producer/checkpoint/ACK/readCut/回收、ARRAY节点内部分行、真实同SCN恢复发布、StreamHouse及main交付四块，不能再称“最后一点”。迁移正常完整链已在本轮打通：raw120的16笔SYS事务/477条original、新heap/PK/两SecureFile LOB绑定、19.1直接加载完整块及所属事务/存储控制均接入；迁移前后完整值与SQL一致，只发布迁移后业务UPDATE1条，内部复制不发布。独立JVM恢复与直接读取相同，批次SHA256 `21818e2c84f8a1b7d634895ee511568d3a3c269752476183bf58fbaa4ba23562`，毒化字典状态拒绝；B/S已通过的TRUNCATE仍有效。完整入口测试migration-event-tests-r3 rc0及migration-events-r6 rc0，不等于统一LOB恢复回收已完成。转向统一主链，不再扩迁移边界测试或交付局部包；源DDL/DML0，新改动未提交/部署，D5/TDE搁置。

2026-10-12 统一收尾继续（开发未结束）：冻结raw120迁移字典完整回放已通过16笔相关事务/477条original，新增新表TAB$/COL$创建、分配/默认回填、LOB$分配更新和统计历史两索引，以及OBJAUTH$失败插入撤销后的重锁。列/对象/索引引用毒化检查保留；完整入口r40已恢复16次提交，最后因迁移后入口仍绑定旧对象编号而拒绝，正在补新heap/PK/LOB绑定和迁移后完整事件。源DDL/DML0，仅自有库只读核对统计布局，停库rc0；已有TRUNCATE证据复用。统一LOB producer/checkpoint/ACK/readCut/回收、ARRAY节点内部分行、真实同SCN产品链和main交付仍在同一收尾包内，未提交/部署，D5/TDE搁置。证据：`lab/out/lob-migration-20261011/` 的selected-metadata-survey-r1、lob-allocation-tests-r1、statistics-history-tests-r1、migration-dictionary-entry-r39/r40及metadata-relations-r6；原失败保留。

2026-10-11 统一收尾继续（开发未结束）：迁移raw120的结束事务201条original已越过物理解析与闭合检查；快照元数据/索引、内部索引删除、CON$两组编号交换及行引用迁移、CDEF$更新已接入。按原始OBJ$编号追溯前置创建及版本事务，13个相关owner共452条original清单可读取；两笔OBJAUTH$失败插入→5.11撤销→重新锁定已有行的零前驱路径增加完整物理配对与恢复检查。相关45项首次44通过/1仅测试封套SHA格式错误；修正后新增2项通过，原错误保留，BasicFile/SecureFile TRUNCATE已通过证据继续有效。完整迁移入口现到前置CCOL$依赖32/29，尚未发布迁移后业务事件，统一LOB producer/checkpoint/ACK/readCut/回收及其它收尾项继续，不标完成、不提交局部交付。仅只读补查SYS布局/索引关系，专属库及Desktop均停止rc0；源DDL/DML0，新代码未提交/部署，D5/TDE仍搁置。证据在 `lab/out/lob-migration-20261011/` 的snapshot-metadata-tests-r5、index-cluster-tests-r1、constraint-exchange-tests-r1、constraint-definition-tests-r2、object-owner-closure-tests-r1、authorization-retry-tests-r1/r2、object-owner-closure-probe-r2、migration-dictionary-entry-r13及metadata-relations-r3/r4；失败轮次保留。

2026-10-11 本轮继续开发（未收尾）：冻结117–119的BasicFile/SecureFile完整TRUNCATE入口均通过；每类4事件（ADD UPDATE、DROP UPDATE、TRUNCATE清旧行DELETE、新代际INSERT），独立SQL最终只留新行且完整值差异0。字典代际V2往返与错误绑定拒绝通过；两独立JVM最终恢复各4事件，与直接读取相同，批次SHA256为B `40425821f32fee302d36fd234a4ffb95f15f56f30116b90e4d021f968ec103aa`、S `71390ecf571f1eba77317f8a1cb1515d854776b0e0747d520cd487118719fafa`。这不包含LOB统一producer checkpoint/ACK/readCut/回收；相关收尾目标仍保留。迁移raw120的OBJ$登记/版本变化、六次名称置换与SYS36/37/39/40新旧引用、SYS81延迟建段及SYS82引用已解析，正在接入完整历史入口，未标迁移或迁移后事件通过。源DDL/DML0；仅启动专属库读取SYS列布局后停库rc0，Desktop已停；清理日志解码失败原文保留。新改动尚未提交/部署，旧证据复用，D5/TDE继续搁置。最终恢复证据在 `lab/out/lob-history-20261011/offline-r1/*truncate*final-r5*`，迁移过程与原失败在 `lab/out/lob-migration-20261011/`。

2026-10-11 实际开发续接：新增 SYS39 DATAOBJ#/TYPE#/SPARE3/ROWID 物理配对（保留 NULL DATAOBJ#）、SYS OBJ$ 存储25列的五列更新、TRUNCATE空间头flags128及受控bitmap首根配对。raw119 B/S共8处heap/PK/BLOB/CLOB代际对应通过；raw120 SYS39共33条配对通过；B/S原始undo图分别44/78条闭合。相关44项通过，受影响恢复/LOB测试执行19项通过、1项条件跳过。源动作0；这仍是解析依赖补齐，未发布事件、未部署。完整TRUNCATE入口现在拒绝未接入的旧PK空间依赖80999/80999，迁移全事务、LOB统一恢复回收、ARRAY节点内部分行、真实同SCN产品链及main统一交付继续开发，不标完成。原始命令/rc在lab/out/lob-history-20261011/offline-r1的generation-root-tests-r2、generation-affected-tests-r1、B/S-generation-r1、B-truncate-graph-r1、S-truncate-graph-r2、B-truncate-entry-r2，以及lob-migration-20261011/data-object-index-profile-r4；失败轮次保留。

2026-10-11 用户要求一次安排、整体收尾：ARRAY部分行撤销、LOB历史对象变化、LOB统一恢复回收、真实同SCN恢复发布、产品及main统一交付五项合成一个收尾包。中间只报进度，全部完成后统一交付；不再按局部测试通过结束整轮。D5/TDE搁置，已过证据复用。具体完成判据与执行顺序见[统一执行单](DISPATCH_COMMON_FUNCTIONS_D5_20261010.md)。

2026-10-11 用户再次明确 D5 搁置：RAC/ASM 不纳入本轮开发收尾，不自动恢复；TDE 继续搁置。执行单已同步覆盖旧顺序。常规功能继续，当前开发尚未结束。

2026-10-11 LOB迁移解析续接：raw120复用/源动作0，补真实 SYS 空间扩展、重定义多对象标记及 OBJ$ 五列UPDATE/25与26列INSERT配对和完整值边界；相关68项通过/失败错误跳过0，B/S原ADD→DROP完整2事件及摘要不变。package-r2 rc0/内核b6bb1a25，未部署。迁移完整入口仍拒绝 SYS 索引依赖36/36，完整对象切换、TRUNCATE、统一恢复回收与产品新链继续，不标迁移通过。[反馈](DELIVERY_LOB_HISTORY_20261011.md)。

2026-10-11 LOB ADD/DROP正常链：BasicFile/SecureFile各2完整UPDATE、值差异0，两独立进程恢复与直接解析完全一致/毒化拒绝；43项执行39/跳过4、失败错误0，package rc0/内核26284ca9，未部署。存储迁移源动作一次已成功，raw120冻结/库和Desktop停；TRUNCATE及迁移解析仍有明确缺口，统一checkpoint/回收/产品新链继续。用户要求能验证的全部收口后开发结束；当前尚未收口，不标完成。D5继续搁置。[反馈](DELIVERY_LOB_HISTORY_20261011.md)。

2026-10-11 用户补充 LOB 存储迁移为必验项：先覆盖 BasicFile→SecureFile，连同迁移后正常 DML、历史对象代际、完整值、独立进程恢复及 checkpoint/ACK 约束下的安全回收；不以 TRUNCATE/静态 LOB 证据替代。迁移各环节待证，原五项目标不变，D5搁置。验收范围已同步[执行单](DISPATCH_COMMON_FUNCTIONS_D5_20261010.md)。

2026-10-11 ARRAY UPDATE补偿续接：raw116复用/源动作0，最后KTB守卫重新编译，34项相关回归通过；projection-r5只发布保留事务3条完整UPDATE，独立after.snapshot完整值差异0/撤销事件0，两独立JVM恢复与直接读取及投影证据完全一致。package rc0/内核f02b15ca；本候选未部署。任意节点部分行及LOB历史/代际/恢复回收继续开发，五项未全完，D5搁置。[反馈](DELIVERY_ARRAY_UPDATE_ROLLBACK_20261011.md)。

2026-10-11 同SCN离线衔接：不同XID/相同commit SCN/反向开始顺序，提交位置排序→checkpoint恢复→事务边界发布后另一个事务仍保留，颠倒顺序拒绝；25相关项＋最终受影响类5项通过，生产代码不变，无重复制品/部署/源动作。合成测试，不冒充实库同SCN产品双窗；LOB静态schema/切点拒绝仍待补，ARRAY未覆盖撤销继续。容器/Desktop已停，五项未全完，D5搁置。[合并反馈](DELIVERY_ARRAY_TEMPORAL_PRODUCT_20261011.md)。

2026-10-11 时间新产品链通过：62df082/app fca25207，完整862项通过；原HTTP基线及115复用，源动作0，3完整UPDATE/target3/3/1/3，九列BC/纳秒/区域/LTZ差异0，实际CPACK同字节。ARRAY原20条产品证据复用；源已停/helper移除，同SCN恢复与LOB历史回收及ARRAY未覆盖撤销继续，D5搁置，五项未全完。[反馈](DELIVERY_ARRAY_TEMPORAL_PRODUCT_20261011.md)。

2026-10-11 新产品接线：最新main隔离候选完整862项通过；认证HTTP后ARRAY20事件，目标20/20/1/20、完整行差异0、实际CPACK同字节。时间新UPDATE3已冻结115，九列后缀拒绝修复，25相关项通过/离线3完整事件；新候选时间目标CPACK续接待证，源DML不重放。库/helper/Desktop停；同SCN恢复与LOB历史回收继续，D5搁置，五项未全完。[反馈](DELIVERY_ARRAY_TEMPORAL_PRODUCT_20261011.md)。

2026-10-11 ARRAY部分执行失败/pending跨窗：一次新实验raw112/113冻结，20堆行/10索引键失败后补偿及savepoint逐行UPDATE撤销，补齐10键位图/ITL2/Z填充；首窗pending1/事件0，独立JVM续读20唯一INSERT/SQL完整值差异0/撤销事件0，与直接历史工厂相同，34项相关测试通过。库/helper/Desktop已停；任意节点部分行及11.19回滚未证明，产品新链及其它四项继续，D5搁置。[反馈](DELIVERY_ARRAY_PARTIAL_RECOVERY_20261011.md)。

2026-10-11 ARRAY已提交状态恢复：冻结raw108复用/源DML0，修复同undo位置多行误判重复，两独立JVM V6状态恢复20事件与直接读取及投影证据完全相同；12项相关回归失败0。部分行补偿/pending跨窗、同SCN恢复发布、LOB历史回收及产品新接线仍待；五项未全完，D5搁置。[续接反馈](DELIVERY_ARRAY_ROLLBACK_20261011.md)。

2026-10-11 时区正式链：V4原生获取→raw111 ARRAY UPDATE→3完整事件→真实JDBC首次3/重试0，target3/3/1/3，Paris区域/冬夏令/负偏移、LTZ与9位纳秒独立SQL差异0，再获取6字节值差异0。11.19/KDO21三行同undo提交解析补齐，49内核/10适配器相关测试通过；目标核27044/最终核04c7de81事件证据不变，适配器ddf291e4。库停/helper删除/Desktop停；时间新HTTP CPACK/产品新依赖及ARRAY部分补偿恢复、同SCN恢复发布、LOB历史回收仍待，五项未全完，D5搁置。[合并反馈](DELIVERY_TEMPORAL_VARIANTS_SCALARS_20261011.md)。

2026-10-11 区域/LTZ上下文前置：原raw109复用/只读源DML0，两会话原生TSTZ/LTZ共18字段字节差异0，DBTZ +00:00/时区文件32/595区域映射已冻结；内核上下文解析及V4可信帧/schema绑定完成，相关37项通过，源停/helper删除/Desktop停止。正式V4获取输出、时区投影/目标/HTTP CPACK仍待，不标时区功能完整支持；五项目标继续/D5搁置。[续接反馈](DELIVERY_TEMPORAL_VARIANTS_SCALARS_20261011.md)。

2026-10-11 BC/精度正常JDBC链：原生datum获取、raw110后缀布局与隐式PK NULL$1补齐；基线后1完整UPDATE，44BC/1BC及精度0/3/6/9独立SQL差异0，真实首次1/重试0、目标1/1/1/1六列差集0；BC再获取原始字节差异0。内核84774209/适配器6ce27a89、相关40＋7项通过，库停/helper删除；区域/LTZ上下文、新HTTP CPACK与其余五项缺口继续，D5搁置。[反馈](DELIVERY_BC_PRECISION_JDBC_20261011.md)。

2026-10-11 时间变体首包：专属raw109 3行/24字段字节核等，BC/精度0/3/6/9及偏移时区18解析值/SQL差异0；内核03135a33/适配器d2c561a4，35内核回归＋2投影测试通过，源停rc0/Desktop停止。BC纪年/纳秒/偏移解析和非LOB九列IRP补齐；获取层、region/LTZ上下文、完整事件/真实目标/HTTP CPACK待补，不能标时间类型完整支持。其余五项目标继续，D5搁置。[字节解析反馈](DELIVERY_TEMPORAL_VARIANTS_SCALARS_20261011.md)。

2026-10-11 ARRAY回滚首包：raw108完整入口输出20唯一INSERT、仅ID200–219，完整值差异0/撤销事件0；整笔回滚与保存点撤销完整节点通过。113受影响回归及最终补偿门禁13项相关复查失败0，旧raw107 envelope未变；候选5f459036888a。仅离线开发，库保持停止；部分行补偿/pending恢复、同SCN恢复发布、LOB历史回收、时间类型及新产品接线继续，D5搁置。[首包反馈](DELIVERY_ARRAY_ROLLBACK_20261011.md)。

2026-10-11 用户收窄当前目标：完成 ARRAY 回滚/部分行补偿、相同 commit SCN 排序恢复、LOB 历史恢复回收、时间类型变体及 StreamHouse 新能力正常端到端后开始模拟测试；D5 RAC/ASM 暂时搁置，不作为阻塞。新 ARRAY 回滚样本已冻结：SCN3225957、heap80544/index80545，raw108 SHA256 `716ac017f4a5c6c5c034046830fc820eac089e58fbb277bc3cfecd7f70232ecb`、7784960字节；整笔回滚与保存点回滚后 SQL 仅保留20条新行，raw/native 核对发现 11.12 QMD 批量补偿、10.3 多键PURGE及5.6三字段布局。当前只完成取证，事件解析/恢复/目标链未通过；原始失败保留，source/native停rc0。已有 commit SCN+提交位置排序实现复用，重点补同SCN恢复与发布核验。

2026-10-11 官方普通 RAC 镜像 `database/rac:19.3.0` 已获用户接受条款并拉取到 E 盘独立 OracleRedoD5 引擎，摘要 `e60fdd7428187383e62b1a28fe25fff1c3fa1e21ea7ee2dde43acc9d9367876a`；pull rc0，Grid crsctl.bin/DB oracle 文件检查及 SQL*Plus 19.3.0.0.0 版本检查 rc0，临时容器移除、WSL 停 rc0。`rac_ru` 页面明确当前账号无 CPU 镜像访问权限；首轮 TLS 超时及旧镜像检查命令修正过程保留于 E:\OracleRedoKernel-lab\d5\rac-pull-r2\delivery.md。RAC/ASM 尚未配置/验证，旧版 OL7 与 WSL cgroup/systemd 兼容性待实测，D5 未通过；下一步双节点/共享 ASM/真实多 thread redo，普通功能原待项继续。

2026-10-11 D5两个19.3安装包均在E盘核对官方SHA通过；OL8首次安装INS-08101/build rc127原文保留，改用官方19.3 Dockerfile默认OL7后Grid/DB软件安装及RAC relink已有成功输出。用户要求释放本机内存，已停止全部8个Desktop业务容器、Docker Desktop及专属D5 WSL；镜像层提交期间主动停止/build rc1、WSL停rc0，缓存及数据保留，未启动RAC/ASM、D5未通过。内存即时值约68%/可用19.8GiB；后续先恢复镜像并检查二进制，再做真实接入，普通功能原待项继续保留。[环境反馈](DELIVERY_D5_MEDIA_BUILD_STOP_20261011.md)。

2026-10-10 基线后ARRAY INSERT正常链：新原始107输出20唯一完整事件，独立SQL差异0；实际JDBC首次20/同批重试0，目标20/20/1/21及双向差集0，48项受影响回归通过。候选e2575809138a，源停rc0/helper移除；一般ARRAY回滚及新产品HTTP/CPACK仍待。用户授权E盘Docker D5实验；Grid官方SHA核等，E盘独立WSL/Docker及loop块设备前置通过后已停止，Database Home包待拷入，RAC/ASM真实接入未通过。LOB历史恢复回收/类型变体继续；[反馈](DELIVERY_ARRAY_NORMAL_D5_PREP_20261010.md)。

2026-10-10 常规九列混合LOB产品接线完成：同件c8382f8/app a49de18c，完整产品861项通过；BasicFile/SecureFile新HTTP获取后各1未来TXT变化，target/ledger均1/1/1/1、九列完整值零差异，实际CPACK/ACK字节一致。内核3a9ccc651b60/适配器87eb1e9064de固定坐标已装本地Maven；主仓新装Oracle V729–731八处DEFAULT语法修复并实库通过，原失败保留。源停rc0/helper删除，主仓合并/部署清单继续，ARRAY已启动但完整链待证、LOB历史回收/D5待；[反馈](DELIVERY_COMMON_PRODUCT_20261010.md)。

2026-10-10 常规混合LOB首包：BasicFile/SecureFile九列各1基线后事件、完整值零差异，真实JDBC目标各1/1/1、独立提交核验与恢复复读新增0；原三列8项及相关生命周期24项通过，库停止rc0。开发候选内核3a9ccc651b60/适配器87eb1e9064de，新产品HTTP/CPACK/固定适配器坐标及部署待证，ARRAY/LOB历史回收仍待，D5尚未启动。[首包反馈](DELIVERY_COMMON_MIXED_LOB_20261010.md)。

2026-10-10 用户授权新顺序：先补常规混合大字段、ARRAY/同commitSCN、类型及LOB历史/恢复回收功能，正常链闭合后推进D5 RAC/ASM读取与thread接入；长期/压力/现场等后移模拟生产，TDE继续搁置。混合LOB首包代码开发与原冻结受影响回归已启动，新样本/产品链及新制品待证，D5尚未启动；[执行单](DISPATCH_COMMON_FUNCTIONS_D5_20261010.md)。以下旧“不启动D5”为此前授权时点，不覆盖本次顺序授权。

2026-10-10 集中裁定：D1–D4已实现能力本轮有限测试环境已验收。原P2修复独立复核及最终终态只读评审PASS；app-r4/7eaca10回收批准101/102共419431424字节、保留103、新进程恢复/毒化409零删除及精确恢复通过，目标4/4/4/4、20行匹配、ACK不变。已通过证据复用，本窗口无实验/构建/源DML；生产现场/发布与完整一般能力仍待，未批准OPEN约7.13GB不算总存储有界。D5不启/TDE Issue#2搁置。 [最终合并反馈](REVIEW_TEST_ENV_FINAL_CLOSEOUT_20261010.md)。

以下保留历史时点；本派单已收尾，无待执行实验，最新裁定以上述反馈为准。

2026-10-10 最终修复候选7eaca10/app6b26d085实库闭环通过：切点102319102395412480/SCN3160456保留103，raw派生snapshot SHA91fb4cdabdee1b03f262c3f781131701a662505fdf0d97dfde773c4f076cb163；只回收批准101/102两副本419431424字节。独立新进程水位103/目标4/4/4/4/20行一致；切点毒化业务409拒绝、零删除、精确恢复原snapshot，ACK不变/新增DML与事件0。final-closeout.rc0，helper删除/库停止rc0。36→34文件、约7.55→7.13GB：未批准OPEN观察文件仍占用，不能宣称全部中央存储有界。已完成代码独立评审及既有D1/D2/D4/背压/两未来窗原CPHEX与实际ACK集中只读核对；最终本次终态独立核对与统一验收结论待收尾，不新增实验，D5不启。

2026-10-10 第二条未来窗口实际闭合：raw-stream-21002-4 / batch102311534864363520，CONSUMED/PUBLISHED rc0/1事件；目标applied/member/commit/ledger 4/4/4/4、20行与规范化完整前后像一致，实际DONE任务CP与ACK逐字节相同，producer2f032ce7读至103/pending0。future-window-2-proof.json、windows-r10-verify.rc0；两条新UPDATE各执行一次/旧DML重放0，helper清理/源停止rc0。独立评审P2修复7eaca10已推送，16相关测试通过、复核通过；正式app-r4 SHA256 6b26d0852e15b6b6227f43b734f3c437911f971d2f38e5a507648aa648e5f32f 构建rc0，尚未运行该修复候选。剩余只做新切点保留103/回收批准101与102、一次恢复及切点毒化拒绝，再统一验收反馈；不重复已通过窗口，不启动长期平台/性能调优/D5。

2026-10-10 独立只读评审发现并修复回收前置条件：已消费不等于已封存。删除前要求所有引用受删raw的历史绑定均属于已逐项核验SEALED_COMMIT的前驱链；链外有限窗口拒绝回收。修复独立复核通过，相关16项测试rc0；正式修复制品/实库回收尚待。第二条未来UPDATE ID2仅执行一次，103已闭合并登记窗口4；当前GATED rc0/1事件、任务RUNNING，目标/CPACK待闭合。观察超时保留运行进程，未重放源DML，不标测试环境验收。

2026-10-10 首条未来正常产品窗已闭合：core e3ac633/产品37ee409/app3e90524a，raw-stream-21002-3发布1事件rc0/CONSUMED，目标账3/3/3/3、20行/完整像零差异；实际DONE任务3605字节CPACK一致，producer5b1914f2读至102/pending0。首条源UPDATE未重放；观察脚本JSON筛选、Path拼接与停止超时原文保留，库停止/固定helper清理。第二窗与下一次回收/切点毒化继续，独立评审待证；不标测试环境验收通过。 [统一反馈](DELIVERY_CENTRAL_READ_CUT_20261010.md)。

2026-10-10 正常续接冻结解析已过：不重放首条未来 UPDATE，闭合102后补实际 SYS KTB/闭合头37、TAB$范围/单对象marker读取缺口；相关14+30项通过，101/102完整入口唯一UPDATE ID1/NOTE anchor→readcut-next-1、完整像/pending0，next-projection-r3 rc0。有界原生诊断只供验证，库已停。正式制品更新及产品新窗目标/CPACK、第二条正常窗、后续回收与独立评审尚待；未标测试环境验收通过。 [合并反馈](DELIVERY_CENTRAL_READ_CUT_20261010.md)。

2026-10-10 中央redo可信恢复切点有限闭环：core d2b1ea3/产品3658012，原100/101生成SCN3090136保留101；单独中央副本100实际回收200MiB，缺ACK拒绝；删除后独立进程范围101–101/发现仅101、重复回收0，目标账2/2/2/2与原raw/ACK不变，resume-r2 rc0/清理rc0。首次JDBC核验重读旧raw拒绝保留，已修接线并通过相关14项；52项及切换类7项证据复用。正常未来两窗继续执行，独立评审因子代理额度不足待证；集中测试环境验收未过，长期平台/生产现场/D5不启动，TDE Issue#2搁置。 [进展反馈](DELIVERY_CENTRAL_READ_CUT_20261010.md)。

2026-10-10 用户授权测试环境验收前优先任务启动：先实现中央redo可信恢复切点及安全回收，再做正常多窗口连续运行、集中评审/测试环境验收。现阶段开发中，旧通过证据复用；长期平台/生产现场/D5不启动，TDE Issue#2搁置。[执行单](DISPATCH_TEST_ENV_CLOSEOUT_20261010.md)。

2026-10-10 D3保留范围/源队列背压有限闭环通过：复用提交链/CPACK/producer核验，实际范围100–101仍保留，缺ACK拒绝；认证完整SHA/长度及文件/父目录同步后仅释放源冗余副本。220 MiB容量满→交接→释放→自动续采在PID181实证；观察超时后PID127只完成已排队101，三副本释放/队列0，不宣称整轮同PID。旧失败partial独立诊断保留、不入raw；中央raw/原ACK/目标账2/2/2/2未变，源DML/新获取/事件0。相关Java37及C++两程序通过；app f5cda704/agent033bf2f5，内核/适配器原件。清理rc0/库exited，当前已有能力收尾包交付；中央前缀回收/长期平台/ARRAY与独立评审、生产部署/现场/发布仍待，D1–D4整体未完。D5未启/TDE Issue#2搁置。 [简短反馈](DELIVERY_D3_RETENTION_20261010.md)。

2026-10-10 D2/D4已实现能力尾项：复用同件d028ca54，原可信HTTP基线GET核等后，86/87初始水位3事件和88/89同key5事件实际target3/3/1及5/5/1、完整像/最终行零差异、producer及CPACK逐字节一致。RAW/DATE/无时区TIMESTAMP正式HTTP获取与同SCN独立SQL核等，原各4事件产品证据复用；新获取不批准旧raw、不冒充新的HTTP基线后发布场景。源DML/配置0，无全量重建或故障矩阵；helper移除/源停止rc0，库exited。独立评审/生产部署/现场/发布待证，D1–D4整体未完；下一D3保留水位/背压，原raw恢复前缀仍需保留。D5未启/TDE Issue#2搁置。 [简短反馈](DELIVERY_D2_D4_TAILS_20261010.md).

2026-10-10 D3在线闭合代际有限正常接入通过：固定online redo三个普通成员，真实100/101闭合、认证C++传输、自动登记及两窗发布rc0/各1；target2/2/2、ledger/gate各2，20行/完整前后像零差异，实际CPACK同字节、首窗ACK未变，producer sequence100→101/published1→2/pending0。核心01feae8/脚本301f51e、产品99d6b1b，最终候选d028ca54续接原首窗。新基线1/未来源UPDATE2/旧DML重放0；开放快照等待、闭合头36/172重复保留首件，只补实际读取缺口。队列满/变化摘要失败及源锁重复读取实际延迟保留，保留低水位/持续容量背压/平台长期运行待补；有受控重启，不宣称全程同PID。日志保存、helper删除、库exited；独立评审/目标、生产部署/现场/发布未验收，D1–D4整体未完，D5未启。[简短反馈](DELIVERY_D3_ONLINE_NORMAL_20261010.md)。

2026-10-10 本轮D1–D4最小收尾完成：旧通过证据复用，LOB新增可信完整获取/投影/JDBC及正常产品链；BasicFile/SecureFile各一条未来窗口新变化，认证HTTP获取、target1/1/1、完整值零差异、实际CPACK逐字节核等，旧16事件发布0。SecureFile真实99锁布局针对性补齐，最后相关17通过；最终候选对BasicFile原投影全部核等，不重复目标提交。代码4b44c94/产品6ac52b1，最终集成r21 rc0；日志保存、固定helper删除、专属数据库停止。独立目标/评审/约定生产部署/现场/发布未验收，D2具体产品场景及ARRAY/连续在线保留，D1–D4整体不标全部完成。用户指定目标续接同一范围；本包交付后停止，D5未启动、TDE Issue#2不作第一版门槛。 [简短反馈](DELIVERY_D4_LOB_PRODUCT_20261010.md)。

2026-10-10 D1–D4已有能力收尾：只读盘点8份原证据rc0，不重复测试/源DML/部署。D1及D3 producer双窗口44＋2/target46/46/2、D2历史字典/跨块40事件产品链、D3正式C++目录认证传输、D4 RAW及DATE/TIMESTAMP各4事件产品链按有限已交付收口；D2初始水位3/同key5仅内核冻结证据，产品待证。LOB内核16完整事件已交付，可信获取/投影/JDBC/目标CPACK仍开发中，旧97基线不补批批准。整包/独立目标/评审/生产部署/现场/发布未关闭。唯一必要接续按LOB最小正常接入派单集中处理，不堆局部验证；ARRAY/连续在线等真实剩余范围保留。D5未启动，TDE Issue#2不作第一版门槛；不重建automation。 [收尾单与接续派单](DELIVERY_D1_D4_CLOSEOUT_20261010.md)。

2026-10-10 D4 LOB完整事件达到用户指定停点：代码b62a015已推送，56相关测试PASS，最终同件16事件（14提交变化＋2 raw seed），26前后像/冻结SQL零差异、rollback0；维护16事务/130节点零业务事件，native核等120/早期缺10明确保留。产品集成/部署/现场/发布及独立评审未验收，旧97基线不批准；不重源DML，未启容器。[简短反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。本轮完成即停止，不自动做产品CPACK/其它D包/D5。D5：TDE暂缓Issue #2、不作第一版交付/发布门槛；RAC/ASM复用既有解析器，只补实际读取接入缺口，最小正常验证，等待用户决定，未声明支持。

2026-10-09 LOB冻结完整物理图：[同包反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。验证提交2271cd1已推送，19图关闭（16commit/3rollback），129原始/17补偿源顺序完整，rollback live0；17图/125节点native前驱/LIFO/结束/live/tail/head零差异，13tail实际打印核等，2seed图仍缺native/基线不批准。BasicFile18页唯一分配关系：16有owned locator、2临时页淘汰；2不同SCN/1prelude明确保留，尚非writer/可见性授权。170class全部不变/树bf2c87dd，旧验证复用；事件0/部署0。下一生产工厂全输入/自主维护/页-locator与完整LOB行→可信获取→正常产品CPACK；完整D1–D5/独立评审/现场/发布未关闭。

2026-10-09 LOB剩余14项原始undo类型化：[同包反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。内核627a9de已推送，6普通PK/8SecureFile控制配对补齐，19fixture事务129项全类型化；dump范围内125项native完整owner/UBA/位置/opcode零差异，早期4seed undo缺native证据单列/基线不批准。16相关测试rc0，170class树bf2c87dd冻结/前169字节不变，原17补偿输出不变复用。下一步源顺序完整图/begin-advance-head-end/tail/rollback空live，再自主维护/页owner/locator/可信获取/产品CPACK；事件0/部署0，完整D1–D5及独立评审/现场/发布未关闭。

2026-10-09 LOB剩余六条物理补偿补齐：[同包反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。内核4b0759c已推送，19XID/17补偿全部核等（9堆表/2SecureFile/4BasicFile位图/2LOB索引），native完整owner/UBA/原始位置零差异；6951对象号核等、97media对象未打印单列。89相关测试rc0，冻结169class树b928eb6f。129项undo仍有14项待类型化（6普通PK/4控制13.54/2控制13.52/2控制13.50），自主维护/页owner/完整图/可信获取/产品CPACK待补；事件0/部署0/库exited，基线不批准，失败保全。完整D1–D5/独立评审/现场/发布仍开发中。

2026-10-09 LOB物理补偿推进：[同包反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。c0c01ae：完整19XID窗口/17唯一路由，9堆表+2SecureFile补偿/native完整owner与原始位置零差异；44不同相关测试rc0，168class树ffc5085a冻结。全归档59original含cutoff前2seed，后缀仍57/早期基线不批准。剩余6个BasicFile相关13.23/10.22补偿、页owner、完整事务/事件/可信获取及产品CPACK待补；事件0/换装0/库exited。对象范围首拒绝保留，未放宽门禁；完整D1–D5及独立评审/现场/发布未关闭。

2026-10-09 LOB堆表正常功能补齐：[同包反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。代码8b9349d：LOB专用ITL1/2锁/undo及SecureFile小内联候选，最终20相关测试rc0；四堆块57正向配对/native完整owner零差异，4内联/4空/4NULL及原16完整值/SQL零差异，冻结168class树a1291a5f。9回滚行仅识别，LOB完整补偿/事务/可信获取/产品CPACK未完成，事件0/部署0，库仍exited，原失败保留。完整D1–D5/独立评审/现场/发布仍开发中，禁止晚SCN回填批准97。

2026-10-09 LOB正常开发续接：[合并反馈](DELIVERY_D4_LOB_NORMAL_20261009.md)。328e63a：原失败完整回滚/实际字符长度已核，专属两LOB一次16步完成并冻结97；18 BasicFile页、12 SecureFile填充、4页镜像及10正向undo owner与native核等，类型化locator重建16完整值/SQL零差异，12相关测试通过。事件0，原早期3038645未获基线批准，LOB完整事务/事件/产品CPACK仍开发中；库stop0/exited，无换装。产品STATUS首部为当前入口；完整D1–D5及独立评审/部署/现场/发布未完成。

当前执行（2026-10-07前缀登记）：[服务端可信前缀登记与交接](DELIVERY_W6_PREFIX_REGISTRATION_20261007.md)。root代码0c48602/P代码217e5cd，kernel61288022完整330、product04744751完整760；原可信获取与75 capture只读复用，认证HTTP服务端生成pending1/completed0状态7d6eef2a并登记。登记故障后APPROVED_PREFIX/审计0、文件保留；解除后同字节复用/登记一次，重复/强杀137重启GET稳定，毒化拒绝/精确恢复；事件/任务/窗口/批次/发布门0，CPACK0，helper停止143。仅显式前缀API，不是自动pipeline pending或75/76产品完整发布通过；新SQL故障底层原因未持久化，是下一修复项。D1未关闭，其余四包及部署现场发布未关闭。新chat交接以HANDOFF_NEW_CHAT_20261007_PREFIX.md为准，旧chat移交后只保留历史。

当前执行（2026-10-07产品活跃输入）：[可信产品基线/真实75—76与marker2](DELIVERY_W4_PRODUCT_ACTIVE_MARKER2_20261007.md)。proper HTTP获取101320533446070272/SCN2773486/SHA9a99c587，6SYS/230值/3索引/基线零差异；一次XID10.0.2959跨75/76已结束禁止重放，collector0/next77、95320/3块与归档零差异。原593e完整入口KDOM2拒绝保全；代码73c487f/kernel571cac2a完整330，只1class变/127不变，新profile两完整UPDATE/native零差异，75 pending1持久halt23→独立JVM续76输出相同；两重封checksum反例零stdout/四比较器毒化检出。344双SHA/前353+234+113+100未变，helper/collector停止，NO/NO/旧行保持，bindings0/events0/目标0。产品自动pending登记/跨发布尚未实现；下一服务端可信前缀checkpoint登记与私有产品续接，不重获取/不重源；原goal active。

当前执行（2026-10-07恢复失败集成）：[新恢复入口失败与恢复](DELIVERY_W6_RECOVERY_FAILURES_20261007.md)，同件e0a79dd8/原754实际缺失文件、带可信摘要的identity混配均rc2/0事件/零推进；第二UPDATE ORA-02290目标整批回滚/CPACK零推进；ACK目录故障保留PUBLISHED与提交事实/原FileAlreadyExistsException，解除后同任务补ACK、全部目标账/batches/CP及原失败引用不变。353双SHA/前234+113+100+356未变，helper停止；0源业务重放/新获取/日志配置。历史恢复正反例已证，D1下一真正活跃产品跨发布持久恢复；一般矩阵/独立目标/评审/现场/发布仍待证，原goal active。

当前执行（2026-10-07实际产品恢复）：[历史持久恢复HTTP集成](DELIVERY_W6_RECOVERY_HTTP_20261007.md)，同件producte0a79dd8/原754实际恢复文件→两完整历史UPDATE→目标2/2/1→PUBLISHED/CONSUMED/CP→ACK逐字段与字节一致。文件毒化rc2/0事件/零推进，精确恢复同任务通过；重复及提交后SIGKILL137→重启→resume不重写/CPACK不变。234双SHA/前113+100+356未变，owned helper停止；0源业务重放/新获取/日志配置。仅有限历史恢复产品链，D1仍需缺件/组件混配、新恢复晚失败/ACK故障与真正活跃跨发布；独立目标/评审/现场/发布未过，原goal active。

当前执行（2026-10-07产品恢复绑定）：[产品恢复输入候选](DELIVERY_W6_RECOVERY_BINDING_20261007.md)，P代码60f56f5/反馈cb7846e/完整754，producte0a79dd8真实嵌入kernel593e730b，adapter/SPI原件。APPROVED历史绑定增加可选持久恢复文件路径/可信SHA，decode与发布核验，获取路径解析保留，客户端options不可登记；新两反例单位测试通过。113双SHA/前100+45+86+36+42+88+182未变，0源/目标/换装；新候选实际HTTP/target/CPACK恢复仍待证，独立评审PENDING。继续D1真实恢复集成，五开发包详见收口清单；原goal active。

开发收口入口（回应用户整体进度）：[剩余五个开发工作包及判据](DEVELOPMENT_CLOSEOUT_20261007.md)。当前优先D1产品持久恢复；D2一般DML/历史恢复矩阵、D3正式C++采集、D4扩展类型/LOB、D5 RAC/ASM/TDE仍未整体关闭。五包不是五个小任务，不给无证据百分比。部署/生产验证后续与用户协同，完整goal不缩减。

当前执行（2026-10-07统一恢复）：[统一字典/identity/undo恢复与完整事件](DELIVERY_W4_UNIFIED_RECOVERY_20261007.md)，kernel593e730b/完整328；新增HistoricalRecovery源绑定V1，raw前缀核规范状态后恢复identity/undo，物理状态仅续后缀，完整行raw重放并用恢复图生成事件。原73持久halt23→新JVM仅送74，同XID闭合两UPDATE；70→71保留前缀已闭合事务及四→三列历史，两组全部stdout与原件相同。14实际反例rc1/stdout0，100双SHA/前45+86+36+42+88+182未变，0源/目标/产品动作。仍需raw前缀、pending SYS/行免重放/真实pending index及私有产品发布CPACK恢复未证；下一私有产品接持久凭证/完整事件与恢复门禁。原goal active。

当前执行（2026-10-07物理前缀）：[活跃物理前缀状态](DELIVERY_W4_PHYSICAL_PREFIX_20261007.md)，kernel7ff4a5e7/完整328、新六测试。新增内部pending发现/补偿路由/index槽/physical前缀，保留原物理门禁且默认完整入口未闭合仍拒绝。冻结73闭合0/pending1/heap pair1，identity及graph与前独立JVM state字节相同；原73+74闭合1/pending0及两完整事件字节不变。首326/1新fixture同时删marker/end的预期错误原文保全，修正后328通过。45双SHA/前86+36+42+88+182未变，0源/目标/产品动作。尚在内存生成pending，统一持久恢复/仅续后缀/完整行/产品CPACK待证；下一装配字典与physical恢复凭证。原goal active。

当前执行（2026-10-07字典恢复凭证）：[字典恢复/完整事件](DELIVERY_W5_DICTIONARY_RESUME_20261007.md)，kernelb35dcdec/完整322；新增源绑定timeline checkpoint与readRestored，原始capture重建核完整SYS行/提交摘要。原70前缀两SYS/四列及73前缀零SYS/三列持久后halt23，新JVM接71/74，两组两完整UPDATE全部stdout与原封存字节相同；11恢复反例rc1/stdout0。86双SHA/前36+42+88+182未变，0源/目标/产品动作。不当免raw增量SYS或identity/undo/行统一恢复、跨发布CPACK/断电/现场通过。下一统一恢复入口及私有产品；当前goal已核active，旧usageLimited描述属历史。

当前执行（2026-10-07undo图恢复候选）：[pending undo图/独立JVM续接](DELIVERY_W4_UNDO_GRAPH_RESUME_20261007.md)，kernelb6d5d792/完整322、新五测试；checkpoint操作在SHA绑定源inventory重放原图门禁，允许精确保留前缀的扩展。冻结73一个original落盘/Runtime.halt23，新JVM恢复→74第二original/native tail闭合，同XID10.14.2869/begin2743312/end2743344，original2/live2/补偿0、rci10/tail11/native一致；graph毒化实际拒绝/零stdout。补偿续写/全回滚恢复及源SHA变但entries相同拒绝属单位验证。36双SHA/前42+88+182不变；0源/目标/产品动作、0事件，不当完整历史字典/行/跨发布ACK恢复或断电通过。接续完整字典与行状态及产品恢复。goal工具仍usageLimited，额度工具ordinaryUsageAllowed=true/5h已用1%，按授权接续且不擅改goal状态。

当前执行（2026-10-07身份恢复候选）：[事务identity checkpoint/独立JVM续接](DELIVERY_W4_TRANSACTION_IDENTITY_RESUME_20261007.md)，kernel44341f9d/完整317、新四测试；只1生产class变/122不变。冻结73持久identity后Runtime.halt(23)，新JVM从摘要绑定state读取74闭合同XID10.14.2869/begin2743312/end2743344；实际摘要毒化/带新摘要截断均拒绝/零stdout。42双SHA/前88+182未变；首Path/str驱动ValueError原文保全，只补未执行反例不重跑已过轮次。0源/目标/产品动作、0事件；只identity恢复，不冒充完整undo/字典/跨发布/CPACK恢复或断电通过。接续完整typed与raw前缀持久状态/恢复，再私有产品；原goal active。

当前执行（2026-10-07跨日志完整候选）：[mixed范围profile与完整两UPDATE](DELIVERY_W7_CROSS_WINDOW_RANGE_20261007.md)，kernel0bcb39e6/完整313、针对性13；仅2生产class改变/121不变，原73/74完整入口rc0两UPDATE，同XID10.14.2869/begin2743312/commit2743344，三列完整像/native四变化零差异。仅73仍未闭合rc1/0输出；三重封checksum完整入口反例拒绝、四比较器毒化检出、重复字节相同；新候选旧70/71完整输出字节不变。首XOR1比较器反例无效失败原文保留/r2通过，88双SHA/前182不变，0源/目标/产品动作。接续真正跨发布持久活跃/字典状态与崩溃恢复、正式C++；本次只有限内核跨日志，不当跨发布恢复/HTTP/现场/发布通过，原goal active。

当前执行（2026-10-07跨窗）：[真实跨窗事务与格式拒绝](RESEARCH_W7_CROSS_WINDOW_ACTIVE_20261007.md)。新V3 SCN2743256/SHA3619a433，六SYS/230字段/三索引/业务行独立零差异；新XID10.14.2869一次跨73/74，73关闭时会话活跃/commit未发送，最终恢复原行。collector rc0/next75，219166/23观察块与归档数据块零差异；仅73完整入口INCOMPLETE_TRANSACTION_IDENTITIES:1，73+74因新24.4 mixed-flag跨度10拒绝，均rc1/stdout0，不标跨窗恢复通过。182双SHA/前522不变，collector已退出，NO/NO/旧表保持；无目标/CPACK/产品换装，0源重放。接续同冻结日志范围关联/最小profile反例，再持久跨发布状态与正式C++，原goal active。

当前执行（2026-10-07）：聚焦开发/针对性验证/专属集成，生产部署与验证后续协同。最新[历史失败留证与恢复](DELIVERY_W6_HISTORICAL_FAILURE_RECOVERY_20261007.md)：P代码dbab51a/反馈12ce9f9、product2f1b46ad完整752；实际绑定错consumer零事件/零推进、第二UPDATE ORA-02290完整原文/目标整批回滚/CPACK零推进，ACK存储故障保留PUBLISHED与完整控制凭证、同任务恢复补ACK不重写目标、重复/同件重启稳定。首7/1回归、首修复751及ACK8/1回归全部保全，最终读取新批次保存原异常与文件引用，不覆盖提交事实。522双SHA/前356+1181+407不变，owned helpers停止，0源业务重放/新获取/日志配置；原早V3注册复制到新实验控制库且完整metadata相等。下一开发主线跨窗活跃状态/恢复及正式C++采集；同实例目标/capture_complete=false，仅有限集成，不当一般DML/独立目标/现场/发布通过。原goal active，Codex最后查。

最新：[自主I_COL3七记录物理split候选](DELIVERY_W5_AUTONOMOUS_DICTIONARY_SPLIT_20261006.md)：kernel1d5f91fc完整309/0/0/0，源字节自主发现1个维护XID，五original/两advance/13.7+13.6物理freelist控制/end OLD重建闭合；可信segment/file/当前R身份/links/range/445→220+226守恒及全已知页审计通过，slot7待恢复，OLD prev=null/ccnt不当XID。archive/capture/重复完全一致，12合法checksum块scan0/完整split1/stdout0。旧119class不变，新3class；首漏页审计候选不验收/r2未知freelist拒绝原文及驱动失败保全，最终549双SHA/前129+133+139+130+205+407不变。0新源/目标/产品动作，业务历史工厂未接入，下一步10.5可信raw槽恢复+完整历史两UPDATE→产品HTTP/target/CPACK；原goal active，现场发布未过，Codex最后查。

最新：[I_COL3前像/两叶重建typed候选](DELIVERY_W5_DICTIONARY_LEAF_ROWS_20261006.md)：最终r2 kernelb4913b23完整309/0/0/0，typed前像445/NEW220/OLD226全部891行/native字节+NUMBER+rowid+状态0差异，四比较反例检出。旧117class字节不变，仅新增2class；OLD重建prev缺证用null，首0占位候选133保留/最终129双SHA，前139+130+205+407未变。0源/配置/目标/产品构建换装，完整历史工厂未接入/未重跑；下一步自主五original+end完整split闭包及10.5 raw槽凭证，不能局部通过冒充两事件/HTTP/CPACK。原完整goal active、现场发布未过，Codex最后查。

最新：[I_COL3锁/link/branch typed Pair候选](DELIVERY_W5_DICTIONARY_MAINTENANCE_PAIRS_20261006.md)：kernel1911a0cd完整305/0/0/0，新增三局部Pair/current XID/UBA/role/generation/segment/ITL逆向绑定，真实70三对/native0差异；link指向new leaf0040512f、branch slot299/child一致/parent75281。旧115class字节不变，仅新增2class；完整历史工厂尚未接入/未重跑，完整维护闭包和两事件待证，诊断0事件。139双SHA/前130+205+407不变；首探针范围外undo失败保全，r2仅修研究scope。0源/目标/产品构建换装，接续前像/两叶typed重建+全维护闭包+10.5 raw槽关联，再历史产品链；原goal active、现场发布未过，Codex最后查。

最新：[维护begin/原生命周期候选](DELIVERY_W5_INDEX_MAINTENANCE_BEGIN_20261006.md)：kernel4e7eeafc完整301/0/0/0，严格044a首record/size136/opaque3f98/零parent profile及两新增测试；原70/71同早V3窗口XID9.9.660 begin+两advance+五original/live+end/tail完整生命周期闭合，native身份/前驱0差异。完整capture历史工厂仍DICTIONARY_DISCOVERY_INDEX_COVERAGE rc1/stdout0，branch/link/重建全typed维护尚待证。130双SHA/前205+407不变；0新源动作/配置/目标/产品构建或换装，保留旧拒绝；接续完整typed维护→两历史UPDATE→产品HTTP/target/CPACK，不能跳过维护图。原goal active、评审现场发布未过，Codex最后查。

最新：[产品V3新窗口/索引维护拒绝研究](RESEARCH_W5_V3_PRODUCT_INDEX_MAINTENANCE_20261006.md)：实际baseline2566695后seq70/71一次ADD/DROP及两UPDATE已结束，禁止重放；collector terminal0/next72，126644/18观察块与归档数据块0差异，capture_complete=false/原online header保留。完整工厂DICTIONARY_DISCOVERY_INDEX_COVERAGE rc1/stdout0；原445行→220+226行仅一删除标记新增，槽7恢复key/rowid与native字节0差异、四比较反例检出。XID9.9.660五original前驱graph/tail闭合，但lifecycle仍拒绝044a begin，branch/link完整typed维护未证，0事件/产品binding0/event0，无target/HTTP/CPACK。205双SHA/前407不变，失败保全，0生产代码修改/重构旧套件。接续同冻结窗口typed维护，不重获取/源动作；原全goal active，独立评审/现场发布待证，Codex最后查。

最新：[产品真实V3获取/恢复候选](DELIVERY_W5_V3_PRODUCT_ACQUISITION_20261006.md)：root0f2838b/Pa40db7b，productc47a728c完整750，kernel240/adapter65真实嵌入。认证HTTP获取100747510573780992/SCN2566695/SHA323fd35a，6SYS/230值/3index/业务基线与独立ASOF0差异、完整metadata一致。未认证/字段注入、文件截断与五metadata实际GET拒绝/精确恢复，同件重启字段/SHA相同；ACQUIRED_ONLY/binding0/event0，helper37261 running/auto-consume关闭，无新源fixture/日志配置/目标动作。407双SHA/旧216+241+75+189+374不变；比较器SCN/hex/时间精度及SP2长行失败保全。下一步新基线后一次新历史raw→真实产品HTTP/CPACK，不回填68/69；PR#13 draft依赖#12/评审PENDING，原全goal active，Codex最后查。

最新：[历史完整事件目标原子提交候选](DELIVERY_W5_HISTORICAL_TARGET_20261006.md)：root020be30/P65e12c2，adapter65e0fcba/26、完整productac98404d/746，真实嵌入kernel240/adapter65及SPI全class一致。原早V3/68—69两UPDATE在新owned HS6完整4→3列事件账/成员/commit独立一致，重复0；HSL6首更新后第二CHECK ORA-02290整批回滚，序列凭证篡改拒绝/精确恢复。216双SHA/旧241+75+189不变；0源DML/配置/服务换装，只有direct writer集成，可信V3产品获取/历史HTTP/CPACK待证，禁止晚SCN回填旧窗口。下一单V3_PRODUCT_RUNTIME，PR#13 draft依赖#12/独立评审PENDING；原全goal active，其它全量门禁待证，Codex最后查。

最新：[历史schema证据协议/产品兼容候选](DELIVERY_W5_HISTORICAL_SCHEMA_PROTOCOL_20261006.md)：e5477a6/kernel240c2250/299、adapter35340779/24、SPI53b84d18/8；P代码8cc33b1/743完整通过/product18f49be4，真实嵌入仍672f01bc/9516d4f9而非新历史候选。基线3列与逐事件4→3列DTO/完整字段/JSON往返及重复/擦marker一致，摘要仍c23904a9；错摘要/交换/裁列整入口rc1/stdout0，旧V2三IUD全部旧字段/envelope SHA相同。241双SHA/旧75+189不变；P注册关联拒绝/旧存储兼容通过，0源/目标/服务换装。历史decoder/可信V3获取回执登记/target receipt控制CPACK尚未接通，禁止晚SCN回填68/69。PR#13 draft依赖#12评审PENDING，原goal active，所有其它全量门禁待证，Codex最后查。

最新：[历史逐schema投影候选](DELIVERY_W5_HISTORICAL_PROJECTION_20261006.md)：fd0a254/adapter446e5a9b/24；冻结实际V3/68—69两完整UPDATE投影4→3列，hidden NOTE/EXTRA_V3 NULL保留，raw ID/payload/位置/XID/SCN/schema/完整像一致。schema序列摘要c23904a9，重复字节相同/擦marker事件及序列摘要相同；三个比较反例检出、两个完整投影拒绝rc1/stdout0，旧V2静态三IUD完整JSON相同。75双SHA/旧189未变，Maven参数/Jackson依赖首失败保全；0源/目标/StreamHouse代码变更，无产品HTTP/CPACK。P仍efd068f/737/PR#13待评审，接续HISTORICAL_PRODUCT升级SPI有序schema证据/可信获取注册/发布/目标commit控制CP ACK，现旧单schema协议不能直接接新Batch。原全goal active、其它全量门禁待证，Codex最后查。

最新：[实际历史schema完整业务行](DELIVERY_W5_HISTORICAL_ROW_SCHEMA_20261006.md)：9e72011/kernel3ccca289/299；实际V3 SCN2536302后冻结68/69两UPDATE自主完整历史入口通过，SCN2536404/2536423列数4→3，隐藏NOTE anchor与新增EXTRA_V3 NULL保留，native正逆/XID/位置/SCN及独立阶段SQL0差异。重复字节一致，擦四marker事件/schema/ID/payload不变；七SYS+四业务合法checksum完整入口rc1/stdout0，四比较器反例检出，旧V2三IUD/历史拒绝不变。189双SHA/旧374不变，首次end拒绝与跨block定位失败保全。0源变更/目标/产品动作；接续HISTORICAL_PRODUCT逐事件schema账/HTTP/target CP ACK，产品仍旧efd068f/737。原完整goal active，跨窗/采集/独立目标/矩阵/评审现场发布未过，Codex最后查。

最新：[实际新V3后SYS历史](DELIVERY_W5_NEW_V3_HISTORY_RAW_20261006.md)：c9f340d/kerneld7ee0dfd/296，actual V3 SCN2536302/SHA226c9322完整230字段/三index/基线独立0差异，新seq68/69 ADD EXTRA_V3/DROP与两UPDATE已结束禁止重放，capture归档34029/14块0差异。三SYS commit2536394/6401/6419完整22节点/764值native与ASOF0差异，归档/capture/擦四marker历史字节一致，七合法checksum raw scan0/history1/stdout0；旧V2静态三IUD/历史拒绝不变。374双SHA/旧227不变，ckix239窄profile正逆绑定，首拒绝/预检NOTE误判/编译/定位失败保留。仅真实有界SYS历史，不当业务历史DML/产品现场发布；0配置/目标/产品动作，collector terminal0。接续HISTORICAL_ROW_SCHEMA时点schema完整事件与产品V3账HTTP。原goal active，Codex最后查。

最新：[归档自主SYS历史候选](DELIVERY_W5_SYS_HISTORY_KERNEL_20261006.md)：a3fb674/kernel58a6e56e/295，raw自主完整三SYS事务22original、764值独立0差异；archive/actual capture/擦marker输出字节一致，11整入口反例rc1/stdout0，旧V2静态三IUD不变/历史仍拒绝。227双SHA/旧220未变，选择无关SYS与parent检查失败保全，0源/目标/产品动作。旧SCN V3仍synthetic诊断，真实晚V3拒绝；不当历史DML/现场发布。接续NEW_V3_HISTORY_RAW实际新baseline后新日志正功能，再业务时点schema与产品V3账/HTTP。原全goal active，Codex最后查。

最新：[SYS完整历史/OBJ$候选](DELIVERY_W5_SYS_FULL_HISTORY_RESEARCH_20261006.md)：b65ade5/kernelf78ad0f9/292，scoped OBJ$九列30字节URP已实现，普通heap不放宽；十一行pair/十index/一控制22original完整处置，三个commit SCN完整764值及三index lifecycle诊断0差异、四比较反例检出。七合法checksum raw scan0/pair1/stdout0；旧V2静态三IUD字节不变/历史拒绝不变。220双SHA/旧219未变，profile/跨块定位失败原文保全；0业务/配置/目标/产品动作。仅诊断行史、IRP尾NULL假设仍无生产authority；接续SYS_HISTORY_KERNEL自主完整覆盖/可信V3历史入口，再新SCN后历史DML正功能与产品获取账HTTP。原goal active，跨窗/采集/独立目标/矩阵/评审现场发布未过，Codex最后查。

最新：[V3完整SYS获取候选](DELIVERY_W5_DICTIONARY_BASELINE_V3_20261006.md)：6b68786/kernel97dd395f/290、adaptere095822b/22；V3完整布局/六SYS行/三index identity+DBA已实现，实际新SCN2533047/SHA518c0f90及独立230字段/三个索引/业务基线零差异。旧V2三完整IUD字节不变，原/擦marker历史拒绝不变，V3也未解除历史门禁。219双SHA/旧204未变；首次IND$权限ORA-00942拒绝无快照，owned lab补SELECT/FLASHBACK属配置实验，不当生产零侵入证明；无业务DDL/DML/目标动作/产品换装。接续COMPLETE_SYS_HISTORY完整SYS行/三索引lifecycle、新V3之后历史raw正功能/产品获取账HTTP。原goal active，跨窗/采集/独立目标/矩阵/评审现场发布未过，Codex最后查。

最新：[三SYS复合索引pair](DELIVERY_W5_DICTIONARY_INDEX_PAIR_20261006.md)：646841d/kernelc8586fcb/286；冻结十index pair/native parent/rowid一致，十二合法checksum raw scan0/pair1/stdout0。普通单列入口不放宽，旧静态actual V2三完整IUD输出字节一致，原/擦marker历史V2拒绝原因不变。204双SHA/旧169未变；SCN2522819索引地址只读研究，不回填V2authority。0源变更/目标动作/产品构建，产品仍efd068f/737。接续TRUSTED_SYS_HISTORY新版可信布局/角色获取、完整SYS行与三索引生命周期、新baseline后历史DML正功能；原goal active，跨窗/采集/独立目标/矩阵/评审现场发布未过，Codex最后查。

最新：[cluster正逆pair/三SYS列索引](DELIVERY_W5_CLUSTER_PAIR_20261006.md)：b3455f0/kernelcca25465/282、lab9ab3410；八TAB$/COL$ pair完整UndoHeader XID/current UBA/role/table/block/slot/op/extent与冻结native一致，foreign L不当当前身份。九合法checksum raw scan0/pair1/stdout0；旧96class不变。全22原始节点中的十index复合key/rowid native一致，角色I_COL1/2/3(48/49/50)晚只读研究，I_COL2 rowid内嵌/外部data省略，现普通单列key不能放行。169双SHA/旧98未变，比较器keydata/bitmap失败保全，0源变更/目标动作/新产品HTTP。接续DICTIONARY_COMPOSITE_INDEX完整三索引依赖和新版可信获取，parent/历史DML尚未授权，原goal active，跨窗/采集/独立目标/评审现场发布未过，Codex最后查。

最新：[SYS clustered行片候选](DELIVERY_W5_CLUSTER_ROW_PATCH_20261006.md)：0bd5005/kernel9288afbc/278、验证5da1b55；仅新增2class、旧94class字节不变。TAB$ tab1/COL$ tab5窄profile与普通heap分开；冻结16 forward/inverse片段98native列字节/NULL零差异、两提交SCN新slot51选定24DUMP值一致及DROP消失，尾四ABSENT不补NULL。专属source只读查layout见ACDRRESCOL物理14/内部37、DEFAULT相反；parent/index generation/正逆pair/完整SYS像和历史schema未授权。98双SHA/旧173未变、0源变更/目标动作，产品仍efd068f/737无新HTTP；接续CLUSTER_PARENT_PAIR可信索引/布局与新baseline后历史正功能，原goal active，跨窗/采集/独立目标/评审现场发布未过，Codex最后查。

最新：[SYS非行控制/行链根候选](DELIVERY_W5_SYS_CONTROL_ROOT_20261006.md)：内核70c1a12/kernel5fb55de7/275、lab6e8ac66；flags51 obj0 begin5.7与唯一flags2首行根严格绑定，分配控制和行链分开、不造33→32边。冻结三SYS事务22节点/native原始前驱、三完整控制窗口/end SCN/tail零差异；非行10原始=1控制+9live行节点。九合法checksum raw scan0/history1/stdout0，原/擦marker actual V2仍历史拒绝。173双SHA、旧34/146未变、0源/目标动作，产品efd068f/737仍旧候选无新HTTP。接续SYS_ROW_HISTORY正功能/间接列依赖，完整历史/跨窗/采集/独立目标/评审现场发布未完成，goal active，Codex最后查。

最新：[SYS非行begin/首COL$控制原始证据](RESEARCH_W5_SYS_BEGIN_CONTROL_20261006.md)：lab6404423，冻结seq66十5.1节点及三begin/head/end控制字段与native零差异，tail...:41对应末分配节点。flags51 obj0 begin5.7→flags2首COL$ rci0没有显式33→32边；首cluster LKR逆向ITL属旧XID10.18.2272，前向属3.30.705，tabn5/to2不能套普通heap锁。无生产/源/目标动作，不重跑271/737；原失败和比较器修复保全，上一146未变。下一步typed非行控制/首行根完整绑定与反例，再SYS行/SCN历史正功能；仅字段研究，不当图闭合/历史DML/现场发布，原goal active，Codex最后查。

最新：[V2身份五实库反例/SYS清单缺口](DELIVERY_W5_V2_IDENTITY_SYS_INVENTORY_20261006.md)：driver5cd9f82复用672/efd068f，无生产改动/不重跑271/737。authority/账SCN/metadata source/role/anchor五实际GET拒绝且逐项恢复全字段/基线SHA精确一致，target/control/source审计不变，helper exited143。seq66/67三SYS XID native22节点：两笔6+6清单零差异，3.30.705十节点因obj0 begin5.7后首COL$ rci0被现有普通门禁拒绝/零输出，原失败保全。146双SHA/旧334未变；仅身份负例/两清单，不当完整SYS图或历史通过，接续SYS_BEGIN_CONTROL派单。原goal active，历史/跨窗/采集/独立目标/评审现场发布未完成，Codex最后查。

最新：[V2历史原始/擦标记产品拒绝](DELIVERY_W5_V2_HISTORICAL_PRODUCT_20261006.md)：同件efd068f/737/application2db7106f不重构；actual V2获取100644812901937152/SCN2522819六anchors/列/基线零差异，新seq66/67 ADD/DROP两源事务闭合禁止重放，capture/归档零差异。原始/DERIVED擦三24.x均按SYS写入拒绝；实际HTTP首次/擦除/重启三拒绝准确reason，target/ledger/CP/ACK0推进。native六锚点写/四COL$位置关联零差异、三比较反例检出，334双SHA/旧446未变，异步snapshot/修复root失败保留，helper exited143。仅安全拒绝，身份实库反例矩阵/完整SYS历史/间接链待补；接续V2_IDENTITY_DICTIONARY_HISTORY，原完整goal active，评审现场发布未过，Codex卡顿最后查。

最新：[V2新raw实际产品提交/重启](DELIVERY_W5_V2_DML_PRODUCT_RUNTIME_20261006.md)：产品efd068f/737通过、文档b2a754d/草稿PR#13待评审，实际V2 SCN2517052六锚点/列/基线值独立零差异。新672候选实际HTTP错authority目标前REFUSED/event0，恢复同task DONE/三完整IUD→目标commit→controlCP→持久ACK全字段独立一致；重复及同件重启核验字节不变。446非secret双SHA/旧197未变、安装参数失败保全；helper exited143、源事务不重放。仅有限静态DML产品链，历史/跨窗/正式采集/独立目标/现场发布仍待证，接续V2_HISTORICAL_PRODUCT派单，goal active。用户要求Codex卡顿排查放到整个Oracle目标完成后。

最新：[V2新raw/NULL逆向尾列](DELIVERY_W5_V2_IMPLICIT_TAIL_20261006.md)：代码391eaf7/kernel672f01bc/271通过；实际V2 SCN2517052→新seq62—64三源事务已结束且禁止重放，capture/归档零差异。旧kernel未知NULL尾标记rc1/stdout0保全，窄profile+精确单列extent/长度配对后3完整IUD/两回滚0；native四图11/4/7及三中间像/SQL零差异，两个比较反例检出。197非secret双SHA。仅内核有界DML，本轮独立ASOF尚待补；旧737产品helper仍running/444，尚无新目标/CP/ACK。继续V2_NEW_RAW_RUNTIME派单私有制品接入与真实HTTP、historical拒绝；独立评审/现场发布待证，goal active。

最新：[V2产品获取账与真实HTTP](DELIVERY_W5_V2_CONTROL_RUNTIME_20261006.md)：产品ea1975c/737通过、文档5f57c56/草稿PR#13已附属依赖#12；actual acquisitionId100631208685281280/SCN2514178六锚点、独立ASOF零差异。坏file/账缺anchors拒绝、恢复/重启持久字段及文件一致；ACQUIRED_ONLY/binding0/event0，0源DML/目标发布，helper exited143。109非secret双SHA，时间精度比较失败原文保全。新raw/目标CPACK/缺marker产品拒绝仍待证，不能用晚基线回填旧capture；接续V2_NEW_RAW_RUNTIME_20261006，P0整体未闭合，goal active。


最新：[媒体标记/结束扩展与有限反例](DELIVERY_W5_DICTIONARY_END_PROFILES_20261006.md)：kernel444630a4/269通过，原始V2及DERIVED擦除均按SYS目标写入拒绝/0输出；去锚点诊断对照2事件复现ADD后漏EXTRA=NULL。native三完整图4/0/4及两结束identity/tail/ext零差异，三目标tail/slot/container派生结构通过语义拒绝，mixed/empty字节不变。292非secret双SHA，旧236文件未变；错误range offset/比较器/负例选点原文保全，0源/目标动作。仅有限19.3窄profile，不给一般历史权限，新产品/HTTP未验证，P0整体未闭合；接续V2_CONTROL_RUNTIME_20261006，goal active。


最新：[实际V2字典获取与新raw](DELIVERY_W5_DICTIONARY_ANCHOR_REAL_20261006.md)：SCN2509388六锚点/历史列与独立ASOF零差异；新seq59/60及ADD/DROP两事务已闭合，捕获/归档零差异。原始batch因未知24.4 rc1/stdout0；DERIVED擦除三条schema-family后V2按目标SYS写入拒绝，native六锚点写/四同XID新COL$位置零差异。去锚点诊断对照因两真实5.4 extension拒绝，未证明本轮两错误事件，P0整体未闭合。失败保全/只读补收尾/源动作不重放，NO/NO/旧表保留；无新目标/产品HTTP/部署。接续DICTIONARY_END_PROFILES_20261006，goal active。


最新：[可信字典锚点代码候选](DELIVERY_W5_DICTIONARY_ANCHOR_CANDIDATE_20261005.md)：V2同SCN角色/位置/历史列及raw逻辑角色屏障已实现；kernel265、adapter21通过。实际既有只读账号SYS获取ORA-00942拒绝/连接关闭/未签发快照；编译重名失败源码与日志保全。仅代码/单位回归/权限负例，不算真实V2获取、marker擦除关闭或产品HTTP通过；无grant/源DML/目标动作，旧seq52—57不重放。继续TRUSTED_DICTIONARY_ANCHORS派单，goal active。


更新：2026-10-05（Asia/Shanghai）。用户授权自主论证、实现、专属实验验证，主攻解析；禁止 Luna。目标仍进行中，不是全流程验收完成。

## 六列状态

| 工作包 | 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|---|
| W0 环境/样本/真值 | 通过：首批fa61476，矩阵继续 | 通过：环境/冻结归档；矩阵待证 | 待证 | 不适用：实验脚本 | 待证 | 未开始 |
| W1 文件/块 scanner | 通过：首批fa61476 | 通过有限19c/21c样本；最新70/71及26合法checksum反例结构通过，语义另判 | 通过有限真实关闭capture→完整历史/两业务事件；正式online/平台集成仍归W7待证 | 未开始 | 待证 | 未开始 |
| W2 record/vector | 通过：首批fa61476 | 通过有限样本：旧19c完整dump/21c选定范围；最新native全22SYS original/764完整值及业务字节一致 | 通过有限70/71真实capture→事务/历史字典/完整行→adapter；一般格式/产品门禁仍待证 | 未开始 | 待证 | 未开始 |
| W3 基础行/类型 | 进行中：scalar/pair/KDOM2及可信基线后完整行 | 通过有限样本：254测试制品；seq44—50非空/NULL尾列INSERT/DELETE、混合/全回滚，seq51独立完整回滚空capture；18pair/302长值patch保留 | 进行中：固定可信基线完整像及独立空batch到目标/checkpoint通过；有限历史完整行已通过；一般完整DML矩阵待证 | 未开始 | 待证 | 未开始 |
| W4 事务/回滚 | 进行中：有序history/覆盖/heap+index补偿、同源完整XID归属、有源位置重放及精确外来begin排除；新增identity checkpoint/摘要恢复 | 通过有限样本：254测试；seq51四控制/native归属及两图6/2/4零差异、三重封结构通过/语义拒绝；旧自主图/跨归档/回滚证据保留 | 进行中：有界自主物理入口已交付；最新44341f9d/317身份state落盘→JVM halt23→独立JVM续接74同XID闭合，毒化/截断拒绝；最新undo候选b6d5/322：pending图落盘/独立JVM续接74/native tail闭合通过，单位补偿恢复通过；字典凭证b35dcdec/322独立JVM恢复完整事件字节一致但依赖raw重放；最新7ff4/328活跃physical前缀完整门禁生成pending identity/graph/pairs，与原state字节一致，默认拒绝保留；最新593e730b/328统一源凭证持久→独立JVM恢复identity/undo仅续后缀，两窗口完整事件字节一致/14反例拒绝；字典/行仍需raw重放；最新e0a79dd8历史持久恢复实际HTTP/目标/CPACK及提交后SIGKILL重启通过，新proper产品基线75/76内核571cac2a/330真实pending恢复完整事件通过，产品自动前缀checkpoint可信登记/跨发布仍待实现；不当通用生产能力 | 未开始 | 待证 | 未开始 |
| W5 SCN字典/基线（当前主线） | 有限历史候选：可信V3实际获取、自主七记录split、10.5可信槽恢复/完整SYS历史与两事件已接入；通用场景仍进行中 | 有限通过：a84db1a3/311；三SYS/22original/764native完整值零差异，两UPDATE完整4→3列、隐藏NOTE/新增NULL；26完整入口反例拒绝/stdout0，14marker擦除；1181SHA/前549不变。三旧SCN ASOF补查ORA-01555保留 | **70/71 raw→完整历史→业务行→adapter→产品HTTP/target/CPACK有限通过；同件重复/重启/凭证反例通过**。新字典恢复凭证/完整事件入口在70→71与73→74独立JVM通过，两组输出字节相同、11反例拒绝/86SHA；仍需完整raw前缀；最新统一恢复593e730b/328两窗口恢复完整事件字节一致，最新e0a79dd8实际历史持久恢复HTTP/CPACK通过，活跃产品跨发布仍待证。旧68/69字节不变；活跃同key/相同commit SCN/多savepoint/跨块/初始恢复等仍待证 | 未开始 | 待证 | 未开始 |
| W6 StreamHouse接入 | 进行中：独立二进制adapter、可信V3注册/绑定、历史原子目标凭证；原P代码dbab51a失败文件关联/提交事实保留；最新代码60f56f5/反馈cb7846e接服务端持久恢复引用及kernel593e730b | 有限通过：kernel311/adapter26原件不重跑，原product752失败恢复门禁保留；最新恢复引用候选e0a79dd8完整754及两个新测试；实际嵌入SHA/SPI全class不变，7/1和8/1原失败及751候选保留 | **原70/71两完整历史UPDATE实际HTTP→目标2/2/1→控制CP→ACK有限通过**；错绑定0事件、第二UPDATE约束失败整批回滚/零推进、原异常/文件引用完整，ACK存储故障保留PUBLISHED/目标与控制已提交、恢复补ACK不重写目标、重复/同件重启精确不变。原522双SHA/前356+1181+407不变；最新e0a79dd8实际历史state恢复完整链、hash毒化零发布、重复及提交后SIGKILL重启通过，234双SHA/前113+100+356不变，helper停止；最新353双SHA缺件/组件混配拒绝、恢复入口晚失败整批回滚/ACK故障同任务补ACK已通过；活跃产品跨发布、其它方言完整Facade/独立目标/正式采集/任意窗口仍待证 | 未开始 | 待证 | 未开始 |
| W7 online采集 | 进行中：关闭代际多member跟踪/回执审计/独占重启续接；共用capture输入接完整图/行 | 通过有限样本：seq51真实3873块/归档零差异、独立空batch checkpoint/ACK全字段核验/重复稳定/缺凭证拒绝；旧collector重启/SIGKILL/半帧拒绝保留；最新73/74活跃跨代际真实采集与归档零差异；新kernel0bcb/313完整合并两UPDATE/native零差异，首73拒绝/三个重封反例拒绝，88SHA/前182不变；真正跨发布状态恢复待证；C++真实采集/实时/高压/断电/Windows/ASM待证 | 通过有限关闭批次→typed事务/可信baseline→完整DML或已审核空batch→target commit→checkpoint/ACK；旧四消费者强杀保留（两文件边界lab插桩）；跨批活跃状态/生产字典/断电/生产现场待证，有关闭延迟 | 未开始 | 待证 | 未开始 |
| W8-W11 扩展类型/LOB/RAC/ASM/TDE | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |

## 当前证据

最新：[历史覆盖缺口/SYS关联](DELIVERY_W5_SCHEMA_COVERAGE_GAP_20261005.md)：seq56/57两组opcode变异DERIVED结构通过、原9275ab90均rc0/2事件；新kernelbb0225bc/260补未知24.x，24.0派生rc1/stdout0、静态混合字节一致，0.0擦除仍rc0/2事件，P0未关闭。原SCN ASOF六个SYS位置、raw六条OBJ$/TAB$写入及四条同XID COL$新行/native位置零差异，仅独立尺子，不是新authority。失败保全，无源/目标动作、无新产品/HTTP，旧735不当新制品测试。下一单DISPATCH_W5_TRUSTED_DICTIONARY_ANCHORS_20261005；历史字典/现场发布/独立评审待证，goal已核active，automation保留。

最新：[历史schema真实ABA反例与raw屏障候选](DELIVERY_W5_HISTORICAL_SCHEMA_BARRIER_20261005.md)。新专属seq56/57、SCN2501884基线后ADD nullable→稀疏UPDATE→DROP恢复→UPDATE，首尾列/对象代际一致，旧内核rc0输出2个三列事件、第一事件漏EXTRA=NULL；原失败保留。新kernel9275ab90/259项通过，同raw rc1/stdout0；受影响混合/空batch冻结结果字节不变，无源重放。产品e4d3aa9/735完整构建通过，实际认证HTTP首次/恢复两批REFUSED/event0，原因精确字节核验；新目标/ledger/CP/ACK零推进，窗口PENDING，三比较反例检出，helper停止，NO/NO/旧表保持。仅19.3已观测24.1 raw marker有限屏障，缺失marker/未知字典/跨窗schema完整覆盖、现场/发布仍待证；独立评审PENDING。两笔源事务及seq56/57已关闭，严禁重放。接续DISPATCH_W5_SCHEMA_COVERAGE_20261005；长期目标未完成，automation保留，goal工具仍usageLimited而实际额度已恢复，未擅改goal状态。

最新：[实际获取账关联新raw完整DML](DELIVERY_W5_ACQUIRED_NEW_DML_20261005.md)。产品996cfd5/735项全通过；actual acquisitionId/SCN2495684→新seq52—54，native四图11/4/7及三完整中间像/SQL/基线DUMP零差异，两回滚0事件。实际HTTP坏authority目标前拒绝/目标0，恢复同task DONE/新专属目标三事件commit→控制CP→持久ACK全字段独立一致，重复及应用重启无新增/核验字节稳定、异步3 DONE/1 FAILED实库审计。284非secret文件双SHA，collector超时host rc未知/leaf探针失败/SQL换行分别保全，不重放源事务。helper停止/旧表及NO/NO保持。静态独占fixture有限范围，历史DDL/ABA、三库新增迁移、跨窗/C++/独立目标/现场发布待证，评审PENDING，goal active；接续DISPATCH_W5_HISTORICAL_SCHEMA_BARRIER_20261005。

最新：[实际获取可信控制账候选](DELIVERY_W5_ACQUISITION_CONTROLLER_20261005.md)。产品ded59ab/733项全通过、adapter50feffa8；专属Oracle控制库实际认证HTTP获取SCN2484139/ACQUIRED_ONLY，重启字段一致、固定SCN独立DUMP所有值/位置/metadata零差异。注入/截断/半文件/authority/SCN/schema/登记SQL故障拒绝，孤儿保留、账仍1/绑定0/事件0；168非secret文件双SHA，失败原文保留。源业务/NO/NO/旧表不变，helper停止。三库新建/升级、Windows/存储/多writer、历史DDL/ABA及新基线后新raw完整DML未通过；评审PENDING，公开生产门禁仍关闭，goal active。

最新：[adapter真实只读源快照获取](DELIVERY_W5_SOURCE_ACQUISITION_20261005.md)。代码83cb49f/模块19全通过，新adapter50feffa8，kernel/SPI复用；三个既有表真实固定SCN2480910/15/20获取并由原内核导入，独立DUMP全部值字节/NULL/隐藏物理列/rowid/source/schema零差异，四实库拒绝无快照/连接关闭。源码/制品/probe执行前SHA与1872文件双SHA（含m2缓存）；只为专属实验新建只读账号/权限，不算生产零配置；源业务/NO/NO/旧表不变，helpers退出。三SCN晚于旧captures，禁止用新快照重建旧DML；没有新事件/目标写入。产品adapter仍旧坐标，可信控制器/持久authority/历史DDL/ABA/leaf/跨窗等待证，评审PENDING，goal active；接续DISPATCH_W5_TRUSTED_ACQUISITION_CONTROLLER_20261005。

最新：[已提交、ACK待恢复事实](DELIVERY_W6_ACK_PENDING_20261005.md)。代码dc18565完整提交树728项全通过；只有目标核验/控制成功后ACK失败返回绑定task/batch/source/window的类型结果，错身份不采信。专属新控制/目标实际HTTP首次ACK冲突与FAILED空待办重复失败，两次审计FAILED且published=1/ACK_PENDING；保全冲突后恢复DONE、唯一批次不增、目标完整中间像/成员/CP/ACK零差异、DONE重复不变、三比较反例检出。125文件双SHA、HTTPdriver执行前封存；首轮编译失败保留。源业务/NO/NO/旧表不变，helper正常停止。评审PENDING；DONE异常恢复状态表达、生产字典/租约/跨窗/C++/独立目标/其他方言/现场发布仍待证，goal active。

最新：[实际HTTP混合DML与两处进程强杀](DELIVERY_W6_HTTP_CRASH_20261005.md)。审计修复411cd64完整提交树724项全通过；同件专属服务认证HTTP，真实目标INSERT等待时SIGKILL137/目标0，目标三事件commit后控制窗口UPDATE等待时SIGKILL137/目标3保留控制回滚；重启HTTP恢复DONE，完整目标中间像/成员/账本/CP/ACK零差异、DONE重复字节不变、三比较反例检出。15+266文件双SHA，源业务/NO/NO/旧表不变，混合helper正常停止。首次SQLPlus长行rc0实际失败、NOWAIT正确拒绝、脚本/观察失败保留；lab源码SHA执行后封存不冒充执行前。独立评审PENDING；生产字典/租约/活跃跨窗/C++/独立目标/其他控制方言HTTP/现场发布及ACK失败published口径仍待证，goal active。

最新：[实际有界Java Facade运行时](DELIVERY_W6_BOUNDED_RUNTIME_20261005.md)。产品完整代码树1c6d75e/723项全通过；批准raw/基线→固定二进制→目标独立commit核验→真实控制事务→持久ACK，专属Oracle控制四组2/2/3/0通过。实际CP SQL后失败全回滚、控制提交后ACK冲突/空pending实例恢复及DONE重复；SQL完整目标/账本/CP/ACK四组零差异、三比较反例检出，316文件双SHA。编译/测试/旧控制eventId冲突失败保全，SNON复用旧冲突轮目标；源业务/配置不变，不重放源DML。HTTP、跨进程/SIGKILL、其他控制方言完整Facade、独立目标、生产字典/租约/C++/现场发布待证，评审PENDING，goal active。


最新：[Facade发布/恢复接线候选](DELIVERY_W6_FACADE_PUBLICATION_20261005.md)。143f78f完整提交树715项全通过；四新增编排单位测试证明Oracle不走旧SQL先checkpoint路径、ACK失败后空pending恢复不重复decode、恢复证明损坏拒绝。仅服务器端口接线，真实Oracle实现/目标首次提交+控制+持久ACK/跨进程恢复仍待证；公开decoder仍拒绝。上一单24项实际控制事务不重跑，评审PENDING，部署未开始/现场待证/发布未开始，goal active。


最新：[控制事务与独立核验](DELIVERY_W6_CONTROL_PUBLICATION_20261005.md)。产品完整代码树24ae9e0/711项0失败0错误0跳过；三库NEW/UPG四组24项实际checkpoint SQL后故障全回滚、真实来源锁双线程单次发布及重复0通过；纯JDBC独立查询24组账本/状态/完整CP/目标凭证零差异，三比较反例拒绝。205文件双SHA；首次编译/审计依赖失败保全，仅补失败，无源业务/配置/既有目标变化。完整Facade、控制提交后ACK、跨进程恢复、独立目标实例/生产字典待证；独立评审PENDING，现场/发布未通过，goal active。


最新：[实际commit后核验失败窗口](DELIVERY_W6_POSTCOMMIT_PROOF_20261005.md)。复用8de94aae adapter/16不重跑，四新专属目标首次应用2/2/3/0；实际commit后查询不可用均rc1/stdout0，无CP/tmp/ACK，恢复/重复0且全target快照不变；独立中间像/成员/凭证/CP/ACK四组零差异，三比较反例检出。空批比较器错切片失败保全，只补只读比较；源业务/NO/NO/原旧表不变，helpers退出。完整产品Facade控制事务/并发/独立目标实例待证，现场发布未通过，goal active。

最新：[独立只读commit核验](DELIVERY_W6_READONLY_COMMIT_PROOF_20261005.md)。adapter实现2d03896/16测试；一条SELECT一致性视图验证真实target/source/schema/batch/成员/完整像。四冻结组2/2/3/0通过、44查询结果篡改拒绝，四已发布CP/ACK缺commit拒绝且原字节保持，健康恢复/重复零DML、实际目标快照不变。首轮新目录反例编排失败保全；产品Facade及新apply后核验失败窗口仍待证，公开gate拒绝，goal active。

最新：[有界可信registry交付](DELIVERY_W6_REGISTRY_20261005.md)。产品完整树9d65d58，707项测试通过；MySQL/SQLServer/Oracle新建与升级四冻结组24/24通过，真实绑定及拒绝记录落库、ledger0；仍ORACLE_ATOMIC_TARGET_NOT_ACCEPTED，公开decoder未开放。181文件双SHA安全摘要，首轮Oracle密码长度及全局V728冲突失败保留；目标提交顺序/完整产品/独立目标实例/生产字典/C++现场与发布待证，评审PENDING，goal active。

最新：[typed evidence与未绑定拒绝](DELIVERY_W6_TYPED_EVIDENCE_20261005.md)。产品代码ddff6ae完整提交树clean verify 704项0失败0错误0跳过，docs f43b66b/草稿PR#3（依赖#2）待独立评审；SPI fb9c443b…绑定私仓坐标，adapter dfcfe38b…/16通过，kernel96095420…复用/254不重跑。四冻结组2/2/3/0事件typed字段及8capture容量独立零差异、JSON roundtrip/三legacy投影不变、四比较反例拒绝；实际product classes/fake Mapper链确认typed到gate，未绑定Oracle含空批次拒绝、账本/target0、checkpoint/消费状态不动，options不能授权公开decoder。编译key误用及Windows换行比较失败源码/原文保留，仅补失败；85文件/14命令双SHA。可信registry/引擎guard未接通，Facade目标前推进checkpoint的顺序尚需Oracle专属真实修复，[接续P0派单](DISPATCH_W6_TRUSTED_REGISTRY_TARGET_ORDER_20261005.md)，完整产品/历史字典/跨窗/C++现场/发布待证，goal active。

最新：[产品null LSN明确拒绝候选](DELIVERY_W6_PRODUCT_NULL_LSN_20261005.md)。产品独立分支代码784a569、文档19f8806，完整提交树reactor 701项0失败0错误0跳过；三种缺LSN测试拒绝/零账本写入（fake Mapper，未当真实台账验收）。三真实冻结批次用实际reactor候选class再核验rc1/stdout0，身份/来源/LSN明确拒绝且无null异常；37文件双SHA、83测试报告指纹。草稿PR#2已推送/附属本chat，独立评审PENDING/未合并部署，shared dirty保留。下一步[typed batch evidence与trusted registry接线](CONTRACT_W6_ORACLE_PRODUCT_BINDING_20261005.md)，不把SCN/paired undo/transport序号混成LSN，不由options授权；完整产品/历史字典/跨窗/C++现场/发布继续待证，goal active。

最新：[实际产品gate与空侧契约](DELIVERY_W6_NULL_SIDES_PRODUCT_GATE_20261005.md)。共享StreamHouse保持只读，实际gate源码隔离编译，三真实冻结批次rc1/stdout0，身份/源拒绝后null legacy LSN异常；未伪造字段开放门禁。adapter c98b1a37…/16通过，内核96095420…复用/254不重跑；INSERT before/DELETE after改null，存在侧SQL NULL保留。新SNON/SNUL各1+1、SMIX3+0+0、重复0/checkpoint原字节稳定；SFAIL约束失败整批0/0/0且无checkpoint/ACK、恢复3/重复0。四新专属目标20像计数/18成员/8凭证与全字段checkpoint/ACK独立零差异，三比較反例拒绝，三个投影逐字节不变；0源DML/配置改变。完整产品typed源/redo位置、生产注册、跨窗/历史字典、C++采集/现场/发布继续待证，goal active。

最新：[真实独立空capture到target/checkpoint](DELIVERY_W7_EMPTY_CAPTURE_TARGET_20261005.md)，源driver f658a9a，一次新空基线/INSERT→ROLLBACK，seq51真实3873块/归档零差异；旧a084完整batch非begin控制拒绝保全，精确外来begin排除修复后kernel96095420…/254测试、adaptercab86009…/15通过。四控制/native归属及两图6/2/4零差异、基线后回滚2/2/0；完整batch0事件/空state，旧四完整batch逐字节不变。专属空目标0业务/0审计/0成员/1凭证events0、实际checkpoint/ACK全字段独立零差异与重复0稳定；缺已发布目标commit拒绝/恢复0，三raw重封结构0/语义1stdout0及三独立比较反例。失败测试越块/预检SQL转义原文保全，仅补失败和未执行项。643文件双SHA清单；源旧DML不重放，原表/NO补充日志保留，helper退出。下一步[产品公开契约门禁](DISPATCH_W6_PRODUCT_GATE_CONTRACT_20261005.md)：只读发现SQL身份/LSN/空侧语义不兼容及C++目录sweep不能证明Oracle采集；生产字典/活跃跨窗/全产品/现场/发布未完成，goal active。

最新：[冻结新DML目标提交/checkpoint](DELIVERY_W6_W7_NEW_DML_TARGET_20261005.md)，私仓版本0.1.0-bounded-4f79691复用a084内核/251不重跑，adapter029ffa13…/15模块测试通过且运行class与旧adapter相同。44→45与46→47各1+1新增，48→49→50为3+0+0，各前缀重复0/checkpoint原字节稳定；四专属目标20完整像/18成员/8凭证及checkpoint全scope/位置/ACK独立零差异。混合UPDATE目标约束失败rc1/stdout0、0/0/0且无checkpoint/ACK，修复目标条件后恢复3/重复0；三比较反例/public拒绝。0源DML/日志配置变化，helper退出。探针参数失败原文保全；49—50单独后缀因可信基线属于48而BASELINE_SCN_OUTSIDE_INPUT_COVERAGE拒绝，不伪造新基线、不当独立空batch通过。下一步[合法空批次/产品执行包](DISPATCH_W7_EMPTY_BATCH_AND_PRODUCT_20261005.md)；同实例跨schema不当独立目标/完整产品/现场/发布，goal active，automation已绑定本chat。

最新：[同事务混合/全回滚capture](DELIVERY_W3_MIXED_ROLLBACK_CAPTURE_20261005.md)，生产4f79691/a084218e…不改/251不重跑，driver30e42f4。专属三事务各一次，48/49/50捕获9039/10/17块与归档数据块零差异；混合INSERT→UPDATE→DELETE三完整事件、两rollback零业务事件，四图11/4/7与native全图、三完整中间像/SQL步骤/capture-archive及最终seed零差异。晚DELETE before和缺回滚marker重封raw scan0而rows/physical1/stdout0，三比较反例/public拒绝。比较器首轮SQL下标漏ID失败源码保全，仅补失败和未执行项。旧fixture保留/源操作不重放；下一步冻结非空/NULL/混合/rollback新DML到target commit/checkpoint，完整产品/现场/发布待证。

最新：[可信nullable尾列完整NULL行](DELIVERY_W3_NULLABLE_TAIL_20261005.md)，生产/lab4f79691、kernela084218e…、251测试通过。物理解析class未变/ABSENT保留；仅可信实际schema下完整F/L INSERT/DELETE inverse省略nullable尾补NULL，普通Baseline不授权。冻结46/47两完整NULL事件native/SQL零差异、capture/archive及最终seed一致；普通Baseline/非nullable/错回执/重封晚DELETE before四拒绝stdout0，三比较反例检出，旧两非空DML/三UPDATE完整batch逐字节不变/public拒绝。0数据库动作/不重放源步骤；下一步同事务混合操作及INSERT/DELETE rollback capture，再新DML target/checkpoint，完整矩阵/产品/现场/发布待证。

最新：[flags0无引用普通commit有限闭合](DELIVERY_W4_UNREFERENCED_COMMIT_20261005.md)，生产/lab69256c9、kernel2bcea2cb…、249测试通过。同块/无补偿/完整source生命周期与undo-marker覆盖才关闭，不伪造native tail；seq46/47三物理图6/0/6与native完整图零差异。六重封raw scan0/physical1/stdout0、缺LIVE比较反例检出；旧两非空DML与三UPDATE完整batch逐字节不变，rollback-only空/实际跨基线/public拒绝。generic合法flags0两字段现在受限支持，旧制品全缺tail拒绝不再作不变能力要求；flags2缺tail仍拒绝。完整NULL batch到达ROW_REPLAY_INCOMPLETE_INSERT、事件0，未认证NULL行；0源动作/旧操作不重放。下一步可信完整F/L rowpiece省略nullable尾列，混合/回滚capture/target/产品/现场/发布待证。

最新研究：[尾列NULL capture失败正样本](RESEARCH_W3_NULL_CAPTURE_20261005.md)，生产 bed7fdb/58da93ea…不改，新driver9f7504e。专属新表一次INSERT11/NULL/NULL和DELETE，seq46/47共1939/7块与归档数据块零差异、raw唯一leaf50333107；完整batch rc1/stdout0/事件0。三raw/native结束记录零差异：实际baseline2423979，DELETE end2424017 flags0两字段无tail，明确在基线后；不能用旧基线过滤修复。两目标raw heap pair/native完整F/L cc1只ID、尾两列物理ABSENT而SQL为NULL，尚未转换为完整行。研究探针首次未筛外来对象失败原文/源码保全，修正只读归属后两pair通过；0源重放/生产测试不重跑。下一步先独立验证无引用commit闭合（不得伪造native tail），再可信完整行nullable省略；NULL/混合/回滚capture及target/产品/现场/发布待证。

最新：[可信基线/capture完整INSERT/DELETE](DELIVERY_W3_BASELINE_CAPTURE_DML_20261005.md)，生产/lab bed7fdb、kernel58da93ea…。完整raw生命周期/节点/foreign路由不删除，可信获取基线前事务被覆盖但不宣称其旧图关闭；基线后完整关闭，generic无tail及跨基线继续拒绝。冻结seq44/45两完整非空INSERT/DELETE、native/SQL与capture/archive行及最终seed零差异；旧三UPDATE完整batch逐字节不变、rollback-only空结果、晚before/错回执/错source/基线后无tail/实际跨基线及public events拒绝。生产245测试、新增后类7测试通过；提前复制旧jar与合成activation=0测试失败保全，仅补失败项。0新数据库动作/源操作不重放；NULL/混合/回滚capture矩阵、新两DML到target、产品/现场/发布待证。

最新研究：[新 INSERT/DELETE capture 基线边界](RESEARCH_W3_FULL_DML_CAPTURE_20261005.md)，生产 ec3b430 不改。专属新表一次 INSERT/DELETE、seq44/45 journal 与归档数据块零差异，raw 唯一 leaf50333091；完整 batch rc1/stdout0/事件0，`TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE`，不算完整 DML 通过。raw/native 三结束 record 零差异：seed end2421224<实际 baseline2421228、flags0无 tail；实际 INSERT/DELETE end2421252/2421268 flags2均有正常 tail。三 native SCN/flags/tail 反例检出。原 DML 不重放、新表仅 seed、旧 fixture 未改；15/245及强杀不重跑。[下一步](DISPATCH_W3_BASELINE_PHYSICAL_SCOPE_20261005.md)修可信基线物理范围，保留 generic 缺 tail、跨基线及 foreign 补偿拒绝；公开 decoder/产品/现场/发布未通过。

最新：[已发布checkpoint恢复拒绝](DELIVERY_W7_CHECKPOINT_REFUSALS_20261005.md)，生产ec3b430不改/lab2f36394，[清单](evidence/DELIVERY-oracle-checkpoint-refusals-20261005.json)。五缺raw/baseline/回执/已发布registry输入拒绝；六实际target commit/member/payload/count/source/schema损坏拒绝，全部rc1/stdout0/消费原文件SHA及集合保留，消费者不补写破坏目标；六条件恢复后各0新增DML，CPBE/CPNO完整快照回原健康状态。0新增源/目标业务DML、仅专属target元数据受控修改/恢复，LGWR5601/helper退出，15/245及强杀轮次不重跑。有限checkpoint恢复负门禁完成，不等于生产trusted registry或完整DML/产品。下一步按[完整INSERT/DELETE执行包](DISPATCH_W3_FULL_DML_CAPTURE_20261005.md)回到解析主线；public decoder/现场/发布仍待证。

最新：[新DML/checkpoint四处消费者强杀](DELIVERY_W7_ORACLE_CHECKPOINT_CRASH_20261005.md)，生产ec3b430不变/labb241002，[清单](evidence/DELIVERY-oracle-checkpoint-crash-20261005.json)。专属新target25表，正常未插桩prefix2+full1/两重复0；四实际client SIGKILL wait137：commit前回滚0/0/0后恢复3，commit后、tmp.force后、checkpoint目录force后恢复各0，全部再次消费0；tmp原字节retained/checkpoint后原字节保留补ACK，第二进程独占拒绝。两文件边界lab-only插桩记录前后class SHA，原jar不变、正常/恢复不插桩；15/245轮次不重跑。五当前target六完整像/成员/凭证及checkpoint全部scope/capture位置字段独立零差异，三个重算checksum比较反例检出，source业务/日志配置无变、LGWR5601/helper退出。不是独立目标实例/无插桩现场/断电证明；缺历史输入与已发布target损坏继续补负门禁，再Oracle SPI/生产可信字典。raw删除/全产品/部署现场发布未通过。

最新：[消费checkpoint实现与既有目标恢复](DELIVERY_W7_ORACLE_CHECKPOINT_20261005.md)，运行时ec3b430/labdeabf27，[清单](evidence/DELIVERY-oracle-checkpoint-20261005.json)。新adapter6f06247e…/15测试，kernel ea9f58c6…/245保留、SPI不变；消费全生命周期文件独占、可信原始窗口registry、实际target完整凭证验证后checkpoint/每批ACK。真实Linux/Oracle既有MIXD commit恢复42/9/end2366759、旧前缀不回退/文件byte稳定；完整temp核验恢复并retained保全、合法落后prefix追回42；四坏temp/checkpoint/ACK/unknown输入反例stdout0/原文件SHA集合不变。目标完整SQL快照不变、0新增DML/0源动作，LGWR5601/helper退出。新DML→checkpoint/四处实际SIGKILL/跨进程锁/缺历史raw仍未验收，文件派生不冒充强杀；下一步专属新target有限2+1及崩溃矩阵。raw删除/全产品/现场/发布未通过。

最新：[类型化固定范围消费窗口](DELIVERY_W7_ORACLE_WINDOW_20261005.md)，运行时8eae1ba/lab4f104cf，[清单](evidence/DELIVERY-oracle-window-20261005.json)。新kernel ea9f58c6…/245测试、adapter ee0ff207…/11测试、新独立m2，原私仓不改；内核仅保留已验证leaf参数，不改parser/digest。私有factory窗口绑定source/schema/baseline/leaf、capture闭合范围；原40—41 seq41/7块/end2366749与40—42 seq42/9块/end2366759扩展/覆盖/同范围及原receipt一致、三完整事件旧输出byte一致，baseline回执派生漂移拒绝/capture不可变。0数据库动作、events继续拒绝。接口不是commit SCN/target证明，持久checkpoint及SIGKILL尚未实现；接续执行包，不授权删除raw，全产品/现场/发布待证。

最新：[真实目标凭证破坏与恢复](DELIVERY_W6_ORACLE_RECEIPT_FAULTS_20261005.md)，lab833b79e，[清单](evidence/DELIVERY-oracle-receipt-faults-20261005.json)。生产模块/制品未变；已commit/ACK下实际缺成员、payload冲突、commitCount冲突、sourceBinding冲突四拒绝rc1/stdout0/ACK不变，损坏状态无消费者补写；四条件恢复后完整目标行集合一致、消费各0新增，MIXD最终健康3/5/2。跨batch同ordinal SQL排序导致首次逐字节断言失败保留，续接排序完整行并纳入IDENT，仅跑未完成项。源业务/日志配置无变，LGWR5601/helper退出。消费checkpoint未实现，接续[执行包](DISPATCH_W7_CONSUMER_CHECKPOINT_20261005.md)；不授权删除capture，公开decoder/全产品/现场/发布待证。

最新：[有界跨批与两旧一新目标凭证](DELIVERY_W6_ORACLE_OVERLAP_20261005.md)，lab557195d/0baea8e，[清单](evidence/DELIVERY-oracle-overlap-20261005.json)。生产模块/245及5测试制品不变、强杀不重跑；metadata-only派生14749帧redo未变，3EVENT/ROW完全一致，新batch在POST0新增/重复0，当前3/6/2及原ACK不变，三比较反例检出。原始40—41先2新增、40—42重叠两旧一新仅1新增，两批重复各0；专属新MIXD五表当前3审计/5成员/2凭证、六完整像及原SQL零差异，两ACK核验。原目标POST旧3/3/1为历史时点；当前MIXD健康。源业务/日志配置无变、LGWR5601保留/helper退出；同实例目标DDL不是生产零数据库配置证明。下一步消费checkpoint/元数据成员实际拒绝与恢复；任意活跃窗口/生产字典/公开decoder/全产品/部署/现场/发布待证。

最新：[真实Oracle目标提交与ACK恢复](DELIVERY_W6_ORACLE_TARGET_20261005.md)，模块6a6ce3e、lab937705b、jar8103c262…，[清单](evidence/DELIVERY-oracle-target-20261005.json)。专属同实例新目标schema/35表，五表原子DML/审计/成员/提交凭证后ACK；三客户端SIGKILL rc137、四同批重复0新增、六实际拒绝；三fresh恢复目标完整六像/凭证/ACK零差异，四独立比较拒绝。NORM最后有意污染，只引用破坏前快照，不当当前健康。密码长度及CRLF失败保留，仅续未执行项；口令排除哈希和提交。源业务DML/日志配置无变、LGWR5601不变、helper已退出；目标实验DDL不是生产零数据库配置证明。模块5测试/内核245保留；公开decoder、全产品接入/部署/现场/发布仍未通过。下一步不同/重叠批次去重与成员，再消费checkpoint及Oracle窗口/可信历史字典。

最新：[Oracle独立SPI转换模块](DELIVERY_W6_ORACLE_PROJECTION_20261005.md)，模块91e675a、内核e720013不变，[清单](evidence/DELIVERY-oracle-projection-20261005.json)。adapter79bbd5cf…、产品完整67bfb77公开SPI contract jar93e451fe…，私有Maven不替换全局制品；5模块测试通过/245内核轮次不重跑。冻结raw三UPDATE投影实际SPI、NUMBER/VARCHAR2/隐藏列六完整像与原SQL零差异；四输入拒绝/晚UTF8 raw内核3事件而转换整批stdout0、两typed比较反例。Oracle身份命名空间及类型化SCN/undo sidecar，不伪造LSN/GUID/commitAt，公开decoder rc2/0事件且无授权配置绕过。GBK首编失败保留，仅UTF8续接。无源/目标/产品部署动作，下一步真实Oracle专属target DML/commit凭证及ACK；窗口/可信schema/完整产品pipeline/现场/发布未通过。

最新：[W6完整capture batch接口](DELIVERY_W6_CAPTURED_BATCH_20261005.md)，生产e720013、独立lab f7303ac，[清单](evidence/DELIVERY-captured-batch-20261005.json)。新jar15b07ddf…/245测试，通过新不可变API缓冲完整scope、绑定source/schema/完整XID/undo位置/实际起止record SCN，eventId与payload/batch摘要分离。冻结40—42三UPDATE输出与旧完整行逐字节一致，新API重复身份/内容稳定；native三undo位置/六起止SCN、三完整行/原SQL零差异，六整批stdout0拒绝、两metadata比较反例检出。没有源动作或既有解析类变化，公共events仍拒绝。下一步独立产品Oracle adapter/严格整批scalar解值和Oracle位置映射，再真实支持方言target receipt；产品部署/现场/发布待证。

最新：[消费目标提交/ACK恢复](DELIVERY_W7_CONSUMER_COMMIT_20261005.md)，lab源码7cd63da，[清单](evidence/DELIVERY-consumer-commit-20261005.json)。冻结40—42 raw journal经同件240测试Java再生成三完整UPDATE，独占本机SQLite目标实际同事务写三DML/应用身份/commit凭证后才ACK。三真实Windows消费者强杀rc1：提交前两写回滚，提交后ACK前从目标凭证恢复0新增，ACK后恢复0新增；正常路径/四重复均零额外应用。八拒绝含双进程锁、迟到before全回滚、ACK无commit、输入/目标/应用凭证冲突；三恢复目标与三原源提交SQL完整中间像及最终行零差异，四独立比较器反例检出。原始Git archive CRLF封存失败保留，仅补LF封存；无Oracle动作，未重建内核/重跑接受轮次。只认证有界实验target，非StreamHouse支持方言/部署/现场/发布，公共events拒绝。下一步[W6接入执行包](DISPATCH_W6_ORACLE_COMMIT_20261005.md)：Oracle envelope/目标同事务receipt及三位置隔离，不把append ACK当target ACK。

最新：[多member重启与真实collector崩溃](DELIVERY_W7_CLOSED_RESUME_20261005.md)，初版084cf37、最终c689b7a，[清单](evidence/DELIVERY-closed-resume-20261005.json)。实际原40—42重启next43，一次专属实验关闭43（无DML/config），保存9163块/9161归档数据块零差异，next44，原journal未变；完整journal+逐代receipt之后/总receipt之前真实自杀SIGKILL rc137，旧总receipt下完整尾恢复不改字节；第一帧100 bytes真实SIGKILL后续接拒绝/stdout0、文件保留。11 ledger/frame/source/双writer反例READY前拒绝，锁释放后最终代码只读40—43续接通过；Oracle LGWR独立存活。只证明进程崩溃，不证明断电；半帧不自动修复。Java240制品不重建。下一步消费checkpoint/目标commit及ACK恢复/W6；消费ACK/实时/现场/发布未通过。

最新：[capture完整事务与行](DELIVERY_W7_CAPTURE_ROWS_20261005.md)，输入8cc8e21、最终生产b712d56、独立lab b3490b8，[清单](evidence/DELIVERY-capture-rows-20261005.json)。最终240测试/jar SHA7d4345f1…，复用已有baseline2366727及seq40—42，完整三图3/0/3、三UPDATE/native/三提交后SQL零差异，最终11/blocking-closed/new-note；capture/归档graph/rows/state一致。初版真实marker3短值拒绝保留，仅新增已证XA/column1/ncol3/16byte profile，不泛化XR/未知长度。六capture语义/连续性反例整批stdout0，晚before physical通过而rows拒绝，四比较器反例检出；旧LKR/全回滚及indexed partial归档输出回归一致，events拒绝。driver拒绝原因断言错误保留，续跑跳过已执行项；无源动作重放。下一步capture/解析/目标ACK独立持久位置及恢复/W6，实时/现场/发布仍未通过。

最新：[journal直接record/vector解析](DELIVERY_W7_CAPTURE_RECORDS_20261005.md)，生产cba8bbc、独立lab da24a3c，[清单](evidence/DELIVERY-capture-records-20261005.json)。237测试通过，jar SHA cf8f4898…；三原始关闭journal不造归档，19516 records/36124 vectors完整native位置/结构/opcode零差异，包含9023跨块record。与独立archive及旧已验收jar全部vector字段byte一致；八capture反例含晚record重封破坏整批stdout0，四比较器反例检出；events继续拒绝。本轮只读/diagnostic dump无源DML/config/switch。结构集成不替代事务/DML/字典/实时/恢复/ACK/现场/发布；接续复用三UPDATE真实baseline把capture接入完整物理事务/行，再W6与三位置持久协议。

最新：[原始关闭代际跨member捕获](DELIVERY_W7_CLOSED_FOLLOW_20261005.md)，源码f46e776，[清单](evidence/DELIVERY-closed-follow-20261005.json)。一次新专属CAS三更新/三实验归档，最终恢复11/blocking-closed/new-note；初版seq40/41/42共14765原始帧，14759归档数据块零差异，关闭SCN连续，原online头未改。真实复用seq43后追40明确缺口拒绝，七原始前缀派生拒绝；最终源码额外保护后实际41/42只读复采对账通过/执行文件SHA已核。首轮执行源码SHA未封，初版三组事实不冒充最终制品三组验收；后续driver封存步骤已补但未重跑源动作。关闭代际有延迟，多member重启/实时record/ACK/接入/现场/发布未通过。接续Java journal原始块直接解析，不造归档。

最新：[固定member观察journal续接](DELIVERY_W7_OBSERVATION_RESUME_20261005.md)，源码00358ce，[清单](evidence/DELIVERY-observation-resume-20261005.json)。专属只读CURRENT seq40前4096块，首次及重启4096帧/3508026 bytes/SHAd4690904…，原前缀保留；合法落后回执后完整尾帧重审续接通过，13反例rc1/stdout0及独占writer/释放后恢复通过。实验副本root属主PermissionError原文保留，仅修副本并续跑未执行项；无源DML/配置/切换，Java233已通过轮次不重跑。仅同member同代际有界观察，不认证LGWR完成/record边界/断电/跨组/ACK；capture_complete=false/事件0。接续多member跨组代际跟踪及缺口拒绝，再完整record恢复/W6；目标继续进行中。

最新：[真实锁等待完整行与LKR节点](DELIVERY_W5_LKR_ROWS_20261005.md)，运行时592c817、独立lab6b9fe9d，[清单](evidence/DELIVERY-lkr-rows-20261005.json)。233测试通过；连续seq37/38/39含旧失败回滚，完整三图5/1/4 native零差异，LKR独立LOCK kind/明确to1与inverse0/列ABSENT、保留在图但不产出UPDATE；A两UPDATE/B一UPDATE三完整行与native/四SQL时点零差异。native证明A end早于B begin，真实等待不等于已活跃同key窗口交叠支持。六重封raw scan0而physical或晚before rows拒绝/stdout0，五比较器反例检出。旧INSERT/DELETE/全及部分回滚/不同行并发native回归、rollback-only空批次与边界拒绝通过；events拒绝，重复events输出名driver防覆盖assert未执行。无源动作重放，最终11/blocking-closed/new-note。主线接续W7持续capture/组切换/record续接/ACK，再W6；一般锁/类型/历史字典/信任/恢复/现场/发布仍待证。

最新：[实际0x4a开始控制有限交付](DELIVERY_W4_START_4A_20261005.md)，运行时3a3a368、单点probe0287d11、只读前缀导出14cebb5，[清单](evidence/DELIVERY-start-4a-20261005.json)。231测试通过；真实38:14:16原始4a/record1/size176/parent0精确同record begin UBA绑定，完整XID10.17.1678与native一致，五重封字段反例scan0/start拒绝stdout0。旧seq30/31/32完整行native回归及真实初始边界拒绝通过。原38/39在4a通过后遇未发现补偿；只读补37及全范围dump后到达真实B LKR锁行opcode拒绝，physical/rows仍rc1/stdout0；并发完整图/行未通过。下一步冻结LKR节点覆盖与非业务值变化语义，不能忽略锁节点，已提交源动作不重放。源表当前11/blocking-closed/new-note；capture/接入/现场/发布仍待证。

最新：[真实同一行锁等待样本及解析拒绝](RESEARCH_W5_BLOCKING_ROWS_20261005.md)，源57bb80f→b7a49ac，复用55c331a，生产代码未改，[清单](evidence/RESEARCH-blocking-rows-20261005.json)。新r2实际snapshot2362439后A两更新、B先SELECT FOR UPDATE，独立v$session证明TX row lock WAITING及A阻塞者绑定，等待时旧值可见；A/B完整XID10.17.1678/10.22.1675均提交rc0，seq38/39冻结，最终一行11/blocking-closed/new-note。physical/rows均UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT rc1/stdout0，native A begin38:14:16 flags0x4a未支持，不能算同key逻辑行通过。首次PRIOR变量导致B语法失败、A回滚，未提交，原目录保留；仅新目录提交实验一次，不重放。下一步只读修真实0x4a开始控制/UBA/parent门禁及完整物理图，再完整三行native/SQL对账；capture/接入/现场/发布仍待证。

最新：[基线之后真实rollback-only空批次](DELIVERY_W5_ROLLBACK_ONLY_20261005.md)，源8da2f77、独立lab d9baca0，复用55c331a/229测试制品不重复构建，[清单](evidence/DELIVERY-rollback-only-20261005.json)。实际snapshot2361733 SHA5f42117b…之后自有XID10.21.1675对ID11两次CAS UPDATE后完整ROLLBACK，无commit；seq36 SHAd7c65405…冻结。物理2/2/0与native完整位置/UBA/取消图零差异，真实begin/end均在基线后，行变化0/完整一行与旧ASOF/回滚后/最终三SQL快照零差异。五重封raw scan通过而physical/rows rc1/stdout0，缺取消、错恢复值、缺end不能按rollback flag跳过；三个比较器反例检出，events拒绝。源码生成器语法失败在源执行前保全；唯一源动作未重放，原行恢复concurrent-closed/new-note。下一步真实同一行阻塞、无目标静默覆盖与index-only指定分支；持续capture/ACK/接入/现场/发布仍待证。

最新：[完整闭合旧批次零变化](DELIVERY_W5_CLOSED_EMPTY_20261005.md)，实际只读获取/初版761e2e0、最终条件及比较器55c331a、状态probe06e07ee，[清单](evidence/DELIVERY-closed-empty-20261005.json)。新实际snapshot2360835及获取SHA5bbba96a…，连续seq33—35，新35 SHA779f0445…；无DML/DDL/config动作。三笔完整物理图6/0/6结束均在基线之前，行rc0/变化0/完整一行与native/实际基线/两SQL零差异；最终制品再次执行physical/rows/state与初版字节等价复用独立对照，两树229测试通过。无目标、缺begin/end、覆盖不足、未知cleanout、错误receipt拒绝；错误state/凭空事件比较器检出。同件seq30/31/32/并发完整行native零差异、初始跨snapshot/晚before/同key/SCNtie拒绝、events拒绝。补回index-only live缺heap证据的保守拒绝，指定分支实际反例未验收；真实rollback-only空批次和完全无目标静默仍待证，不当持续capture/ACK验收。源fixture仍一行11/concurrent-closed/new-note。

最新：[相同结束record SCN拒绝及空结果约束](DELIVERY_W5_COMMIT_TIE_20261005.md)，派生脚本180d640，复用5bfde64/229测试制品、不重复构建与源动作，[清单](evidence/DELIVERY-commit-tie-20261005.json)。native定位A结束34:8:436/2349213、B结束34:2:148/2349206，两方向只改record SCN并重封校验和，均scan及三物理图6/0/6 rc0，rows指定AMBIGUOUS_ROW_REPLAY_COMMIT_SCN rc1/stdout0。只证明派生歧义拒绝，不证明真实相同SCN支持。下一步完整闭合物理事务零变化的空结果；完全无目标事务的静默需补范围覆盖，未收到end不能算静默成功。空结果/同一行阻塞/持续capture及接入等仍待证。

最新：[不同行并发完整行与提交顺序](DELIVERY_W5_CONCURRENT_ROWS_20261005.md)，源脚本c036164、cleanout修正09655d6、运行时5bfde64、独立lab09ddedc，[清单](evidence/DELIVERY-concurrent-rows-20261005.json)。最终229测试通过；真实snapshot2349199后A更新持有、B独立INSERT先commit且SQL仍见A旧值、A再次UPDATE后commit、最后DELETE B新行；seq33/34冻结，完整三物理图6/0/6及B INSERT→A两UPDATE→DELETE四完整行与native/四SQL快照零差异。6 cleanout篡改及晚DELETE before、同rowkey派生冲突共8 raw scan通过而行rc1/stdout0，四比较器反例检出；同件seq30/31/32回归零差异、seq28/29初始边界拒绝、events拒绝。首次leaf4.1拒绝、错误24字节假设导致r2单测失败及缺jar编译、r3并发行拒绝、共享key派生driver越界均保全，源DML未重放。最终专属一行11/concurrent-closed/new-note。接续[行顺序边界与空结果](DISPATCH_W5_ROW_ORDER_BOUNDARIES_20261005.md)；真实同rowkey阻塞、相同commit SCN分支、一般并发/历史字典/信任/恢复/capture/ACK/接入/现场/发布仍待证。

最新：[真实SAVEPOINT存活完整行](DELIVERY_W5_PARTIAL_ROWS_20261005.md)，源脚本9d4ff98、布局0e502ec、运行时1bfa736、lab验证dfcc2ad，[清单](evidence/DELIVERY-partial-rows-20261005.json)。两新树各227测试通过；实际snapshot2348260后新ID13 INSERT→SAVEPOINT→TXT UPDATE取消→NOTE UPDATE commit，再CAS DELETE新行commit，XID10.6.1643/10.26.1642冻结seq32 SHA357ee00b…。完整物理图4/1/3与2/0/2共6/1/5 native零差异，仅live原始heap位置产出INSERT/NOTE UPDATE/DELETE三完整行，与旧ASOF/中间commit/最终三SQL快照零差异，partial-cancel不产出。新column2/control0x10/XA短值首轮拒绝与r2已闭合物理后的partial拒绝保全，没重放源动作；7重封raw scan通过、物理或最后DELETE before失配整批rc1/stdout0，五比较器与语义/原回执反例检出。同件seq31全回滚、seq30完整行和seq28/29真实边界回归通过，events拒绝。最终原有单行11/rollback-proof/new-note未改。接续[并发完整行/commit顺序](DISPATCH_W5_CONCURRENT_ROWS_20261005.md)；多savepoint/跨块/空结果/初始恢复/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[已闭合完整回滚抑制及后续提交完整行](DELIVERY_W5_ROLLBACK_ROWS_20261005.md)，源脚本0340edf、运行时1930b16、lab验证80135e2，[清单](evidence/DELIVERY-rollback-rows-20261005.json)。225测试新冻结树通过；实际snapshot2346786后专属新ID13 INSERT/UPDATE完整ROLLBACK，再提交既有ID11 TXT rollback-proof，XID10.15.1641/10.9.1639冻结seq31 SHAf5538660…。整件自主物理图3/3/0与1/0/1共4/3/1 native零差异；只输出后续commit的一次完整UPDATE，旧ASOF/回滚后/最终三SQL快照零差异。旧行入口UNSUPPORTED_ROW_REPLAY_ROLLBACK保全；只有完整闭合、全取消/零live图可抑制，不按flag跳过未知事务。4重封raw scan通过、物理或晚before门禁整批rc1/stdout0；语义/原回执before拒绝、五比较器反例检出，同件seq30完整行、已合法INSERT后DELETE晚失配及seq28/29真实边界回归通过，events拒绝。源动作未重放，最终专属单行11/rollback-proof/new-note。接续[SAVEPOINT部分回滚](DISPATCH_W5_PARTIAL_ROLLBACK_ROWS_20261005.md)；partial/并发/无提交变化空结果、初始恢复/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[真实INSERT/DELETE完整行](DELIVERY_W5_INSERT_DELETE_ROWS_20261005.md)，源脚本076d183、复用225测试运行时b9320ff，[清单](evidence/DELIVERY-insert-delete-rows-20261005.json)。实际只读获取snapshot2346182后专属新增ID12提交再CAS DELETE新行提交，XID10.22.1639/10.3.1641冻结seq30 SHA98624100…；完整INSERT after/DELETE before及原ASOF/中间两行/最终一行三SQL快照与native零差异，完整两物理图4/0/4零差异。4重封raw scan通过，其中两键篡改物理仍通过而完整行晚before失配整批rc1/stdout0，另外slot/列数物理及行拒绝；现有行语义/原回执额外行拒绝、四比较器反例检出，events拒绝。本例不证明孤立INSERT的index/heap主键语义认证；内核无修改、不重复225测试，源DML没有重放，原ID11未改。接续[完整行rollback](DISPATCH_W5_ROLLBACK_ROWS_20261005.md)，部分回滚/并发/初始恢复/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[真实初始事务跨快照拒绝](DELIVERY_W5_SNAPSHOT_BOUNDARY_20261004.md)，运行时b9320ff、lab比较/派生5a2344b，[清单](evidence/DELIVERY-snapshot-boundary-20261004.json)。225测试通过；自有未提交会话/独立获取snapshot2345429/原会话继续提交，XID6.2.594冻结连续seq28/29。完整2/0/2物理图和begin2345424<=snapshot2345429<end2345432与native零差异后，完整行BASELINE_CROSSES_TRANSACTION_BOUNDARY、rc1/stdout0。真实窗口空heap OP4.1首轮拒绝保全，只支持已观察空条目/SCN/spare严格布局；4重封raw均scan通过、物理及行入口拒绝。缺begin/end、晚SCN语义、原回执SCN篡改及比较器反例检出，旧seq27两完整行同件jar回归零差异，events两件分别拒绝。实验DML没有重放，最终专属项目行11/boundary-closed/new-note；接续[INSERT/DELETE完整行](DISPATCH_W5_INSERT_DELETE_ROWS_20261004.md)。本例安全拒绝不代表通用初始恢复/历史字典/CDC/capture/接入/现场/发布通过。

最新：[W5 typed世代与获取基线完整行链路](DELIVERY_W5_TYPED_REPLAY_20261004.md)，运行时4afea7f、实验1c7106f，[清单](evidence/DELIVERY-typed-replay-20261004.json)。223测试通过；专属既有项目表一次性CAS TXT再PK UPDATE，同XID2.9.595冻结seq27 SHA061b8b3d…；实际获取snapshot2341293经回执/typed scope直接重建两次完整before/after，native及旧ASOF/最终SQL两快照零差异。完整1事务4/0/4物理图零差异。错角色/旧epoch/错logical/原回执改键及3重封checksum原始派生均rc1/stdout0；比较器5反例检出，同件jar seq15长值120/120/0、seq24三图10/4/6回归通过，events拒绝。范围外未知flags、真实short KDOM2首轮拒绝和错误控制字候选均保全，只修读取/解析不重放DML。控制字0短正长度XA已支持，NULL/short XR未证；接续真实初始跨snapshot边界，再攻INSERT/DELETE/rollback/并发完整行，生产可信交接/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[W5真实只读基线获取/回执导入](DELIVERY_W5_FROZEN_BASELINE_20261004.md)，运行时/获取脚本b1ff3af、比较器014297a，[清单](evidence/DELIVERY-frozen-baseline-20261004.json)。217测试通过；专属现有表只读获取snapshot2341293、完整1行/3列，与实际SQL输出及raw/native5身份字段零差异。原获取SHA下9份未改键/值/SCN/source/map/世代/漏行/截断篡改均rc1/stdout0，即使副本自报新SHA仍拒绝。伪造回执authority也可授权伪键，独立比较器2差异，明确可信控制进程边界，不能宣称SHA认证签发者。实际heap73073→73076/index73074→73075导入保留角色，旧equal-object重建scope拒绝，events拒绝。首次比较器SCN标志位错误rc1保全，只补比较器与未执行项；没有DML/DDL/归档或数据库配置改变。接续[typed角色与新DML链路派单](DISPATCH_W5_TYPED_OBJECT_SCOPE_20261004.md)，生产可信获取交接/历史字典/CDC/capture/接入/现场/发布仍待证。

最新：[W5固定基线完整行与来源反例](DELIVERY_W5_ROW_IMAGES_20261004.md)，最终完整树8b222c8，[清单](evidence/DELIVERY-heap-baseline-20261004.json)。213测试通过；真实seq25三次完整before/after、旧ASOF与最终SQL两快照独立零差异。10基线反例、8原始归档派生及新增forward/undo顺序冲突均整批拒绝，events继续拒绝。顺序派生在旧b8cae3c仍rc0，新jar严格拒绝；两轮原件保全。**未改主键伪基线仍rc0、独立比较3处差异，基线来源验收RETURN_FOR_FIX**；真实固定SCN样本通过不认证调用者声明。无源端动作，接续[基线来源/快照边界派单](DISPATCH_W5_BASELINE_PROVENANCE_20261004.md)。INSERT/DELETE分支无本轮新真实证据，rollback/并发/DDL/世代/完整CDC/capture/接入/现场/发布继续待证。

最新：[W5物理尾列缩短交付](DELIVERY_W5_ROW_EXTENT_20261004.md)，运行时/实验完整源码9b3f738，[清单](evidence/DELIVERY-tail-extent-20261004.json)。209测试通过；seq25两物理图5/0/5、8typed patch、4完整UBA pair含1尾列3→2与native零差异。8重封checksum真实派生均scan通过、完整物理入口及typed pair rc1/stdout0；比较器漏变化/伪NULL/错extent均检出。同件jar seq24三图10/4/6与seq15长值120/120/0回归通过，events seq25/26拒绝。第二轮测试复制丢失extent失败保全，仅修测试适配；没有数据库动作。保留ABSENT、packed delta未知，有限尾列标记不是SQL NULL；接续固定SCN完整行/逆向值核对、来源/世代门禁，历史字典/完整CDC/capture/接入/现场/发布仍待证。

最新：[W5基线/物理列/世代研究](RESEARCH_W5_BASELINE_20261004.md)，首源ad9f9e0、只读恢复cfa7187、完整研究9003ec5，[清单](evidence/RESEARCH-baseline-20261004.json)182文件双次SHA一致。专属新表两次受控实验冻结seq25/26，10heap布局raw/native零差异。SCN2282688缓存10/alpha/secret在UPDATE提交后ASOF仍一致；invisible NOTE逻辑列号NULL而segment列3。真实NOTE→NULL是ncol3→2/nnew0无显式字段，当前内核UNSUPPORTED_UPDATE_ROWPIECE拒绝，不计行支持。TRUNCATE后heap data-object73073→73076、同file12/block403/slot0复用，旧ASOF实际ORA-01466；旧logical/新data范围均内核拒绝。首次建表后SCN读取ORA-01466保全，只补只读基线和未执行UPDATE，未重放已提交setup。三比较器反例5/1/1，events两件均拒绝；生产内核203测试树6dd1772不变/不重跑。接续[缩列与世代派单](DISPATCH_W5_ROW_EXTENT_EPOCH_20261004.md)，W5/完整CDC/持续capture/接入/现场/发布仍待证。

最新：[自主物理事务分发交付](DELIVERY_W4_PHYSICAL_TRANSACTIONS_20261004.md)，运行时6dd1772、最终lab树eafdb16，[清单](evidence/DELIVERY-physical-transactions-20261004.json)1316文件双次SHA一致。203测试通过；新内核不预填XID，seq21—24各自主发现基线/A/B，共12笔图42原始/18取消/24保留与native零差异。9真实字节派生rc1/stdout0、四比较器反例5/2/1/1差异；正确A heap slot在基线/B已关闭后的实际遍历拒绝，无部分输出。首轮两测试预期/native参数错误及派生器错改非slot导致实际rc0的原文保全，只补失败/未执行项。没有数据库动作，events继续拒绝；物理范围不是SCN字典，非begin控制/跨块关联/页维护/恢复仍限制。接续W5字典/零侵入基线与完整行可恢复性，持续capture/ACK/接入/现场/发布独立待证。

最新：[自主对象事务发现/头路由交付](DELIVERY_W4_OBJECT_DISCOVERY_20261004.md)，运行时e7f2314、native比较器0d216bc，[清单](evidence/DELIVERY-object-discovery-20261004.json)1118文件双次SHA一致。198测试通过；不预填A/B XID，seq22—24各自主发现基线/A/B三笔完整事务，共32原始undo/22窗口头关联与native零差异。三比较器反例检出，缺heap范围rc1/stdout0拒绝。首轮范围外未知flags拒绝与封存原因断言错误保全，只补未执行检查。events继续rc1/stdout0；这是header入口，不是完整物理分发/DML/字典认证。无数据库动作，接续完整自主物理流/有序head/取消/无部分输出门禁，再攻W5；持续capture/ACK/接入/现场/发布待证。

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

最新：[三批双上下文完整图](DELIVERY_W4_PAIRED_MATRIX_20261004.md)，lab完整树d907834，main/test/pom与192测试内核21cd368差异为空，[清单](evidence/DELIVERY-paired-matrix-20261004.json)1023文件双次SHA一致。同件jar完整路由seq22—24三批A/B，共六图14原始/14取消/0保留；关联14项/8外国项及各图native零差异。seq23七B+三A派生rc1/stdout0；A失败即使B已关闭也不泄漏输出。错fixture来源拒绝，三比较器反例检出3/2/1差异，events拒绝；无数据库动作，不重复seq21/192测试已通过轮次。接续[内核自主发现/分发派单](DISPATCH_W4_ARCHIVED_ROUTER_20261004.md)，尚未实现/验收；通用控制/完整源覆盖/字典/持续capture/集成/现场/发布待证。

最新：[有源归属位置重放/双事务完整图](DELIVERY_W4_OWNED_POSITIONS_20261004.md)，内核/消费者完整树21cd368，native比较器修正树ce23211且main/test/pom差异为空，[清单](evidence/DELIVERY-owned-positions-20261004.json)1328文件主封存/追加失败原文均双次SHA核对。192测试通过；自主绑定完整同叶流及唯一原始头，验证PURGE/RESTORE物理位置后重放INSERT/PURGE槽位、DELETE/RESTORE保留槽位，未知/缺原始/重复取消等拒绝。seq19—24六A位置native零差异；seq23 B图4/4/0、seq24 B2/2/0、seq21 full A/B各2/2/0独立native有序图零差异，seq18旧两图回归通过。7分发+7位置查询+3重封raw拒绝；3比较器反例检出。旧native误把B补偿归A及补充汇总空stdout失败保全，仅补失败/未执行检查。无数据库动作，events拒绝；seq22—24双事务完整分发图、生产分发/其它控制/页维护/字典/持续capture/接入/现场/发布待证。

最新：[真实短Z RESTORE及B图交付](DELIVERY_W4_Z_RESTORE_20261004.md)，运行时b7bb75f、完整封存树bbd2fd6，[清单](evidence/DELIVERY-z-restore-20261004.json)1063文件双次SHA一致。187测试通过；OP10.5增加已观察030db87100000000精确布局，未知bytes2/3不解释/其他变体拒绝。seq24 A/B同叶清单4/2与native零差异，B图2/2/0闭合；13真实vector派生+3图派生rc1/stdout0。旧seq21/22 B图各2/2/0、seq18两笔8/7/1和5/5/0同件jar回归零差异。A位置现在到达中间DELETE拒绝，A单上下文图仍缺B原始节点，events拒绝。首轮旧Java编译失败保全，新目录绑定Java17重建通过；没有数据库动作。接续有源归属DELETE/RESTORE及PURGE位置重放，用seq24 A/seq23 B冻结失败正样本验证；字典/持续capture/集成/现场/发布待证。

最新研究：[普通DELETE/RESTORE及真实短Z缺口](RESEARCH_W4_DELETE_RESTORE_20261004.md)，源脚本4cb3693、驱动树6f49a87、研究比较器修正树6bd7a87，[清单](evidence/RESEARCH-delete-restore-20261004.json)698文件双次SHA一致。专属一次新实验冻结seq24（def82f0b…），A删除50、B删除10后rollback、A再rollback；四叶slot1/0/0/1与raw/native零差异，A/B补偿头关联4/2含A的2外国marker零差异。真实B index RESTORE短Z为030db87100000000；owned清单A/B、A位置及A/B图五入口均rc1/stdout0拒绝，未到DELETE位置模型验收。首轮比较器错误沿用PURGE全零Z及native反例选择器断言保全，补充仅失败/未执行检查，三比较器反例检出1/1/2差异；events拒绝。内核仍184测试树582fec6无变化。接续直接补真实短Z RESTORE布局/反例、清单及B图，再实现有证据的DELETE/RESTORE与PURGE位置模型；字典/持续capture/接入/发布待证。

最新研究：[两次PURGE顺序/完整图缺口](RESEARCH_W4_DOUBLE_PURGE_20261004.md)，源脚本a5846cd、完整研究树4249b73，[清单](evidence/RESEARCH-double-purge-20261004.json)411文件双次SHA一致。专属一次新实验冻结seq23（da6a0d7c…），A删除50、B插入20/30后整事务回滚、A再回滚；六叶slot1/1/2/2/1/1及C/Z PURGE raw/native零差异，A/B owned清单6/4及头关联6/4（A含4外国marker）零差异。B自身第二次取消也因中间PURGE位置拒绝，rc1/stdout0，完整A/B图均不计通过。三比较器反例检出1/1/2差异，events拒绝。main/test/pom与184测试内核582fec6差异为空；不重复已通过测试。接续普通DELETE/RESTORE及有源归属PURGE模型，用本冻结B图作修复前失败正样本；字典/持续capture/接入/发布待证。

最新研究：[较大键INSERT/PURGE位置](RESEARCH_W4_HIGHER_PURGE_20261004.md)，源脚本f332264、完整研究树3d05413，[清单](evidence/RESEARCH-higher-purge-20261004.json)390文件双次SHA一致。专属一次实验冻结seq22（b1fcd87a…），A删除50、B插入70后回滚、A再回滚；四叶slot1/2/2/1与raw/native零差异，A/B完整清单4/2、补偿头关联4/2含A的2外国marker、B图2/2/0均零差异。三项B图派生反例rc1/stdout0；A位置仍拒绝PURGE，A单上下文图仍缺B原始节点，旧静态单match不当A完整图通过。main/test/pom与184测试内核582fec6差异为空，不重复已通过测试；events拒绝。两笔单键净零PURGE实例不能推导通用规则，接续多PURGE/普通DELETE实际矩阵及有源归属位置模型，W5/持续capture/接入/发布仍待证。

最新：[有界并发分发/B物理回滚图交付](DELIVERY_W4_CONCURRENT_ROUTING_20261004.md)，完整研究树40a0fda，main/test/pom与582fec6差异为空，[清单](evidence/DELIVERY-concurrent-routing-20261004.json)。同件184测试jar，双上下文消费者完整消费A窗口四项关联，把B送到B生命周期/物理match/head，B图2/2/0与native零差异；七项分发派生反例rc1/stdout0。full路径先真关闭B，再以A中间PURGE拒绝，越过此前缺B原始节点门禁，仍不认证A图。foreign-only明确只验证B图及A关联；events继续拒绝。无数据库动作，下一步扩大PURGE较小/较大/多次及普通DELETE实际矩阵，生产分发/字典/持续capture/发布待证。

最新：[同源完整XID补偿头关联交付](DELIVERY_W4_COMPENSATION_ASSOCIATIONS_20261004.md)，实现12910d5、完整封存树582fec6，[清单](evidence/DELIVERY-compensation-associations-20261004.json)543文件双次SHA一致。184测试通过；内核自主遍历完整归档，绑定唯一先前原始头与同源完整事务窗口，seq19—21共10项/其中2外国marker与native全字段零差异；8真实查询拒绝和3比较器反例。仅认证header关联，不认证物理行/当前head或取消，cross-block和同record原始关联仍未支持。A旧图、PURGE位置及events继续拒绝，没有补填通过计数。无数据库动作，旧解析类未变且不重复已通过回归；接续消费者须完整分类并独立核B物理/head/取消图，再补PURGE矩阵。

最新：[短Z PURGE/XR DELETE交付](DELIVERY_W4_Z_ROLLBACK_20261004.md)，运行时a6818ea、完整封存树919e6ee，[清单](evidence/DELIVERY-z-rollback-20261004.json)974文件双次SHA一致。175测试通过；真实index Z固定前缀PURGE与heap短Z XR DRP物理输出native零差异，不补造XID/UBA/列值；11项真实vector派生拒绝。seq21 A/B完整同叶清单4/2项native零差异，B图2/2/0通过；A现在分别到达中间PURGE位置拒绝与B marker缺原始节点拒绝，仍未验收。同件jar九笔旧图34/23/11、六批heap756/430/326回归零差异；events继续拒绝。本轮无数据库动作，接续先攻并发marker完整归属，再补PURGE位置及更多实际布局。

最新研究：[中间PURGE/其他XID回滚](RESEARCH_W4_INTERVENING_PURGE_20261004.md)，源脚本0017daa、完整研究树cea54f9，[清单](evidence/RESEARCH-intervening-purge-20261004.json)。专属源一次新实验冻结seq21（f01a212c…），A删除50、B插入20后rollback、A再rollback；native/raw四叶操作slot1/1/1/1零差异，前三record SCN相同，须按RBA/vector排序。真实B index PURGE为短KTB Z，owned清单/位置与A/B图均rc1/stdout0拒绝；B heap短Z XR DELETE直接拒绝。旧静态A单补偿rc0不当完整图通过。无内核变化，五项是不支持入口证据而非安全派生反例。接续先补两个真实短Z布局与反例，再验证清单、PURGE位置及并发marker完整归属；events继续拒绝。

最新：[多次INSERT并发图交付](DELIVERY_W4_INDEX_MULTI_GRAPH_20261004.md)，完整研究树e3552af，main/test/pom与aa44b0d差异为空，[清单](evidence/DELIVERY-index-multi-graph-20261004.json)。冻结seq20同件jar两笔图8原始/2取消/6保留与native零差异；七项调用者选择/模型派生反例rc1/stdout0，三项比较器反例检出。首次wrong-live派生未改变的封存失败保留，只补跑正确反例及未执行events，events继续拒绝。无数据库动作，本批有限位置和选定图验证完成；中间PURGE/普通DELETE、完整控制/字典/持续capture/发布仍待证。

最新研究：[多次并发INSERT位置](RESEARCH_W4_INDEX_MULTI_INSERT_20261004.md)，源脚本d3fd77b、完整研究树c469cd3，[清单](evidence/RESEARCH-index-multi-position-20261004.json)。专属源一次受控实验冻结seq20（620c6361…），A删除50，B按20/70/30插入后提交，A回滚；native slot1/1/3/2/3及三键raw字节零差异。已交付同件jar精确关联A原slot1→RESTORE slot3，旧静态matcher继续拒绝；无内核变化。新批完整A/B heap+index图、反例覆盖、其他中间操作仍待证，不补填旧批图计数或开放CDC。接续优先补本冻结批图和漏vector反例，再研究前方PURGE。

最新：[同叶INSERT位置重放与短Z回滚交付](DELIVERY_W4_INDEX_POSITION_20261004.md)，完整内核树aa44b0d，补充lab驱动b934d99且main/test/pom差异为空，[清单](evidence/DELIVERY-index-position-20261004.json)封存1030文件双次SHA核对。167测试通过；真实A原slot1经B插入变slot2成功关联，native零差异；A/B heap+index图4原始/2取消/2 live、4新heap pair零差异。同件jar旧五笔混合图22/19/3与六批heap756/430/326回归通过；7位置+4图+7短Z真实vector派生反例rc1/stdout0。中间DELETE/PURGE/RESTORE仍拒绝；不将有限图当完整DML/字典认证。首次合成marker错误及真实短Z拒绝原文已保全，无新数据库动作，events继续拒绝，目标保持active。

最新：[同叶完整操作清单交付](DELIVERY_W4_INDEX_LEAF_INVENTORY_20261004.md)，最终完整树13a87c0，[清单](evidence/DELIVERY-leaf-inventory-20261004.json)。154测试通过；内核绑定完整归档，在verified record窗口收录同叶其他XID操作，真实A窗口3项含B插入、B窗口1项与native窗口/字段零差异。4选择反例拒绝；3重封raw反例结构scan rc0而清单拒绝未知10.31/4.1/缺B undo；3比较器反例检出。同件jar五笔混合图22/19/3回归通过、slot迁移matcher和events继续拒绝，1000文件SHA重算。没有数据库动作；位置重放尚未实现，block/object字典映射/完整并发图/页维护/持续capture/发布未验收。

最新：[并发索引slot迁移反例](RESEARCH_W4_INDEX_SLOT_SHIFT_20261004.md)，研究完整树b19e5a2，[清单](evidence/RESEARCH-index-slot-shift-20261004.json)。专属无supplemental新表一次性A DELETE键3、B INSERT键2提交、A rollback；seq19 SHA497d09a5…冻结，同一叶原DELETE slot1而RESTORE slot2，11index vector/native零差异、3比较器篡改反例检出。既有5002f18内核matcher rc1/stdout0、MISSING_INDEX_COMPENSATION_ORIGINAL；是真实功能缺口，不算并发DML支持。569文件SHA重算，首轮PLSQL保留字及封存Java8误选失败保留；生产内核不变、147测试不重跑，events拒绝。下一步按W4-SLOT完整同叶操作流跟踪位置并保持错误slot/缺操作拒绝，不能删slot条件。

最新：[index RESTORE与主键回滚图交付](DELIVERY_W4_INDEX_RESTORE_20261004.md)，完整封存树5002f18，[清单](evidence/DELIVERY-index-restore-20261004.json)。147项测试通过；seq18两笔新混合图13/12/1与native零差异，索引取消4 RESTORE+3 PURGE；9新heap pair逐列字节零差异，8新派生rc1/stdout0。旧三笔混合图/index及六批756/430/326同件jar回归通过，events拒绝。首次full图遇heap0x12 C-cleanout拒绝及研发jar/diff保全，补真实70字节XA DELETE入口后重新封存。无源端动作；slot迁移/页维护/并发/恢复/其他控制/W5及持续capture仍待证。

最新：[真实主键更新及index RESTORE研究](RESEARCH_W4_INDEX_RESTORE_20261004.md)，fixture418cd0c、研究完整树d957a34，[清单](evidence/RESEARCH-index-restore-20261004.json)。专属新表一次性主键UPDATE/DELETE/savepoint及full rollback，seq18 SHA5d5203bc…冻结；35index相关vector/native直接字段零差异，出现4条10.5 RESTORE，现有matcher均拒绝UNSUPPORTED_INDEX_PURGE_LAYOUT。SQL两阶段最终3行通过，研究比较器7项反例检出。生产内核9e80b0f不变，138测试不重跑；下一步RESTORE匹配与7索引取消/混合图，不能以SQL最终相等或结构scan代替DML支持。

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
