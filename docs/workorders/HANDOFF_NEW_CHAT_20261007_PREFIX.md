# 新chat接续：D1产品活跃前缀恢复

交接同步结果：root交付cd90e04、P反馈9e7775a均本地已commit且clean；两个仓库首次推送与一次重试均GitHub Internal Server Error拒绝，不能声称已远端同步。重试完整原文及rc保留lab/out/prefix-handoff-publish-20261007/root-retry.txt、product-retry.txt；首失败Request ID分别560F:BA21E:1413C2:1BC2D7:6AC660BB与6A0B:11810C:145029:1C0392:6AC660BB，时间2026-10-07T15:09:49Z。新chat先核remote/local差异，在服务恢复后推指定root/main与P分支，不force、不改他人分支。此故障不阻断本地D1开发。

原完整goal不缩减：严格零侵入raw→可信事务/历史字典→完整事件→StreamHouse→目标原子提交→CP/ACK→独立验证/现场/发布；全部Oracle目标后查Codex卡顿并关闭oracle-redo。禁止Luna/子代理。用户授权由当前chat自行决定新开chat时机；切换后旧chat只历史，不并行写入。

先读根AGENTS、STATUS、DEVELOPMENT_CLOSEOUT_20261007、本文件与DELIVERY_W6_PREFIX_REGISTRATION_20261007；旧20261006交接只作历史。共享D:/work/vibecode/StreamHouse只读；产品唯一可修改P=D:/work/vibecode/OracleRedoKernel/lab/out/streamhouse-oracle-gate-worktree，branch codex/oracle-dictionary-acquisition。读P的AGENTS/产品方案/开发流程。PR13 draft依赖12，独立评审PENDING；接续要attach PR https://github.com/liuzx778899-hue/StreamHouse/pull/13 。不要生成子代理。

当前代码root0c48602/P217e5cd；新候选产品04744751250fd7cfadc9669647f5c2d20bee5de8eb68ab5e4aa88ee87520820f，内核61288022bcf9d1cfe860a436387fe0acda6371cd7de18732dea5e6abd8c74f1a，330/760通过。代码提交后还有交付/交接提交，以git HEAD实时核。已通过冻结轮次不重跑。封存前缀两目录不可追加或改写；新验证另建输出目录。

已完成显式认证POST /api/v1/oracle/prefixes/9101/checkpoint服务端生成状态、控制库登记、GET核来源。不是自动PipelineFacade pending路径。真实DB失败保留孤儿文件/登记回滚，解除后复用/审计仅1，重复及SIGKILL137重启GET同字节，毒化拒绝/精确恢复；事件/任务/窗口/批次/发布门/ACK均0。helper ork-prefix-runtime-20261007停止143；端口37267/控制ORKPFXCTL_20261007保留，不重建。共享旧helper ork-oracle-http-20261005不碰。专属source oraredo-19c-lab-20261004运行，不停止/重启。

立即下一步（有序执行）：

1. 修复OraclePrefixRecoveryService异常保全：当前catch只记通用reason/id，底层SQL cause未存。按现有产品失败留证惯例最小实现，秘密不泄露；同件真实登记故障核完整原始原因。P新实现要独立评审，不自评通过。
2. 在新实验目录复用原基线/原75/76，建立prefixBindingId=9101引用的完整APPROVED_BOUNDED绑定；实际HTTP任务→两完整UPDATE→目标账→控制CP→ACK独立对账。未曾执行，不能把内核两事件或GET恢复当产品完整发布通过。
3. 补真实并发锁/两进程和三控制库同场景；再按需求将显式API接自动pending窗口流程，pending不发布、不消费推进；闭合后统一完整发布，崩溃/重试不能漏重。
4. D1闭合后依次D2一般DML/事务/SYS/index矩阵，D3正式C++采集，D4类型LOB，D5 RAC/ASM/TDE；五包均尚未整体关闭，后续部署/生产验证用户参与。

原可信输入A=lab/out/product-active-20261007，只读：acquisition101320533446070272/SCN2773486，snapshotSHA9a99c587d5f21eaaec42eb6554b18bffe5e9eba7d05950132136a6c1556d55cc。原登记在ORKACTCTL_20261007，authority owned-active-acquisition-20261007，source oracle-v3-HIST。capture/sequence-75.oracap SHA44c0d201b7e9a8ca29977ef7ca1795f14703e72929f1a76f5c6fe3f5fc3a95ba；76 SHA自己读manifest，不猜。一次XID10.0.2959两UPDATE已经结束，禁止重放或晚SCN新获取回填。基线row0/history-product-restored/anchor，heap/index75185/75186，leaf50342539。原active.state在lab/out/product-active-marker2-20261007，SHA7d6eef2a完整见交付。新增根HistoricalRecovery.prefixCaptured返回checkpoint/pending/completed；raw前缀仍必需，不当免raw恢复。

prefix登记源码P: baseline/OraclePrefixRecoveryService、appdb/OraclePrefixMapper+XML、config/OraclePrefixRecoveryConfig、web/OraclePrefixRecoveryController；OracleInputBinding.Recovery增加prefixBindingId互斥旧path/hash，OracleInputBindingService解析引用成已核路径/hash。路径必须input-directory直接子文件/不可symlink；输出force+atomicmove+目录force，先文件后DB，失败孤儿按字节核复用。SQL更新/成功审计同事务。

实验驱动lab/prefix_runtime19.py可作新驱动参考，不在封存目录执行。新产品发布可参考lab/recovery_http19.py与lab/verify_recovery_http19.py（旧70/71场景，仅参考），投影probe lab/AcquiredHistoricalProjectionProbe.java用raw/baseline产独立预期。绑定capturePaths=/inputs/sequence-75.oracap和76，acquisitionId原ID，recovery={prefixBindingId:9101}；目标新自有前缀SHPX7五表还没建立，ROW seed0/history-product-restored/anchor，不误用旧history-v3-restored。此来源identity字符串字段顺序见旧驱动；schemaSHA9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0。

运行秘密A/admin.secret.json、runtime.secret.env；新控制秘密lab/out/product-prefix-runtime-20261007/control.secret.json，均禁止打印/哈希/提交。可在新runtime目录重用该控制库及原readonly挂载，把新输出目录设置为可写，旧prefix目录readonly，或者新拥有控制库复制原prefix登记（完整metadata核等）+挂原state。切勿改封存state路径/hash。原停止helper不能直接在封存输出目录产生新logs/acks；要新helper/目录并核资源归属。

工具：Java17 D:/work/Java/jdk-17.0.7；Maven D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd；Python C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe。正式制品来自完整commit树，不上传secret/lab大样本；保留他人dirty，指定路径stage，不reset/stash/add-A。

迁移后建立同完整goal，自动化oracle-redo目标指向新chat，每小时仅实质交付/失败/需要行动通知；额度未恢复或工作在运行保持安静。账户ordinaryUsageAllowed已恢复，旧goal工具usageLimited不可当新阻断；新chat实时核额度。
