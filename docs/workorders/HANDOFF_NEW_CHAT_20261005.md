# 精简 chat 主线交接（2026-10-05）

最新：[实际V2字典获取与新raw](DELIVERY_W5_DICTIONARY_ANCHOR_REAL_20261006.md)：SCN2509388六锚点/历史列与独立ASOF零差异；新seq59/60及ADD/DROP两事务已闭合，捕获/归档零差异。原始batch因未知24.4 rc1/stdout0；DERIVED擦除三条schema-family后V2按目标SYS写入拒绝，native六锚点写/四同XID新COL$位置零差异。去锚点诊断对照因两真实5.4 extension拒绝，未证明本轮两错误事件，P0整体未闭合。失败保全/只读补收尾/源动作不重放，NO/NO/旧表保留；无新目标/产品HTTP/部署。接续DICTIONARY_END_PROFILES_20261006，goal active。


最新：[可信字典锚点代码候选](DELIVERY_W5_DICTIONARY_ANCHOR_CANDIDATE_20261005.md)：V2同SCN角色/位置/历史列及raw逻辑角色屏障已实现；kernel265、adapter21通过。实际既有只读账号SYS获取ORA-00942拒绝/连接关闭/未签发快照；编译重名失败源码与日志保全。仅代码/单位回归/权限负例，不算真实V2获取、marker擦除关闭或产品HTTP通过；无grant/源DML/目标动作，旧seq52—57不重放。继续TRUSTED_DICTIONARY_ANCHORS派单，goal active。


最新：marker覆盖缺口未关闭，详见STATUS/DELIVERY_W5_SCHEMA_COVERAGE_GAP_20261005。kernelbb0225bc/260补未知24.x，24.0拒绝，0.0擦除仍rc0/2事件；新制品尚无产品/HTTP，不挪用旧735。原SCN SYS ASOF六个锚点及raw六条写入/四条同XID新COL$关联/native零差异，只作事后尺子，不补旧V1为authority。下一单DISPATCH_W5_TRUSTED_DICTIONARY_ANCHORS_20261005：先实际fresh同SCN锚点获取/格式/权限/获取账，再新raw和产品前拒绝；无新源/目标动作，旧seq52—57及源事务严禁重放，goal已核active，automation保留，独立评审/现场发布未通过。

最新：历史schema真实ABA反例及raw marker屏障候选已交付，详见STATUS/DELIVERY_W5_HISTORICAL_SCHEMA_BARRIER_20261005。kernel9275ab90/259；产品e4d3aa9/735，实际认证HTTP两次拒绝且target/ledger/CP/ACK零推进。新T_SCHEMA_BARRIER_20261005、seq56/57、ADD/DROP后两笔UPDATE源事务已关闭，禁止重放；baseline SCN2501884，源码/制品/失败及原始错误事件保全，helper已停止。仅有限24.1屏障，不算完整历史字典/现场/发布；下一单DISPATCH_W5_SCHEMA_COVERAGE_20261005，marker缺失/未知字典关联、跨窗历史及V731三库NEW/UPGRADE仍待证。目标未完成，automation保留；goal工具usageLimited与当前额度恢复状态分别记录，不人工改状态。以下旧交接仅历史。

最新接续以STATUS入口为准：本chat已交付实际获取账关联新raw完整DML，产品996cfd5/735，源新fixtureT_ACQUIRED_DML_20261005及seq52—54三事务均已结束，严禁重放。raw/完整图/行/目标/控制/ACK及应用重启有限通过；collector观察超时host rc未知原文保全，仅只读取证续接。helper已停止。下一单DISPATCH_W5_HISTORICAL_SCHEMA_BARRIER_20261005，历史DDL/ABA和V731三库新建/升级仍待证，目标active。下面保留原交接历史，不再当最新待办。

用户明确要求新开 chat，继续原 Oracle Direct Redo 零侵入 + StreamHouse 全流程目标；解析优先、自主决策、禁止 Luna。旧 chat 在交接后不并行修改共享 checkout。目标仍未完成，不把结构、安全拒绝、有界实验当现场或发布通过。先读 AGENTS.md、STATUS.md 和本文件；STATUS 六列表部分计数是历史时点，以最新交付和实际制品为准。

## 立即收尾：混合/全回滚交付

- 当前 main HEAD fbd9fa0；上一推送 aef0fae。30e42f4 是一次性源实验 driver，fbd9fa0 是独立行比较和图验证研究脚本。生产内核最近变更 4f79691，jar SHA256 a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968，251 测试已通过，本轮未改生产代码，勿重跑已接受测试。
- 当前待交付：修改 STATUS.md、未跟踪 DELIVERY_W3_MIXED_ROLLBACK_CAPTURE_20261005.md；该报告链接的 evidence/DELIVERY-mixed-rollback-capture-20261005.json 尚未创建。先封存报告对应证据，再显式 stage 安全文档/清单并提交推送，包含本交接。不要 git add -A。
- 唯一原始证据目录 lab/out/mixed-dml-capture-20261005；大型 raw/dump 留 ignored。不上传密码、业务原始数据或外仓源码。封存每个文件 streamed SHA256 两遍一致、命令 rc、事件数、源码实际字节和 Git blob 区别；失败原文保留。可参考 evidence/DELIVERY-nullable-tail-capture-20261005.json，实际文件名先核。
- executed-driver.py、driver-sha256.txt、kernel-sha256.txt 是执行前封存；补冻结 lab/compare_mixed_capture_rows.py、verify_mixed_capture_graph.py 及实际使用的比较/探针源码。不要用封存时源码冒充执行时版本；首次失败比较器 failed-native-row-comparator.py 已保留。

## 已执行源动作，严禁重复

专属容器 oraredo-19c-lab-20261004 / ORKPDB，owner OracleRedoKernel。DBID1276726821 activation1276717861 compat318767104 thread1 resetlogs1245693159/resetSCN1920977 PDBUID677737149/cid3，supplemental NO/NO。仅专属容器允许 fixture DDL/DML、dump、switch；共享只读。旧表 ORKLAB.T_BASELINE_20261004 的 11/blocking-closed/new-note 要保留。实验 DDL 不是生产零侵入证明。

新表 ORKLAB.T_DML_MIX_20261005 heap73256/index73257，seed0/mixed-seed/anchor，snapshot SCN2426700 SHA e5d52f81bf5845c363de8bc8722e990e2a5eebce3388ac8de87301edb98dcdba。三源事务各一次且已结束：

1. XID10.27.1795：INSERT11/mixed-before/mixed-note → UPDATE TXT mixed-after → DELETE11 → COMMIT。
2. XID10.7.1795：INSERT12/undo-insert/undo-note → 整事务 ROLLBACK。
3. XID10.31.1797：DELETE seed0 → 整事务 ROLLBACK。

seq48/49/50 捕获9039/10/17块，collector rc0、next51，当前没有采集/辅助进程继续运行。journal SHA：48=5b874a71b821307e2f3204b7424968ea19dcbb60d098e853a5c4357a82fb08eb；49=0d748b2723aec1169e7fc28190ffe5d17fb9c4a0a70e3684ddd19e61b4396166；50=2aba4b6578e4371b1e09d98c569acf5e445c325553fb74df05f97c422c4cc053。与原归档数据块零差异。

raw-complete-batch rc0 三完整事件，XID10.27.1795，heap73256/dba50333117/slot1，位置48:9032:40:8、:10、:11；两回滚零业务事件。batch SHA68bdcaa731964f6dfdf2bba989bcef8bc5de399416a7bdd7728db1d15248b261，schema SHAec13b3ab617c4e2fe99900cfe3866b00f0de58ccefc4127b2cdfbf85c39cf738。

四图：seed8.0.650=2/0/2；mixed=5/0/5；insert rollback=2/2/0；delete rollback=2/2/0。合计11原始/4补偿/7保留，与完整 native 图零差异。native独立配对三完整像与逐步SQL、archive/capture ROW、最终seed零差异。

失败保全：native-mixed-rows 首轮 rc1，SQL split 下标4漏ID；只修比较下标3后 native-mixed-rows-r2 rc0，不重放源动作。missing-update/wrong-before/wrong-order 三输出比较反例 rc1。两重封 archive 输入分别 late-delete-before 和 missing-delete-compensation，scan rc0，完整行/图入口 rc1/stdout0，原因 ROW_REPLAY_BEFORE_VALUE_MISMATCH / MISSING_INDEX_LEAF_INVENTORY_MARKER。raw-mutations.json 保存逐字节差异含 checksum。public-events rc1/stdout0。delivery-proof.json 汇总当前有界通过，初始 proof.json 不覆盖。

## 下一主线：冻结新DML到 target/checkpoint

已完成 capture 解析：seq44/45 非空 INSERT/DELETE、46/47 NULL尾列 INSERT/DELETE、48—50 mixed/全回滚。读对应 DELIVERY_W3_BASELINE_CAPTURE_DML、DELIVERY_W4_UNREFERENCED_COMMIT、DELIVERY_W3_NULLABLE_TAIL、DELIVERY_W3_MIXED_ROLLBACK_CAPTURE 报告与证据。不要重复源实验。每组 fixture/schema/baseline/leaf 不同，不能装成同一消费窗口。

先读 integration/streamhouse-engine-oracle、此前目标/重叠/checkpoint 交付和 DISPATCH。确认私有可信完整 batch 投影是否完整支持 INSERT/DELETE/NULL/空批次，使用隔离 Maven 版本升级私有内核依赖，保留旧制品。适当单元验证后，冻结输入进入专属新目标验证完整顺序/中间像、零回滚业务事件、原子目标凭证、幂等成员、消费前沿与 checkpoint/ACK；不要以最终行相等代替三次变更证据。

已验收旧 UPDATE 链：seq40/41/42三事件→SPI→专属同实例目标原子提交→checkpoint/ACK；跨批2+1、重复0新增、SIGKILL、实际target控制拒绝已完成，勿重跑。当前 adapter runtime ec3b4306ce088b93fedc5794b4ffb57f225a2306，jar6f06247e400a0a802cb395a224f983fcf4c600a8a7991293cc804e374edbab86、私有kernel ea9f58c629d39fb1a1c15dddd72b74322581a641c8fb6fa252fad43bc4e39d51，模块15测试。新a084内核尚未用于目标矩阵。

专属目标用户 ORKTGT_CMT_20261005；秘密只在 lab/out/oracle-target-20261005/target.secret，绝不输出/哈希/上传。旧NORM有意污染，FACK非法空target，其他控制故障已恢复；不要把历史快照当当前健康，不清理旧证据。目标是同源专属实例跨schema，不是独立生产目标。Java helper container 共享源网络、vendor ojdbc8.jar，只执行Java，不部署Flink服务。

StreamHouse checkout D:/work/vibecode/StreamHouse 共享只读，有他人 dirty 文件，保留。公开Oracle decoder/scanner仍拒绝；不得伪造 SQL LSN/GUID，Oracle位置独立sidecar。C++ sh_agent realtime Oracle、完整产品pipeline、生产可信历史schema/基线、活跃同key跨窗、ASM/Windows/高压/断电/现场/发布均未通过。

## 工具与接续

Java17 D:/work/Java/jdk-17.0.7；Maven D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd，要显式 JAVA_HOME/PATH17（默认8）。Python C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe。实验脚本命令 rc 和输出均写 lab/out。

旧chat goal active 是长期原目标，不误标完成；新chat接续同目标。自动化 oracle-redo 应迁到新chat，保持原每小时频率及安静通知策略。禁止Luna；额度不足封存SHA/原文/下一步，恢复空闲后接续。不要同时派两chat改共享仓库。
