# W5 字典恢复凭证与完整事件候选

kernel SHA256 `b35dcdecf1dd528cd6bc1121499d86dbceb4856cae6265eae4b63a08cb13e208`，完整测试 **322/0/0/0**。生产修改仅 ArchivedDictionaryHistory 与 CapturedHistoricalHeapBatch：增加源绑定字典 checkpoint、原始输入重建核验和完整事件 readRestored 入口。四 class 字节变化，其中 Context 仅声明行移动造成调试信息变化，源码行为未改；120 class 不变。无新运行时依赖。

checkpoint 的 V1 规范字节保存可信基线 SHA、所有源 header 摘要及逐提交摘要；提交摘要覆盖完整 identity、原始 undo、行/索引/control 数量以及完整 SYS 行值。恢复要求外部可信 checkpoint SHA、原冻结基线及完整原始 capture 前缀。先核摘要/基线/源前缀，再由 raw 重建完整历史，核对前缀全部提交与状态，规范字节必须完全相同。跨前缀边界未闭合 SYS 事务拒绝，不接受调用方自填 schema、漏提交、未知来源或新增后缀冒充旧状态。完整事件仍通过原 physical graph、完整行、schemaAt、位置归属、event ID/payload 门禁。

独立 JVM 实验仅重读冻结输入，未重放源事务：

- 70 前缀两笔完整 SYS 提交、四列 schema，checkpoint SHA `28beb8a4b049827990e4f1f580423eb5b3b9e03804eb2d808b0a8790c835b701`。force(true) 后 Runtime.halt(23)，新 JVM 接原 71，用恢复入口产出两完整 UPDATE、schema 四列→三列。全部 stdout 与原 r6 完整事件逐字节相同，输出 SHA `b65210234362e04aa4d785c568e199cbe03be3c709a6361285c0ca37ede77c3e`。
- 73 前缀没有目标 SYS 提交、三列 schema，checkpoint SHA `7dac38b1f9d35b2d9b8dc253fef34d924f610255d05b72478d0c196e169ce57e`。同样持久后硬退出，新 JVM 接原 74 完整两 UPDATE，同 XID10.14.2869/begin2743312/end2743344；全部 stdout 与此前完整候选逐字节相同，输出 SHA `f9cc40d0286107476e07c985a0e1f9fc8c744213aabb2ca1e9b73bb2f99fcf1c`。
- 两窗口各四反例：保留原摘要毒化、毒化后重算摘要、追加伪造提交、缺最终换行；另三个历史反例：漏真实提交、漏源前缀、错冻结基线。合计十一项均 rc1、stdout 零字节，完整命令/异常原文/rc 保存。

[86 文件双 SHA 证据](evidence/DELIVERY-dictionary-resume-20261007.json)封存于 `lab/out/cross-window-dictionary-20261007`；前36+42+88+182文件逐项复核未变。构建、正例和反例均已通过，不重跑。0源动作、0目标动作、0产品换装，不上传原始数据或 secret。

这是依赖原始前缀重放的字典恢复凭证，不是无需前缀的增量 SYS 状态恢复。业务 identity/undo checkpoint 仍为此前分离入口，本次事件工厂重新执行完整 physical/row 门禁，不能称三者已统一恢复；未证明 pending SYS 恢复、跨发布产品恢复、ACK、主机断电、一般 DML、独立目标或现场发布。原完整 goal active。

下一主线：统一 identity/undo/字典与业务行恢复入口，明确已处理源前缀与后缀边界及完整事件凭证，在同冻结73/74验证续接只处理后缀，再接指定私有 StreamHouse 的跨窗口发布/目标/控制 CP/ACK。正式 C++、一般矩阵、独立目标、评审/部署/现场/发布仍保留；Oracle 全部目标完成后才排查 Codex 卡顿。
