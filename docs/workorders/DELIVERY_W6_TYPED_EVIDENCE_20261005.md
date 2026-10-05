# W6 typed evidence 与未可信绑定拒绝交付

产品代码ddff6aef9a84af1fdac5eed5bc02591dae9df2ea、文档tip f43b66b；后者仅docs变化。草稿[PR #3](https://github.com/liuzx778899-hue/StreamHouse/pull/3)基于PR #2的独立分支，已推送/附到本chat，独立评审PENDING，未合并部署。共享StreamHouse原dirty保留。

## 变更与真实验证

SPI新增OracleBatchEvidence纯数据载体，包含来源八字段、闭合capture journal摘要/字节数/容量与闭合块/sequence/SCN范围、baseline acquisition/schema、对象与物理列、leaf/batch摘要、逐事件ID/payload/完整XID/record SCN范围/paired undo位置/row key。旧4/5参DecodeResult构造器保留；decode→outcome→Facade→gate透传，非零rc/迭代晚失败不输出证据。Oracle未可信绑定（含空batch）明确拒绝，其他引擎携带Oracle证据拒绝；不改legacy ID/LSN，不从options授权public decoder。

产品完整提交树clean verify 704项0失败0错误0跳过；新SPI fb9c443b…以私仓坐标0.1.0-oracle-evidence-ddff6ae绑定，adapter dfcfe38b…/16测试通过。内核96095420…复用，254已通过不重跑。adapter首轮误用不存在key()编译失败源码/日志保全，只修rowKey()、补跑adapter-r2，产品reactor不重跑。旧SPI编译的4/5参字节码在新SPI链接成功、默认证据null；首轮外层比较器把Windows CRLF与LF逐字节比较失败保全，仅恢复比较，成功命令不重跑。

冻结非空/NULL/混合/独立空四组seq44—51，事件2/2/3/0：所有typed字段与既有认证raw CLI/基线获取记录独立零差异；8份capture容量另从首帧原始file header精确核验。JSON roundtrip一致、legacy投影三组逐字节不变；源/位置/payload/capture四派生比较反例拒绝。没有新源/目标数据库动作。

实际产品reactor classes运行decode→Facade→gate，由实验连接器提供二进制内核已核验结果；gate收到相同typed证据，四组均ORACLE_TRUSTED_BINDING_REQUIRED、任务FAILED、事件账本0、target0、checkpoint未推进/源窗口未消费。使用产品测试Mapper与实验连接器，不宣称真实控制库、生产registry或公开decoder通过；options声称授权仍拒绝。旧无绑定Oracle空批次不能靠0事件进入发布。

85文件/14命令双SHA、83测试报告指纹、新SPI/adapter/kernel及当前源码指纹、全部失败原文见[清单](evidence/DELIVERY-oracle-typed-evidence-20261005.json)。大制品/记录在lab/out/release-oracle-typed-evidence-20261005与oracle-typed-evidence-20261005，默认不入Git；重现产品构建须使用完整代码提交ddff6ae，不把文档tip称重建制品来源。

## 剩余门禁

1. 当前所有Oracle gate明确未可信绑定，typed数据本身不是trust。生产registry、source/schema世代与capture范围/唯一消费身份仍未接通。
2. 现有BaselineDecodeGuard读SQL Server cap target规则，不能按其字符串标签比对替代Oracle header/source绑定；需按引擎接入独立守卫。
3. 现有Facade先记发布账本/markConsumed/checkpoint再target。完整Oracle必须按真实target原子commit/恢复凭证→发布账本/水位/ACK次序核验，目标失败与并发竞争不能留下已消费水位；没有该证据不得移除当前拒绝。
4. 历史字典、跨窗活跃事务、独立目标实例、C++实际采集/高压/断电/Windows/ASM及现场发布保持原目标，未关闭。

接续[可信registry与事务顺序派单](DISPATCH_W6_TRUSTED_REGISTRY_TARGET_ORDER_20261005.md)，禁止Luna/子代理；独立人员审阅不能由作者自检代替。
