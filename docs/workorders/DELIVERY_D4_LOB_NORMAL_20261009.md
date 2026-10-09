# D4 LOB 正常功能开发反馈 · 2026-10-09

## 2026-10-10：D4 LOB完整事件停点

内核代码 `b62a01546981efc54b2836bc248473cce0151e79`（`codex/d2-normal-index-restore`）已推送。既有raw/capture→物理事务图→完整行业务事件路径补齐BasicFile页/分配/locator与SecureFile镜像/填充；声明范围为冻结19c样本的三列ID/BLOB/CLOB，未接可信获取或产品发布。BLOB保留原始二进制，CLOB严格解码；大值、小内联、EMPTY、NULL、删除、partial、trim均产完整前后像。未知格式、缺页/缺owner/不完整图拒绝，不以安全拒绝计支持。

最终同件 `events-r73`：Java rc0，19业务图，16事件（14提交变化＋2 raw seed INSERT），26前后像/冻结SQL零差异，唯一事件ID16，rollback事件0。BasicFile2维护事务/16原始节点，SecureFile14维护事务/114节点，均无业务事件；native逐位置核等120节点，10早期节点不在dump范围，完整清单保留。业务图native与17补偿等已过证据复用；两个seed图的native缺口仍保留，原3038645不是批准基线。元数据SHB存在RBA晚于end但SCN更早的真实记录，保留原位置，按SCN/subSCN核结束tail；仅完整缓冲输入，不宣称流式安全发布水位。

验证：56相关测试通过/0失败/0跳过，`events-tests-r72.log`；错误SHA探针rc1/零stdout；比较器检出字节及NULL/EMPTY毒化。只执行离线定向回归及`jar:jar`，不重放源DML，不重跑旧已过矩阵，不启动容器。原失败r49–r71及早期trim长度误判等日志保全。

证据均在 `lab/out/d4-lob-source-20261009/continue-r2/`：`events-r73/comparison.json`、命令/stdout/stderr/rc、`lob-events-final-proof.json`；安全摘要与脚本入Git，样本和jar不上传。SHA256：

- raw97：`9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995`
- 最终stdout：`07984a8fb075b7d6eef69b8d5edda061ff9a410fc06572da3c69ec862296aad1`
- kernel jar：`e861868a48a3d90eb83bfd039c3aebcc12d8816619b1e99960447c2ec47c0d2d`

交付状态：代码DELIVERED；定向冻结样本验证PASS；产品集成PENDING；部署NOT_EXECUTED；现场验收NOT_VERIFIED；发布NOT_ACCEPTED，独立评审PENDING。本轮达到用户指定“D4 LOB完整事件”停点，停止，不自动做产品CPACK、其它D包或集中加固；不将有限19c支持标为D4所有格式或完整D1–D5验收。

D5最新范围：TDE明确搁置，[Issue #2](https://github.com/liuzx778899-hue/OracleRedoKernel/issues/2)保持OPEN，不开发、不搭环境、不作第一版交付/发布前置项。后续RAC/ASM先检查现实现，复用redo解析器，只补实际读取接入缺口：ASM完整连续逻辑字节；仅直接底层磁盘路径涉及extent/条带/镜像。RAC逐thread sequence/续读/缺段、多thread事务/字典顺序与发布水位待核。最小正常接入，未知真实记录再针对补解析，不提前声明支持。本轮未启动D5，等待用户决定。

本轮验证提交 `2271cd1677e3d3eb75c3d3640d75dbd75b07f5ba` 已推送：在冻结原始输入中，129项类型化undo和17条已核物理补偿按源顺序接入现有UndoHistory/TransactionLifecycle。19个fixture事务图全部关闭（16 commit/3 rollback，含原失败）；17补偿严格消去当前active head，三个rollback的live节点均0；SecureFile大值一次跨undo块分配通过完整UBA及前驱/slot控制门禁。独立native重建17个图/125原始节点的前驱、LIFO撤销、结束身份/标志、live/tail/head零差异；13个结束记录实际打印分配tail核等，未打印tail不编造。两seed图/4undo仍不在native dump范围，早期基线不批准。

BasicFile18页都有唯一L1 bitmap offset分配关系，其中16页有同一完整XID内LOB ID/版本/页指针对应的locator，2个临时页未形成可见locator后被同一事务LOB索引淘汰。LOB index key仍opaque，索引data指针在样本中指向被淘汰旧页，不能当新页writer证明。记录2个页/分配SCN不同、1个page早于首条undo；等SCN/严格page晚于begin/每页必有locator均不能作通用门禁。当前关联为诊断证据，物理writer/生产可见性尚未授权；不得按邻近或SCN相等偷配。等SCN假设r42、每页必须可见假设r43与漏C型KTB的r45失败原文保全；r46补完整UBA身份后通过，r47修正文件字节SHA256后通过。

证据内核 `lab/out/d4-lob-source-20261009/continue-r2/graph-candidate-proof.json`、`rollback-r41/proof.json`、`rollback-r44/graph-comparison.json`（复用r41同字节stdout，非再次Java扫描）、`allocation-r47/scope-proof.json` 及命令/日志/rc。170内核class与前件全部字节相同，树SHA256仍 `bf2c87dd690a50d5b15dcae7f1641383e11af70d2647f2952ece8b2621a40499`；不重复内核构建/旧已过测试，原16完整值/SQL及17物理补偿证据复用。事件0/部署0；本轮只推进冻结物理图及分配诊断，完整LOB事务生产工厂/事件不判完成。

下一判据：将已证页分配、可见locator及淘汰关系接入生产字节工厂，显式审计2临时页/1prelude/全对象相关输入，不把独立自主维护遗漏当覆盖完成；补SecureFile镜像/填充与可见locator，按commit/rollback重建完整LOB业务行。随后面向未来窗口完成可信获取及一次正常产品投影/JDBC/目标/CPACK；禁止晚SCN回填批准旧97。完整D1–D5及独立评审/现场/发布仍在开发。

本轮内核 `627a9de429af55e163a89b8d0eacddba53bfa8a2` 已推送：普通PK索引复用原解析，新增SecureFile控制undo窄格式13.54（PUB split撤销）、13.52（FSB队列逆向）、13.50（HBB队列逆向）。PUB正向13.53位于另一记录，按对象/块/完整XID/SCN/chunk DBA/长度筛选唯一前驱并核源顺序，不按邻近位置配对；队列13.49/13.51与原始undo同记录核等。19个fixture事务的129项原始undo均有类型化配对，未类型化项0；本轮16相关测试rc0，错误对象、顺序、SCN、XID、chunk/长度及重复候选拒绝。

独立native比较125项完整owner/UBA/原始位置/opcode零差异，新8项控制与4项正常PK打印字段零差异。旧dump不覆盖cutoff前两seed的4项undo（2堆表+2PK），明确仅原始字节诊断，不宣称native已核。PUB的segment/forward XID在native未打印，原始字段内部核等单列；Ctime及opaque尾部不作语义推断。原129/125计数差失败 `control-comparison-r38.log/.rc` 保全，r39明确范围后rc0，未启动库补dump或重放源。

冻结170class树SHA256 `bf2c87dd690a50d5b15dcae7f1641383e11af70d2647f2952ece8b2621a40499`，前件169class全部逐字节相同；原17补偿输出也逐行相同，复用前件验证。证据内核 `lab/out/d4-lob-source-20261009/continue-r2/control-candidate-proof.json`、`rollback-r38/proof.json`、`rollback-r38/control-comparison.json` 与 `lob-control-tests-r36.log/.rc`。大型制品不入Git。

下一判据：按完整原始输入/补偿源顺序接入UndoHistory，验证begin/advance/head/end、原始分配tail和rollback空live图；然后补源范围内自主维护、BasicFile/SecureFile页owner、可见locator和完整行。类型化129配对不等于完整事务已关闭或LOB事件功能支持；事件0/产品集成0/部署0，旧cutoff不批准为基线，禁止回填旧97。完整D1–D5/可信获取/正常产品CPACK及独立评审/现场/发布继续开发。

本轮内核 `4b0759cb586211b01470a7f2ebc534a0a7b3919c` 已推送：补齐向量头对象号（偏移6高16位/22低16位），BasicFile位图13.23↔13.22正逆配对及显式父块13.24核等、LOB索引10.2/10.22与10.3清除。19完整XID窗口的17补偿均匹配物理逆操作：9堆表/2 SecureFile填充/4位图/2 LOB索引，native完整owner、UBA及原始位置零差异；普通索引格式门禁不变。6951个native打印对象号的向量全部吻合，97个未打印对象号的media marker只核位置/opcode，不宣称对象号已核。89个不同相关测试rc0（结构/库存63，位图/LOB索引/原普通及字典索引26）；错误对象、父块、逆向字节、计数/状态、前驱、slot及重复候选均拒绝。

冻结169class树SHA256 `b928eb6f314a483cca0ed0fb70c511f79e20a6ee59f59b532f8695bc3b1861e6`。证据为内核 `lab/out/d4-lob-source-20261009/continue-r2/basicfile-candidate-proof.json`、`rollback-r34/proof.json`、`rollback-r34/heap-rollback-comparison.json`、`inventory-r22/vector-object-comparison.json` 及测试/命令/rc/日志。探针r29范围外undo拒绝、r30截断计数2过窄拒绝与对象比较r22–r26失败原文保留；修正有真实字节依据，不重放源DML。

完整129项undo中115项已有上述类型化配对，剩余14项明确分派：6项普通PK索引10.22（含cutoff前两seed）、4项SecureFile控制13.54、2项13.52、2项13.50；下一判据为同记录完整身份/逆操作配对及全原始覆盖，再做页owner/locator与事务图闭合。独立维护XID9.12.794/9.18.794不在这19个fixture事务范围，仍待单独处置；不能从邻近记录推断LOB页owner。BasicFile父块来自显式冻结fixture元数据，尚非生产可信获取证明。早期cutoff不批准为基线，不回填授权旧97。当前事件0/集成0/部署0，专属库只读确认exited；完整行、可信获取及正常产品投影/JDBC/目标/CPACK继续开发，独立评审/现场/发布均未通过。

当前代码：`codex/d2-normal-index-restore` / `2271cd1677e3d3eb75c3d3640d75dbd75b07f5ba`（验证提交；内核170class仍627a9de候选）（前件 `8b9349d` / `328e63a`）。本工作包完成失败续接、冻结样本、LOB locator/页/填充解码与有限正向 undo/物理补偿配对；完整 LOB 事件及产品链仍在开发，不判验收。

最新离线推进：原始堆表undo发现19个完整XID及其完整inventory/补偿窗口，17个唯一补偿头路由全部按源顺序消费；9堆表逆操作（含3解锁）和2 SecureFile局部填充撤销逐一绑定完整原始身份与逆向字节，native完整owner/原始位置零差异。起始112字节格式、begin解锁、原始UBA/低wrap复用/重复行/跨容器/填充flags与payload反例覆盖，44个不同相关测试通过。SecureFile旧ITL与当前10.26.4262身份分开，opaque fill flags原样核等；追加high-water撤销尚不宣称支持。

最新证据 `continue-r2/rollback-candidate-proof.json`、`rollback-r6/heap-rollback-comparison.json`、`rollback-r6/proof.json` 与完整命令/rc/日志；冻结168class树SHA256 `ffc5085a83efb2dd1a99a87f3ad1d563faa166281c884fd8c6dab3616d97943b`。全归档59堆表original包含诊断cutoff前2个seed INSERT（SCN3036388/3036447），后缀仍为前件57，不据此批准基线。首两次探针的对象范围及起始格式拒绝保留，未放宽对象门禁；剩余6个BasicFile相关13.23/10.22物理补偿明确列为待实现，尚不能调用完整图关闭/发布事件。本候选部署0、事件0，库仍exited；继续BasicFile控制/索引与页owner闭合，然后完整行/可信获取/正常产品CPACK。

后续离线补齐：三列 LOB 专用锁/undo 入口支持实测 ITL1/2 与 rollback unlock，普通堆表入口门禁不变；新增 SecureFile 小内联 locator。全量四个 fixture 堆块共57正向配对（13 LOCK/40 UPDATE/2 INSERT/2 DELETE）与 native 完整 XID/UBA/对象零差异；9回滚行仅结构识别，未完成补偿归属或事务闭合。新增4内联值/4空LOB/4NULL与逐步SQL真值相同，原16完整值在最终class上仍零差异。最终20相关测试 rc0，失败/错误/跳过0。

最新证据：`continue-r2/heap-candidate-proof.json`、`inventory-r15/heap-comparison.json`、`inventory-r15/full-values-comparison.json`，路径前缀为 `lab/out/d4-lob-source-20261009/`。冻结开发class168个，树SHA256 `a1291a5f27ded5d5db634f5916a60afb63e854ab867b51e122578fdfeceacb87`，文件在 `continue-r2/heap-candidate/`。测试日志 `heap-final-tests-r14.log/.rc`；原 `inventory-r10` 小内联拒绝、比较器误读LWN位置及遗漏空值的失败保留。未重放源、未启动库或换装；最新只读确认库仍 exited。事件0，完整事务/可信基线/产品集成均未通过。

## 实证结果

- 专属库只读确认两表完整初值与早期证据相等；原 SQLPlus `exit failure rollback` 生效。ORA-22921 根因已实测：RPAD 返回 500 字符却请求 1000；改明确构造 1000 字符并用实际长度写入。首续接字节数假设错误在只读阶段停止，未写业务；失败保留后续接未执行步骤。
- BasicFile/SecureFile 各一次大值、插入、空 LOB、NULL、删除、局部写入、截断与回滚，16 步 SQL rc0且逐步完整值相等；14 commit / 2 rollback。未重建表、未重放旧动作或源75–96。
- 冻结原始97：SHA256 `9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995`；bounded native SHA256 `807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472`。范围 SCN3038645–3043748，原失败及续接均保留。SQL/native仅独立比较，不参与事件生产。
- BasicFile 18页/146376 payload字节，SecureFile 12填充/8144字节（含2撤销）及4页镜像/11880字节与 native 全字段/完整字节零差异。类型化 locator 与真实页范围闭合后，大值→局部修改→截断→回滚共16完整 BLOB/CLOB 值与 SQL 真值零差异；BLOB 10000字节/CLOB 5000字符大值覆盖两页。
- SecureFile 10个原始正向/undo配对按同记录完整 UBA、对象/代际、当前 XID、LOB ID和覆盖范围严格绑定；追加逆操作恢复旧 high-water，局部写逆向保留完整旧字节。native owner零差异：10.13.4260 / 10.17.4266 / 10.26.4262；后者回滚处置尚未授权。旧 ITL不是当前事务身份。
- 前件12个不同相关测试通过（4+2+3+3，rc0/失败错误跳过0）；原编译/API、page offset、比较器、测试截断及追加undo flags拒绝均保留。只重试受影响项，没有全链部署或故障矩阵重跑；本次最终20相关测试见首部。

证据入口：`lab/out/d4-lob-source-20261009/continue-r2/candidate-proof.json`（开发class树摘要/代码commit/事件0）、`source-frozen-proof.json`、`inventory-r2/page-comparison.json`、`inventory-r3/fill-comparison.json`、`inventory-r6/full-values-comparison.json`、`inventory-r8/owner-comparison.json`，以及同目录完整命令/stdout/stderr/rc与失败原文。大型字节和制品不上Git。

## 状态与下一判据

| 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|
| 进行中：LOB结构/有限owner候选 | 通过：上述有限真实样本 | 待证：LOB完整事件/目标CPACK | 未开始：本候选未换装 | 待证 | 未开始 |

专属库 stop rc0 / exited，数据库和卷保留；无helper残留。产品仍原DATE/TIMESTAMP候选，本轮目标/CPACK动作0。早期 SCN3038645仍 `diagnostic-only-not-approved-kernel-baseline`，不批准回填旧97。

唯一作者继续完成：BasicFile页→LOB index/locator→完整owner绑定；SecureFile页镜像/填充/补偿与事务闭合和可见locator绑定；在原有完整行、字典与恢复链接入 LOB 值；面向后续日志补可信早期获取，禁止晚SCN回填批准旧日志；随后集中一次正常产品投影/JDBC/目标/CPACK实证。未知或缺字节仍零事件拒绝，不把本次事件0当LOB功能支持。

完整D1–D5、独立目标/独立评审、约定部署、用户生产验证、现场验收和发布继续保留。当前总状态开发中，不自审验收，不自合并；当前入口为产品仓 `docs/workorders/STATUS.md` 首部。
