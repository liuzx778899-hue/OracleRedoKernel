# W6 有界可信 registry 交付 · 2026-10-05

## 结果与范围

产品代码 `02c9317d6b57d39ff25b51f1fdef476afc117c75`，迁移修正完整提交树 `9d65d58b8c30713b8cb826a24dfd36ee1b6eaf1a` clean verify：707 tests，0 errors/failures/skipped。内核与 adapter 复用前轮已验证制品，不重复254/16项轮次。

三种真实控制库 MySQL 8、SQL Server 2022、Oracle 19c，各 NEW/UPG 路径、SNON/SNUL/SMIX/EMPT 四冻结 raw 输入，共24项通过。UPG从全模块728升729；NEW从空库建立22项迁移；validate及二次migrate零新增通过。真实JDBC批准有界输入记录、MyBatis只读查询、baseline/capture哈希与typed证据及完整envelope摘要匹配；错误consumer/transport/input、缺typed、伪造event、未登记窗口均拒绝。普通options不能授权。

实际 PublishGate 每项仍 `REFUSED / ORACLE_ATOMIC_TARGET_NOT_ACCEPTED`，真实gate记录1、event ledger0；登记匹配不是目标发布许可。公开decoder继续拒绝。没有验证完整Facade、真实产品checkpoint顺序或生产登记生命周期。

## 资源、失败与证据

专属控制容器 `orkctrl-mysql-20261005`、`orkctrl-sqlserver-20261005`，专属Oracle实验容器内仅新建控制schema `ORKCTRLR_NEW_20261005`/`ORKCTRLR_UPG_20261005`；共享三库未使用，源业务DML0，源端未重启、网络未改变。实验控制DDL不算生产零侵入证明。同实例schema不算独立目标实例。

首次Oracle实验密码长度导致ORA-00972，确认schema未建立后修正；含密码原文隔离为ignored secret文件，公开失败文本保留脱敏原因。第一次三库驱动发现app/gateway重复V728，rc1；保留失败驱动与迁移，改用全局唯一V729后只接续未通过项。第一次707测试绿不替代三库迁移验收。

[安全摘要](evidence/DELIVERY-oracle-registry-20261005.json)关联本地 `lab/out/oracle-registry-20261005/seal.json`：181文件双次SHA。全部secret文件和m2排除，不上传raw输入、密码、制品或依赖。准备脚本补入实际采用的专属bridge地址发现，未重复创建资源；该脚本补正不是新增执行轮次。

## 六列与下一门禁

代码交付通过；针对性验证通过（有限输入）；集成有限通过（真实registry/guard/gate拒绝）；部署未开始；现场待证；发布未开始。独立评审PENDING，候选不可合并部署。

下一步按 [P0派单](DISPATCH_W6_TRUSTED_REGISTRY_TARGET_ORDER_20261005.md)实现Oracle专属目标commit凭证先于账本/checkpoint/ACK，覆盖失败、空批、恢复、并发独占及损坏拒绝。历史字典、跨窗活跃状态、完整产品、独立目标实例、C++实时采集和现场发布门禁仍待证。长期goal active。
