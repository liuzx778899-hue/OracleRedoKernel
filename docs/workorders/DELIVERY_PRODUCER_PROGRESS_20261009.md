# 产品 producer 进度接线候选 · 2026-10-09

## 后续正常产品集成：通过有限实验

StreamHouse `24895aa`、应用 SHA `19725963ac6146846268f73bf35355ba63aa8d38d5fd6ae7d03cad521406e2bf` 已在专属实例隔离控制库 `ORKPROGRESSCTL_20261009`、目标 `ORKTGT_CMT_20261005.RPROG1` 集成。证据 `lab/out/d3-stream-runtime-20261009/producer-progress-product-r123/normal-published.json`，驱动 rc0。

第一任务 `101953300483780608` 从冻结90–93发布44完整事件，pending1，producer SHA `72f490c25351c2c2bd5f52146e4e239921bff348f9969b8f1846923e6b3d06f2`，实际CP/ACK同字节 SHA `22a36a4da78482c6b439f16c613195ae38f256c47452742c286cb2f4346eae7a`。第二任务 `101953552494342144` 在核验第一任务后使用其 producer 状态续接冻结94，只发布2事件，pending0，producer SHA `f4009f90ad408b6d74630beba9d99b8162e8dbb9e99cd7f2688add8396e29f01`，实际CP/ACK同字节 SHA `c51a649214ac4677135cfac331c1d78da8687db53c096e8dbbb83407d888b743`。两份Linux持久文件都与原r112冻结状态同字节；第二收据完整累计46/completed8/through94/rawFirst90。

目标APPLIED/MEMBERS/COMMITS=46/46/2，ledger46/PASS2，两窗口CONSUMED。逐事件完整前后像、0-based ordinal、eventId、payload摘要及历史schemaSequence均核等，0 mismatch；最终20 anchor。原SHPBL1保持46/46/4、原RPROD5保持46/46/1；源20 anchor、启动前CURRENT95/NO NO，源DML/新获取0。固定helper日志保存、stop/remove均rc0，自有数据库stop rc0、inspect exited，卷保留。

第二窗口GATED等待较长，按同一驱动句柄继续观察至完成，无重启或重放；只读诊断未发现DB锁阻塞，原等待记录保留。本轮不扩展性能排查或故障矩阵。r121投影的摘要尚未按产品canonical规则排序，静态检查发现后仅改探针规则，r122生成正式绑定投影，旧输出保留；无产品代码修复或源实验重做。

本实验绑定由实验脚本登记，任务消费和可信前一producer续接由产品执行；尚不证明正式连续采集/自动登记/低水位背压或平台覆盖。代码、针对性回归、本冻结正常集成及自有实例换装通过；独立目标/评审、约定生产部署、用户生产验证、现场与发布未完成。旧raw仍保留。下一主攻D2一般事务和D4类型/LOB，D5补明确环境与输入；不再以新增恢复版本作为功能前置。

## 候选交付时点记录

用户选择继续当前窗口并及时调整。本包集中补产品正常恢复入口，不新增恢复状态版本，不重跑源 DML 或已通过的冻结内核实验。

StreamHouse 分支 `codex/oracle-dictionary-acquisition`：产品入口使用 `extendPrefixCaptured` 推进 V3–V6 恢复状态。前一发布的 producer 引用只有在 task/batch/window/ledger/target/控制 CP/ACK 全部核验后才返回并用于下一批；客户端 offset/count 不能授权。目标核验后、控制提交前封存 producer 文件，控制 CP 包含完整收据，ACK 覆盖全文；封存失败阻断控制进度。旧 CP 重放保持原字节。

收据包括路径/SHA、源数、解码至序号、保守原始输入低水位、pending/completed、已发布事件数与前缀摘要。当前低水位仍取首个源输入；旧 raw 全部保留，尚无删除授权或背压闭环。

证据入口 `lab/out/d3-stream-runtime-20261009/producer-progress-r116/`：

- r118/r119 四类共 40 个不同相关测试通过（control16、producer3、ACK9、committed12）。r118 新测试空收据被正确拒绝，失败原文保留，仅修夹具并复验 committed 类；首轮 Windows 夹具路径失败也保留。
- r119 产品入口冻结探针 rc0：90–93 原 V6 状态续接 94，46 完整事件/pending0；状态与原 r112 扩展结果同字节，SHA `f4009f90ad408b6d74630beba9d99b8162e8dbb9e99cd7f2688add8396e29f01`。产品读取投影一致，producer 收据重放一致；发布前缀 SHA `64d1766983d552f6282912d276a9d0cdcfdbc9ee134218493a1ba681c07bde9c`。探针使用真实产品入口/内核与冻结输入，绑定服务替身，无数据库/目标/ACK 写入。
- r120 必要 reactor 离线打包 rc0，不重复测试。应用 SHA `19725963ac6146846268f73bf35355ba63aa8d38d5fd6ae7d03cad521406e2bf`。内核坐标 `0.1.0-producer-e604ac40b571`，SHA `e604ac40b5716eba704dd0536822642fd273aeced2c4f1402805a12e6c77f7e9`。

代码、针对性回归与冻结产品入口通过；本包尚未换装、实际 Linux 持久文件封存/产品 CPACK/后一任务自动引用尚未集成验证。当前已运行产品仍 r109 V5；独立目标/评审、约定生产部署、用户生产验证、现场验收与发布均待证。

下一步只做一次集中正常产品集成，验证封存、实际 CP/ACK 和后一任务引用；通过后转 D2 一般事务与 D4 类型/LOB。D5 明确 RAC/ASM/TDE 输入及必要环境，完整 D1–D5 目标保留。极端并发、多故障组合和强杀矩阵继续后移。
