# 产品 producer 进度接线候选 · 2026-10-09

用户选择继续当前窗口并及时调整。本包集中补产品正常恢复入口，不新增恢复状态版本，不重跑源 DML 或已通过的冻结内核实验。

StreamHouse 分支 `codex/oracle-dictionary-acquisition`：产品入口使用 `extendPrefixCaptured` 推进 V3–V6 恢复状态。前一发布的 producer 引用只有在 task/batch/window/ledger/target/控制 CP/ACK 全部核验后才返回并用于下一批；客户端 offset/count 不能授权。目标核验后、控制提交前封存 producer 文件，控制 CP 包含完整收据，ACK 覆盖全文；封存失败阻断控制进度。旧 CP 重放保持原字节。

收据包括路径/SHA、源数、解码至序号、保守原始输入低水位、pending/completed、已发布事件数与前缀摘要。当前低水位仍取首个源输入；旧 raw 全部保留，尚无删除授权或背压闭环。

证据入口 `lab/out/d3-stream-runtime-20261009/producer-progress-r116/`：

- r118/r119 四类共 40 个不同相关测试通过（control16、producer3、ACK9、committed12）。r118 新测试空收据被正确拒绝，失败原文保留，仅修夹具并复验 committed 类；首轮 Windows 夹具路径失败也保留。
- r119 产品入口冻结探针 rc0：90–93 原 V6 状态续接 94，46 完整事件/pending0；状态与原 r112 扩展结果同字节，SHA `f4009f90ad408b6d74630beba9d99b8162e8dbb9e99cd7f2688add8396e29f01`。产品读取投影一致，producer 收据重放一致；发布前缀 SHA `64d1766983d552f6282912d276a9d0cdcfdbc9ee134218493a1ba681c07bde9c`。探针使用真实产品入口/内核与冻结输入，绑定服务替身，无数据库/目标/ACK 写入。
- r120 必要 reactor 离线打包 rc0，不重复测试。应用 SHA `19725963ac6146846268f73bf35355ba63aa8d38d5fd6ae7d03cad521406e2bf`。内核坐标 `0.1.0-producer-e604ac40b571`，SHA `e604ac40b5716eba704dd0536822642fd273aeced2c4f1402805a12e6c77f7e9`。

代码、针对性回归与冻结产品入口通过；本包尚未换装、实际 Linux 持久文件封存/产品 CPACK/后一任务自动引用尚未集成验证。当前已运行产品仍 r109 V5；独立目标/评审、约定生产部署、用户生产验证、现场验收与发布均待证。

下一步只做一次集中正常产品集成，验证封存、实际 CP/ACK 和后一任务引用；通过后转 D2 一般事务与 D4 类型/LOB。D5 明确 RAC/ASM/TDE 输入及必要环境，完整 D1–D5 目标保留。极端并发、多故障组合和强杀矩阵继续后移。
