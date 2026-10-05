# 实际 HTTP 混合 DML 与两处进程强杀（2026-10-05）

本轮在专属实验环境执行实际 Spring Boot HTTP 链：冻结 seq48—50 原始 capture → 已批准可信基线 → 内核完整三事件 → 目标原子提交 → 控制事务 → 持久 ACK。没有重放源 fixture DML、改变补充日志或重启源库。

产品审计修复代码提交 `411cd645703464c234bed527a975599e3af85322`，完整提交树 clean verify **724/0/0/0**，同件 app SHA256 `1583a759c53f7423b61e9d7e57412586283f268503ab18c1d92ae880645fdd2c`。内核96095420及adapter8de94aae复用，既有接受轮次未重跑。新增测试证明 Oracle 发布失败即使无 legacy target result，也持久写入 SH_FAILCLOSED_RECORD；不改变发布门禁或 NOWAIT 保护。

控制schema为新建 `ORKHTTP_20261005`，实际23迁移到V730；目标为同一专属Oracle实例另一schema的HTMIX五表。服务仅源容器网络内loopback37216，无host映射。真实任务100528677405655040，最终发布批次100532952412258304。

| 判据 | 实际证据 |
|---|---|
| 目标提交前强杀 | 独立v$session确认目标INSERT等待专属目标表锁，目标0/0，控制ledger0/window PENDING/CP NULL，无ACK；应用SIGKILL退出137，解锁后仍0/0 |
| 目标提交后、控制提交前强杀 | 独立v$session确认控制SH_CAP_WINDOW UPDATE等待，目标APPLIED3/COMMITS1，控制ledger0/window PENDING/CP NULL，无ACK；应用SIGKILL退出137，目标3/1保留、控制回滚 |
| 跨进程恢复 | 同件应用重启，通过真实认证HTTP resume；最终DONE、目标APPLIED3/MEMBERS3/COMMITS1、控制ledger3/PASS1/CONSUMED及完整CP/ACK |
| 重复与语义 | DONE重复实际异步执行结束；目标与控制完整SQL快照字节不变；三事件INSERT→UPDATE→DELETE完整中间像、成员顺序、身份/摘要、最终seed，与既有独立raw/native/SQL对照零差异 |
| 独立比较反例 | wrong-operation、checkpoint-source、ACK conflict三派生比较均检出，未修改真实目标 |

ACK SHA256 `7223e15d1a371de31cf20d58500a28e82232cc3463a13420334f72959787353c`，字节等于数据库完整checkpoint。两次强杀均非OOM，StartedAt不同；实验锁只施加在专属目标/控制元数据，不插桩产品代码。完成后混合HTTP服务正常停止，源Oracle保持运行；源补充日志NO/NO、旧表11/blocking-closed/new-note不变。

失败追加保留：SQLPlus长行SP2-0341但rc0导致绑定未插入（窗口已插入）；仅分行补绑定，不重复窗口或源DML。首个观察器误用表名rc1。身份行锁触发既有FOR UPDATE NOWAIT安全拒绝，不能当强杀边界；改用专属目标表锁才实际证明INSERT等待。首次源码编码读取失败、控制secret复制到自身失败保留；首次构建启动早于代码提交，仅作过程记录，最终完整提交树构建单独执行通过。原723制品及初始失败证据保留。

证据在ignored `lab/out/oracle-http-20261005`（15文件）与`lab/out/oracle-http-mixed-20261005`（266文件），双次流式SHA一致，秘密文件不封存/不上传。当前lab脚本SHA为执行后封存，不冒充执行前指纹；应用同件SHA、完整提交树、两实际进程状态、实库等待和独立快照构成运行证据。

六态：代码交付通过｜针对性验证通过（有限）｜集成验证进行中（本HTTP混合/两SIGKILL通过）｜产品部署未开始（专属实验换件已执行）｜现场待证｜发布未开始。[审计修复草稿PR#8](https://github.com/liuzx778899-hue/StreamHouse/pull/8)，独立评审PENDING，不合并、不换装共享服务。

剩余：其他控制方言完整HTTP、独立目标实例、生产可信字典/基线及历史schema、消费者租约/并发、活跃跨窗、真实C++持续采集、高压/Windows/ASM/断电、现场及发布。Oracle ACK失败时WindowRun.published口径仍需与已提交数据库事实区分；本轮只补失败留痕，不宣称该问题关闭。长期goal active。

[安全证据摘要](evidence/DELIVERY-oracle-http-crash-20261005.json)。
