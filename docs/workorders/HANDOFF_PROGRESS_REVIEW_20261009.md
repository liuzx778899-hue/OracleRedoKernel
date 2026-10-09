# 2026-10-09 进度复盘与换窗口交接

用户要求先check进度、回顾过度验证/死胡同并评估换窗口。本轮停止新增实现；已在跑的r117测试已结束rc0。未创建新chat，未修改goal状态。goal工具当前返回usageLimited，tokensUsed873435；不是目标完成或用户暂停。

## 当前事实与完整范围

- root `codex/d2-normal-index-restore` HEAD8249b81，当前源码干净；P `lab/out/streamhouse-oracle-gate-worktree` 分支codex/oracle-dictionary-acquisition HEAD75f36ef，P STATUS是当前入口，root STATUS旧快照不可作当前状态。
- D1有限正常链已真实贯通：r109 V5应用6e814887c1708b81cc3ffce220d682e4049a3c3a6a9101e3f7bc3d88d71ced41，可信基线/冻结90–94→46完整事件→RPROD5 target/member/commit46/46/1→控制CP/实际ACK同字节；独立目标/生产/现场未验收。
- D2有限I/U/D、回滚、历史字典及初始水位有真实样本；一般同key/同commitSCN/跨块/ARRAY等正常能力未整体收口。
- D3正式agent有限采集/认证传输与恢复组件有证据；连续在线代际、整条自动增量恢复、持久进度/背压和适用平台未收口。
- D4扩展类型/LOB、D5 RAC/ASM/TDE没有整体正常功能交付。独立评审、约定部署、用户生产验证、现场验收及发布均未完成。不得给无证据百分比。

## 验证复盘与纠偏

最近V3–V6逐轮逻辑确有变化，对应冻结正确性回归有价值；未重复源DML，未反复整套部署/强杀。但小改动多轮重复同一相关测试类、逐组件全量/重复等价比较和长反馈明显过细。r104/r105/r106及r110/r111等应合并后一次相关回归；夹具、命令文件名、Decimal和共享classes等harness问题产生不必要返工。通过证据后只有相关逻辑改变才复验；只重试失败部分。

恢复组件方向有价值，但不能成为永久前置门：V6全PDB候选记录使业务state18,024,589字节/SYS6,850,633字节，仍依赖旧raw做物理范围/pair/maintenance及新owner验证。尚非只读suffix的整体producer，不能删raw。已发布正常链已存在，应集中完成一次自动推进产品路径，再转D2/D4正常功能；D5先明确必要环境和输入，不能静默悬置。低概率并发/多故障/强杀矩阵继续后移。

## 未提交P工作包：保留，不能当已交付

P中pom与OracleBoundedRuntimeConfig、OracleApprovedInput、OracleCommittedInput、OracleControlPublication及相应测试有dirty；新增OracleProducerCheckpoint.java/OracleProducerCheckpointTest.java未跟踪。不可reset/stash/覆盖，不要把这些源码混入文档提交。

本包把producerState路径/SHA、sourceCount/decodedThroughSequence/requiredRawFirstSequence、pending/completed和published boundary放入控制CP，ACK覆盖全文；封存位于目标核验之后/控制提交之前，prior核验要求producer verifier。旧CP重放保持无producer字段。**当前OracleApprovedInput.producer调用prefixCaptured全量重建；仍未用已验证prior producer checkpoint执行extendPrefixCaptured，尚非自动增量闭环。** 封存测试主要是mock/本地文件；新持久文件fsync、实际Linux产品CPACK尚未验证，不能算实库通过。

新本地内核坐标0.1.0-producer-e604ac40b571，SHAe604ac40b5716eba704dd0536822642fd273aeced2c4f1402805a12e6c77f7e9已离线安装，P pom已改但未提交/打包/换装。原产品仍r109 V5。

证据lab/out/d3-stream-runtime-20261009/producer-progress-r116：kernel package/install rc0；初轮39测试1error（Windows夹具路径toRealPath规范差异），其余control16/committed12/ACK8通过；仅改测试夹具路径规范并补ACK字段测试，r117 producer3+ACK9全部通过。共40个不同相关测试通过，未重复control/committed。原失败日志及initial-producer-failure.txt保留。

## 新窗口首轮具体动作

1. 读取本文件、P STATUS、DEVELOPMENT_CLOSEOUT和未提交diff；唯一作者同目录续接，无需新worktree/新branch。不要重新跑r109/r112或源75–94动作。
2. 审视当前producer包：先把prior task/batch/target/CP/ACK验证后的producer凭证用于真正续接，避免新增全量重建；只补关键正常增量判据。若本轮不能贯通，明确卡点，不继续新增状态版本/局部兼容优化。
3. 合并工作包后一次相关回归，必要自有实例一次正常CPACK实证；保存日志、删除固定helper、停库。再推进D2一般正常能力及D4类型/LOB；D5写具体环境缺件和接续路径。低水位仍保守，旧raw全留。

当前测试进程已终止；自有oraredo-19c-lab-20261004 inspect=exited，ork-product-runtime不存在。无待运行集成或需要轮询的任务。建议新开窗口；须由用户明确要求后才创建和移交，旧窗口届时停止写入/自动推进，完整D1–D5目标与禁止自审/自合并保持。
