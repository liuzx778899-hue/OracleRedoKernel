# W6 Oracle 独立只读提交核验 · 2026-10-05

## 实现与判据

目标writer新增 `verifyCommitted`：fresh autocommit独立连接，一条Oracle SELECT连接IDENT、COMMITS、MEMBERS、APPLIED，同一statement一致性视图验证source八字段、target/schema/batch、事件数、有序成员、payload/完整before/after/operation。不执行DML、锁、commit或ACK；缺少/损坏凭证拒绝。

消费checkpoint对已提交registry范围使用只读核验；新apply返回后再开独立连接核验，成功才加入已提交集合并落checkpoint/ACK。零事件仍要求实际commit且不得有成员。公开decoder和产品gate仍拒绝，本次未装配产品Facade。

## 实测证据与限制

实现提交 `2d03896`，完整提交树adapter clean verify16项通过，kernel/SPI复用原指纹，不重跑254或产品707项。四真实冻结组SNON/SNUL/SMIX/EMPT，事件2/2/3/0，直接查询已提交专属目标通过；三非空组各12列、空组8列查询结果篡改，共44反例拒绝。篡改仅在lab ResultSet代理返回值，不冒充实际持久化损坏实验。代理禁止connection commit/rollback/setAutoCommit、锁查询、executeUpdate等写入动作。

四新消费者目录从已存在的实际commit恢复，新增业务DML0；重复0且checkpoint字节不变。复制合法checkpoint/ACK后模拟commit查询缺失，四组均拒绝、文件原字节保留。全目标五表独立SQL快照前后完全相同，旧已接受输入/目标不重放。helper退出，源与目标业务DML0，无源配置改变。

首轮反例错误使用无历史ACK的新目录，消费者按首次消费尝试apply，被只读代理拦截，SNON rc1；原驱动、脚本、stdout/stderr/rc保留。修正为已发布恢复语境，只重新核验只读过程与未完成项，没有源/目标DML重放。

[安全摘要](evidence/DELIVERY-oracle-readonly-receipt-20261005.json)关联本地封存；secret及m2排除，文件两次SHA核对。新apply后核验失败的实际窗口、控制库发布失败恢复、完整产品Facade/控制库事务、双消费者跨库独占、独立目标实例尚待验证。此轮恢复核验不替代它们。

代码交付通过｜针对性验证有限通过｜集成有限恢复核验通过｜部署未开始｜现场待证｜发布未开始；独立评审PENDING。下一步接入Oracle专属目标端口和产品编排，目标可复核commit先于账本/消费状态/checkpoint/ACK，保持SQL Server既有行为。长期目标active。
