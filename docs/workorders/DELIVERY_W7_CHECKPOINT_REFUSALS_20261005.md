# W7 已发布checkpoint/ACK恢复拒绝门禁

生产ec3b430及6f06247e…adapter/ea9f58c6…kernel/SPI不改；lab2f36394f915e611c76f3626a9b4f1a03d446eb15。原15/245测试和强杀轮次不重跑，[双次SHA清单](evidence/DELIVERY-oracle-checkpoint-refusals-20261005.json)。使用上一轮两个已独立验收目标CPBE（3/3/1）与CPNO（3/5/2）的checkpoint/ACK实验副本，原消费文件留原目录不改。

五已发布状态输入拒绝：实际mounted fixture历史sequence42不可用、baseline不可用、错误获取回执SHA、checkpoint已发布full但registry仅prefix、已发布prefix但registry仅full。分别真实Java rc1/stdout0；保留全部checkpoint/ACK及证据文件SHA/集合，独立target快照不变，不从当前源行倒推，不补授权或忽略旧注册输入。

六实际target控制元数据反例在专属CPBE目标条件修改且SQL%ROWCOUNT=1：删除full commit、删除ordinal2 member、payload SHA冲突、commit eventCount冲突、source binding冲突、schema SHA冲突。已有checkpoint/ACK下全部rc1/stdout0，消费状态完整保留，消费者对破坏目标不做补写，target破坏前后完整行集合相同。明确缺commit返回ORACLE_TARGET_ACK_WITHOUT_COMMIT，而不重放业务DML掩盖已发布前沿。各项条件恢复之后全部业务行/审计/成员/commit/IDENT回到原行集合，再消费各0新增DML、checkpoint/ACK字节稳定。

最终CPBE及CPNO恢复健康，LGWR5601保留、所有本轮helper退出。0新增源/目标业务DML；六受控target元数据修改与恢复为专属同实例实验写入，不能宣称0数据库写。Oracle source配置无修改动作，raw保留不删除；没有重新创建源fixture/归档/采集/强杀。

有限固定baseline/leaf闭合窗口的checkpoint恢复负门禁已补齐；不等于生产可信registry来源认证、任意活跃跨窗、历史字典、实时高压、断电、全产品/部署/现场/发布通过。接续解析主线：[完整INSERT/DELETE capture执行包](DISPATCH_W3_FULL_DML_CAPTURE_20261005.md)。现有生产公开decoder/events继续拒绝，未通过功能仍保留门禁。Oracle实际窗口/位置SPI与可信字典仍要落地，不把当前三UPDATE链路当完整DML支持。
