# W6 产品接口门禁接续执行包

前置已通过：非空/NULL/混合/两回滚前缀及真实独立空capture→私有SPI→专属目标commit/checkpoint/ACK。入口DELIVERY_W7_EMPTY_CAPTURE_TARGET_20261005与STATUS。原产品shared checkout保持只读，已有dirty不得混入。禁止Luna/子代理。

## 已确认的不兼容

当前StreamHouse实际源码HEAD67bfb775…、SHA见空batch交付清单：EventEnvelope缺少Oracle typed source/position字段；PublishGateService.checkEnvelope要求v2:<hash>及server/database，SQL LSN列表用List.of（null会抛异常），INSERT before/DELETE after要求null。当前独立OracleBatchProjection的oracle1 ID、null源/LSN、空map与这些规则不一致。Java二进制链接成功和目标写入成功不等于产品语义gate通过。

接续证据：[实际gate/空侧交付](DELIVERY_W6_NULL_SIDES_PRODUCT_GATE_20261005.md)。第1项已实际编译并用三组真实输入复现；第3项已做最小空侧修复及新目标验证。第2/4/5项仍待实施。产品checkEvents还有跨事件recordLsn排序检查，不能只修checkEnvelope；Oracle事件按commit顺序发布时，paired undo物理位置可能回退，不能将其塞进SQL LSN比较器。现有ClosedIdentity只有完整XID/container与begin/end record SCN，没有begin/end物理RBA；缺字段保持缺失，不从SCN伪造RBA。新契约须分别定义事务提交顺序与事件物理定位，精确绑定可信batch/schema/baseline，再由Oracle专属验证器审核；SQL Server分支维持原语义。

## P0 执行顺序及判据

1. 在隔离目录冻结产品公开SPI/gate源码及必要binary，保留源码/制品SHA和实际HEAD。用真实冻结Oracle batch复现当前公开gate拒绝/异常，完整rc和原因落盘；不写共享checkout，不绕过public decoder。
2. 形成最小引擎位置契约及发布gate变更：Oracle sourceBinding、完整XID、pairedUndo Position、实际SCN范围及batch/schema/baseline摘要留类型化字段；transport issueSeq与redo位置分别验证。真实server/database只能来自可信源登记，不由输出器猜；null legacy LSN允许Oracle专属验证器审核，不能伪造SQL格式或无条件放过null。
3. 操作不存在侧遵循公开null契约，NULL列仍在存在侧map内保留null。私有目标writer与输出工具须同步兼容，旧完整像/幂等摘要不得漂移；SQL Server现有gate保持原拒绝语义。
4. 生产输入注册绑定实际可信source/schema/baseline acquisition、capture闭合链、哈希、固定范围、字典generation及唯一消费身份；任何授权选项/编辑相邻JSON不能启用public decoder。所有完整事件先缓冲，晚失败零输出。空batch也校验控制覆盖、target receipt和前沿。
5. 经过范围/字典安全门禁后，再真实产品pipeline目标/发布账本/checkpoint/ACK与同源重复、跨scope串源、错位置、错NULL、缺marker/凭证负例；完整C++真实Oracle采集另列HIGH门禁。仅Linux实验Pythoncollector不满足C++单进程产品要求。

交付：最小接口方案、可复现失败原文、窄代码/针对性验证、独立审阅凭据、六列状态和安全提交推送。结构、private adapter与单实例专属target不升级为产品/部署/现场/GA；不足信息限定支持并fail closed。源旧DML不重放，已通过旧强杀不重跑。后续实现优先推进真实产品兼容，不扩无关UI或自动配置源数据库。
