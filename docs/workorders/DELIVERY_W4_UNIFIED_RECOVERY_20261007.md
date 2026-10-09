# W4 统一历史恢复与完整事件候选

## 2026-10-09 合并恢复接线（开发态，产品尚未换装）

分支 `codex/d2-normal-index-restore`。HistoricalRecovery 的现有产品 public API 现在生成 V2：统一封装 durable pending SYS 上下文和 physical prefix（已闭合图、pending 正反向 pair、完整 undo inventory、生命周期），外部可信 checkpoint SHA、baseline/leaf、两部分来源前缀及 canonical 守卫后直接恢复 typed 状态。V1 读取兼容保留。此次不再先重建物理前缀和 pending SYS 图；关联发现、已闭合 SYS 重放和完整行回放仍使用旧 raw，尚未实现完整 producer 无旧 raw 恢复或删除授权。

并入交接的 CompensationWindowState 包：复用 r84/r85/r86 已通过证据，不重跑。r86 保存 90–93 pending window 1463 字节，续94闭合72 marker，恢复 route72与全读相同；该包仍不独立证明 marker 归属或完全增量关联。

本次相关9项通过（失败/错误/跳过0）。统一入口冻结90–93导出 pending1/completed6，独立JVM续原94得到46完整事件；冻结84导出pending SYS1/completed0，续原85得到2完整事件；两批完整事件列表及batch SHA与strict一致。V1原84状态续85也得到同2事件。V2 checkpoint SHA：业务 `c3b5a182162f6c9e4820b962ba3e05582aae17ad21301590baf8aaa59a0c87d0`，SYS `2b3e6dff1762de059748bd9da1d0e22a082cc3a0d3131444f395bd5ad14d94bf`。证据 `lab/out/d3-stream-runtime-20261009/producer-recovery-r87/summary.json` 与各 command/stdout/stderr/rc；先前夹具SCN小于resetlogs及核验器错leaf失败原文保留，未放宽守卫。

代码交付/针对性验证：通过；产品整合、部署、独立现场验收、发布：待证。源/获取/目标写入/容器动作0，产品仍r48/target46。下一将同一提交树打包接产品恢复注册与目标原子提交/checkpoint/ACK正常链路，再继续闭合字典/行像复用及增量发现。D1–D5完整目标active，ARRAY/类型/LOB/RAC/ASM/TDE与独立评审、约定部署、用户生产验证/现场验收均保留。

kernel SHA256 `593e730b67a2622ac9084eeae837eb64477249efe1946f4c5e4856059fe39ea9`，完整 **328/0/0/0**。新增 HistoricalRecovery public 入口；修改 physical 读取器、完整事件工厂及 RedoBatch 前缀视图。四 class 字节变化、新增一个 class、123 class 不变，无新依赖、源动作、目标动作或产品换装。

`checkpointCaptured` 从可信冻结基线与原始 capture 生成 V1 恢复凭证，绑定 baseline/leaf、完整源 header 前缀、字典 checkpoint、identity checkpoint、每个 pending XID/segment/消费计数、heap/index/marker数量、完整操作摘要与 undo checkpoint，以及前缀已闭合事务的完整身份/操作/图摘要。原始 pairs 的 authority 由源 SHA 与恢复时 raw 重建取得，文件不自行提供可信行值。

`readCaptured` 先核外部可信 SHA、冻结基线/leaf与源前缀；在原始前缀重新执行字典和完整物理门禁，规范凭证必须逐字节相同。完整字典扩展仍核旧 timeline 前缀。随后在扩展 inventory 上恢复 identity/undo，保留前缀已闭合结果，重建已核 pairs 和 association cursor；事务状态循环从 prefixCount 开始，仅处理后缀。前缀字节仍用于 discovery、物理范围审计与补偿路由恢复，不重复送入恢复后的 identity/undo。完整业务行还从原始输入重放，并直接使用该恢复结果的闭合图、live节点、身份与完整历史 schema 生成事件。

实际两个冻结窗口，均 force(true) 后 Runtime.halt(23)、新 JVM 恢复：

- 73→74：前缀 pending XID10.14.2869、begin2743312、一个原始/heap pair，统一 checkpoint SHA `6d99bf83a3ac087e8df305f2cb3230db24b67bb27cd73ecf66092a71edeb000c`。其 identity/undo 字节和此前独立 JVM 恢复的 state 完全相同；恢复后仅送74，第二原始→end2743344闭合；两完整 UPDATE 的全部 stdout 与原完整候选相同，SHA `f9cc40d0286107476e07c985a0e1f9fc8c744213aabb2ca1e9b73bb2f99fcf1c`。
- 70→71：前缀包含两 SYS 提交/四列字典及已闭合业务事务；checkpoint SHA `486299c0e2317cf502ba9d56fd5d80d08c24e48e6bc634159ca1b249b2ade5a7`。恢复保留已闭合结果，接71后输出原两完整 UPDATE/schema四列→三列，全部 stdout 与原 r6 相同，SHA `b65210234362e04aa4d785c568e199cbe03be3c709a6361285c0ca37ede77c3e`。
- 十四实际反例全部 rc1/stdout0：两组各原摘要毒化、重算摘要毒化、错 leaf、缺 pending/closed owner、缺源前缀；另缺74后缀、混用另一窗口 identity、混用另一窗口字典、错冻结基线。完整命令/异常/rc保留。缺后缀仍 INCOMPLETE_TRANSACTION_IDENTITIES:1，不生成部分事件。

[100文件双SHA清单](evidence/DELIVERY-unified-recovery-20261007.json)封存于 `lab/out/unified-recovery-20261007`，前45+86+36+42+88+182逐项未变。所有已过轮次不重跑；数据/secret不上传。

这是有限内核统一恢复候选：字典及业务完整行仍需 frozen raw 前缀重放；未实现无前缀恢复或 pending SYS 字典提交恢复。真实 pending index/savepoint 跨窗恢复、一般并发/相同commit SCN/多块矩阵、主机断电、正式 C++、独立目标及评审/现场发布继续待证。新恢复入口尚未进入 StreamHouse/adapter 的实际任务持久状态、发布/目标/控制 CP/ACK；不能将内核完整事件相同当产品或现场通过。原完整 goal active。

下一主线：指定私有产品消费路径接入恢复凭证的持久状态及完整事件入口，旧产品注册/早V3与冻结70/71仅原样使用，禁止晚SCN补洞；核其 HTTP、原子 target、控制CP/ACK、重复/硬退出恢复及晚失败。真实活跃73/74有独立内核证据，但不能把手工基线冒充产品可信获取登记。若需新产品真实跨窗 fixture，先 proper HTTP V3 获取再一次新动作，不能重放73/74。Oracle 全部原目标完成后才排查 Codex 卡顿并关闭 automation。
