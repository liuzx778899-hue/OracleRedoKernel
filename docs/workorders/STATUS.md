# OracleRedoKernel 当前状态

更新：2026-10-04（Asia/Shanghai）。用户授权自主论证、实现、专属实验验证，主攻解析；禁止 Luna。目标仍进行中，不是全流程验收完成。

## 六列状态

| 工作包 | 代码交付 | 针对性验证 | 集成验证 | 部署 | 现场验收 | 发布 |
|---|---|---|---|---|---|---|
| W0 环境/样本/真值 | 通过：首批fa61476，矩阵继续 | 通过：环境/冻结归档；矩阵待证 | 待证 | 不适用：实验脚本 | 待证 | 未开始 |
| W1 文件/块 scanner | 通过：首批fa61476 | 通过：19c/21c有界样本/5个真实派生反例 | 待证：真实capture | 未开始 | 待证 | 未开始 |
| W2 record/vector | 通过：首批fa61476 | 通过：19c seq8/9完整dump、21c选定范围 | 待证：事务/字典 | 未开始 | 待证 | 未开始 |
| W3 基础行/类型 | 进行中：scalar + heap/undo pair | 通过：18精确pair/完整行待证 | 进行中：物理配对；字典/基线待证 | 未开始 | 待证 | 未开始 |
| W4 事务/回滚 | 进行中：身份/补偿/原始undo链 | 通过：11原始链/32节点、四heap补偿；跨块待证 | 进行中：同块原始链；index效果/恢复待证 | 未开始 | 待证 | 未开始 |
| W5 SCN字典/基线 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
| W6 StreamHouse接入 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
| W7 online采集 | 未开始 | 待证 | 未开始 | 未开始 | 待证 | 未开始 |
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

最新：[原始undo链反馈](DELIVERY_W4_UNDO_CHAIN_20261004.md)，源码254e5e7，[封存清单](evidence/DELIVERY-254e5e7.json)。78项测试0失败0跳过；11链/18heap+14index节点、9原生commit tail与dump零差异；6个真实节点派生反例拒绝。两个rollback tail仅为唯一图尾。跨undo block、index效果/完整保留集合、恢复及W5仍待证，CDC零发布。

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
