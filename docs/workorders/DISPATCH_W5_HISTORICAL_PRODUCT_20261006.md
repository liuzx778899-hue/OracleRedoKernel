# W5 历史完整事件接入产品与目标状态协议

接续 [历史业务完整行交付](DELIVERY_W5_HISTORICAL_ROW_SCHEMA_20261006.md)，全目标保持active，禁止Luna/子代理，Codex最后查。源seq68/69 ADD/DROP/两UPDATE均结束，严禁重放；共享StreamHouse checkout只读。

1. 检查私有worktree `lab/out/streamhouse-oracle-gate-worktree`、PR#13及依赖#12状态与AGENTS，保持他人修改。新历史内核kernel3ccca289/299、实际V3 baseline SHA226c9322已有189双SHA；旧产品efd068f/737不是新候选构建证据。adapter e095822b/22不能冒充已安装产品。
2. 核实际V3获取代码、可信账/回执持久化与恢复，把真实历史factory产生的逐事件schema、完整hidden/新增列、raw位置、XID、SCN、event ID/payload接入adapter。旧固定schema路径保留门禁；不得把最终三列当整批schema，不得丢EXTRA_V3以适配旧Batch/Window/Checkpoint协议。批次有序schema清单须参与receipt/ack/恢复绑定，不能只改投影而留下单schema凭证。
3. 适当单测验证两schema顺序、字段/NULL、可信获取失败拒绝、错baseline/schema/input、重复与重启持久稳定；隔离Maven版本/制品保留旧件。新候选必要全构建通过后冻结输入进入实际HTTP路由；不得以直接Java调用代替HTTP接入。
4. 专属目标验证两UPDATE完整过程凭证/字段映射/事件顺序；目标原子commit→控制CP→ACK，每次执行关联kernel/adapter/product/input/output SHA、rc、事件数。重复0新增、重启恢复，错误schema/缺原始/history断裂/凭证篡改0目标推进。目标表schema管理与源严格零侵入分开，明确策略；业务字段不可裁剪或偷偷用SQL/dump产事件。
5. 当前专属目标ORKTGT_CMT_20261005是同源实例跨schema，真实产品HTTP通过仍不证明独立目标/现场发布。完整原goal继续活动跨窗/正式C++采集租约/独立目标/类型环境/高压断电/独立评审现场发布；不能将本有界交付等同全目标完成。

完成代码与针对性证据后显式提交推送安全摘要/脚本/源码/测试；所有失败原文及旧封存保留，不提交lab/out/secret，不重跑已通过源实验。
