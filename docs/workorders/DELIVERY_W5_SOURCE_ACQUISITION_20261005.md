# adapter 实际只读源快照获取（2026-10-05）

代码83cb49f92d8ba07f87f81000cd813afe5707397c新增OracleSnapshotAcquirer，移出“只有实验脚本可以取得基线”的依赖。源码在adapter，内核不增加数据库运行时依赖。完整模块clean verify **19/0/0/0**，kernel96095420/SPI fb9c443b复用，内核已接受254轮次不重跑。候选adapter SHA256 `50feffa8494a9225e48a8886752b071918e9ae50d3373c0952c6add398cb3b35`；产品仍绑定旧8de94aae坐标，未替换、未开放decoder。

## 获取与信任边界

接口只接受调用方提供的fresh source JDBC连接、严格大写标识符Scope及外部预期source八字段。代码只有SELECT：实际CDB身份/PDB/compatibility/字符集、固定SCN、heap/index逻辑与data对象、物理列、同SCN ASOF完整行和行数、查询后来源/对象/物理列再次核对。当前profile为19.0.0/AL32UTF8、NUMBER/VARCHAR2、连续segment/internal、非虚拟列及非分区normal index；unsupported明确失败。没有LogMiner、diagnostic dump或事后SQL真值事件输入。

输出为已有FrozenHeapBaseline格式的完整字节、实际SCN与整个获取结果SHA。Acquisition仅由真实获取方法创建，snapshot getter防御复制；不从旁边可编辑manifest读取“可信SHA”。调用方必须信任并认证source连接及获取进程，本接口不解决来源通道认证、持久签发authority或服务器登记授权，不自动升级APPROVED_BOUNDED，更不自动批准生产发布。

查询前后相等只证明这些观察点，没有证明历史SCN区间无DDL/ABA；物理字典与ASOF行查询不是一个通用历史字典事务。完整快照先缓冲，晚schema漂移/查询失败不返回Acquisition；本轮没有真实并发DDL漂移实验，仅单位测试覆盖晚物理列变化的拒绝。

## 实际独立验证

| case | 获取SCN | 快照SHA256 | 行/物理列 |
|---|---|---|---|
| SNON | 2480910 | fbde75fed7e2a43007465dfcf222b878b4b5a93067f84131ac0ab482eb603445 | 1/3 |
| SNUL | 2480915 | 8eff9693a1a34b09fa24b37d136e0d38d06eea4d97aa6b4fbd1239832ef331e0 | 1/3 |
| SMIX | 2480920 | f0e18e2f8d4c3499cc862a4c0de15540f41343ab95a0e620e563d627c0eb033f | 1/3 |

三次真实JDBC取得既有fixture当前SCN完整快照，原内核导入通过，连接关闭及返回字节防修改通过。独立SYS只读ASOF查询使用DUMP(...,16)而不是获取器的UTL_RAW方法，核对每个值字节、NULL、物理rowid、列映射/隐藏列、heap/index及全部source字段；三组零差异。四真实拒绝为错source、既有事务、错index归属、无表SELECT/FLASHBACK权限，均无快照文件/连接关闭。异常stdout及命令rc留原件，拒绝不是完整DML通过。

仅为实验创建专属只读账号ORKACQ_20261005及最少测试读取权限；这是源端实验账户/权限DDL，**不能算生产零配置证明**，产品代码没有grant或配置fallback。生产路线依赖既有获授权读取权限，不具备就拒绝。本轮源业务DML0、业务表DDL0、logging变更0、源重启0；NO/NO及旧表11/blocking-closed/new-note前后字节不变；所有Java helper退出。

当前SCN晚于旧冻结seq44—51，三新快照不能用于重建那些旧DML；本轮未重放旧源操作，没有新CDC事件、目标写入或发布登记。旧批准基线/目标/制品保持。

证据目录ignored `lab/out/oracle-snapshot-acquisition-20261005`，1872非秘密文件双SHA（含隔离m2缓存，不以文件数计验收）；实际执行前probe classes/source及adapter/kernel/SPI/driver SHA封存，其他lab driver当前SHA为执行后记录，不冒充执行前。原始行、凭据和大制品不上传；[安全摘要](evidence/DELIVERY-oracle-source-acquisition-20261005.json)。

代码交付通过｜针对性验证通过（有限实际获取）｜集成进行中（内核导入通过，产品可信获取/登记尚未接通）｜部署未开始｜现场待证｜发布未开始。独立评审PENDING，goal active。接续[可信获取控制器执行包](DISPATCH_W5_TRUSTED_ACQUISITION_CONTROLLER_20261005.md)，不以本接口替代生产字典/持续采集/消费者租约/独立目标/现场发布。
