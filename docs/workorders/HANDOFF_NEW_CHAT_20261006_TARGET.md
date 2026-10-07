# 2026-10-06 新 chat 接续：历史 schema 目标提交

最新优先（2026-10-07完整跨日志候选）：DELIVERY_W7_CROSS_WINDOW_RANGE_20261007.md，kernel0bcb39e6/313，mixed24.4窄profile仅完整历史入口上下文识别，首端必须已读同UID SCN且>baseline、末端=marker SCN−1；固定头/padding/flags与旧profile保留，marker非begin/schema authority。原73/74两完整UPDATE同XID10.14.2869/begin2743312/commit2743344/native四变化零差异；首73未闭合拒绝不变，三DERIVED重封结构完整入口拒绝/零输出，四比较器毒化/重复通过。新候选旧70/71全部输出字节不变；2class变/121不变。88双SHA/前182不变，首XOR1碰到已观察SCN导致比较器失败原文/源码/输入输出保全/r2未观察端点通过，0源/目标/产品动作。新目录cross-window-range-20261007已冻结。下一真正持久跨发布活跃事务/字典状态和崩溃恢复，正式C++；不要把拼接两个日志通过冒充状态恢复。原源73/74禁止重放、晚SCN不得回填70/71，完整goal active。

最新优先（2026-10-07跨窗）：RESEARCH_W7_CROSS_WINDOW_ACTIVE_20261007.md。新目录cross-window-active-20261007已182双SHA封存，原522不变。新V3 SCN2743256/SHA3619a433仅供新73/74；新XID10.14.2869两UPDATE同会话跨关闭日志一次完成，源已恢复且禁止重放。首73 rc1/stdout0 INCOMPLETE_TRANSACTION_IDENTITIES:1；合并73/74 rc1/stdout0 UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE，74:3:16原24.4 range首2743309 flag0000/末2743319 flag8000跨度10，native确认opcode但未解释完整范围语义。collector terminal0/next75并最终/proc无collector；NO/NO/旧表保持，无产品/目标/CPACK动作。下一只读研究冻结新输入，不能擦marker/放宽至忽略范围或重源；定义独立关联及反例后修profile/完整两事件，再真正跨发布持久状态/崩溃恢复及C++。跨窗恢复未验收，完整goal active。

最新优先（2026-10-07故障恢复）：DELIVERY_W6_HISTORICAL_FAILURE_RECOVERY_20261007.md。P代码dbab51a/反馈12ce9f9已推送，product2f1b46ad完整752；仅失败留证路径修复，kernel a84/adapter fbf1/SPI类不变。新实验控制ORKSPLCTL_20261007原注册完整复制/metadata相等，原早V3/70—71只读；错consumer绑定rc2/0事件零推进，guard恢复后第二UPDATE真实ORA-02290完整文件/批次关联，目标整批回滚/CPACK零推进；去掉owned late constraint后仅ACK目录故障，目标2/2/1及ledger/门禁/CP已提交，原批101221037735239680保持PUBLISHED而task101217591124647936 FAILED，APPLY_RC NULL，不冒充目标失败。保全移开阻挡文件，同task DONE补ACK SHAaa8f7d2b不重写目标，重复/同件重启异步审计增量及目标/批次/CP/ACK精确通过。522双SHA/前356+1181+407不变，新capsule split-history-late-http-20261007已冻结，所有对应helper均停止false/143；源继续running/NO NO/旧表和fixture保持。首7/1回归缺原cause、740223e/751候选从未运行，ACK8/1回归复现GATED覆盖PUBLISHED；最终dbab读最新批次保存诊断，不自审通过。下一开发主线跨窗活跃事务/字典状态/恢复、同key/同commit SCN/多savepoint/跨块/初始恢复及正式C++采集，进入新目录；不重放旧源动作/不回填晚SCN。独立目标/一般DML/扩展类型/现场发布/评审仍待证，原全goal active，Codex最后查。

最新优先（2026-10-07产品链）：DELIVERY_W6_SPLIT_HISTORY_PRODUCT_20261007.md。P1755975已推送/product ef852e71完整750、adapter fbf1d30c/26，真正嵌入kernel a84/SPI类一致；原acquisition100747510573780992/早V3/70—71实际认证HTTP两完整UPDATE→SHP7目标2/2/1→控制PUBLISHED/CONSUMED/CP→持久ACK精确一致。重复/同件重启异步DONE/windows0/published0，目标/CP/ACK字节不变；COMMITS schema序列凭证改零实际恢复ORACLE_TARGET_COMMIT_CONFLICT拒绝，旧DONE不改FAILED，原文/审计保留，guard精确恢复后通过。356双SHA，前1181/549/407不变；新helper ork-split-history-product-20261007/37262及旧V3helper都已停止，只源继续running，禁止自动重启旧件。0源重放/新获取/日志配置；capture_complete=false、目标同实例跨schema，现场/发布/评审仍待证。新capsule已冻结，下一步新目录保留同基线/日志/制品，补历史晚失败/绑定反例再跨窗恢复与正式C++采集，不能回填晚SCN。下方未过HTTP/CPACK描述为历史，原完整goal active。

最新优先（2026-10-07）：DELIVERY_W5_SPLIT_HISTORY_20261007.md。原早V3/70—71已接自主split+10.5 raw槽恢复、ckix144/flags004a-size316有限profile，kernel a84db1a3完整311，三SYS/22original/764native完整值零差异；两完整UPDATE/schema4→3、archive/capture/重复、既有adapter投影通过。26合法checksum完整入口拒绝/stdout0；14marker擦除语义相同，captureInput随batch/journal SHA改变。1181双SHA/前549未变，三旧SCN ASOF ORA-01555保留，不重新获取/不重放源。新产品/目标/HTTP/CPACK尚未执行，下一步升级指定私有P不可变kernel→实际产品同acquisition100747510573780992/原70—71窗口→新owned prefix target原子提交/CPACK/重复重启/晚失败。用户最新要求聚焦开发、针对性验证和专属集成收口，部署及生产验证后续协同；仍保持全部原目标，禁止Luna/子代理，共享StreamHouse只读。STATUS六列表已同步，不能以下方历史“完整工厂未接入”当当前状态。

最新优先：DELIVERY_W5_AUTONOMOUS_DICTIONARY_SPLIT_20261006.md，最终r3 kernel1d5f91fc/完整309，自主五original+13.7/13.6 freelist物理控制+end OLD重建共七记录闭合，源字节选XID、全已知页审计/currentR/links/range/行守恒，slot7待恢复；archive/capture/重复一致，12合法checksum全split拒绝/stdout0。ccnt不冒充XID/OLD prev仍null。549双SHA，原129/133/139/130/205/407不变，首候选漏页审计不验收/r2拒绝额外freelist原文保留。业务历史未接入、新源/产品/目标动作0；下一完整执行包为主字典10.5 raw槽恢复+同输入/baseline位置证明→完整两业务历史事件→新产品HTTP/target/CPACK。原70/71早V3不重放/不重获取，goal active，禁止Luna/子代理。

最新优先：DELIVERY_W5_DICTIONARY_LEAF_ROWS_20261006.md，最终r2 kernelb4913b23/完整309，typed前像445与两叶220/226的891行/native raw/NUMBER/rowid/flags0差异；OLD prev未知=null，首0占位候选133保全不当authority，最终129/旧139/130/205/407不变。原117class未变，完整历史工厂未接入，新源/产品/目标动作0。下一步按该完整执行包建立自主五original/end维护闭包与10.5 raw槽恢复，再两历史业务事件/真实产品CPACK，不再以局部Pair或行解码通过代替完整split。原70/71和早V3不重放/不重获取，goal active。

最新优先：DELIVERY_W5_DICTIONARY_MAINTENANCE_PAIRS_20261006.md，kernel1911a0cd/完整305，I_COL3 lock/prev-link/branch三局部typed Pair与真实native0差异，旧115class字节未变，新增2class。139双SHA/旧130/205/407未变，首诊断scope失败保全；完整历史工厂尚未接入此Pair，完整split和两事件未过。接续新执行包typed前像/new与old叶重建→全五original/6记录含end闭包→10.5 raw槽恢复，原70/71和早V3，不重源/获取/封存；产品未构建换装、goal active。

最新优先：DELIVERY_W5_INDEX_MAINTENANCE_BEGIN_20261006.md，kernel4e7eeafc/完整301，044a严格begin profile已支持，原维护生命周期begin/两advance/五original与end/tail/native闭合。130双SHA、旧205/407未变；完整历史工厂仍索引覆盖拒绝/0事件。下一步第2—5项完整typed branch/link/叶重建与restore槽证据，再实际历史HTTP/target/CPACK，不重获取、不重源动作、封存目录不覆盖。0产品构建/换装，完整goal保持active。

当前接续（优先级最高）：真实产品V3获取已封存407项（productc47a728c/750，actual100747510573780992/SCN2566695）。此基线后新70/71一次源动作已结束，禁止重放或重新获取晚SCN补洞。最新205项研究封存在RESEARCH-v3-product-index-maintenance-20261006.json；完整历史入口因索引维护覆盖拒绝、0事件。445→220+226叶行守恒仅一删除标记新增/槽7恢复raw关联/native0差异、四比较反例、五original graph/tail闭合；044a begin lifecycle及branch/link typed完整证据尚待补。接续RESEARCH_W5_V3_PRODUCT_INDEX_MAINTENANCE_20261006.md的DISPATCH，在新输出目录继续，原205/407不改。helper37261仍running，auto-consume关闭，binding0/event0，目标/历史HTTP/CPACK未执行；完整goal active，禁止Luna/子代理。下方历史dirty/制品/下一步描述不作为当前状态。

接续结果（本节优先于下方历史待办）：新chat已完成root020be30/P65e12c2候选，productac98404d完整746，嵌入kernel240/adapter65e0fcba且SPI全class一致。HS6两完整历史UPDATE/独立事件账/receipt/重复0，HSL6晚CHECK首更新后整批回滚，序列凭证篡改拒绝并精确恢复；216双SHA封存、前241/75/189不变。无源重放/配置/服务换装，helper已退出。详见DELIVERY_W5_HISTORICAL_TARGET_20261006与STATUS，下一单DISPATCH_W5_V3_PRODUCT_RUNTIME_20261006：proper产品V3获取后一次新fixture，禁止晚SCN回填旧68/69。原完整goal active；PR#13已附属新chat，draft依赖#12/评审PENDING，禁止Luna/子代理，Codex最后查。下方未提交/未编译/尚未写探针等为交接时历史记录，不再是当前状态。

用户因旧 chat 反复压缩上下文要求新开窗口。旧 chat 从此只保留历史，不再修改共享 checkout；新 chat 接续全部原目标，不能缩成当前子任务。禁止 Luna、禁止子代理。Oracle 全部目标完成之后才查 Codex 卡顿。

## 当前真相与边界

- 先读根 AGENTS.md、STATUS.md、HANDOFF_NEW_CHAT_20261005.md、本交接及 DISPATCH_W5_HISTORICAL_PRODUCT_20261006.md。STATUS 最新封存交付是 HISTORICAL_SCHEMA_PROTOCOL；下面是尚未封存的新候选，不能说已验收。
- 完整目标：严格零侵入原始 redo/capture → 事务/可信历史字典 → 完整事件 → StreamHouse → 目标原子提交 → 控制 checkpoint/ACK → 独立验证/现场/发布。旧静态 V2 三 IUD 已有真实 HTTP/目标/CP/ACK/重复重启证据；最新历史 ADD/DROP 两 UPDATE 已经真实字节解析/逐事件 schema 投影通过，历史产品提交链还没有通过。跨窗、正式采集、独立目标、矩阵、评审、现场发布仍待证。
- 专属源 oraredo-19c-lab-20261004/ORKPDB 允许受控 fixture；共享源只读。旧 ORKLAB.T_BASELINE_20261004 的 11/blocking-closed/new-note 保留。NO/NO supplementary。旧 fixture 动作已结束，不重放 seq44—69 实验。
- StreamHouse 共享 D:/work/vibecode/StreamHouse 只读；唯一可修改私有 P=lab/out/streamhouse-oracle-gate-worktree。先读 P AGENTS 和产品设计/开发流程。PR#13 OPEN draft，依赖 #12，独立评审 PENDING：https://github.com/liuzx778899-hue/StreamHouse/pull/13 。已附属旧 chat；新 chat 接续该 PR 时须 attach_artifact。
- Java17 D:/work/Java/jdk-17.0.7；Maven D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd；Python C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe。PowerShell Maven 所有 -D 参数必须单引号包住，避免带点版本截断。
- secret 不输出、不哈希、不上传。target.secret 在 lab/out/oracle-target-20261005，3行 user/password/url；目标 ORKTGT_CMT_20261005 是同实例跨 schema，不是独立目标。旧 NORM 有意污染/FACK 非法，不清理。source.secret 在 lab/out/oracle-snapshot-acquisition-20261005。

## Git 和未提交修改

根 HEAD 9f257ebd02b2ba139655391526575e065dc54215，main 已推送；最后封存代码 e5477a6。P HEAD 135a1d9f6ba87fd75fb3747608fdc5c729a0f8d9 已推送，代码8cc33b1。两处都有下面当前候选 dirty，保留，不 reset/stash、不 git add -A。

根 integration/streamhouse-engine-oracle：
1. OracleBatchProjection 新增 project(CapturedHistoricalHeapBatch)，返回原私有 Batch、baseline schema、完整逐事件 evidence/envelopes。
2. OracleTargetBatchWriter CommitReceipt 增可空 schemaSequenceSha256，旧4参构造兼容。IDENT.SCHEMA_SHA 保持基线；历史 COMMITS.SCHEMA_SHA 存有序 schema 摘要，旧批仍存单 schema；重复和 verifyCommitted 使用同一绑定。历史 targetColumns 取基线+各事件列并集，同名列元数据不一致拒绝；targetImage 仅将缺席 nullable 列补物理 NULL，未知列/缺必填拒绝。目标写前像和后像使用并集，APPLIED 存原逻辑像（4→3列）不补列。只有受控预建 nullable 并集支持，不是任意非空 DROP 或自动 DDL。
3. 新 OracleHistoricalTargetMappingTest 两项：逻辑像不变/物理nullable并集/hidden NOTE 与旧批兼容、必填缺失及未知列拒绝。

P server/streamhouse-app：
1. OracleApprovedInput 从可信已注册 evidence 的非空 schemaSequenceSha256 选择历史私有工厂，仍核完整 DTO equality/envelope SHA；没有任意 client option。
2. OracleTargetCommitVerifier.Receipt 增 NON_NULL schemaSequenceSha256，旧7参兼容；OracleJdbcTarget 转传目标 receipt 摘要。
3. OracleControlPublication 用 mapper.copy + mixin 对旧 evidence 空 eventSchemas/null摘要省略，避免旧 CP 严格 JSON 恢复改变；目标 receipt 摘要必须与 evidence 一致，失败回滚不推进。
4. OracleControlPublicationTest 增3测试：旧 CP 字节形状、新历史 evidence/receipt 摘要、错 receipt 回滚。
P pom 尚未升级：server/streamhouse-app/pom.xml adapter仍0.1.0-dictionary-snapshot-9516d4f9，kernel仍0.1.0-inverse-tail-672f01bc。当前P修改未编译，不可声称通过。

## 当前已完成命令与下一动作

N=lab/out/historical-target-candidate-20261006。当前 adapter 完整 clean package 已 terminal rc0，26 tests/0 failure/0 error/0 skip，stdout/stderr/rc 分别 adapter-build.stdout.txt、adapter-build.stderr.txt、adapter-build.rc.txt；最后 session63388 已结束。无当前采集/产品 helper/本 chat 执行任务。初次查交接误用 .rc 和漏 server/ 的 pom路径找不到，不是构建失败，正确如上。

构建使用已安装 kernel0.1.0-schema-protocol-240c2250f002 和 SPI0.1.0-schema-protocol-53b84d186ba2。下一步冻结 integration/.../target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar 为 N/adapter.jar，计算SHA并复制 reports；Q/kernel.jar、Q/spi.jar复制到N，禁止改封存Q。新adapter按SHA不可变 Maven coordinate 安装，更新P上述两个依赖，再完整 clean package，预计旧743+3=746，实际以报告为准。验证可执行jar真实嵌入新 kernel/adapter/SPI 字节，而非旧产品的18f49be4假充新历史支持。根内核299已通过，本轮未改内核不重复重跑。

接着编写 lab/OracleHistoricalTargetProbe.java 和驱动（尚未写）：冻结原68/69 +实际早V3经私有历史工厂投影，然后才连接 owned目标；新前缀建议HS6正向/HSL6晚失败。只新建自有 ROWS(ID,TXT,NOTE,EXTRA_V3 nullable)、IDENT、COMMITS、APPLIED、MEMBERS，格式参照 writer；初始化 marker/命令先封存，若已存在/部分失败禁止重复建表或drop。IDENT绑定来源+基线schema，seed0/dictionary-restored/anchor/NULL。

HS6 apply2/verify/重复新增0，独立 SQL 核 ordered APPLIED 的4列/3列原始像、MEMBERS2、COMMITS1、目标最终0/history-v3-restored/anchor/NULL，receipt基线+seq摘要同时正确。HSL6 CHECK 拒绝 TXT='history-v3-restored'，让第二事件失败，证明第一事件/ledger/member/commit全部原子回滚，目标回baseline。必要token篡改反例仅作用新 owned表且精确恢复。Java helper使用 flink:1.18-java17、network container:oraredo-19c-lab-20261004、只运行Java，vendor ojdbc8.jar从旧capsule只读复制，secret排除封存。每命令rc/stdout/stderr/SHA保存，不使用最终行相等代替事件链，不称直接writer验证为产品HTTP/CPACK通过。

## 冻结输入与已封存制品

O=lab/out/v3-historical-raw-20261006；actual-v3.snapshot SCN2536302，SHA226c9322176bb6fafffb9c6a2cecd093bb76a1bf2f9e6bd71570b7f4c5bc839a。heap/index75185/75186，ORKLAB.T_V2_HIST_20261006/PK_V2_HIST_20261006；baseline ID1非空、TXT2 nullable、NOTE3 hidden nullable；row DBA50342532/slot0，索引已声明leaf50342539（只读IND$ root50342538不得误当leaf）。

O/capture/sequence-68.oracap SHA d91fc80d4b120e235a70e82c1b905409923a1541637f4ad6ab13f23eb92269e9；69 SHA43f3a79d0692ef258c52ca282e7dbcadf6a167dff9737657dd268a5f53db1592。ADD EXTRA_V3 nullable、UPDATE history-v3-added、DROP EXTRA_V3、UPDATE history-v3-restored 已完成，禁止重放。两业务位置68:34027:16:4/69:12:16:4，XID10.25.2305/5.27.602，recordSCN2536404/2536423；完整像4→3列，NOTE=anchor、新EXTRA=NULL，最终恢复基线schema。schema4SHAc88266cebfb41e24b20729bfaa31e36fea8b45467be4f3fdb85dfe6a3d69b4b2，schema3/baselineSHA9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0。

有序 schemaSequenceSHA c23904a9efaed17aaf82a68d8d88478dcf84d89804c14376b6c29aa9f077a2e4。重复同输入字节一致；DERIVED擦四schema marker仍相同事件/schema/ID/payload/序列摘要，batch/inputSHA改变。生产事件不读dump/SQL。R=lab/out/historical-row-schema-candidate-20261006 有完整行/native/SQL/11安全反例；旧V2静态三IUD和历史拒绝保持，legacy比较用 lab/verify_dictionary_index_impact19.py。

Q=lab/out/historical-schema-protocol-20261006：kernel SHA240c2250f00256209bf02ccb9b17918e90797c920e113ac552db2881c678c25c/299，SPI53b84d186ba2cfa7f14143ae04cd553354a783e98a71642d271825d65059d829/8，旧adapter353407790ac810cefda2d47688d48cc9ed4ae1d46fd705b67d799ddfd65960f9/24。封存产品18f49be40e91897094e581170da77738515536cffeb78f0ce73a58202d93a453/743真实仍嵌旧kernel672/adapter951，勿称新历史运行时。Q清单241文件；前序projection75、row-schema189、raw374均封存。只读旧capsule，下一封存复核不变，排除自己仍打开的sealer日志。禁止全局数字替换，旧188→189曾误改SHA已完整修复。

## 产品未闭合关键点

实际产品 OracleAcquisitionService 仍 acquireDictionaryBound(V2)、ACQUIRED_ONLY账。**原可信V3获取回执登记尚未实现**。不能手工伪造旧账、客户端随意SHA或用晚SCN回填68/69。需受控核验原获取回执登记，或先proper新产品V3获取后一次新fixture；旧68/69只能用原早SCN可信获取。之后真实HTTP→target→CPACK、重复/重启/拒绝必须实际执行才算有界通过。

新版SPI evidence含 baseline columns/schema + eventSchemas(eventId,schemaSha,columns) + schemaSequenceSha256；OracleSchemaSequence校验关联与摘要，不证明任意columns真实性，必须核私有factory完整DTO。ACK复制整份CP JSON，须证明新seq证据和targetreceipt通过真实持久链，不能以单测代替。

新交付封存后显式提交安全源码/脚本/文档/摘要，排除lab/out和secret，推root/main与P当前分支codex/oracle-dictionary-acquisition，PR#13继续draft待独立评审。新chat建立同完整goal，持续推进；旧goal只是历史，不标complete。自动化oracle-redo迁移新chat且保留每小时/有实质变化才通知；额度恢复且空闲继续，不重复通过轮次。额度不足保全代码SHA/失败原文/下一步，绝不用Luna。Codex卡顿仍最后查。
