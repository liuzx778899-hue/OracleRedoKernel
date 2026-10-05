# Oracle 已提交、ACK 待恢复事实（2026-10-05）

问题：实际有界Oracle链的目标与控制事务已经提交，ACK失败后WindowRun.published仍被记为false，审计published=0，掩盖已提交事实。本轮保持事务/门禁/ACK顺序，只修正结果表达。

代码 `dc185655c0eb01d746fc4b4a7a8f1130b8ebfebf` 完整提交树clean verify **728/0/0/0**；app SHA256 `a05e5c65755149b048125803c0445efc13cea39aff803ad02778a055706da95e`。新增跨模块AckPendingException绑定task/batch/source/window，仅在目标核验和控制成功返回后包装ACK失败。Facade严格核对全部身份，published反映控制提交事实，任务仍FAILED，原因及审计为ACK_PENDING。普通目标/控制失败和错身份不能据异常文字升级为published。非DONE空待办恢复重新核验旧目标和控制，再补ACK；ACK再次失败保留原窗口/批次及已提交事实。没有新增SQL或迁移，没有改变public decoder拒绝。

四新增测试覆盖实际端口分类、错批次不采信、空待办重复失败、审计同时保留FAILED与published=1；已有前目标/控制失败测试保留。首轮编译模式变量与既有循环变量重名，rc1完整原文保留；只改变量名并补审计原因区分，最终完整提交树另建通过。内核254/adapter16既有接受轮次不重跑。

## 实际验证

专属新控制 `ORKACK_20261005` 实际23迁移到V730；同专属Oracle实例另一schema的新AKMIX目标五表；服务只在源容器网络loopback37217，无host映射。复用冻结seq48—50批准raw/物理基线，不重放源DML。任务100537634274742272，唯一批次100537635168129024。

首次HTTP提交前预置冲突ACK `{}`：真实目标INSERT→UPDATE→DELETE三完整事件及控制事务成功，随后ACK拒绝。独立SQL为target3/commit1、batch PUBLISHED/window CONSUMED/ledger3/完整CP、task FAILED；实际异步审计为`state=FAILED windows=1 published=1`，SH_FAILCLOSED_RECORD原因ACK_PENDING。原冲突ACK未覆盖。

再次真实HTTP resume时pending为空：重新核验后ACK仍冲突，仍FAILED及published=1；唯一批次不增、目标三事件不增。将冲突文件保全后再resume恢复DONE，持久ACK字节等于原完整CP。DONE重复成功；前后目标身份、完整中间像、成员顺序、commit与最终seedSQL逐字节不变，并与原独立raw/native/SQL证据零差异。所有Oracle evidence、target receipt字段独立核对。三比较反例false-unpublished/false-done/wrong-source均拒绝。

源NO/NO及旧表11/blocking-closed/new-note前后字节不变；仅专属目标/控制fixture DDL与目标业务DML，源业务DML0、源配置0、源重启0。实验服务正常停止。输入与生产制品固定；HTTP driver及其依赖在服务启动/HTTP执行前封存。125个非秘密证据文件双SHA一致，原始数据/口令/大jar仅留ignored目录`lab/out/oracle-ack-pending-20261005`；[安全摘要](evidence/DELIVERY-oracle-ack-pending-20261005.json)。

代码交付通过｜针对性验证通过（有限）｜集成进行中（首次/FAILED空待办ACK事实有限通过）｜产品部署未开始（仅专属实验换件）｜现场待证｜发布未开始。[草稿PR#9](https://github.com/liuzx778899-hue/StreamHouse/pull/9)独立评审PENDING，依赖PR#8，禁止合并/共享服务换装。

本轮没有重跑上一制品的SIGKILL矩阵，不将旧SHA的崩溃证据移算新SHA。DONE复核失败仍走既有异常审计路径，不能把REST返回受理态当后台恢复完成；该异常状态表达继续待证。生产字典/可信基线获取、消费者租约/并发、活跃跨窗、独立目标实例、其他方言完整HTTP、C++持续采集/高压/Windows/ASM/断电、现场及发布均未关闭。goal active。
